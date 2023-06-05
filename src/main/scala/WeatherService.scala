import TemperatureType.TemperatureType
import cats.effect.IO
import io.circe.Encoder

import scala.util.Try

// Case classes to represent interested part of JSON responses from OpenWeatherMap
case class WeatherCondition(main: String, description: String)
case class CurrentWeather(temp: Double, weather: List[WeatherCondition])
case class WeatherAlert(event: String, start: Long, end: Long, description: String)

// Case class to represent JSON response to the client of this Weather Server
case class WeatherResponse(currentWeather: CurrentWeather, temperatureType: TemperatureType, alerts: List[WeatherAlert])

trait WeatherService {
  /**
   * Try to get WeatherResponse
   * @param latitude
   * @param longitude
   * @param units unit type used for weather temperature like C or F
   * @return
   */
  def getWeather(latitude: Double, longitude: Double, units: String): IO[Try[WeatherResponse]]
}

object TemperatureType extends Enumeration {
  type TemperatureType = Value
  val Hot, Moderate, Cold, Unknown = Value
}

object JsonProtocol {
  implicit val temperatureTypeEnumEncoder: Encoder[TemperatureType.Value] = Encoder.encodeString.contramap(_.toString)
}

object WeatherService {
  private val unitsMetric = "metric"
  //key: the unit, value: function1 that takes temperature and returns temperature type
  //Only support "metric" for now.
  private val temperatureTypeBuilderMap = Map[String, Double => TemperatureType](
    unitsMetric -> (t => {
      if (t < 10) TemperatureType.Cold
      else if (t > 25) TemperatureType.Hot
      else TemperatureType.Moderate
    })
  )

  def buildTemperatureType(t: Double, units: String): TemperatureType = {
    val builder: Double => TemperatureType = temperatureTypeBuilderMap.getOrElse(units, _ => TemperatureType.Unknown)
    builder(t)
  }

  def isValidLatitude(latitude: Double): Boolean = latitude >= -90 && latitude <= 90

  def isValidLongitude(longitude: Double): Boolean = longitude >= -180 && longitude <= 180
}