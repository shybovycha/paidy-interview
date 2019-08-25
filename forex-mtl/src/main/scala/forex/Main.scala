package forex

import cats.effect.IO
import cats.effect._
import cats.syntax.all._
import forex.config._
import forex.domain.Rate
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

  def stream: Stream[F, Unit] =
    for {
      config <- Config.stream("app")
      cache <- Stream.eval(SelfRefreshingCache.create[F, Rate.Pair, Rate](QuoteRefresher.refreshRatesCache[F], 5.minutes))
      // TODO: check if this does start refresher in a separate thread indeed and that 30 second response from API won't break this
      _ = Stream.eval(IO(cache).unsafeRunSync)
      module = new Module[F](config, cache)
      _ <- BlazeServerBuilder[F]
            .bindHttp(config.http.port, config.http.host)
            .withHttpApp(module.httpApp)
            .serve
    } yield ()

}
