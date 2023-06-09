package forex.services.cache

import java.net.ConnectException
import cats.effect.IO
import forex.config.ForexConfig
import forex.domain._
import forex.services.rates.Errors.Error._
import forex.services.rates.oneforge.{OneForgeLiveClient, QuoteDTO}
import org.http4s.InvalidMessageBodyFailure
import org.http4s.Status
import org.http4s.Uri
import org.http4s.client.UnexpectedStatus
import org.scalatest.FunSuite
import org.scalatest.Matchers._

import scala.concurrent.TimeoutException
import scala.concurrent.duration._

class OneForgeLiveClientTest extends FunSuite {

  val config = ForexConfig(host = "http://example.com", apiKey = "TEST_API_KEY", ttl = 5.minutes)

  test("#fetchPossiblePairs returns all currency pairs available") {
    val client = new OneForgeLiveClient[IO](config)

    val fetcher = (_: Uri) => IO(List("AUDJPY", "EURUSD"))

    client.fetchPossiblePairs(fetcher).unsafeRunSync() should contain only (Rate.Pair(Currency.AUD, Currency.JPY), Rate.Pair(Currency.EUR, Currency.USD))
  }

  test("#fetchQuotes converts all the currencies passed") {
    val client = new OneForgeLiveClient[IO](config)

    val fetcher = (_: Uri) => IO(List(QuoteDTO("AUDJPY", 70.0)))
    val currencyPairs = List(Rate.Pair(Currency.AUD, Currency.JPY))

    all (client.fetchQuotes(currencyPairs, fetcher).unsafeRunSync()) should (have ('pair (Rate.Pair(Currency.AUD, Currency.JPY)), 'price (70.0)))
  }

  ignore("not testing #oneForgeConvertRate since this would only test http4s client which we do not want to test ourselves") {}

  ignore("not testing #oneForgeSymbols since this would only test http4s client which we do not want to test ourselves") {}

  test("#convertRateUri constructs URI from configuration passed") {
    val client = new OneForgeLiveClient[IO](config)

    val currencyPairs = List(Rate.Pair(Currency.AUD, Currency.JPY))

    client.convertRateUri(currencyPairs).unsafeRunSync().toString() should be ("http://example.com/convert?pairs=AUDJPY&api_key=TEST_API_KEY")
  }

  test("#convertRateUri returns CanNotParseConvertUri when unable to parse URI") {
    val brokenConfig = ForexConfig(host = "zzpt:\\\\1::2:3:::4", apiKey = "?api?key", ttl = 1.second)
    val client = new OneForgeLiveClient[IO](brokenConfig)

    val currencyPairs = List(Rate.Pair(Currency.AUD, Currency.JPY))

    a [CanNotParseConvertUri] should be thrownBy client.convertRateUri(currencyPairs).unsafeRunSync()
  }

  test("#symbolsUri constructs URI from configuration passed") {
    val client = new OneForgeLiveClient[IO](config)

    client.symbolsUri.unsafeRunSync().toString() should be ("http://example.com/symbols?api_key=TEST_API_KEY")
  }

  test("#symbolsUri returns CanNotParseSymbolsUri when unable to parse URI") {
    val brokenConfig = ForexConfig(host = "zzppt:\\\\1:2::3:::4", apiKey = "?api?key", ttl = 1.second)
    val client = new OneForgeLiveClient[IO](brokenConfig)

    a [CanNotParseSymbolsUri] should be thrownBy client.symbolsUri.unsafeRunSync()
  }

  test("#quoteToRate extracts Rate from QuoteDTO response object") {
    val client = new OneForgeLiveClient[IO](config)

    client.quoteToRate(QuoteDTO(symbol = "AUDJPY", price = 71.0)) should have ( 'pair (Rate.Pair(Currency.AUD, Currency.JPY)), 'price (71.0) )
  }

  test("#parseCurrencyPairFromCode extracts pair of currencies from string response") {
    val client = new OneForgeLiveClient[IO](config)

    client.parseCurrencyPairFromCode("JPYUSD") should be (Rate.Pair(Currency.JPY, Currency.USD))
  }

  ignore("#parseCurrencyPairFromCode returns error when unable to parse response string") {
     val client = new OneForgeLiveClient[IO](config)

    a [CanNotParseSymbolsUri] should be thrownBy client.parseCurrencyPairFromCode("123456")
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
