package com.github.hobbitvt.election

import java.util.Base64

import dispatch._
import io.circe.parser
import org.asynchttpclient.Response

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.{ Duration, _ }

/**
 * Election dealer which use consul as leader election algorithm
 * @param consulAddress Where-is-consul config
 * @param path Path to election key
 * @param ttl Consul session live time
 */
class ConsulElectionDealer(consulAddress: String, path: String, ttl: Duration)(implicit ec: ExecutionContext)
    extends ElectionDealer {
  import ConsulElectionDealer._
  import ElectionDealer._

  private val session = new ConsulSession(consulAddress, ttl)

  /**
   * Try to acquire a lock
   * @return True if success acquire a lock, otherwise false
   */
  override def tryAcquire(instanceId: InstanceId): Future[Boolean] = {
    session().flatMap(id => {
      val req = url(s"$consulAddress/v1/kv/$path?acquire=$id")
        .setBody(instanceId)
      Http.default(req.PUT > identity[Response] _).map(rep => {
        val content = rep.getResponseBody
        content match {
          case "true" => true
          case "false" => false
          case _ => throw new IllegalArgumentException(s"Can't parse boolean from $content")
        }
      })
    })
  }

  /**
   * Get current leader
   */
  override def getLeader: Future[Option[InstanceId]] = {
    getLeader(0, 0.seconds).map(_.data)
  }

  /**
   * Wait for changes in a leadership. If nothing change during given duration, returns an actual leader
   */
  override def waitForLeader(
    howLong: Duration,
    lastKnownLeader: Option[InstanceId]
  ): Future[Option[InstanceId]] = {
    getLeader(0, 0.seconds).flatMap(electionResult => {
      val currentLeader = electionResult.data
      if (currentLeader == lastKnownLeader) {
        getLeader(electionResult.modifyIndex, howLong).map(_.data)
      } else {
        Future.successful(electionResult.data)
      }
    })
  }

  /**
   * Release a lock
   */
  override def release(): Future[Unit] = {
    session().flatMap(id => {
      val req = url(s"$consulAddress/v1/kv/$path?release=$id")
      Http.default(req.PUT > identity[Response] _).map(rep => {
        rep.getStatusCode match {
          case 200 =>
          case _ =>
            throw new Exception(s"Can't serve request by consul, ${rep.getResponseBody}")
        }
      })
    })
  }

  /**
   * Close a dealer
   */
  override def close(): Future[Unit] = {
    session.close()
  }

  private def getLeader(modifyIndex: Long, howLong: Duration): Future[ElectionResult] = {
    val wait = howLong.toSeconds + "s"
    val req = url(s"$consulAddress/v1/kv/$path?index=$modifyIndex&wait=$wait")
    Http.default(req.GET > identity[Response] _).map(rep => {
      val maybeJson = parser.parse(rep.getResponseBody)
      val maybeIndex = maybeJson.toOption.flatMap(j => j.hcursor.downArray.downField("ModifyIndex").focus.flatMap(_.asNumber.flatMap(_.toLong)))
      val maybeSession = maybeJson.toOption.flatMap(j => j.hcursor.downArray.downField("Session").focus.flatMap(_.asString))
      val maybeEncodedValue = maybeJson.toOption.flatMap(j => j.hcursor.downArray.downField("Value").focus.flatMap(_.asString))
      maybeIndex match {
        case Some(index) =>
          (maybeSession, maybeEncodedValue) match {
            case (Some(_), Some(encodedValue)) =>
              val value = new String(Base64.getDecoder.decode(encodedValue))
              ElectionResult(index, Some(value))
            case _ =>
              ElectionResult(index, None)
          }
        case _ => throw new Exception("Can't retrieve leader from consul: " + rep.getResponseBody)
      }
    })
  }
}

object ConsulElectionDealer {
  case class ElectionResult(modifyIndex: Long, data: Option[ElectionDealer.InstanceId])
}
