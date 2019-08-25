package forex.services.rates.oneforge.clients

import java.net.ConnectException
import java.util.concurrent.TimeoutException

import cats.Monad
import cats.effect._
import cats.implicits._
import forex.domain.Currency
import forex.domain.Price
import forex.domain.Rate
import forex.domain.Timestamp
import forex.services.rates.Algebra
import forex.services.rates.Errors.Error.BadResponseFailure
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
import org.http4s.client.blaze.BlazeClientBuilder

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class OneForgeLiveClient[F[_]: Monad](cache: F[Cache[F, Rate.Pair, Rate]]) extends Algebra[F] {

  override def get(pair: Rate.Pair): F[Error Either Rate] =
    cache.flatMap(_.get(pair))
      .map(_.toRight(CanNotRetrieveFromCache()))

}

object OneForgeLiveClient {

  def apply[F[_]: ConcurrentEffect: Timer]: Algebra[F] = {
    val timeout = 5.seconds

    val cache = SelfRefreshingCache.create[F, Rate.Pair, Rate](QuoteRefresher.refreshRatesCache[F], timeout)

    new OneForgeLiveClient(cache)
  }

}

object QuoteRefresher {

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

  private def fetchQuotes[F[_]: ConcurrentEffect](currencyPairs: List[(String, String)]): F[Error Either List[QuoteDTO]] = {
    implicit val quoteListDecoder: EntityDecoder[F, List[QuoteDTO]] = jsonOf[F, List[QuoteDTO]]

    val x: F[Either[Error, List[QuoteDTO]]] = BlazeClientBuilder[F](global)
      .resource
      .use(_.expect[List[QuoteDTO]](getRefreshCacheUri(currencyPairs)))
      .attempt
      .map(either => either.leftMap(handleRefreshError))

    IO(x).unsafeRunSync
  }

  private def handleRefreshError(error: Throwable): Error = error match {
    case _: TimeoutException => NetworkFailure(getErrorMessage(error))
    case _: ConnectException => NetworkFailure(getErrorMessage(error))
    case _: InvalidMessageBodyFailure => BadResponseFailure(getErrorMessage(error))
    case _ => UnknownFailure(getErrorMessage(error))
  }

  private def getErrorMessage(error: Throwable): String = error.toString

  private def getRefreshCacheUri(currencyPairs: List[(String, String)]): Uri = Uri.uri("http://localhost:4567/quotes")
    .withQueryParam("pairs", currencyPairs.flatMap(e => List(e._1, e._2)).mkString(""))
    .withQueryParam("api_key", "API_KEY")

}
