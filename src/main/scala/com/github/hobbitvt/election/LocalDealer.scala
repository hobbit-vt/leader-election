package com.github.hobbitvt.election

import com.github.hobbitvt.election.ElectionDealer.InstanceId

import scala.concurrent.Future
import scala.concurrent.duration.Duration

final class LocalDealer(id: InstanceId) extends ElectionDealer {
  def tryAcquire(id: InstanceId): Future[Boolean]                                   = Future.successful(true)
  def getLeader: Future[Option[InstanceId]]                                         = Future.successful(Some(id))
  def release(): Future[Unit]                                                       = Future.unit
  def close(): Future[Unit]                                                         = Future.unit
  def waitForLeader(w: Duration, l: Option[InstanceId]): Future[Option[InstanceId]] = Future.never
}
