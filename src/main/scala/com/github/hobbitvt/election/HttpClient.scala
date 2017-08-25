package com.github.hobbitvt.election

import org.asynchttpclient._

import scala.concurrent.{ Future, Promise }

private object HttpClient {
  private lazy val client = new DefaultAsyncHttpClient()

  def get(url: String): Future[Response] =
    execute(client.prepareGet(url))
  def put(url: String, data: String): Future[Response] =
    execute(client.preparePut(url).setBody(data))
  def put(url: String): Future[Response] =
    execute(client.preparePut(url))
  def delete(url: String): Future[Response] =
    execute(client.prepareDelete(url))

  private def execute(req: BoundRequestBuilder): Future[Response] = {
    val promise = Promise[Response]
    req.execute(new AsyncCompletionHandler[Response] {
      override def onCompleted(response: Response): Response = {
        promise.success(response)
        response
      }
      override def onThrowable(t: Throwable): Unit = {
        super.onThrowable(t)
        promise.failure(t)
      }
    })
    promise.future
  }
}
