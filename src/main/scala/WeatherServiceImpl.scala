import OpenWeatherMapJsonHelper.parseJsonByKey
import WeatherService.{buildTemperatureType, isValidLatitude, isValidLongitude}
import cats.effect.Temporal
import cats.implicits._
import cats.{Monad, MonadError}
import io.chrisdavenport.circuit.CircuitBreaker
import io.circe.Decoder.Result
import io.circe.generic.auto._
import io.circe.{Decoder, Json}
import org.http4s.client.Client
import org.http4s.{EntityDecoder, Uri}
import org.typelevel.log4cats._

import scala.util.{Failure, Try}

/**
 * WeatherService implementation for OpenWeatherMap
 *
 * @param client     Used to call OpenWeatherMap API
 * @param apiUrl     OpenWeatherMap URL
 * @param apiId      OpenWeatherMap api eky
 * @param apiExclude OpenWeatherMap exclude query parameter
 * @param circuitBreaker protect call to open weather map API
 */
class WeatherServiceImpl[F[+_] : Monad](client: Client[F], apiUrl: String, apiId: String, apiExclude: String, circuitBreaker: CircuitBreaker[F])
                                             (implicit decoder: EntityDecoder[F, Json],
                                              logger: Logger[F],
                                              monadError: MonadError[F, Throwable],
                                              temporal: Temporal[F]) extends WeatherService[F] {

  override def getWeather(latitude: Double, longitude: Double, units: String): F[Try[WeatherResponse]] = {
    if (!isValidLatitude(latitude) || !isValidLongitude(longitude)) {
      Failure[WeatherResponse](new IllegalArgumentException("Invalid latitude or longitude")).pure[F]
    } else {
      val uri: Uri = Uri.unsafeFromString(s"$apiUrl?lat=$latitude&lon=$longitude&appid=$apiId&exclude=$apiExclude&units=$units")
      val call: F[Json] = client.expect[Json](uri)
      val protectedCall = for {
        cbs <- circuitBreaker.state
        _ <- logger.info(s"Circuit Breaker Status: $cbs for $circuitBreaker")
        r <- circuitBreaker.protect(call)
      }  yield r

      val parseJson: F[Try[WeatherResponse]] = protectedCall.map { json =>
        buildResponseBody(json, units)
      }.handleErrorWith(e => handleError(e))

      logger.info(s"client: ${client.toString} $uri") *> parseJson
    }
  }

  def buildAlerts(json: Json): Try[List[WeatherAlert]] = {
    val alertsOption: Option[Result[List[WeatherAlert]]] = parseJsonByKey(json, "alerts")
    alertsOption.getOrElse(Right(List.empty)).toTry
  }

  def buildCurrentWeather(json: Json): Try[CurrentWeather] = {
    val currentWeatherOption: Option[Result[CurrentWeather]] = parseJsonByKey(json, "current")
    currentWeatherOption match {
      case Some(r) => r.toTry
      case None => Failure(new Throwable("Current weather not found"))
    }
  }


  def buildResponseBody(json: Json, units: String): Try[WeatherResponse] = {
    for {
      currentWeather <- buildCurrentWeather(json)
      alerts <- buildAlerts(json)
    } yield WeatherResponse(currentWeather, buildTemperatureType(currentWeather.temp, units), alerts)
  }

  def handleError(error: Throwable): F[Try[WeatherResponse]] = {
    for {
      _ <- logger.error(s"${error.getClass}: ${error.getMessage}")
    } yield Failure(new Throwable("Error from openWeatherMap API"))
  }
}

object OpenWeatherMapJsonHelper {
  // in the given Json, find the first targetKey, and decode its Json object to T
  def parseJsonByKey[T](json: Json, targetKey: String)(implicit d: Decoder[T]): Option[Result[T]] = {
    json.hcursor.downField(targetKey).focus.map(_.as[T])
  }
}