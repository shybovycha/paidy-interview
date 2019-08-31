package forex.services.rates.oneforge.cache

import java.net.ConnectException

import cats.effect.IO
import forex.config.ForexConfig
import forex.domain._
import forex.services.rates.Errors.Error._
import org.http4s.InvalidMessageBodyFailure
import org.http4s.Status
import org.http4s.Uri
import org.http4s.client.UnexpectedStatus
import org.scalatest.FunSuite
import org.scalatest.Matchers._

import scala.concurrent.TimeoutException
import scala.concurrent.duration._

class OneForgeLiveClientTest extends FunSuite {

  val config = ForexConfig(host = "http://example.com", apiKey = "TEST_API_KEY", dataExpiresIn = 5.minutes)

  test("#fetchPossiblePairs returns all currency pairs available") {
    val client = new OneForgeLiveClient[IO](config)

    val fetcher = (_: Uri) => IO(List("AUDJPY", "EURUSD"))

    client.fetchPossiblePairs(fetcher).unsafeRunSync() should contain only (Rate.Pair(Currency.AUD, Currency.JPY), Rate.Pair(Currency.EUR, Currency.USD))
  }

  test("#fetchQuotes converts all the currencies passed") {
    val client = new OneForgeLiveClient[IO](config)

    val fetcher = (_: Uri) => IO(List(QuoteDTO("AUDJPY", 70.0)))
    val currencyPairs = List(Rate.Pair(Currency.AUD, Currency.JPY))

    // TODO: this will be updated at convert time and thus might fail / flake
    val timestamp = Timestamp.now

    client.fetchQuotes(currencyPairs, fetcher).unsafeRunSync() should contain only (Rate(Rate.Pair(Currency.AUD, Currency.JPY), Price(70.0), timestamp))
  }

  ignore("not testing #oneForgeConvertRate since this would only test http4s client which we do not want to test ourselves") {}

  ignore("not testing #oneForgeSymbols since this would only test http4s client which we do not want to test ourselves") {}

  test("#convertRateUri constructs URI from configuration passed") {
    val client = new OneForgeLiveClient[IO](config)

    val currencyPairs = List(Rate.Pair(Currency.AUD, Currency.JPY))

    client.convertRateUri(currencyPairs).unsafeRunSync().toString() should be ("http://example.com/convert?pairs=AUDJPY&api_key=TEST_API_KEY")
  }

  test("#convertRateUri returns CanNotParseConvertUri when unable to parse URI") {
    val brokenConfig = ForexConfig(host = "123", apiKey = "?api?key", dataExpiresIn = 1.second)
    val client = new OneForgeLiveClient[IO](brokenConfig)

    val currencyPairs = List(Rate.Pair(Currency.AUD, Currency.JPY))

    // TODO: check the MonadError rather than `not be`
    client.convertRateUri(currencyPairs).unsafeRunSync().toString() should not be ("123/convert?pairs=AUDJPY&api_key=?api?key")
  }

  test("#symbolsUri constructs URI from configuration passed") {
    val client = new OneForgeLiveClient[IO](config)

    client.symbolsUri.unsafeRunSync().toString() should be ("http://example.com/symbols?api_key=TEST_API_KEY")
  }

  test("#symbolsUri returns CanNotParseSymbolsUri when unable to parse URI") {
    val brokenConfig = ForexConfig(host = "123", apiKey = "?api?key", dataExpiresIn = 1.second)
    val client = new OneForgeLiveClient[IO](brokenConfig)

    // TODO: check the MonadError rather than `not be`
    client.symbolsUri.unsafeRunSync().toString() should not be ("123/convert?api_key=?api?key")
  }

  test("#quoteToRate extracts Rate from QuoteDTO response object") {
    val client = new OneForgeLiveClient[IO](config)

    // TODO: this will be updated at convert time and thus might fail / flake
    val timestamp = Timestamp.now

    client.quoteToRate(QuoteDTO(symbol = "AUDJPY", price = 71.0)) should be (Rate(Rate.Pair(Currency.AUD, Currency.JPY), Price(71.0), timestamp))
  }

  test("#parseCurrencyPairFromCode extracts pair of currencies from string response") {
    val client = new OneForgeLiveClient[IO](config)

    client.parseCurrencyPairFromCode("JPYUSD") should be (Rate.Pair(Currency.JPY, Currency.USD))
  }

  ignore("#parseCurrencyPairFromCode returns error when unable to parse response string") {
    // val client = new OneForgeLiveClient[IO](config)

    // TODO: what should it be then?
    // client.parseCurrencyPairFromCode("123456") should not be isInstanceOf[Rate.Pair]
  }

  test("#handleHttpError converts timeout exception to error") {
    val exception = new TimeoutException()

    OneForgeLiveClient.handleHttpError(exception) should be (NetworkFailure(exception))
  }

  test("#handleHttpError converts connect exception to error") {
    val exception = new ConnectException()

    OneForgeLiveClient.handleHttpError(exception) should be (NetworkFailure(exception))
  }

  test("#handleHttpError converts invalid message body exception to error") {
    val exception = InvalidMessageBodyFailure("details")

    OneForgeLiveClient.handleHttpError(exception) should be (BadResponseFailure(exception))
  }

  test("#handleHttpError converts unexpected status exception to error") {
    val exception = UnexpectedStatus(Status.Gone)

    OneForgeLiveClient.handleHttpError(exception) should be (BadResponseFailure(exception))
  }

  test("#handleHttpError converts any other exception to error") {
    val exception = new RuntimeException("details")

    OneForgeLiveClient.handleHttpError(exception) should be (UnknownFailure(exception))
  }

}
