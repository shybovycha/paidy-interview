package forex.services.rates.oneforge.clients

import java.net.ConnectException
import java.util.concurrent.TimeoutException

import cats.Functor
import cats.effect._
import cats.implicits._
import forex.config.ApplicationConfig
import forex.config.ForexConfig
import forex.domain.Currency
import forex.domain.Price
import forex.domain.Rate
import forex.domain.Timestamp
import forex.services.rates.Algebra
import forex.services.rates.Errors.Error.BadConfiguration
import forex.services.rates.Errors.Error.BadResponseFailure
import forex.services.rates.Errors.Error.CacheIsOutOfDate
import forex.services.rates.Errors.Error.CanNotRetrieveFromCache
import forex.services.rates.Errors.Error.NetworkFailure
import forex.services.rates.Errors.Error.UnknownFailure
import forex.services.rates.Errors._
import forex.services.rates.oneforge.cache.Cache
import forex.services.rates.oneforge.cache.SelfRefreshingCache
import io.circe.generic.auto._
import org.http4s.EntityDecoder
import org.http4s.InvalidMessageBodyFailure
import org.http4s.Uri
import org.http4s.circe.jsonOf
import org.http4s.client.UnexpectedStatus
import org.http4s.client.blaze.BlazeClientBuilder

import scala.concurrent.ExecutionContext.Implicits.global

class OneForgeLiveClient[F[_]: Functor](implicit cache: Cache[F, Rate.Pair, Option[Rate]]) extends Algebra[F] {

  override def get(pair: Rate.Pair): F[Error Either Rate] =
    cache.get(pair)
      .map((e: Option[Option[Rate]]) => e.fold(Either.left[Error, Rate](CacheIsOutOfDate()))(_.toRight(CanNotRetrieveFromCache())))

}

object OneForgeLiveClient {

  def apply[F[_]: ConcurrentEffect: Timer](implicit cache: Cache[F, Rate.Pair, Option[Rate]]): Algebra[F] =
    new OneForgeLiveClient

}

class QuoteRefresher(config: ForexConfig) {

  case class QuoteDTO(symbol: String, price: Double, timestamp: Int)

  def fetchPossiblePairs[F[_]: ConcurrentEffect]: F[Either[Error, Map[Rate.Pair, Option[Rate]]]] = {
    symbolsUri.fold(
      error => Either.left[Error, Map[Rate.Pair, Option[Rate]]](error).pure[F],
      uri => oneForgeSymbols(uri)
    )
  }

  def refreshRatesCache[F[_]: ConcurrentEffect](existingRates: Map[Rate.Pair, Option[Rate]]): F[Option[Map[Rate.Pair, Option[Rate]]]] = {
    val currencyPairs: List[(String, String)] = existingRates.keySet.map(pair => (pair.from.toString, pair.to.toString)).toList
    val newQuoteDTOs: F[Error Either List[QuoteDTO]] = fetchQuotes(currencyPairs)

    val newRates: F[Option[List[Rate]]] = newQuoteDTOs.map(_.toOption.map(_.map(quoteToRate)))

    def updateCache(rates: List[Rate]): Map[Rate.Pair, Option[Rate]] =
      rates.foldRight(existingRates)((rate: Rate, acc: Map[Rate.Pair, Option[Rate]]) => acc.updated(rate.pair, Some[Rate](rate)))

    newRates.map(_.map(updateCache))
  }

  private def quoteToRate(quote: QuoteDTO): Rate = {
    val pair = parseCurrencyPairFromCode(quote.symbol)
    val price = Price(quote.price * 100)
    val timestamp = Timestamp.now

    Rate(pair, price, timestamp)
  }

  private def fetchQuotes[F[_]: ConcurrentEffect](currencyPairs: List[(String, String)]): F[Error Either List[QuoteDTO]] =
    convertRateUri(currencyPairs).fold(
      error => Either.left[Error, List[QuoteDTO]](error).pure[F],
      uri => oneForgeConvertRate(uri)
    )

  private def oneForgeConvertRate[F[_]: ConcurrentEffect](uri: Uri): F[Either[Error, List[QuoteDTO]]] = {
    implicit val quoteListDecoder: EntityDecoder[F, List[QuoteDTO]] = jsonOf[F, List[QuoteDTO]]

    BlazeClientBuilder[F](global)
      .resource
      .use(_.expect[List[QuoteDTO]](uri))
      .attempt
      .flatMap(either => either.leftMap(handleRefreshError).pure[F])
  }

  private def oneForgeSymbols[F[_]: ConcurrentEffect](uri: Uri): F[Either[Error, Map[Rate.Pair, Option[Rate]]]] = {
    implicit val quoteListDecoder: EntityDecoder[F, List[String]] = jsonOf[F, List[String]]

    BlazeClientBuilder[F](global)
      .resource
      .use(_.expect[List[String]](uri))
      .attempt
      .map(_.bimap(handleRefreshError, currencyCodesToEmptyCache))
  }

  private def currencyCodesToEmptyCache(currencyCodes: List[String]): Map[Rate.Pair, Option[Rate]] =
    currencyCodes
    .map(parseCurrencyPairFromCode)
    .foldRight(Map.empty[Rate.Pair, Option[Rate]])((elt: Rate.Pair, acc: Map[Rate.Pair, Option[Rate]]) => acc.updated(elt, None))

  private def parseCurrencyPairFromCode(codePair: String): Rate.Pair = {
    val codes = (codePair.substring(0, 3), codePair.substring(3, 6))

    Rate.Pair(Currency.fromString(codes._1), Currency.fromString(codes._2))
  }

  private def handleRefreshError(error: Throwable): Error = error match {
    case _: TimeoutException => NetworkFailure(getErrorMessage(error))
    case _: ConnectException => NetworkFailure(getErrorMessage(error))
    case _: InvalidMessageBodyFailure => BadResponseFailure(getErrorMessage(error))
    case _: UnexpectedStatus => BadResponseFailure(getErrorMessage(error))
    case _ => UnknownFailure(getErrorMessage(error))
  }

  private def getErrorMessage(error: Throwable): String = error.toString

  private def convertRateUri(currencyPairs: List[(String, String)]): Either[Error, Uri] =
    Uri.fromString(config.host)
      .map(_.withPath("/convert")
            .withQueryParam("pairs", currencyPairs.map(e => s"${e._1}${e._2}").mkString(","))
            .withQueryParam("api_key", config.apiKey)
      )
      .leftMap(parseError => BadConfiguration(parseError.message))

  private def symbolsUri: Either[Error, Uri] =
    Uri.fromString(config.host)
      .map(_.withPath("/symbols")
            .withQueryParam("api_key", config.apiKey)
      )
      .leftMap(parseError => BadConfiguration(parseError.message))

}

object QuoteRefresher {

  def createCache[F[_]: ConcurrentEffect: Timer](config: ApplicationConfig): F[Cache[F, Rate.Pair, Option[Rate]]] = {
    val quoteRefresher = new QuoteRefresher(config.forex)

    quoteRefresher.fetchPossiblePairs
      .map(_.fold(Map.empty, identity))
      .flatMap(initialCacheState =>
        SelfRefreshingCache.create[F, Rate.Pair, Option[Rate]](initialCacheState, quoteRefresher.refreshRatesCache[F], config.forex.dataExpiresIn)
      )
  }

}
