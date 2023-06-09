package forex.services.rates.oneforge

import cats.MonadError
import cats.effect._
import cats.syntax.all._
import forex.config.ForexConfig
import forex.domain._
import forex.services.rates.Errors.Error._
import forex.services.rates.Errors._
import forex.services.rates.oneforge.OneForgeLiveClient.handleHttpError
import io.circe.generic.auto._
import org.http4s.circe.jsonOf
import org.http4s.client.{Client, UnexpectedStatus}
import org.http4s.{EntityDecoder, InvalidMessageBodyFailure, Uri}

import java.net.ConnectException
import java.util.concurrent.TimeoutException

class OneForgeLiveClient[F[_]: Async](config: ForexConfig, httpClient: Resource[F, Client[F]])(implicit ce: MonadError[F, Throwable])
    extends OneForgeClient[F] {

  override def fetchKnownSymbols(fetcher: Uri => F[List[String]]): F[List[Rate.Pair]] =
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
    (for {
      baseUri <- Uri.fromString(config.host)
      path <- Uri.fromString("/convert")
      uri = baseUri
        .withPath(path.path)
        .withQueryParam("pairs", currencyPairs.map(e => s"${e.from}${e.to}"))
        .withQueryParam("api_key", config.apiKey)
    } yield uri) match {
      case Left(error) => ce.raiseError(CanNotParseConvertUri(error))

      case Right(uri) => uri.pure[F]
    }

  private def symbolsUri: F[Uri] =
    (for {
      baseUri <- Uri.fromString(config.host)
      path <- Uri.fromString("/symbols")
      uri = baseUri
        .withPath(path.path)
        .withQueryParam("api_key", config.apiKey)
    } yield uri) match {
      case Left(error) => ce.raiseError[Uri](CanNotParseSymbolsUri(error))

      case Right(uri) => uri.pure[F]
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

  override def quotes(uri: Uri): F[List[QuoteDTO]] = {
    implicit val quoteListDecoder: EntityDecoder[F, List[QuoteDTO]] = jsonOf[F, List[QuoteDTO]]

    httpClient
      .use(_.expect[List[QuoteDTO]](uri))
      .handleErrorWith(error => ce.raiseError[List[QuoteDTO]](handleHttpError(error)))
  }

  override def knownSymbols(uri: Uri): F[List[String]] = {
    implicit val symbolListDecoder: EntityDecoder[F, List[String]] = jsonOf[F, List[String]]

    httpClient
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
