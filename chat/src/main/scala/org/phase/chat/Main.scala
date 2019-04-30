package org.phase.chat

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._

object Main extends IOApp {
  def run(args: List[String]) =
    ChatServer.stream[IO].compile.drain.as(ExitCode.Success)
}