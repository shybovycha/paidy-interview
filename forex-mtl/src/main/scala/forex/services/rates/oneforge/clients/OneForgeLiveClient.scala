package forex.services.rates.oneforge.clients

import java.net.ConnectException
import java.util.concurrent.TimeoutException

import cats.Applicative
import cats.effect.IO
import cats.effect._
import cats.syntax.all._
import forex.domain.Price
import forex.domain.Rate
import forex.domain.Timestamp
import forex.services.rates.Algebra
import forex.services.rates.Errors.Error.BadResponseFailure
import forex.services.rates.Errors.Error.NetworkFailure
import forex.services.rates.Errors.Error.UnknownFailure
import forex.services.rates.Errors._
import io.circe.generic.auto._
import org.http4s.InvalidMessageBodyFailure
import org.http4s.EntityDecoder
import org.http4s.Uri
import org.http4s.circe.jsonOf
import org.http4s.client.blaze.BlazeClientBuilder

import scala.concurrent.ExecutionContext.Implicits.global

class OneForgeLiveClient[F[_]: Applicative] extends Algebra[F] {

  override def get(pair: Rate.Pair): F[Error Either Rate] = QuoteCache.refreshCache(List(getCurrencyCodePair(pair)))
    .map(_.head)
    .map(quote => new Rate(pair, new Price((quote.price * 100.0).toInt), Timestamp.now))
    .pure[F]

  private def getCurrencyCodePair(pair: Rate.Pair): (String, String) = (pair.from.toString, pair.to.toString)

}

object OneForgeLiveClient {

  def apply[F[_]: Applicative]: Algebra[F] = new OneForgeLiveClient[F]()

}

object QuoteCache {

  import JsonHelpers._

  implicit val cs: ContextShift[IO] = IO.contextShift(global)
  implicit val timer: Timer[IO] = IO.timer(global)

//  implicit val responseDecoder: EntityDecoder[IO, List[Quote]] = jsonOf[IO, List[Quote]]

  def refreshCache(currencyPairs: List[(String, String)]): Either[Error, List[Quote]] = BlazeClientBuilder[IO](global)
    .resource
    .use(_.expect[List[Quote]](getRefreshCacheUri(currencyPairs)))
    .attempt
    .unsafeRunSync
    .leftMap(handleRefreshError)

//  private def handleRefreshError[A <: Coproduct](error: Throwable)(implicit inj1: Inject[A, UnknownFailure], inj2: Inject[A, NetworkFailure]): A = error match {
//    case _: TimeoutException => Coproduct[A](NetworkFailure(getErrorMessage(error)))
//    case _: ConnectException => Coproduct[A](NetworkFailure(getErrorMessage(error)))
//    case _ => Coproduct[A](UnknownFailure(getErrorMessage(error)))
//  }

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

object JsonHelpers {

  implicit val quoteListDecoder: EntityDecoder[IO, List[Quote]] = jsonOf[IO, List[Quote]]

}

case class Quote(symbol: String, price: Double, timestamp: Int)
