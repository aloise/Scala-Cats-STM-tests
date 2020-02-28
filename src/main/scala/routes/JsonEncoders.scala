package routes

import cats.Applicative
import cats.effect.Sync
import io.circe.{Decoder, Encoder}
import org.http4s.{EntityDecoder, EntityEncoder}
import org.http4s.circe._

trait JsonEncoders {
  implicit def entityEncoder[T: Encoder, F[_]: Applicative]: EntityEncoder[F, T] = jsonEncoderOf

  implicit def entityDecoder[T: Decoder, F[_]: Sync]: EntityDecoder[F, T] = jsonOf[F, T]
}
