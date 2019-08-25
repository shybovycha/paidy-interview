package forex

import cats.effect.IO
import cats.effect._
import cats.syntax.functor._
import forex.config._
import forex.domain.Rate
import forex.services.rates.oneforge.cache.Cache
import forex.services.rates.oneforge.cache.SelfRefreshingCache
import forex.services.rates.oneforge.clients.QuoteRefresher
import fs2.Stream
import org.http4s.server.blaze.BlazeServerBuilder

import scala.concurrent.duration._

object Main extends IOApp {

  override def run(args: List[String]): IO[ExitCode] =
    new Application[IO].stream.compile.drain.as(ExitCode.Success)

}

class Application[F[_]: ConcurrentEffect: Timer] {

  private val timeout = 5.minutes

  private implicit val cache: F[Cache[F, Rate.Pair, Rate]] = SelfRefreshingCache.create[F, Rate.Pair, Rate](QuoteRefresher.refreshRatesCache[F], timeout)

  def stream: Stream[F, Unit] =
    for {
      config <- Config.stream("app")
      module = new Module[F](config)
      _ <- Stream.eval(IO(cache).unsafeRunSync)
      _ <- BlazeServerBuilder[F]
            .bindHttp(config.http.port, config.http.host)
            .withHttpApp(module.httpApp)
            .serve
    } yield ()

}
