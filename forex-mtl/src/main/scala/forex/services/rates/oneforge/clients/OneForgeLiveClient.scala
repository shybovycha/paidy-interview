package forex.services.rates.oneforge.clients

import java.net.ConnectException
import java.util.concurrent.TimeoutException

import cats.Functor
import cats.effect._
import cats.implicits._
import forex.config.ForexConfig
import forex.domain.Currency
import forex.domain.Price
import forex.domain.Rate
import forex.domain.Timestamp
import forex.services.rates.Algebra
import forex.services.rates.Errors.Error.BadConfiguration
import forex.services.rates.Errors.Error.BadResponseFailure
import forex.services.rates.Errors.Error.CanNotRetrieveFromCache
import forex.services.rates.Errors.Error.NetworkFailure
import forex.services.rates.Errors.Error.UnknownFailure
import forex.services.rates.Errors._
import forex.services.rates.oneforge.cache.Cache
import io.circe.generic.auto._
import org.http4s.EntityDecoder
import org.http4s.InvalidMessageBodyFailure
import org.http4s.Uri
import org.http4s.circe.jsonOf
import org.http4s.client.blaze.BlazeClientBuilder

import scala.concurrent.ExecutionContext.Implicits.global

class OneForgeLiveClient[F[_]: Functor](implicit cache: Cache[F, Rate.Pair, Rate]) extends Algebra[F] {

  override def get(pair: Rate.Pair): F[Error Either Rate] =
    cache.get(pair)
      .map(_.toRight(CanNotRetrieveFromCache()))

}

object OneForgeLiveClient {

  def apply[F[_]: ConcurrentEffect: Timer](implicit cache: Cache[F, Rate.Pair, Rate]): Algebra[F] =
    new OneForgeLiveClient

}

class QuoteRefresher(config: ForexConfig) {

  case class QuoteDTO(symbol: String, price: Double, timestamp: Int)

  def refreshRatesCache[F[_]: ConcurrentEffect](existingRates: Map[Rate.Pair, Rate]): F[Option[Map[Rate.Pair, Rate]]] = {
    val currencyPairs: List[(String, String)] = existingRates.keySet.map(pair => (pair.from.toString, pair.to.toString)).toList
    val newQuoteDTOs: F[Error Either List[QuoteDTO]] = fetchQuotes(currencyPairs)

    val newRates: F[Option[List[Rate]]] = newQuoteDTOs.map(_.toOption.map(_.map(quoteToRate)))

    def updateCache(rates: List[Rate]): Map[Rate.Pair, Rate] =
      rates.foldRight(existingRates)((rate: Rate, acc: Map[Rate.Pair, Rate]) => acc.updated(rate.pair, rate))

    newRates.map(_.map(updateCache))
  }

  private def quoteToRate(quote: QuoteDTO): Rate = {
    val fromCurrencySymbol = quote.symbol.substring(0, 2)
    val toCurrencySymbol = quote.symbol.substring(2, 4)

    val from = Currency.fromString(fromCurrencySymbol)
    val to = Currency.fromString(toCurrencySymbol)

    val price = Price(quote.price * 100)

    val timestamp = Timestamp.now

    Rate(Rate.Pair(from, to), price, timestamp)
  }

  private def fetchQuotes[F[_]: ConcurrentEffect](currencyPairs: List[(String, String)]): F[Error Either List[QuoteDTO]] =
    getRefreshCacheUri(currencyPairs).fold(
      error => Either.left[Error, List[QuoteDTO]](error).pure[F],
      uri => IO(getOneForgeResponse(uri)).unsafeRunSync
    )

  private def getOneForgeResponse[F[_]: ConcurrentEffect](uri: Uri): F[Either[Error, List[QuoteDTO]]] = {
    implicit val quoteListDecoder: EntityDecoder[F, List[QuoteDTO]] = jsonOf[F, List[QuoteDTO]]

    BlazeClientBuilder[F](global)
      .resource
      .use(_.expect[List[QuoteDTO]](uri))
      .attempt
      .flatMap(either => either.leftMap(handleRefreshError).pure[F])
  }

  private def handleRefreshError(error: Throwable): Error = error match {
    case _: TimeoutException => NetworkFailure(getErrorMessage(error))
    case _: ConnectException => NetworkFailure(getErrorMessage(error))
    case _: InvalidMessageBodyFailure => BadResponseFailure(getErrorMessage(error))
    case _ => UnknownFailure(getErrorMessage(error))
  }

  private def getErrorMessage(error: Throwable): String = error.toString

  private def getRefreshCacheUri(currencyPairs: List[(String, String)]): Either[Error, Uri] =
    Uri.fromString(config.host)
      .map(_.withQueryParam("pairs", currencyPairs.flatMap(e => List(e._1, e._2)).mkString(""))
            .withQueryParam("api_key", config.apiKey)
      )
      .leftMap(parseError => BadConfiguration(parseError.message))

}
