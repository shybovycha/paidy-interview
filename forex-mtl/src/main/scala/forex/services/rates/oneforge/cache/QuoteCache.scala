package forex.services.rates.oneforge.cache

import java.net.ConnectException
import java.util.concurrent.TimeoutException

import cats.Applicative
import cats.effect._
import cats.implicits._
import forex.config.ApplicationConfig
import forex.config.ForexConfig
import forex.domain.Currency
import forex.domain.Price
import forex.domain.Rate
import forex.domain.Timestamp
import forex.services.rates.Errors.Error.BadConfiguration
import forex.services.rates.Errors.Error.BadResponseFailure
import forex.services.rates.Errors.Error.CanNotParseConvertUri
import forex.services.rates.Errors.Error.CanNotParseSymbolsUri
import forex.services.rates.Errors.Error.NetworkFailure
import forex.services.rates.Errors.Error.UnknownFailure
import forex.services.rates.Errors._
import io.circe.generic.auto._
import org.http4s.EntityDecoder
import org.http4s.InvalidMessageBodyFailure
import org.http4s.Uri
import org.http4s.circe.jsonOf
import org.http4s.client.UnexpectedStatus
import org.http4s.client.blaze.BlazeClientBuilder

import scala.concurrent.ExecutionContext.Implicits.global

class QuoteCache(config: ForexConfig) {

  case class QuoteDTO(symbol: String, price: Double, timestamp: Int)

  private def fetchPossiblePairs[F[_]: ConcurrentEffect]: F[Either[Error, Map[Rate.Pair, Option[Rate]]]] = {
    symbolsUri
      .fold(
        error => Either.left[Error, Map[Rate.Pair, Option[Rate]]](CanNotParseSymbolsUri(error.toString)).pure[F],
        uri => oneForgeSymbols(uri)
      )
  }

  def refreshRatesCache[F[_]: ConcurrentEffect](existingRates: Map[Rate.Pair, Option[Rate]]): F[Option[Map[Rate.Pair, Option[Rate]]]] = {
    val updateCache = (rates: List[Rate]) =>
      rates.foldRight(existingRates)((rate: Rate, acc: Map[Rate.Pair, Option[Rate]]) => acc.updated(rate.pair, Some[Rate](rate)))

    getCurrencyPairs[F](existingRates)
      .map(_.keySet.map(pair => (pair.from.toString, pair.to.toString)).toList)
      .flatMap(fetchQuotes(_))
      .map(_.toOption.map(_.map(quoteToRate)))
      .map(_.map(updateCache))
  }

  private def fetchQuotes[F[_]: ConcurrentEffect](currencyPairs: List[(String, String)]): F[Error Either List[QuoteDTO]] =
    convertRateUri(currencyPairs)
      .fold(
        error => Either.left[Error, List[QuoteDTO]](CanNotParseConvertUri(error.toString)).pure[F],
        uri => oneForgeConvertRate(uri)
      )

  private def getCurrencyPairs[F[_]: Applicative: ConcurrentEffect](existingRates: Map[Rate.Pair, Option[Rate]]): F[Map[Rate.Pair, Option[Rate]]] =
    if (existingRates.isEmpty) {
      fetchPossiblePairs.map((e: Either[Error, Map[Rate.Pair, Option[Rate]]]) => e.getOrElse(Map.empty))
    } else {
      existingRates.pure[F]
    }

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

  private def quoteToRate(quote: QuoteDTO): Rate = {
    val pair = parseCurrencyPairFromCode(quote.symbol)
    val price = Price(quote.price * 100)
    val timestamp = Timestamp.now

    Rate(pair, price, timestamp)
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

object QuoteCache {

  def create[F[_]: ConcurrentEffect: Timer](config: ApplicationConfig): F[Cache[F, Rate.Pair, Option[Rate]]] = {
    val quoteRefresher = new QuoteCache(config.forex)

    SelfRefreshingCache.create[F, Rate.Pair, Option[Rate]](Map.empty, quoteRefresher.refreshRatesCache[F], config.forex.dataExpiresIn)
  }

}
