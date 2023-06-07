package forex.services.rates.oneforge.cache

import java.net.ConnectException
import java.util.concurrent.TimeoutException

import cats.MonadError
import cats.effect._
import cats.implicits._
import forex.config.ForexConfig
import forex.domain._
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

case class QuoteDTO(symbol: String, price: Double)

class OneForgeLiveClient[F[_]](config: ForexConfig)(implicit ce: MonadError[F, Throwable]) {

  def fetchPossiblePairs(fetcher: Uri => F[List[String]]): F[List[Rate.Pair]] = {
    for {
      uri <- symbolsUri
      symbols <- fetcher(uri)
    } yield symbols.map(parseCurrencyPairFromCode)
  }

  def fetchQuotes(currencyPairs: List[Rate.Pair], fetcher: Uri => F[List[QuoteDTO]]): F[List[Rate]] = {
    for {
      uri <- convertRateUri(currencyPairs)
      quotes <- fetcher(uri)
    } yield quotes.map(quoteToRate)
  }

  private[cache] def convertRateUri(currencyPairs: List[Rate.Pair]): F[Uri] =
    Uri.fromString(config.host) match {
      case Left(error) => ce.raiseError[Uri](CanNotParseConvertUri(error))

      case Right(uri) => uri
        .withPath("/convert")
        .withQueryParam("pairs", currencyPairs.map(e => s"${e.from}${e.to}").mkString(","))
        .withQueryParam("api_key", config.apiKey)
        .pure[F]
    }

  private[cache] def symbolsUri: F[Uri] =
    Uri.fromString(config.host) match {
      case Left(error) => ce.raiseError[Uri](CanNotParseSymbolsUri(error))

      case Right(uri) => uri
        .withPath("/symbols")
        .withQueryParam("api_key", config.apiKey)
        .pure[F]
    }

  private[cache] def quoteToRate(quote: QuoteDTO): Rate = {
    val pair = parseCurrencyPairFromCode(quote.symbol)
    val price = Price(quote.price)
    val timestamp = Timestamp.now

    Rate(pair, price, timestamp)
  }

  private[cache] def parseCurrencyPairFromCode(codePair: String): Rate.Pair = {
    val codes = (codePair.substring(0, 3), codePair.substring(3, 6))

    Rate.Pair(Currency.fromString(codes._1), Currency.fromString(codes._2))
  }

}

object OneForgeLiveClient {

  private[cache] def oneForgeConvertRate[F[_]](uri: Uri)(implicit ce: ConcurrentEffect[F]): F[List[QuoteDTO]] = {
    implicit val quoteListDecoder: EntityDecoder[F, List[QuoteDTO]] = jsonOf[F, List[QuoteDTO]]

    BlazeClientBuilder[F](global)
      .resource
      .use(_.expect[List[QuoteDTO]](uri))
      .handleErrorWith(error => ce.raiseError[List[QuoteDTO]](handleHttpError(error)))
  }

  private[cache] def oneForgeSymbols[F[_]](uri: Uri)(implicit ce: ConcurrentEffect[F]): F[List[String]] = {
    implicit val quoteListDecoder: EntityDecoder[F, List[String]] = jsonOf[F, List[String]]

    BlazeClientBuilder[F](global)
      .resource
      .use(_.expect[List[String]](uri))
      .handleErrorWith(error => ce.raiseError[List[String]](handleHttpError(error)))
  }

  private[cache] def handleHttpError(error: Throwable): Error = error match {
    case _: TimeoutException => NetworkFailure(error)
    case _: ConnectException => NetworkFailure(error)
    case _: InvalidMessageBodyFailure => BadResponseFailure(error)
    case _: UnexpectedStatus => BadResponseFailure(error)
    case _ => UnknownFailure(error)
  }

}
