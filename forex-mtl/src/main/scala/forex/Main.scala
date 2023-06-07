package forex

import cats.effect._
import scala.concurrent.ExecutionContext

import forex.config._
import forex.services.rates.oneforge.cache.OneForgeLiveClient
import forex.services.rates.oneforge.cache.QuoteCache
import fs2.Stream
import org.http4s.server.blaze.BlazeServerBuilder

object Main extends IOApp {

  override def run(args: List[String]): IO[ExitCode] =
    new Application[IO].stream(executionContext).compile.drain.as(ExitCode.Success)

}

class Application[F[_]: ConcurrentEffect: Timer] {

  def stream(ec: ExecutionContext): Stream[F, Unit] =
    for {
      config <- Config.stream("app")
      oneForgeClient = new OneForgeLiveClient[F](config.forex)
      cache <- Stream.eval(QuoteCache.create(oneForgeClient, config.forex.dataExpiresIn))
      module = new Module[F](config, cache)
      _ <- BlazeServerBuilder[F](ec)
            .bindHttp(config.http.port, config.http.host)
            .withHttpApp(module.httpApp)
            .serve
    } yield ()

}
