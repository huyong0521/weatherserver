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
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.server.Server

import scala.util.{Failure, Success}

object WeatherServer extends IOApp {

  case class OpenWeatherMapConfig(apiUrl: String, apiId: String, apiExclude: String)
  case class AppConfig(openWeatherMap: OpenWeatherMapConfig)

  val appConfig = ConfigSource.default.loadOrThrow[AppConfig] // use pureconfig to read configuration
  val openWeatherMapConfig  = appConfig.openWeatherMap

  // The EmberClientBuilder sets up a connection pool, enabling the reuse of connections for multiple requests, supports HTTP/1.x and HTTP/2. Ref: https://http4s.org/v0.23/docs/client.html
  val clientResource: Resource[IO, Client[IO]] = EmberClientBuilder.default[IO].build

  val apiKey = sys.env.get("apiKey").getOrElse(openWeatherMapConfig.apiId) // use environment variable to override config
  val weatherService: WeatherService = new WeatherServiceImpl(clientResource, openWeatherMapConfig.apiUrl, apiKey, openWeatherMapConfig.apiExclude)

  val weatherRoutes = {
    object Units extends QueryParamDecoderMatcher[String]("units")
    HttpRoutes.of[IO] {
      case GET -> Root / "weather" / latitude / longitude :? Units(units) =>
        getWeather(latitude, longitude, units)
      case GET -> Root / "weather" / latitude / longitude =>
        getWeather(latitude, longitude, "metric")
    }
  }

  val finalHttpApp = Logger.httpApp(true, true)(weatherRoutes.orNotFound)

  private def getWeather(latitude: String, longitude: String, units: String): IO[Response[IO]] = {
    weatherService.getWeather(latitude.toDouble, longitude.toDouble, units).flatMap(_ match {
      case Success(value) => Ok(value.asJson)
      case Failure(e: IllegalArgumentException) => BadRequest(e.getMessage)
      case Failure(e) => InternalServerError(e.getMessage)
    })
  }

val server: Resource[IO, Server] = EmberServerBuilder
  .default[IO]
  .withHost(ipv4"0.0.0.0")
  .withPort(port"8080")
  .withHttpApp(finalHttpApp)
  .build


  override def run(args: List[String]): IO[ExitCode] = server.use(_ => IO.never)
}
