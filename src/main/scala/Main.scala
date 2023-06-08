import cats.effect.{IO, IOApp}
import org.typelevel.log4cats._
import org.typelevel.log4cats.slf4j._

object Main extends IOApp.Simple {

  implicit val logger  = LoggerFactory[IO].getLogger

  val run = WeatherServer.run[IO]
}
