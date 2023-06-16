package forex

import cats.effect.{Async, Resource}
import forex.config.ApplicationConfig
import forex.http.rates.RatesHttpRoutes
import forex.programs._
import forex.services._
import forex.services.rates.OneForgeInterpreter
import forex.services.rates.oneforge.{LiveInterpreter => OneForgeLiveInterpreter}
import org.http4s._
import org.http4s.client.{Client => HttpClient}
import org.http4s.server.middleware.{AutoSlash, Timeout}

class Module[F[_]: Async](config: ApplicationConfig, httpClient: Resource[F, HttpClient[F]]) {

  private val oneForgeClient = new OneForgeLiveInterpreter[F](config.forex, httpClient)
  val ratesCache = OneForgeInterpreter.createCache[F](config.forex.ttl, oneForgeClient)

  private val ratesService: RatesService[F] = RatesServices.live[F](ratesCache)

  private val ratesProgram: RatesProgram[F] = RatesProgram[F](ratesService)

  private val ratesHttpRoutes: HttpRoutes[F] = new RatesHttpRoutes[F](ratesProgram).routes

  private type PartialMiddleware = HttpRoutes[F] => HttpRoutes[F]
  private type TotalMiddleware   = HttpApp[F] => HttpApp[F]

  private val routesMiddleware: PartialMiddleware = {
    { http: HttpRoutes[F] =>
      AutoSlash(http)
    }
  }

  private val appMiddleware: TotalMiddleware = { http: HttpApp[F] =>
    Timeout(config.http.timeout)(http)
  }

  private val http: HttpRoutes[F] = ratesHttpRoutes

  val httpApp: HttpApp[F] = appMiddleware(routesMiddleware(http).orNotFound)

}
