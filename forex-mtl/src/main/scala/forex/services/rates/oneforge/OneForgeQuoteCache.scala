package forex.services.rates.oneforge

import cats.Monad
import cats.effect.{ Concurrent, Timer }
import cats.implicits.toFunctorOps
import cats.syntax.all._
import forex.domain.Rate
import forex.services.cache.SelfRefreshingCache
import forex.services.cache.SelfRefreshingCache.{ createCache, createRepeatedTrigger }

import scala.concurrent.duration.FiniteDuration

object OneForgeQuoteCache {

  def create[F[_]: Concurrent: Timer](
      client: OneForgeClient[F],
      dataExpiresIn: FiniteDuration
  ): F[SelfRefreshingCache[F, Rate.Pair, Rate]] = {
    val refreshTrigger = createRepeatedTrigger[F](dataExpiresIn)
    val refreshFn      = refreshRatesCache[F](client, _)

    createCache[F, Rate.Pair, Rate](Map.empty, refreshFn, refreshTrigger)
  }

  private def refreshRatesCache[F[_]: Monad](client: OneForgeClient[F],
                                             existingRates: Map[Rate.Pair, Rate]): F[Map[Rate.Pair, Rate]] =
    for {
      currencyPairs <- getCurrencyPairs(client, existingRates)
      rates <- client.fetchQuotes(currencyPairs, uri => client.oneForgeConvertRate(uri))
    } yield updateCache(existingRates, rates)

  private def getCurrencyPairs[F[_]: Monad](client: OneForgeClient[F],
                                            existingRates: Map[Rate.Pair, Rate]): F[List[Rate.Pair]] =
    if (existingRates.isEmpty) {
      client.fetchPossiblePairs(client.oneForgeSymbols)
    } else {
      existingRates.keySet.toList.pure[F]
    }

  private def updateCache(existingRates: Map[Rate.Pair, Rate], newRates: List[Rate]): Map[Rate.Pair, Rate] =
    newRates.foldRight(existingRates)((rate, acc) => acc.updated(rate.pair, rate))

}
