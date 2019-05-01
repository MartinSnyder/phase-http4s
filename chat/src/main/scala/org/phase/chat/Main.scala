package org.phase.chat

import cats.effect.concurrent.Ref
import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import fs2.concurrent.{Queue, Topic}
import fs2.Stream
import org.http4s.websocket.WebSocketFrame


case class State(messageCount: Int)

case class FromClient(userName: String, message: String)
case class ToClient(message: String)

object Main extends IOApp {
  def run(args: List[String]) = {
    for (
      q <- Queue.unbounded[IO, FromClient];
      t <- Topic[IO, ToClient](ToClient("==="));
      ref <- Ref.of[IO, State](State(1));
      exitCode <- {
        val messageStream = q
          .dequeue
          .evalMap(fromClient => {
            ref.modify(currentState => {
              ( State(currentState.messageCount + 1)
                , ToClient(s"(${currentState.messageCount}): ${fromClient.userName} - ${fromClient.message}"))
            })
          })
          .through(t.publish)

        val serverStream = ChatServer.stream[IO](q, t, ref)
        val combinedStream = Stream(messageStream, serverStream).parJoinUnbounded

        combinedStream.compile.drain.as(ExitCode.Success)
      }
    ) yield exitCode
  }
}