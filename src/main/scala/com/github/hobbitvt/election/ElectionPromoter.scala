package com.github.hobbitvt.election

import java.util.concurrent.atomic.AtomicBoolean

import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration.{ Duration, _ }
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.util.{ Failure, Success }

/**
 * Promoter starts leader election cycle.
 * When a leadership changes, you'll receive a notification about it.
 * @param electionDealer Dealer, provide you a leader election staff
 * @param instanceId Host which you want to promote as a leader
 * @param failRecoveryInterval How long you recover yourself from some errors
 */
class ElectionPromoter(
    val electionDealer: ElectionDealer,
    val instanceId: ElectionDealer.InstanceId,
    failRecoveryInterval: Duration
)(implicit ec: ExecutionContext) extends LazyLogging {
  import ElectionDealer.InstanceId

  @volatile
  private var leader = Option.empty[InstanceId]
  @volatile
  private var closed = false
  private val started = new AtomicBoolean(false)
  private val done = Promise[Unit]

  val event = new Event[Option[InstanceId]]

  /**
   * Start promote someone as a leader
   */
  def start(): Event[Option[InstanceId]] = {
    if (started.compareAndSet(false, true)) {
      logger.info(s"Start a promotion of $instanceId")
      acquire()
    }
    event
  }

  def whenCompletelyStarted: Future[Unit] = {
    done.future
  }

  /**
   * Get current leader
   */
  def getLeaderAddress: Option[InstanceId] = leader

  /**
   * Check whether given address was promoted as a leader or not
   */
  def isLeader: Boolean = leader.contains(instanceId)

  /**
   * Check whether leader exists
   */
  def isLeaderExist: Boolean = leader.isDefined

  /**
   * Stop promotion
   */
  def close(): Future[Unit] = {
    keepLeader(None)
    closed = true
    electionDealer.release()
  }

  /**
   * Process which trying to acquire a leadership for you
   */
  private def acquire(): Unit = {
    if (!closed) {
      electionDealer.tryAcquire(instanceId).andThen({
        case Success(elected) =>
          done.trySuccess(())
          val actualLeader = if (elected) {
            val leader = Some(instanceId)
            Future.successful(leader)
          } else {
            electionDealer.getLeader
          }
          actualLeader
            .flatMap(waitForNoLeader)
            .andThen({
              case Success(_) => acquire()
              case Failure(ex) => scheduleRecovery(ex)
            })
        case Failure(ex) =>
          done.trySuccess(())
          scheduleRecovery(ex)
      })
    }
  }

  /**
   * Process which waiting a state of no leader
   */
  private def waitForNoLeader(
    lastKnownLeader: Option[InstanceId]
  ): Future[Unit] = {
    keepLeader(lastKnownLeader)
    lastKnownLeader match {
      case Some(_) if !closed =>
        electionDealer.waitForLeader(30.seconds, lastKnownLeader)
          .flatMap(waitForNoLeader)
      case _ => Future.successful(())
    }
  }

  /**
   * Keeps leader for getting current leader immediately
   */
  private def keepLeader(leader: Option[InstanceId]): Unit = synchronized {
    if (!closed && this.leader != leader) {
      this.leader = leader
      event.notify(leader)
      logLeader(leader)
    }
  }

  /**
   * Schedule recovery process
   */
  private def scheduleRecovery(ex: Throwable): Unit = {
    event.notify(None)
    logger.warn(s"Exception's been risen during promotion of [$instanceId] as a leader", ex)
    if (!closed) {
      SingleThreadedTimer.Default.schedule(failRecoveryInterval) {
        acquire()
      }
    }
  }

  /**
   * Logs a current leader in a pretty way.
   */
  private def logLeader(leader: Option[InstanceId]): Unit = {
    leader match {
      case Some(v) if v == instanceId => logger.info(s"$v becomes a leader, and it's me")
      case Some(v) => logger.info(s"$v becomes a leader")
      case _ => logger.info("No one is a leader")
    }
  }
}
