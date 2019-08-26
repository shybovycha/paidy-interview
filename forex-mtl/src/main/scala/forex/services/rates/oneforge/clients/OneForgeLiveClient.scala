package forex.services.rates.oneforge.clients

import cats.Functor
import cats.effect._
import cats.implicits._
import forex.domain.Rate
import forex.services.rates.Algebra
import forex.services.rates.Errors.Error.CacheIsOutOfDate
import forex.services.rates.Errors.Error.CanNotRetrieveFromCache
import forex.services.rates.Errors._
import forex.services.rates.oneforge.cache.Cache

class OneForgeLiveClient[F[_]: Functor](implicit cache: Cache[F, Rate.Pair, Option[Rate]]) extends Algebra[F] {

  override def get(pair: Rate.Pair): F[Error Either Rate] =
    cache.get(pair)
      .map(_.fold(Either.left[Error, Rate](CacheIsOutOfDate()))(_.toRight(CanNotRetrieveFromCache())))

}

object OneForgeLiveClient {

  def apply[F[_]: ConcurrentEffect: Timer](implicit cache: Cache[F, Rate.Pair, Option[Rate]]): Algebra[F] =
    new OneForgeLiveClient

}
