package com.github.hobbitvt.election

import java.util.concurrent.{ Executors, TimeUnit }

import scala.concurrent.duration.Duration

class SingleThreadedTimer {
  private val scheduler = Executors.newSingleThreadScheduledExecutor()

  def schedule(after: Duration)(fn: => Unit): Unit = {
    scheduler.schedule(new Runnable {
      override def run(): Unit = fn
    }, after.toMillis, TimeUnit.MILLISECONDS)
  }
}

object SingleThreadedTimer {
  val Default = new SingleThreadedTimer
}
