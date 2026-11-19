package jots.intro

import cats.effect.IO
import cats.effect.IOApp
import cats.syntax.all.*
import io.circe.Decoder
import io.circe.Encoder
import java.time.Instant
import java.time.temporal.ChronoUnit
import jots.JwtAlgorithm
import jots.JwtBuilder
import jots.JwtDecoder
import jots.JwtEncoder
import jots.JwtSigningBuilder
import jots.JwtVerificationBuilder
import jots.crypto.SecretKey
import jots.http4s.*
import jots.http4s.JwtAuthMiddleware
import jots.syntax.*
import org.http4s.AuthedRoutes
import org.http4s.Request
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.dsl.io.*
import org.http4s.syntax.all.*
import scala.util.Try

object Introduction extends IOApp.Simple {
  final case class UserJwt(userId: String, issuedAt: Instant)

  object UserJwt {
    val generate: IO[UserJwt] =
      (
        IO.randomUUID.map(_.toString),
        IO.realTimeInstant.map(_.truncatedTo(ChronoUnit.SECONDS))
      ).mapN(apply)

    private given Encoder[Instant] =
      Encoder[Long].contramap(_.getEpochSecond)

    private given Decoder[Instant] =
      Decoder[Long].emapTry(epochSecond => Try(Instant.ofEpochSecond(epochSecond)))

    given Encoder.AsObject[UserJwt] =
      Encoder.forProduct2("userId", "iat")(jwt => (jwt.userId, jwt.issuedAt))

    given Decoder[UserJwt] =
      Decoder.forProduct2("userId", "iat")(apply)

    given JwtEncoder[UserJwt] =
      JwtEncoder.encodeClaims

    given JwtDecoder[UserJwt] =
      JwtDecoder.decodeClaims
  }

  override def run: IO[Unit] =
    for {
      userJwt <- UserJwt.generate
      _ <- IO.println(userJwt)
      _ <- IO.println(userJwt.asJwt)

      /* Signing */
      algorithm = JwtAlgorithm.HS256
      secretKey <- SecretKey("this-is-a-secret-key-that-is-very-secret").liftTo[IO]
      signing <- JwtSigningBuilder.default[IO].hmac(algorithm, secretKey).build
      signedJwt <- userJwt.asJwt.signWith(signing)
      _ <- IO.println(signedJwt.toString)
      _ <- IO.println(signedJwt.show)

      /* Verification */
      verification <- JwtVerificationBuilder.default[IO].hmac(algorithm, secretKey).build
      userJwtDecoded <- verification.verifyAs[UserJwt](signedJwt)
      // parsedSignedJwt <- SignedJwt.parse(signedJwt.show).liftTo[IO]
      // verifiedJwt <- parsedSignedJwt.verifyWith(verification)
      // userJwtDecoded <- verifiedJwt.as[UserJwt].liftTo[IO]
      _ <- IO.println(userJwtDecoded)
      _ <- IO.println(userJwt == userJwtDecoded)

      /* Http4s */
      authMiddleware = JwtAuthMiddleware[IO, UserJwt](verification)
      authedRoutes = AuthedRoutes.of[UserJwt, IO] { case GET -> Root / "identity" as userJwt =>
        Ok(userJwt.userId)
      }
      app = authMiddleware(authedRoutes).orNotFound
      client = Client.fromHttpApp(app)
      userId <- client.expect[String](Request(GET, uri"/identity").putHeaders(signedJwt))
      _ <- IO.println(userId)
    } yield ()
}
