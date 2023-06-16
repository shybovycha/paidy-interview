package forex.services.rates.oneforge

import cats.effect.{Async, Resource}
import forex.config.ForexConfig
import org.http4s.client.Client

object Interpreters {

  def live[F[_]: Async](config: ForexConfig, httpClient: Resource[F, Client[F]]): LiveInterpreter[F] =
    new LiveInterpreter[F](config, httpClient)

}
