package forex.services.rates.oneforge.cache

import cats.effect._
import cats.implicits._
import forex.domain.Rate
import forex.services.rates.oneforge.cache.OneForgeLiveClient.{oneForgeConvertRate, oneForgeSymbols}
import forex.services.rates.oneforge.cache.SelfRefreshingCache.{createAsyncRefresher, createCache, createRecursiveRefresher, createRepeatedTrigger}

import scala.concurrent.duration.FiniteDuration

object QuoteCache {

  def create[F[_]: ConcurrentEffect: Timer](client: OneForgeLiveClient[F], dataExpiresIn: FiniteDuration): F[Cache[F, Rate.Pair, Rate]] = {
    val trigger = createRepeatedTrigger(dataExpiresIn)
    val refreshFn = refreshRatesCache[F](client)
    val refresher = createRecursiveRefresher(refreshFn, trigger)
    val asyncRefresher = createAsyncRefresher(refresher)

    createCache[F, Rate.Pair, Rate](Map.empty, asyncRefresher)
  }

  def refreshRatesCache[F[_]: ConcurrentEffect](client: OneForgeLiveClient[F])(existingRates: Map[Rate.Pair, Rate]): F[Map[Rate.Pair, Rate]] = {
    for {
      currencyPairs <- getCurrencyPairs[F](client, existingRates)
      quotes <- client.fetchQuotes(currencyPairs, oneForgeConvertRate[F])
    } yield updateCache(existingRates, quotes)
  }

  def getCurrencyPairs[F[_]: ConcurrentEffect](client: OneForgeLiveClient[F], existingRates: Map[Rate.Pair, Rate]): F[List[Rate.Pair]] =
    if (existingRates.isEmpty) {
      client.fetchPossiblePairs(oneForgeSymbols[F](_))
    } else {
      existingRates.keySet.toList.pure[F]
    }

  def updateCache(existingRates: Map[Rate.Pair, Rate], newRates: List[Rate]): Map[Rate.Pair, Rate] =
    newRates.foldRight(existingRates)((rate: Rate, acc: Map[Rate.Pair, Rate]) => acc.updated(rate.pair, rate))

}
