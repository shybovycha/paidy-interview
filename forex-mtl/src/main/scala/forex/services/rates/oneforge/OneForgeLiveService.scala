package forex.services.rates.oneforge

import cats.Functor
import cats.effect._
import cats.implicits._
import forex.domain.Rate
import forex.services.rates.Algebra
import forex.services.rates.Errors.Error.CacheIsOutOfDate
import forex.services.rates.Errors._
import forex.services.rates.oneforge.cache.Cache

class OneForgeLiveService[F[_]: Functor](implicit cache: Cache[F, Rate.Pair, Rate]) extends Algebra[F] {

  override def get(pair: Rate.Pair): F[Error Either Rate] =
    cache.get(pair)
      .map(_.toRight(CacheIsOutOfDate()))

}

object OneForgeLiveService {

  def apply[F[_]: ConcurrentEffect: Timer](implicit cache: Cache[F, Rate.Pair, Rate]): Algebra[F] =
    new OneForgeLiveService

}
