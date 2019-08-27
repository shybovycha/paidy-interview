package forex.services.rates.oneforge.cache

import java.net.ConnectException
import java.util.concurrent.TimeoutException

import cats.effect._
import cats.syntax.all._
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

class QuoteCache[F[_]: ConcurrentEffect](config: ForexConfig) {

  case class QuoteDTO(symbol: String, price: Double, timestamp: Int)

  private def fetchPossiblePairs: F[Either[Error, List[Rate.Pair]]] = {
    symbolsUri
      .fold(
        error => Either.left[Error, List[Rate.Pair]](CanNotParseSymbolsUri(error.toString)).pure[F],
        uri => oneForgeSymbols(uri)
      )
  }

  def refreshRatesCache(existingRates: Map[Rate.Pair, Rate]): F[Map[Rate.Pair, Rate]] = {
    val updateCache = (rates: List[Rate]) =>
      rates.foldRight(existingRates)((rate: Rate, acc: Map[Rate.Pair, Rate]) => acc.updated(rate.pair, rate))

    getCurrencyPairs(existingRates)
      .flatMap(fetchQuotes)
      .map(_.map(updateCache))
  }

  private def fetchQuotes(currencyPairs: List[Rate.Pair]): F[Error Either List[Rate]] =
    convertRateUri(currencyPairs)
      .fold(
        error => Either.left[Error, List[Rate]](CanNotParseConvertUri(error.toString)).pure[F],
        uri => oneForgeConvertRate(uri)
      )

  private def getCurrencyPairs(existingRates: Map[Rate.Pair, Rate]): F[List[Rate.Pair]] =
    if (existingRates.isEmpty) {
      fetchPossiblePairs.map((e: Either[Error, List[Rate.Pair]]) => e.getOrElse(Nil))
    } else {
      existingRates.keySet.toList.pure[F]
    }

  private def oneForgeConvertRate(uri: Uri): F[Either[Error, List[Rate]]] = {
    implicit val quoteListDecoder: EntityDecoder[F, List[QuoteDTO]] = jsonOf[F, List[QuoteDTO]]

    BlazeClientBuilder[F](global)
      .resource
      .use(_.expect[List[QuoteDTO]](uri))
      .attempt
      .map(_.bimap(handleRefreshError, _.map(quoteToRate)))
  }

  private def oneForgeSymbols(uri: Uri): F[Either[Error, List[Rate.Pair]]] = {
    implicit val quoteListDecoder: EntityDecoder[F, List[String]] = jsonOf[F, List[String]]

    BlazeClientBuilder[F](global)
      .resource
      .use(_.expect[List[String]](uri))
      .attempt
      .map(_.bimap(handleRefreshError, _.map(parseCurrencyPairFromCode)))
  }

  private def quoteToRate(quote: QuoteDTO): Rate = {
    val pair = parseCurrencyPairFromCode(quote.symbol)
    val price = Price(quote.price * 100)
    val timestamp = Timestamp.now

    Rate(pair, price, timestamp)
  }

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

  private def convertRateUri(currencyPairs: List[Rate.Pair]): Either[Error, Uri] =
    Uri.fromString(config.host)
      .map(_.withPath("/convert")
        .withQueryParam("pairs", currencyPairs.map(e => s"${e.from}${e.to}").mkString(","))
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

  def create[F[_]: ConcurrentEffect: Timer](config: ApplicationConfig): F[Cache[F, Rate.Pair, Rate]] = {
    val quoteRefresher = new QuoteCache(config.forex)

    SelfRefreshingCache.create[F, Rate.Pair, Rate](Map.empty, quoteRefresher.refreshRatesCache, config.forex.dataExpiresIn)
  }

}
