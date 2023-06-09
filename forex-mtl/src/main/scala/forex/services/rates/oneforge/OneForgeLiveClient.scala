package forex.services.rates.oneforge

import cats.MonadError
import cats.effect._
import cats.implicits._
import forex.config.ForexConfig
import forex.domain._
import forex.services.rates.Errors.Error._
import forex.services.rates.Errors._
import forex.services.rates.oneforge.OneForgeLiveClient.handleHttpError
import io.circe.generic.auto._
import org.http4s.circe.jsonOf
import org.http4s.client.UnexpectedStatus
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.{ EntityDecoder, InvalidMessageBodyFailure, Uri }

import java.net.ConnectException
import java.util.concurrent.TimeoutException
import scala.concurrent.ExecutionContext.Implicits.global

class OneForgeLiveClient[F[_]: ConcurrentEffect](config: ForexConfig)(implicit ce: MonadError[F, Throwable])
    extends OneForgeClient[F] {

  override def fetchPossiblePairs(fetcher: Uri => F[List[String]]): F[List[Rate.Pair]] =
    for {
      uri <- symbolsUri
      symbols <- fetcher(uri)
    } yield symbols.map(parseCurrencyPairFromCode)

  override def fetchQuotes(currencyPairs: List[Rate.Pair], fetcher: Uri => F[List[QuoteDTO]]): F[List[Rate]] =
    for {
      uri <- convertRateUri(currencyPairs)
      quotes <- fetcher(uri)
    } yield quotes.map(quoteToRate)

  private def convertRateUri(currencyPairs: List[Rate.Pair]): F[Uri] =
    Uri.fromString(config.host) match {
      case Left(error) => ce.raiseError[Uri](CanNotParseConvertUri(error))

      case Right(uri) =>
        uri
          .withPath("/convert")
          .withQueryParam("pairs", currencyPairs.map(e => s"${e.from}${e.to}"))
          .withQueryParam("api_key", config.apiKey)
          .pure[F]
    }

  private def symbolsUri: F[Uri] =
    Uri.fromString(config.host) match {
      case Left(error) => ce.raiseError[Uri](CanNotParseSymbolsUri(error))

      case Right(uri) =>
        uri
          .withPath("/symbols")
          .withQueryParam("api_key", config.apiKey)
          .pure[F]
    }

  private def quoteToRate(quote: QuoteDTO): Rate = {
    val pair      = parseCurrencyPairFromCode(quote.symbol)
    val price     = Price(quote.price)
    val timestamp = Timestamp.now

    Rate(pair, price, timestamp)
  }

  private def parseCurrencyPairFromCode(codePair: String): Rate.Pair = {
    val codes = (codePair.substring(0, 3), codePair.substring(3, 6))

    Rate.Pair(Currency.fromString(codes._1), Currency.fromString(codes._2))
  }

  override def oneForgeConvertRate(uri: Uri): F[List[QuoteDTO]] = {
    implicit val quoteListDecoder: EntityDecoder[F, List[QuoteDTO]] = jsonOf[F, List[QuoteDTO]]

    BlazeClientBuilder[F](global).resource
      .use(_.expect[List[QuoteDTO]](uri))
      .handleErrorWith(error => ce.raiseError[List[QuoteDTO]](handleHttpError(error)))
  }

  override def oneForgeSymbols(uri: Uri): F[List[String]] = {
    implicit val quoteListDecoder: EntityDecoder[F, List[String]] = jsonOf[F, List[String]]

    BlazeClientBuilder[F](global).resource
      .use(_.expect[List[String]](uri))
      .handleErrorWith(error => ce.raiseError[List[String]](handleHttpError(error)))
  }

}

object OneForgeLiveClient {

  private[oneforge] def handleHttpError(error: Throwable): Error = error match {
    case _: TimeoutException          => NetworkFailure(error)
    case _: ConnectException          => NetworkFailure(error)
    case _: InvalidMessageBodyFailure => BadResponseFailure(error)
    case _: UnexpectedStatus          => BadResponseFailure(error)
    case _                            => UnknownFailure(error)
  }

}
