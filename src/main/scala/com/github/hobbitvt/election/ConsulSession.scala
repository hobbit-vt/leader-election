package com.github.hobbitvt.election

import com.typesafe.scalalogging.LazyLogging
import io.circe.syntax._
import io.circe.{ Json, parser }
import monix.execution.misc.AsyncSemaphore

import scala.concurrent.duration.Duration
import scala.concurrent.{ ExecutionContext, Future }

/**
 * Represents session in consul
 */
class ConsulSession(consulAddress: String, ttl: Duration)(implicit ec: ExecutionContext)
    extends LazyLogging with (() => Future[String]) {
  private val lock = AsyncSemaphore(1)
  @volatile
  private var maybeId: Option[String] = None
  @volatile
  private var closed: Boolean = false

  /**
   * Obtains session id
   */
  override def apply(): Future[String] = {
    lock.greenLight(() => {
      maybeId match {
        case _ if closed => Future.failed(new UnsupportedOperationException("Can't retrieve already closed session"))
        case Some(id) => Future.successful(id)
        case None =>
          val idF = create()
          idF.foreach(id => {
            scheduleRenew()
            maybeId = Option(id)
          })
          idF
      }
    })
  }

  def close(): Future[Unit] = {
    lock.greenLight(() => {
      closed = true
      maybeId.map(destroy)
        .getOrElse(Future.successful(()))
    })
  }

  /**
   * Clear an internal session
   */
  private def clearSession(): Future[Unit] = {
    logger.info(s"ConsulSession $maybeId was destroyed.")
    lock.greenLight(() => {
      maybeId = None
      Future.successful(())
    })
  }

  /**
   * Create session in consul
   */
  private def create(): Future[String] = {
    val body = Json.obj(
      "LockDelay" -> "0s".asJson,
      "TTL" -> s"${ttl.toSeconds}s".asJson
    ).noSpaces

    HttpClient.put(s"$consulAddress/v1/session/create", body).map(rep => {
      val content = rep.getResponseBody
      val maybeId = parser.parse(content).toOption
        .flatMap(jValue => jValue.hcursor.downField("ID").focus.flatMap(_.asString))
      maybeId match {
        case Some(id) => id
        case None => throw new IllegalArgumentException(s"Can't parse create payload $content")
      }
    })
  }

  private def destroy(id: String): Future[Unit] = {
    HttpClient.put(s"$consulAddress/session/destroy/$id").map(_ => ())
  }

  /**
   * Renews session id in consul
   */
  private def renew(id: String): Future[Unit] = {
    HttpClient.put(s"$consulAddress/v1/session/renew/$id").flatMap(rep => {
      rep.getStatusCode match {
        case 200 => Future.successful(scheduleRenew())
        case _ => clearSession()
      }
    })
  }

  /**
   * Schedules renew in consul based on ttl
   */
  private def scheduleRenew(): Unit = {
    SingleThreadedTimer.Default.schedule(ttl / 2) {
      maybeId.foreach(renew)
    }
  }
}
