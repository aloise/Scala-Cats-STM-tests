import cats.effect.{ExitCode, IO, IOApp}
import server.Server

object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    Server.stream[IO].compile.drain.map(_ => ExitCode.Success)
}