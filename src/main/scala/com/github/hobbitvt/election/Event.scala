package com.github.hobbitvt.election

class Event[A] {
  @volatile
  private var handlers = Seq.empty[(A) => Unit]
  private var lastEvent = Option.empty[A]

  def notify(value: A): Unit = {
    handlers.foreach(_.apply(value))
    lastEvent = Some(value)
  }
  def register(handler: (A) => Unit): Unit = synchronized {
    handlers = handlers :+ handler
    lastEvent.foreach(handler)
  }
}
