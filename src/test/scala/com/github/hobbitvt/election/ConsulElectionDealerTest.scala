package com.github.hobbitvt.election

import org.scalatest.{ FunSpec, Matchers }

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{ Duration, _ }
import scala.concurrent.{ Await, Future }


class ConsulElectionDealerTest extends FunSpec with Matchers {
  private val consul = "http://localhost:8500"
  private val cleanFns = ListBuffer.empty[() => Unit]

  describe("when consul on sandbox server is running") {
    it("should simply work") {
      val key = java.util.UUID.randomUUID().toString
      onExit(rmKey(key))
      val ttl = 10.second
      val electionDealer1 = new ConsulElectionDealer(consul, key, ttl)
      val electionDealer2 = new ConsulElectionDealer(consul, key, ttl)
      val service1 = "123"
      val service2 = "321"

      info("Service1 becomes a leader")
      electionDealer1.tryAcquire(service1).await shouldEqual true
      info("Service2 can't become a leader")
      electionDealer2.tryAcquire(service2).await shouldEqual false
      info("Check that service1 is a leader")
      electionDealer1.getLeader.await shouldEqual Some(service1)
      electionDealer2.getLeader.await shouldEqual Some(service1)
      info("Service1 release a leadership")
      electionDealer1.release().await

      info("Service2 becomes a leader")
      electionDealer2.tryAcquire(service2).await shouldEqual true
      info("Service1 can't become a leader")
      electionDealer1.tryAcquire(service1).await shouldEqual false
      info("Check that service2 is a leader")
      electionDealer1.getLeader.await shouldEqual Some(service2)
      electionDealer2.getLeader.await shouldEqual Some(service2)
      info("Service2 release a leadership")
      electionDealer2.release().await

      info("Check that none is a leader")
      electionDealer2.getLeader.await shouldEqual None

      electionDealer1.close().await
      electionDealer2.close().await
    }

    it("should simply wait for changes") {
      val key = java.util.UUID.randomUUID().toString
      onExit(rmKey(key))
      val wait = 2.seconds
      val ttl = 10.second
      val electionDealer1 = new ConsulElectionDealer(consul, key, ttl)
      val service1 = "123"

      mkKey(key)

      info("Wait for service1 becomes a leader")
      var leaderWaiting = electionDealer1.waitForLeader(wait, None)
      info("Service1 becomes a leader")
      electionDealer1.tryAcquire(service1).await shouldEqual true
      info("Check a leader is Service1")
      leaderWaiting.await shouldEqual Some(service1)

      info("Wait for no leader")
      leaderWaiting = electionDealer1.waitForLeader(wait, Some(service1))
      info("Service1 releases a leadership")
      electionDealer1.release().await
      info("Check a leader is nothing")
      leaderWaiting.await shouldEqual None

      electionDealer1.close().await
    }

    it("should wait all time for changes and returns with the last leader") {
      val key = java.util.UUID.randomUUID().toString
      onExit(rmKey(key))
      val wait = 2.seconds
      val ttl = 10.second
      val electionDealer1 = new ConsulElectionDealer(consul, key, ttl)

      mkKey(key)

      electionDealer1.waitForLeader(wait, None).await shouldEqual None

      electionDealer1.close().await
    }

    it("should promote a leader and cancel that") {
      val key = java.util.UUID.randomUUID().toString
      onExit(rmKey(key))
      val ttl = 10.second
      val checkInterval = 1.seconds
      val service1 = "123"
      val service2 = "321"
      val electionDealer1 = new ConsulElectionDealer(consul, key, ttl)
      val promoter1 = new ElectionPromoter(electionDealer1, service1, 10.second)
      val electionDealer2 = new ConsulElectionDealer(consul, key, ttl)
      val promoter2 = new ElectionPromoter(electionDealer2, service2, 10.second)

      info("Start promoting service1 as a leader")
      val service1Promotion = promoter1.start()
      var isService1Leader = false
      service1Promotion.register({
        case Some(v) if v == service1 => isService1Leader = true
        case _ => isService1Leader = false
      })

      info("Wait...")
      wait(checkInterval)

      info("Start promoting service2 as a leader")
      val service2Promotion = promoter2.start()
      var isService2Leader = false
      service2Promotion.register({
        case Some(v) if v == service2 => isService2Leader = true
        case _ => isService2Leader = false
      })

      info("Wait...")
      wait(checkInterval)

      info("Check that service1 is a leader")
      isService1Leader shouldEqual true
      promoter1.getLeaderAddress shouldEqual Some(service1)
      promoter1.isLeader shouldEqual true
      promoter2.getLeaderAddress shouldEqual Some(service1)
      promoter2.isLeader shouldEqual false
      info("Check that service2 is not a leader")
      isService2Leader shouldEqual false

      info("Stop promoting of service1")
      promoter1.close().await
      info("Wait...")
      wait(checkInterval * 2)

      info("Check that service2 is a leader")
      isService2Leader shouldEqual true
      promoter1.getLeaderAddress shouldEqual None
      promoter1.isLeader shouldEqual false
      promoter2.getLeaderAddress shouldEqual Some(service2)
      promoter2.isLeader shouldEqual true
      info("Check that service1 is not a leader")
      isService1Leader shouldEqual false

      info("Stop promoting of service2")
      promoter2.close().await
      info("Check that none is a leader")
      promoter2.getLeaderAddress shouldEqual None
      promoter2.isLeader shouldEqual false
      isService2Leader shouldEqual false

      electionDealer1.close().await
      electionDealer2.close().await
    }

    it("should expire session ttl") {
      val key = java.util.UUID.randomUUID().toString
      onExit(rmKey(key))
      val ttl = 10.second
      val electionDealer1 = new ConsulElectionDealer(consul, key, ttl)
      val electionDealer2 = new ConsulElectionDealer(consul, key, ttl)
      val service1 = "123"

      info("Service1 becomes a leader")
      electionDealer1.tryAcquire(service1).await shouldEqual true
      info("Check that service1 is a leader")
      electionDealer1.getLeader.await shouldEqual Some(service1)
      info("Wait...")
      wait(ttl / 2)
      info("Check that service1 is still a leader")
      electionDealer1.getLeader.await shouldEqual Some(service1)
      info("Wait...")
      wait(ttl)
      info("Check that service1 is still a leader")
      electionDealer1.getLeader.await shouldEqual Some(service1)
      info("Close election dealer")
      electionDealer1.close().await
      info("Check that none is a leader")
      electionDealer2.getLeader.await shouldEqual None

      electionDealer1.close().await
      electionDealer2.close().await
    }
  }

  def wait(duration: Duration): Unit = {
    Thread.sleep(duration.toMillis)
  }

  def rmKey(key: String): Unit = {
    Await.result(HttpClient.delete(s"$consul/v1/kv/$key"), Duration.Inf)
  }

  def mkKey(key: String): Unit = {
    Await.result(HttpClient.put(s"$consul/v1/kv/$key"), Duration.Inf)
  }

  def onExit(fn: => Unit): Unit = {
    cleanFns.append(() => fn)
  }

  implicit class RichFuture[A](future: Future[A]) {
    def await: A = Await.result(future, 3.seconds)
  }

  Runtime.getRuntime.addShutdownHook(new Thread {
    override def run(): Unit = {
      cleanFns.foreach(_.apply())
    }
  })
}
