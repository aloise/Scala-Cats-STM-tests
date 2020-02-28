package routes

import cats.effect.Sync
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import cats.implicits._
import io.circe.Decoder.Result
import io.circe.{HCursor, Json}
import services.account.{Account, AccountId, AccountService, Transfer, TransferError, TransferException}

import scala.util.Try

object AccountRoutes extends JsonEncoders with AccountRoutesJson {
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
      case GET -> Root / "accounts" / AccountIdVar(id) =>
        for {
          account <- accounts.get(id)
          resp <- account.map(Ok(_)).getOrElse(NotFound())
        } yield resp

      case DELETE -> Root / "accounts" / AccountIdVar(id) =>
        accounts.delete(id).flatMap {
          case true => Ok(AccountDeleted(id))
          case false => NotFound()
        }

      case req @ POST -> Root / "accounts" =>
        for {
          createAccount <- req.as[CreateAccount]
          // TODO - Validate first
          newAccount <- accounts.create(createAccount.name, createAccount.amount)
          resp <- Ok(newAccount)
        } yield resp

      case req @ POST -> Root / "transfers"  =>
        for {
          transferReq <- req.as[TransferRequest]
          transfers = accounts.transfer(transferReq.fromAccountId, transferReq.toAccountIds.toSet)(transferReq.transferAmountForEach)
          resp <- transfers.flatMap(trx => Ok(TransferSuccess(trx))).recoverWith {
            case TransferException(errors) => BadRequest(TransferErrorsResponse(errors.toList))
          }
        } yield resp


    }
  }
}

private[routes] trait AccountRoutesJson {
  import io.circe.{Codec, Decoder, Encoder}
  import io.circe.generic.semiauto._

  case class CreateAccount(name: String, amount: Int)
  case class AccountDeleted(deletedAccountId: AccountId)
  case class TransferRequest(fromAccountId: AccountId, toAccountIds: List[AccountId], transferAmountForEach: Int)

  case class TransferErrorsResponse(errors: List[TransferError])
  case class TransferSuccess(transactions: List[Transfer])

  implicit val accountIdCodec: Codec[AccountId] = new Codec[AccountId] {
    override def apply(a: AccountId): Json = Json.fromInt(a.id)
    override def apply(c: HCursor): Result[AccountId] = c.as[Int].map(AccountId)
  }

  implicit val accountCodec: Codec[Account] = deriveCodec

  implicit val createAccountDecoder: Decoder[CreateAccount] = deriveDecoder
  implicit val transferRequestDecoder: Decoder[TransferRequest] = deriveDecoder

  implicit val accountDeletedEncoder: Encoder[AccountDeleted] = deriveEncoder
  implicit val transferErrorsEncoder: Encoder[TransferError] = deriveEncoder
  implicit val transferErrorsResponseEncoder: Encoder[TransferErrorsResponse] = deriveEncoder
  implicit val transferEncoder: Encoder[Transfer] = deriveEncoder
  implicit val transferSuccessEncoder: Encoder[TransferSuccess] = deriveEncoder
}