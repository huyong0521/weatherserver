import TemperatureType.{Hot, Moderate}
import cats.effect.{IO, Resource}
import io.circe.Json
import io.circe.parser.parse
import munit.CatsEffectSuite
import org.http4s.client.Client
import org.http4s.{EntityDecoder, Uri}
import org.mockito.ArgumentMatchersSugar.any
import org.mockito.MockitoSugar._

import scala.util.{Failure, Try}

class WeatherServiceSpec extends CatsEffectSuite {
  val apiUrl = "http://example.com"
  val apiId = "api-id"
  val apiExclude = "api-exclude"

  val mockClient = mock[Client[IO]]
  val httpClient: Resource[IO, Client[IO]] = Resource.pure[IO, Client[IO]](mockClient)

  val weatherService = new WeatherServiceImpl(httpClient, apiUrl, apiId, apiExclude)

  val units = "metric"

    test ("return Failure for invalid latitude or longitude") {
      val latitude = 100.0
      val longitude = 200.0

      val expectedFailure = Failure(new IllegalArgumentException("Invalid latitude or longitude"))
      weatherService.getWeather(latitude, longitude, units).flatMap { result =>
        IO(assertEquals(result.toString, expectedFailure.toString))
      }
    }

  test ("return Success with WeatherResponse for valid latitude and longitude")  {
    val latitude = 40.0
    val longitude = -80.0
    val mockedOpenMapJsonResponse = parse(
      """
        |{
        |  "current": {
        |    "temp": 30.0,
        |    "weather": [
        |      {
        |        "main": "Clear",
        |        "description": "clear sky"
        |      }
        |    ]
        |  },
        |  "alerts": [
        |    {
        |      "event": "Heat Advisory",
        |      "start": 1621955200,
        |      "end": 1621965200,
        |      "description": "Expect hot temperatures."
        |    }
        |  ]
        |}
        |""".stripMargin).getOrElse(Json.Null)

    mockThenCallService(mockedOpenMapJsonResponse, latitude, longitude).flatMap(result => {
      val expectedResult = WeatherResponse(
        CurrentWeather(30.0,List(WeatherCondition("Clear","clear sky"))),Hot,List(WeatherAlert("Heat Advisory",1621955200,1621965200,"Expect hot temperatures.")))

      for {
        _ <- IO(assert(result.isSuccess))
        _ <- IO(assertEquals(expectedResult, result.get))
      } yield ()
    })
  }

  test("return Success when alerts not found but current found weather in the JSON response") {
    val latitude = 40.0
    val longitude = -80.0
    val mockedOpenMapJsonResponse = parse(
      """
        |{
        |  "current": {
        |    "temp": 25.0,
        |    "weather": [
        |      {
        |        "main": "Clear",
        |        "description": "clear sky"
        |      }
        |    ]
        |  }
        |}
        |""".stripMargin).getOrElse(Json.Null)

    mockThenCallService(mockedOpenMapJsonResponse, latitude, longitude).flatMap(result => {
      for {
        _ <- IO(assert(result.isSuccess))
        _ <- IO(assertEquals(25.0, result.get.currentWeather.temp))
        _ <- IO(assertEquals(Moderate, result.get.temperatureType))
      } yield ()
    })
  }

  test("return Failure when current weather not found in the JSON response") {
    val latitude = 40.0
    val longitude = -80.0
    val mockedOpenMapJsonResponse = parse(
      """
        |{
        |  "alerts": [
        |    {
        |      "event": "Heat Advisory",
        |      "start": 1621955200,
        |      "end": 1621965200,
        |      "description": "Expect hot temperatures."
        |    }
        |  ]
        |}
        |""".stripMargin).getOrElse(Json.Null)

    mockThenCallService(mockedOpenMapJsonResponse, latitude, longitude).flatMap(result => {
      for {
        _ <- IO(assert(result.isFailure))
        _ <- IO(assertEquals("Current weather not found", result.failed.get.getMessage))
      } yield ()
    })
  }

  private def mockThenCallService(jsonResponse: Json, latitude: Double, longitude: Double): IO[Try[WeatherResponse]] = {
    when(mockClient.expect[Json](any[Uri])(any[EntityDecoder[IO, Json]])).thenReturn(IO.pure(jsonResponse))
    val httpClient: Resource[IO, Client[IO]] = Resource.pure[IO, Client[IO]](mockClient)
    val weatherService = new WeatherServiceImpl(httpClient, apiUrl, apiId, apiExclude)
    weatherService.getWeather(latitude, longitude, units)
  }

}
