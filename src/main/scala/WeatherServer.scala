import cats.effect.kernel.Resource
import cats.effect.{ExitCode, IO, IOApp}
import com.typesafe.config.ConfigFactory
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s._
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.implicits._

import scala.util.{Failure, Success}

object WeatherServer extends IOApp {

  val config = ConfigFactory.load()

  // OpenWeatherMap API configuration
  val openWeatherMapConfig = config.getConfig("openWeatherMap")
  val apiUrl = openWeatherMapConfig.getString("apiUrl")
  val apiId = openWeatherMapConfig.getString("apiId")
  val apiExclude = openWeatherMapConfig.getString("apiExclude")

  val httpClient: Resource[IO, Client[IO]] = BlazeClientBuilder[IO].resource
  val weatherService: WeatherService = new WeatherServiceImpl(httpClient, apiUrl, apiId, apiExclude)

  val weatherRoutes: HttpRoutes[IO] = {
    object Units extends QueryParamDecoderMatcher[String]("units")
    HttpRoutes.of[IO] {
      case GET -> Root / "weather" / latitude / longitude :? Units(units) =>
        getWeather(latitude, longitude, units)
      case GET -> Root / "weather" / latitude / longitude =>
        getWeather(latitude, longitude, "metric")
    }
  }

  val routes = weatherRoutes.orNotFound

  private def getWeather(latitude: String, longitude: String, units: String): IO[Response[IO]] = {
    import JsonProtocol.temperatureTypeEnumEncoder
    weatherService.getWeather(latitude.toDouble, longitude.toDouble, units).flatMap(_ match {
      case Success(value) => Ok(value.asJson)
      case Failure(e: IllegalArgumentException) => BadRequest(e.getMessage)
      case Failure(e) => InternalServerError(e.getMessage)
    })
  }

  def run(args: List[String]): IO[ExitCode] =
    BlazeServerBuilder[IO]
      .bindHttp(8080, "0.0.0.0")
      .withHttpApp(routes)
      .serve
      .compile
      .drain
      .as(ExitCode.Success)
}
