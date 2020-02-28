package routes

import cats.effect.Sync
import org.http4s.{HttpRoutes, Response}
import org.http4s.dsl.Http4sDsl
import services.{Account, AccountId, AccountService}
import cats.implicits._
import org.http4s.dsl.impl.PathVar

import scala.util.Try

object AccountRoutes {

  object AccountIdVar {
    def unapply(str: String): Option[AccountId] =
      if (!str.trim.isEmpty)
        Try(AccountId(str.trim.toInt)).toOption
      else
        None
  }

  def accountRoutes[F[_]: Sync](accounts: AccountService[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F]{}
    import dsl._
    HttpRoutes.of[F] {
      case GET -> Root / "account" / AccountIdVar(id) =>
        for {
          account <- accounts.get(id)
          resp <- account.map(Ok(_)).getOrElse(NotFound())
        } yield resp
    }
  }
}
