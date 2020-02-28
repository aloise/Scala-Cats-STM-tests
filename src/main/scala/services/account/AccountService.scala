package services.account

import cats.Applicative
import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyChain, ValidatedNec}
import cats.effect.concurrent.Ref
import cats.effect.{Async, Sync}
import cats.implicits._
import io.circe.Codec
import io.circe.generic.semiauto._
import io.github.timwspence.cats.stm.{STM, TVar}
import org.http4s.circe._
import org.http4s.{EntityDecoder, EntityEncoder}

case class AccountId(id: Int) extends AnyVal

final case class Account(id: AccountId, name: String, amount: Int)

final case class TransferId(id: Int) extends AnyVal
final case class TransferGroupId(id: Int) extends AnyVal
final case class Transfer(from: AccountId, to: AccountId, amount: Int)

sealed trait TransferError
case class FromAccountNotFound(accountId: AccountId) extends TransferError
case object EmptyListOfDestinations extends TransferError
case class SelfTransferIsNotAllowed(accountId: AccountId) extends TransferError
case class ToAccountNotFound(accountId: AccountId) extends TransferError
case class InsufficientAmount(accountId: AccountId) extends TransferError

final case class TransferException(errors: NonEmptyChain[TransferError]) extends
  Exception(s"Transfer Failed: ${errors.toList.mkString(", ")}")

trait AccountService[F[_]] {
  def create(name: String, initialAmount: Int): F[Account]
  // transfer is transactional - it's either executed at once of fails
  def get(id: AccountId): F[Option[Account]]
  def transfer(from: AccountId, to: Set[AccountId])(amountForEachTransfer: Int): F[List[Transfer]]
  def delete(id: AccountId): F[Boolean]
}

final class MemoryAccountService[F[_] : Async](private val accountsRef: Ref[F, Map[AccountId, TVar[Account]]], private val nextIdRef: Ref[F, Int])
  extends AccountService[F] {

  override def create(name: String, initialAmount: Int): F[Account] =
    for {
      _ <- nextIdRef.update(_ + 1)
      nextId <- nextIdRef.get
      newAccount = Account(AccountId(nextId), name, initialAmount)
      accountSTM <- STM.atomically[F] {
        TVar.of(newAccount)
      }
      _ <- accountsRef.update(accounts => accounts + (newAccount.id -> accountSTM))
    } yield newAccount

  override def transfer(from: AccountId, to: Set[AccountId])(amountForEachTransfer: Int): F[List[Transfer]] =
    accountsRef.get.flatMap { accounts =>
      val (fromTVarValidated, toTVarsValidated) = validateFromAndToAccountIds(from, to, accounts)

      val validatedResults =
        (fromTVarValidated, toTVarsValidated).mapN { case (fromAcc, toAccounts) =>
          transferSTMTransaction(amountForEachTransfer)(fromAcc, toAccounts)
        }

      val flatErrors: STM[ValidatedNec[TransferError, List[Transfer]]] =
        validatedResults.fold(validationErrors => STM.pure(validationErrors.invalid), identity)

      STM.atomically[F](flatErrors).flatMap { validated =>
        validated.fold(
          err => TransferException(err).raiseError[F, List[Transfer]],
          result => Sync[F].pure(result)
        )
      }
    }


  private def transferSTMTransaction(amountToDepositInEach: Int)(fromAcc: TVar[Account], toAccounts: Map[AccountId, TVar[Account]]): STM[ValidatedNec[TransferError, List[Transfer]]] = {
    for {
      withdrawFrom <- fromAcc.get
      amtToWithdraw = toAccounts.size * amountToDepositInEach
      result <- if (withdrawFrom.amount < amtToWithdraw)
        STM.pure((InsufficientAmount(withdrawFrom.id): TransferError).invalidNec)
      else
        fromAcc.modify(a => a.copy(amount = a.amount - amtToWithdraw)) *>
          toAccounts.map { case (toId, accountTVar) =>
            accountTVar.modify(acc => acc.copy(amount = acc.amount + amountToDepositInEach)) *> STM.pure(toId)
          }.toList.sequence.map { depositedIds =>
            depositedIds.map(toId => Transfer(withdrawFrom.id, toId, amountToDepositInEach)).validNec
          }
    } yield result
  }

  private def validateFromAndToAccountIds(from: AccountId, to: Set[AccountId], accounts: Map[AccountId, TVar[Account]]) = {
    val emptyToList: ValidatedNec[TransferError, Unit] = if(to.isEmpty) EmptyListOfDestinations.invalidNec else ().validNec

    val fromAccountIdNotInTo: ValidatedNec[TransferError, Unit] =
      if (to.contains(from)) SelfTransferIsNotAllowed(from).invalidNec else ().validNec

    val fromTVarValidated: ValidatedNec[TransferError, TVar[Account]] =
      emptyToList *> fromAccountIdNotInTo *> accounts.get(from).toValidNec(FromAccountNotFound(from))

    val toTVarsValidated: ValidatedNec[TransferError, Map[AccountId, TVar[Account]]] =
      (to -- accounts.keySet).map(ToAccountNotFound).toList match {
        case head :: tail => Invalid(NonEmptyChain(head, tail: _*))
        case _ => Valid(accounts.filter {
          to contains _._1
        })
      }
    (fromTVarValidated, toTVarsValidated)
  }

  override def delete(id: AccountId): F[Boolean] = for {
    accounts <- accountsRef.get
    _ <- accountsRef.update { accs =>
      accs - id
    }
  } yield accounts.contains(id)

  override def get(id: AccountId): F[Option[Account]] = accountsRef.get.flatMap { accs =>
    val accountOpt: Option[F[Account]] =
      accs.get(id).map { tvar =>
        STM.atomically[F](tvar.get)
      }
    accountOpt.sequence
  }
}

object MemoryAccountService {

  def create[F[_]: Async: Applicative](initialAccounts: List[Account]): F[MemoryAccountService[F]] = {
    for {
      accountsMap <- STM.atomically[F] {
        initialAccounts.map { a =>
          TVar.of(a).map(tvar => a.id -> tvar)
        }.sequence.map(_.toMap)
      }
      accountsRef <- Ref[F].of(accountsMap)
      nextIdCounter <- Ref.of(0)
    } yield new MemoryAccountService(accountsRef, nextIdCounter )
  }

}
