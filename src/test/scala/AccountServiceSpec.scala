import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import org.scalatest.Assertion
import org.scalatest.matchers.must.Matchers
import org.scalatest.freespec.AsyncFreeSpec
import services.{AccountService, MemoryAccountService}

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
  }
}
