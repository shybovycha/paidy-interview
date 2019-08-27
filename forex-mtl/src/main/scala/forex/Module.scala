package forex

import cats.effect.ConcurrentEffect
import cats.effect.Timer
import forex.config.ApplicationConfig
import forex.domain.Rate
import forex.http.rates.RatesHttpRoutes
import forex.programs._
import forex.services._
import forex.services.rates.oneforge.cache.Cache
import org.http4s._
import org.http4s.implicits._
import org.http4s.server.middleware.AutoSlash
import org.http4s.server.middleware.Timeout

class Module[F[_]: ConcurrentEffect: Timer](config: ApplicationConfig, cache: Cache[F, Rate.Pair, Rate]) {

  private val ratesService: RatesService[F] = RatesServices.live[F](implicitly, implicitly, cache)

  private val ratesProgram: RatesProgram[F] = RatesProgram[F](ratesService)

  private val ratesHttpRoutes: HttpRoutes[F] = new RatesHttpRoutes[F](ratesProgram).routes

  type PartialMiddleware = HttpRoutes[F] => HttpRoutes[F]
  type TotalMiddleware   = HttpApp[F] => HttpApp[F]

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
