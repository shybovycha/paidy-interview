package forex.services.rates

import cats.Applicative
import cats.effect.Temporal
import forex.domain.Rate
import forex.services.cache.Cache

object Interpreters {

  def dummy[F[_]: Applicative]: DummyInterpreter[F] =
    new DummyInterpreter[F]

  def live[F[_]: Temporal](cache: Cache[F, Rate.Pair, Rate]): OneForgeInterpreter[F] =
    new OneForgeInterpreter[F](cache)

}
