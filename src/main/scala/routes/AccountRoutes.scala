package routes

import cats.Applicative
import cats.effect.Sync
import org.http4s.{EntityDecoder, EntityEncoder, HttpRoutes}
import org.http4s.dsl.Http4sDsl
import cats.implicits._
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import services.account.{Account, AccountId, AccountService}
import scala.util.Try

object AccountRoutes {

  implicit val accountIdCodec: Codec[AccountId] = deriveCodec
//  implicit def accountIdEntityDecoder[F[_]: Sync]: EntityDecoder[F, Account] = jsonOf
  implicit def accountIdEntityEncoder[F[_]: Applicative]: EntityEncoder[F, AccountId] = jsonEncoderOf

  implicit val accountCodec: Codec[Account] = deriveCodec
//  implicit def accountEntityDecoder[F[_]: Sync]: EntityDecoder[F, Account] = jsonOf
  implicit def accountEntityEncoder[F[_]: Applicative]: EntityEncoder[F, Account] = jsonEncoderOf

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
