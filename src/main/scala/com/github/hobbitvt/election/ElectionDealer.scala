package com.github.hobbitvt.election

import scala.concurrent.Future
import scala.concurrent.duration.Duration


trait ElectionDealer {

  /**
   * Try to acquire a lock
   * @return True if success acquire a lock, otherwise false
   */
  def tryAcquire(instanceId: ElectionDealer.InstanceId): Future[Boolean]

  /**
   * Get current leader
   */
  def getLeader: Future[Option[ElectionDealer.InstanceId]]

  /**
   * Wait for changes in leadership. If nothing change during given duration, returns actual leader
   */
  def waitForLeader(
    waitForChanges: Duration,
    lastKnownLeader: Option[ElectionDealer.InstanceId]
  ): Future[Option[ElectionDealer.InstanceId]]

  /**
   * Release a lock
   */
  def release(): Future[Unit]

  /**
   * Close a dealer
   */
  def close(): Future[Unit]
}

object ElectionDealer {
  type InstanceId = String
}
