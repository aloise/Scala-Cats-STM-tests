import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.freespec.AsyncFreeSpec
import services.account.{AccountService, MemoryAccountService, TransferException}

class AccountServiceSpec extends AsyncFreeSpec with AsyncIOSpec with Matchers {

  def accountService: IO[AccountService[IO]] = MemoryAccountService.create[IO](Nil)

  "AccountService" - {
    "create new accounts" in {
      (
        for {
          svc <- accountService
          created <- svc.create("test", 150)
          newAccount <- svc.get(created.id)
        } yield newAccount
        ) asserting { opt =>
        (opt must not be empty)
        //        (opt.get.name must be "test")
        //        (opt.get.amount must be 150)
      }
    }

    "delete accounts" in {
      (
        for {
          svc <- accountService
          created <- svc.create("test", 150)
          isDeleted <- svc.delete(created.id)
        } yield isDeleted
      ) asserting { isDeleted =>
        isDeleted must be (true)
      }
    }

    "transfer fund between accounts" in {
      (
        for {
          svc <- accountService
          from <- svc.create("test", 150)
          to1 <- svc.create("test2", 10)
          to2 <- svc.create("test3", 50)
          transfers <- svc.transfer(from.id, Set(to1.id, to2.id))(5)
          fromUpdated <- svc.get(from.id)
          to1Updated <- svc.get(to1.id)
          to2Updated <- svc.get(to2.id)
        } yield (transfers, fromUpdated.get, to1Updated, to2Updated)
        ) asserting { case (transfers, fromUpdated, to1Updated, to2Updated) =>
        transfers must have size 2
      }
    }

    "fail to transfer funds - insufficient amount" in {
      (
        for {
          svc <- accountService
          from <- svc.create("test", 1)
          to1 <- svc.create("test2", 10)
          to2 <- svc.create("test3", 50)
          transfers <- svc.transfer(from.id, Set(to1.id, to2.id))(5)
          fromUpdated <- svc.get(from.id)
          to1Updated <- svc.get(to1.id)
          to2Updated <- svc.get(to2.id)
        } yield (transfers, fromUpdated.get, to1Updated, to2Updated)
        ).assertThrows[TransferException]
    }
  }
}
