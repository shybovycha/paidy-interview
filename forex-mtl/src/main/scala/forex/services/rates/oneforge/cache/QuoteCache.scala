package forex.services.rates.oneforge.cache

import cats.mtl.ApplicativeHandle
import cats.effect._
import cats.syntax.applicative._
import cats.syntax.functor._
import cats.syntax.flatMap._
import cats.mtl.instances.all._
// import cats.syntax.all._
import forex.config.ApplicationConfig
import forex.config.ForexConfig
import forex.domain.Currency
import forex.domain.Price
import forex.domain.Rate
import forex.domain.Timestamp
import forex.services.rates.Errors.Error.BadConfiguration
import forex.services.rates.Errors._
import io.circe.generic.auto._
import org.http4s.EntityDecoder
import org.http4s.Uri
import org.http4s.circe.jsonOf
import org.http4s.client.blaze.BlazeClientBuilder

import scala.concurrent.ExecutionContext.Implicits.global

class QuoteCache[F[_]: ConcurrentEffect](config: ForexConfig)(implicit F: ApplicativeHandle[F, Error]) {

  case class QuoteDTO(symbol: String, price: Double, timestamp: Int)

  def refreshRatesCache(existingRates: Map[Rate.Pair, Rate]): F[Map[Rate.Pair, Rate]] = {
    val updateCache = (rates: List[Rate]) =>
      rates.foldRight(existingRates)((rate: Rate, acc: Map[Rate.Pair, Rate]) => acc.updated(rate.pair, rate))

    getCurrencyPairs(existingRates)
      .flatMap(fetchQuotes)
      .map(updateCache)
  }

  private def fetchPossiblePairs: F[List[Rate.Pair]] = {
    symbolsUri.flatMap(oneForgeSymbols)
  }

  private def fetchQuotes(currencyPairs: List[Rate.Pair]): F[List[Rate]] =
    convertRateUri(currencyPairs).flatMap(oneForgeConvertRate)

  private def getCurrencyPairs(existingRates: Map[Rate.Pair, Rate]): F[List[Rate.Pair]] =
    if (existingRates.isEmpty) {
      fetchPossiblePairs
    } else {
      existingRates.keySet.toList.pure[F]
    }

  private def oneForgeConvertRate(uri: Uri): F[List[Rate]] = {
    implicit val quoteListDecoder: EntityDecoder[F, List[QuoteDTO]] = jsonOf[F, List[QuoteDTO]]

    BlazeClientBuilder[F](global)
      .resource
      .use(_.expect[List[QuoteDTO]](uri))
      .map(_.map(quoteToRate))

      // .handle[Throwable](handleRefreshError) // ???

//      .attempt
//      .map(_.bimap(handleRefreshError, _.map(quoteToRate)))
  }

  private def oneForgeSymbols(uri: Uri): F[List[Rate.Pair]] = {
    implicit val quoteListDecoder: EntityDecoder[F, List[String]] = jsonOf[F, List[String]]

    BlazeClientBuilder[F](global)
      .resource
      .use(_.expect[List[String]](uri))
      .map(_.map(parseCurrencyPairFromCode))

//      .attempt
//      .map(_.bimap(handleRefreshError, _.map(parseCurrencyPairFromCode)))
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

//  private def handleRefreshError(error: Throwable): Error = error match {
//    case _: TimeoutException => NetworkFailure(getErrorMessage(error))
//    case _: ConnectException => NetworkFailure(getErrorMessage(error))
//    case _: InvalidMessageBodyFailure => BadResponseFailure(getErrorMessage(error))
//    case _: UnexpectedStatus => BadResponseFailure(getErrorMessage(error))
//    case _ => UnknownFailure(getErrorMessage(error))
//  }

//  private def getErrorMessage(error: Throwable): String = error.toString

  private def convertRateUri(currencyPairs: List[Rate.Pair]): F[Uri] =
    Uri.fromString(config.host)
      .map(_.withPath("/convert")
        .withQueryParam("pairs", currencyPairs.map(e => s"${e.from}${e.to}").mkString(","))
        .withQueryParam("api_key", config.apiKey)
      )
      .fold(
        parseError => F.raise[Uri](BadConfiguration(parseError.message)),
        uri => uri.pure[F]
      )

  private def symbolsUri: F[Uri] =
    Uri.fromString(config.host)
      .map(_.withPath("/symbols")
        .withQueryParam("api_key", config.apiKey)
      )
      .fold(
        parseError => F.raise[Uri](BadConfiguration(parseError.message)),
        uri => uri.pure[F]
      )

}

object QuoteCache {

  def create[F[_]: ConcurrentEffect: Timer](config: ApplicationConfig): F[Cache[F, Rate.Pair, Rate]] = {
    val quoteRefresher = new QuoteCache(config.forex)

    SelfRefreshingCache.create[F, Rate.Pair, Rate](Map.empty, quoteRefresher.refreshRatesCache, config.forex.dataExpiresIn)
  }

}
