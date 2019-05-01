package org.phase.chat

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import fs2.concurrent.{Queue, Topic}
import fs2.Stream
import org.http4s.websocket.WebSocketFrame


case class State(messageCount: Int)

object Main extends IOApp {
  def run(args: List[String]) = {
    for (
      q <- Queue.unbounded[IO, WebSocketFrame];
      t <- Topic[IO, WebSocketFrame](WebSocketFrame.Text("Initial Message"));
      exitCode <- {
        val messageStream = q
          .dequeue
          .mapAccumulate(State(1))({
            case (currentState, nextWsMessage) =>
              nextWsMessage match {
                case WebSocketFrame.Text(text, _) =>
                  (State(currentState.messageCount + 1), WebSocketFrame.Text(s"(${currentState.messageCount}): $text"))

                case _ =>
                  (currentState, nextWsMessage)
              }
          })
          .map(_._2)
          .through(t.publish)



        val serverStream = ChatServer.stream[IO](q, t)
        val combinedStream = Stream(messageStream, serverStream).parJoinUnbounded

        combinedStream.compile.drain.as(ExitCode.Success)
      }
    ) yield exitCode
  }
}