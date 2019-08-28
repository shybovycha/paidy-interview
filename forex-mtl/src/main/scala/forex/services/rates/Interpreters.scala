package forex.services.rates

import cats.Applicative
import cats.effect.ConcurrentEffect
import cats.effect.Timer
import forex.domain.Rate
import forex.services.rates.oneforge.OneForgeInterpreter
import forex.services.rates.oneforge.cache.Cache
import forex.services.rates.oneforge._

object Interpreters {
  def dummy[F[_]: Applicative]: Algebra[F] = OneForgeInterpreter[F](OneForgeDummyService[F])

  def live[F[_]: ConcurrentEffect: Timer](implicit cache: Cache[F, Rate.Pair, Rate]): Algebra[F] = OneForgeInterpreter[F](OneForgeLiveService[F])
}
