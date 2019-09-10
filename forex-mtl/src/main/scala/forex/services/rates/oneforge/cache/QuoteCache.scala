package forex.services.rates.oneforge.cache

import cats.effect._
import cats.implicits._
import forex.domain.Rate
import org.http4s.Uri

import scala.concurrent.duration.FiniteDuration

object QuoteCache {

  def create[F[_]: ConcurrentEffect: Timer](client: OneForgeLiveClient[F], dataExpiresIn: FiniteDuration): F[Cache[F, Rate.Pair, Rate]] =
    SelfRefreshingCache.createCache[F, Rate.Pair, Rate](
      Map.empty,
      SelfRefreshingCache.createAsyncRefresher(
        SelfRefreshingCache.createRecursiveRefresher(refreshRatesCache[F](client), SelfRefreshingCache.createRepeatedTrigger(dataExpiresIn))
      )
    )

  def refreshRatesCache[F[_]: ConcurrentEffect](client: OneForgeLiveClient[F])(existingRates: Map[Rate.Pair, Rate]): F[Map[Rate.Pair, Rate]] =
    getCurrencyPairs[F](client, existingRates)
      .flatMap(client.fetchQuotes(_, OneForgeLiveClient.oneForgeConvertRate[F]))
      .map(updateCache(existingRates, _))

  def getCurrencyPairs[F[_]: ConcurrentEffect](client: OneForgeLiveClient[F], existingRates: Map[Rate.Pair, Rate]): F[List[Rate.Pair]] =
    if (existingRates.isEmpty) {
      client.fetchPossiblePairs((uri: Uri) => OneForgeLiveClient.oneForgeSymbols[F](uri))
    } else {
      existingRates.keySet.toList.pure[F]
    }

  def updateCache(existingRates: Map[Rate.Pair, Rate], newRates: List[Rate]): Map[Rate.Pair, Rate] =
    newRates.foldRight(existingRates)((rate: Rate, acc: Map[Rate.Pair, Rate]) => acc.updated(rate.pair, rate))

}
