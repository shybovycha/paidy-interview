package forex

import cats.effect._
import cats.syntax.all._
import com.comcast.ip4s.{Host, IpLiteralSyntax, Port}
import forex.config._
import forex.services.rates.OneForgeInterpreter
import forex.services.rates.oneforge.{LiveInterpreter => OneForgeLiveInterpreter}
import fs2.Stream
import fs2.io.net.Network
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder

object Main extends IOApp {

  override def run(args: List[String]): IO[ExitCode] =
    new Application[IO].stream.compile.drain
      .as(ExitCode.Success)

}

class Application[F[_]: Async: Network] {

  def stream: Stream[F, Unit] =
    for {
      config <- Config.stream("app")
      httpClient <- Stream.eval(EmberClientBuilder
        .default[F]
        .build
        .pure[F])
      oneForgeClient = new OneForgeLiveInterpreter[F](config.forex, httpClient)
      ratesCache = OneForgeInterpreter.createCache[F](config.forex.ttl, oneForgeClient)
      module = new Module[F](config, ratesCache)
      server = EmberServerBuilder
        .default[F]
        .withHostOption(Host.fromString(config.http.host))
        .withPort(Port.fromInt(config.http.port).getOrElse(port"80"))
        .withHttpApp(module.httpApp)
        .build
      _ <- Stream.eval(ratesCache.start())
      _ <- Stream.resource(server)
    } yield ()

}
