import OpenWeatherMapJsonHelper.parseJsonByKey
import WeatherService.{buildTemperatureType, isValidLatitude, isValidLongitude}
import cats.effect.IO
import io.circe.Decoder.Result
import io.circe.generic.auto._
import io.circe.{Decoder, Json}
import org.http4s.Uri
import org.http4s.circe._
import org.http4s.client.Client
import org.typelevel.log4cats._
import org.typelevel.log4cats.slf4j._

import scala.util.{Failure, Try}

/**
 * WeatherService implementation for OpenWeatherMap
 * @param client Used to call OpenWeatherMap API
 * @param apiUrl OpenWeatherMap URL
 * @param apiId OpenWeatherMap api eky
 * @param apiExclude OpenWeatherMap exclude query parameter
 */
class WeatherServiceImpl(client: Client[IO], apiUrl: String, apiId: String, apiExclude: String) extends WeatherService {

  val logger = LoggerFactory[IO].getLogger
  override def getWeather(latitude: Double, longitude: Double, units: String): IO[Try[WeatherResponse]] = {
    if (!isValidLatitude(latitude) || !isValidLongitude(longitude)) {
      IO.pure(Failure(new IllegalArgumentException("Invalid latitude or longitude")))
    } else {
      val uri: Uri = Uri.unsafeFromString(s"$apiUrl?lat=$latitude&lon=$longitude&appid=$apiId&exclude=$apiExclude&units=$units")
      val call = client.expect[Json](uri).map { json =>
        buildResponseBody(json, units)
      }.handleErrorWith(e => handleError(e))
      for {
        _ <- logger.info(s"client: ${client.toString} $uri")
        r <- call
      } yield r
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

  def handleError(error: Throwable): IO[Try[WeatherResponse]] = {
    for {
      _ <- logger.error(error.getMessage)
    } yield Failure(new Throwable("Error from openWeatherMap API"))
  }
}

object OpenWeatherMapJsonHelper {
  // in the given Json, find the first targetKey, and decode its Json object to T
  def parseJsonByKey[T](json: Json, targetKey: String)(implicit d: Decoder[T]): Option[Result[T]] = json.hcursor.downField(targetKey).focus.map(_.as[T])
}