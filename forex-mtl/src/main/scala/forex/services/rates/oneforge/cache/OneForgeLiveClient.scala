package forex.services.rates.oneforge.cache

import java.net.ConnectException
import java.util.concurrent.TimeoutException

import cats.effect._
import cats.implicits._
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

class OneForgeLiveClient[F[_]](config: ForexConfig)(implicit ce: ConcurrentEffect[F]) {

  case class QuoteDTO(symbol: String, price: Double, timestamp: Int)

  def fetchPossiblePairs: F[List[Rate.Pair]] = {
    symbolsUri
      .fold(
        error => ce.raiseError[List[Rate.Pair]](CanNotParseSymbolsUri(error)),
        uri => oneForgeSymbols(uri)
      )
  }

  def fetchQuotes(currencyPairs: List[Rate.Pair]): F[List[Rate]] =
    convertRateUri(currencyPairs)
      .fold(
        error => ce.raiseError[List[Rate]](CanNotParseConvertUri(error)),
        uri => oneForgeConvertRate(uri)
      )

  private def oneForgeConvertRate(uri: Uri): F[List[Rate]] = {
    implicit val quoteListDecoder: EntityDecoder[F, List[QuoteDTO]] = jsonOf[F, List[QuoteDTO]]

    BlazeClientBuilder[F](global)
      .resource
      .use(_.expect[List[QuoteDTO]](uri))
      .handleErrorWith(error => ce.raiseError[List[QuoteDTO]](handleHttpError(error)))
      .map(_.map(quoteToRate))
  }

  private def oneForgeSymbols(uri: Uri): F[List[Rate.Pair]] = {
    implicit val quoteListDecoder: EntityDecoder[F, List[String]] = jsonOf[F, List[String]]

    BlazeClientBuilder[F](global)
      .resource
      .use(_.expect[List[String]](uri))
      .handleErrorWith(error => ce.raiseError[List[String]](handleHttpError(error)))
      .map(_.map(parseCurrencyPairFromCode))
  }

  private def convertRateUri(currencyPairs: List[Rate.Pair]): Error Either Uri =
    Uri.fromString(config.host)
      .map(_.withPath("/convert")
        .withQueryParam("pairs", currencyPairs.map(e => s"${e.from}${e.to}").mkString(","))
        .withQueryParam("api_key", config.apiKey)
      )
      .leftMap(BadConfiguration)

  private def symbolsUri: Error Either Uri =
    Uri.fromString(config.host)
      .map(_.withPath("/symbols")
        .withQueryParam("api_key", config.apiKey)
      )
      .leftMap(BadConfiguration)

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

  private def handleHttpError(error: Throwable): Error = error match {
    case _: TimeoutException => NetworkFailure(error)
    case _: ConnectException => NetworkFailure(error)
    case _: InvalidMessageBodyFailure => BadResponseFailure(error)
    case _: UnexpectedStatus => BadResponseFailure(error)
    case _ => UnknownFailure(error)
  }

}