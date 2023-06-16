package forex.services.rates

import cats.data.OptionT
import cats.effect.Temporal
import cats.syntax.all._
import cats.{Functor, Monad}
import forex.domain.Rate
import forex.services.cache.{Cache, SelfRefreshingCache}
import forex.services.rates.Errors.Error
import forex.services.rates.Errors.Error.CacheIsOutOfDate
import forex.services.rates.oneforge.{Algebra => OneForgeClientAlgebra}

import scala.concurrent.duration.FiniteDuration

class OneForgeInterpreter[F[_]: Functor](cache: Cache[F, Rate.Pair, Rate]) extends Algebra[F] {

  override def get(pair: Rate.Pair): F[Error Either Rate] =
    OptionT(cache.get(pair)).toRight[Error](CacheIsOutOfDate()).value

}

object OneForgeInterpreter {

  def createCache[F[_]: Temporal](ttl: FiniteDuration, oneForge: OneForgeClientAlgebra[F]): SelfRefreshingCache[F, Rate.Pair, Rate] = {
    implicit val oneForgeClient: OneForgeClientAlgebra[F] = oneForge

    val refreshFn      = refreshRatesCache[F](_)

    SelfRefreshingCache.createCache[F, Rate.Pair, Rate](Map.empty, refreshFn, ttl)
  }

  private def refreshRatesCache[F[_]: Monad](existingRates: Map[Rate.Pair, Rate])(implicit oneForge: OneForgeClientAlgebra[F]): F[Map[Rate.Pair, Rate]] =
    for {
      currencyPairs <- getCurrencyPairs(existingRates)
      rates <- oneForge.rates(currencyPairs)
    } yield updateCache(existingRates, rates)

  private def getCurrencyPairs[F[_]: Monad](existingRates: Map[Rate.Pair, Rate])(implicit oneForge: OneForgeClientAlgebra[F]): F[List[Rate.Pair]] =
    if (existingRates.isEmpty) {
      oneForge.knownSymbols()
    } else {
      existingRates.keySet.toList.pure[F]
    }

  private def updateCache(existingRates: Map[Rate.Pair, Rate], newRates: List[Rate]): Map[Rate.Pair, Rate] =
    newRates.foldRight(existingRates)((rate, acc) => acc.updated(rate.pair, rate))

}
