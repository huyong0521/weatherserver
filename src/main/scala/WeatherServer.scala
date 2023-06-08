import cats.effect.kernel.Resource
import cats.effect.{ExitCode, IO, IOApp}
import com.comcast.ip4s.IpLiteralSyntax
import pureconfig._
import pureconfig.generic.auto._
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s._
import org.http4s.implicits._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.server.middleware.Logger
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.server.Server

import scala.util.{Failure, Success}

object WeatherServer extends IOApp {

  case class OpenWeatherMapConfig(apiUrl: String, apiId: String, apiExclude: String)

  case class AppConfig(openWeatherMap: OpenWeatherMapConfig)

  val appConfig = ConfigSource.default.loadOrThrow[AppConfig] // use pureconfig to read configuration
  val openWeatherMapConfig = appConfig.openWeatherMap

  val apiKey = sys.env.get("apiKey").getOrElse(openWeatherMapConfig.apiId) // use environment variable to override config

  def weatherRoutes(implicit weatherService: WeatherService) = {
    object Units extends QueryParamDecoderMatcher[String]("units")
    HttpRoutes.of[IO] {
      case GET -> Root / "weather" / latitude / longitude :? Units(units) =>
        getWeather(latitude, longitude, units)
      case GET -> Root / "weather" / latitude / longitude =>
        getWeather(latitude, longitude, "metric")
    }
  }

  private def getWeather(latitude: String, longitude: String, units: String)(implicit weatherService: WeatherService): IO[Response[IO]] = {
    weatherService.getWeather(latitude.toDouble, longitude.toDouble, units).flatMap(_ match {
      case Success(value) => Ok(value.asJson)
      case Failure(e: IllegalArgumentException) => BadRequest(e.getMessage)
      case Failure(e) => InternalServerError(e.getMessage)
    })
  }

  private def server(implicit weatherService: WeatherService): Resource[IO, Server] = EmberServerBuilder
    .default[IO]
    .withHost(ipv4"0.0.0.0")
    .withPort(port"8080")
    .withHttpApp(Logger.httpApp(true, true)(weatherRoutes.orNotFound))
    .build

  override def run(args: List[String]): IO[ExitCode] = {
    for {
      client <- EmberClientBuilder.default[IO].build // The EmberClientBuilder sets up a connection pool, enabling the reuse of connections for multiple requests, supports HTTP/1.x and HTTP/2. Ref: https://http4s.org/v0.23/docs/client.html
      weatherService: WeatherService = new WeatherServiceImpl(client, openWeatherMapConfig.apiUrl, apiKey, openWeatherMapConfig.apiExclude)
      _ <- server(weatherService)
    } yield ()
  }.useForever
}
