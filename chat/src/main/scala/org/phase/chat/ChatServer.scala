package org.phase.chat

import cats.effect.concurrent.Ref
import cats.effect.{ConcurrentEffect, ContextShift, Effect, ExitCode, IO, IOApp, Timer}
import cats.implicits._
import fs2.concurrent.{Queue, Topic}
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.implicits._
import org.http4s.server.middleware.Logger
import fs2.{INothing, Stream}
import org.http4s.websocket.WebSocketFrame

import scala.concurrent.ExecutionContext.global

object ChatServer {

  def stream[F[_]: ConcurrentEffect](q: Queue[F, FromClient], t: Topic[F, ToClient], ref: Ref[F, State])(implicit T: Timer[F], C: ContextShift[F]): Stream[F, INothing] = {
    for {
      client <- BlazeClientBuilder[F](global).stream
      helloWorldAlg = HelloWorld.impl[F]
      jokeAlg = Jokes.impl[F](client)

      // Combine Service Routes into an HttpApp.
      // Can also be done via a Router if you
      // want to extract a segments not checked
      // in the underlying routes.
      httpApp = (
        ChatRoutes.helloWorldRoutes[F](helloWorldAlg) <+>
        ChatRoutes.jokeRoutes[F](jokeAlg) <+>
        ChatRoutes.chatRoutes(q, t, ref)
      ).orNotFound

      // With Middlewares in place
      finalHttpApp = Logger.httpApp(true, true)(httpApp)

      exitCode <- BlazeServerBuilder[F]
        .bindHttp(8080, "0.0.0.0")
        .withHttpApp(finalHttpApp)
        .serve
    } yield exitCode
  }.drain
}