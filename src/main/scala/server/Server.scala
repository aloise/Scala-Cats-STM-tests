package server

import cats.effect.{ConcurrentEffect, ContextShift, Timer}
import fs2.Stream
import org.http4s.server.blaze.BlazeServerBuilder
import routes.{AccountRoutes, HealthRoutes}
import services.{AccountService, MemoryAccountService}
import cats.implicits._
import org.http4s.implicits._

object Server {

  def stream[F[_]: ConcurrentEffect](implicit T: Timer[F], C: ContextShift[F]): Stream[F, Nothing] = {
    for {
      accountService: AccountService[F] <- Stream.eval(MemoryAccountService.create(List.empty))

      httpApp = (
        AccountRoutes.accountRoutes[F](accountService) <+>
        HealthRoutes.healthRoutes[F]
      ).orNotFound

      exitCode <- BlazeServerBuilder[F]
        .bindHttp(9001, "0.0.0.0")
        .withHttpApp(httpApp)
        .serve
    } yield exitCode
  }.drain
}
