package jots.intro

import cats.effect.IO
import cats.effect.IOApp
import io.circe.Decoder
import java.time.Instant
import jots.JwtAlgorithm
import jots.JwtDecoder
import jots.JwtVerificationBuilder
import jots.SignedJwt
import jots.http4s.*
import jots.http4s.JwtAuthMiddleware
import jots.http4s.RefreshingJwtVerificationBuilder
import jots.testing.syntax.*
import org.http4s.AuthedRoutes
import org.http4s.Request
import org.http4s.client.Client
import org.http4s.dsl.io.*
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.syntax.all.*
import scala.util.Try

object Refreshing extends IOApp.Simple {
  final case class UserJwt(userId: String, issuedAt: Instant)

  object UserJwt {
    private given Decoder[Instant] =
      Decoder[Long].emapTry(epochSecond => Try(Instant.ofEpochSecond(epochSecond)))

    given Decoder[UserJwt] =
      Decoder.forProduct2("userId", "iat")(apply)

    given JwtDecoder[UserJwt] =
      JwtDecoder.decodeClaims
  }

  override def run: IO[Unit] = {
    val client =
      for {
        client <- EmberClientBuilder.default[IO].build
        uri = uri"https://example.auth0.com/.well-known/jwks.json"
        verification <- RefreshingJwtVerificationBuilder
          .refreshWith(client, uri) { jwkSet =>
            JwtVerificationBuilder
              .default[IO]
              .jwkSet(JwtAlgorithm.All, jwkSet)
              .build
          }
          .build

        /* Http4s */
        authMiddleware = JwtAuthMiddleware[IO, UserJwt](verification)
        authedRoutes = AuthedRoutes.of[UserJwt, IO] { case GET -> Root / "identity" as userJwt =>
          Ok(userJwt.userId)
        }
        app = authMiddleware(authedRoutes).orNotFound
      } yield Client.fromHttpApp(app)

    client.use { client =>
      for {
        signedJwt =
          signedJwt"eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOiIwNDlhNTIzNi05NTI5LTQxYWItOWEwZi01YjBlMmU4OWQ0OGMiLCJpYXQiOjE3OTUwOTI5ODh9.nh7vKon0-et67sy8qmhGKqHJcV-9-8tS_8eehdQXCmA"
        userId <- client.expect[String](Request(GET, uri"/identity").putHeaders(signedJwt))
        _ <- IO.println(userId)
      } yield ()
    }
  }
}
