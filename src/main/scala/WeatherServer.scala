import cats.effect.kernel.Resource
import cats.effect.{Async, Sync}
import cats.implicits.toFlatMapOps
import com.comcast.ip4s.IpLiteralSyntax
import io.chrisdavenport.circuit.{Backoff, CircuitBreaker}
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.dsl.io._
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import org.http4s.server.Server
import org.typelevel.log4cats.Logger
import pureconfig._
import pureconfig.generic.auto._

import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

object WeatherServer {

  case class OpenWeatherMapConfig(apiUrl: String, apiId: String, apiExclude: String)

  case class AppConfig(openWeatherMap: OpenWeatherMapConfig)

  val appConfig = ConfigSource.default.loadOrThrow[AppConfig] // use pureconfig to read configuration
  val openWeatherMapConfig = appConfig.openWeatherMap

  val apiKey = sys.env.get("apiKey").getOrElse(openWeatherMapConfig.apiId) // use environment variable to override config

  def weatherRoutes[F[_] : Sync](weatherService: WeatherService[F]): HttpRoutes[F] = {
    object Units extends QueryParamDecoderMatcher[String]("units")
    val dsl = new Http4sDsl[F]{}
    import dsl._
    def getWeather(latitude: String, longitude: String, units: String): F[Response[F]] = {
      weatherService.getWeather(latitude.toDouble, longitude.toDouble, units).flatMap(_ match {
        case Success(value) => Ok(value.asJson)
        case Failure(e: IllegalArgumentException) => BadRequest(e.getMessage)
        case Failure(e) => InternalServerError(e.getMessage)
      })
    }

    HttpRoutes.of[F] {
      case GET -> Root / "weather" / latitude / longitude :? Units(units) =>
        getWeather(latitude, longitude, units)
      case GET -> Root / "weather" / latitude / longitude =>
        getWeather(latitude, longitude, "metric")
    }
  }

  def run[F[+_] : Async](implicit logger: Logger[F]): F[Nothing] = {

    def buildServer(weatherService: WeatherService[F]): Resource[F, Server] = EmberServerBuilder
      .default[F]
      .withHost(ipv4"0.0.0.0")
      .withPort(port"8080")
      .withHttpApp(org.http4s.server.middleware.Logger.httpApp[F](true, true)(weatherRoutes(weatherService).orNotFound))
      .build

    val circuitBreakerEffect: F[CircuitBreaker[F]] = CircuitBreaker.of[F](
      maxFailures = 2,
      resetTimeout = 10.seconds,
      backoff = Backoff.exponential,
      maxResetTimeout = 10.minutes
    )
    for {
      client <- EmberClientBuilder.default[F].build // The EmberClientBuilder sets up a connection pool, enabling the reuse of connections for multiple requests, supports HTTP/1.x and HTTP/2. Ref: https://http4s.org/v0.23/docs/client.html
      circuitBreaker <- Resource.eval(circuitBreakerEffect)
      weatherService = new WeatherServiceImpl[F](client, openWeatherMapConfig.apiUrl, apiKey, openWeatherMapConfig.apiExclude, circuitBreaker)
      _ <- buildServer(weatherService)
    } yield ()
  }.useForever
}
