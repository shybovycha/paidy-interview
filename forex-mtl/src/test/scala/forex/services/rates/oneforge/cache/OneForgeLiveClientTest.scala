package forex.services.rates.oneforge.cache

import cats.effect.Concurrent
import cats.effect.IO
import cats.syntax.all._
import org.scalatest.FunSuite
import org.scalatest.Ignore
import org.scalatest.Matchers._

class OneForgeLiveClientTest extends FunSuite {

  test("#fetchPossiblePairs returns all currency pairs available") {}

  test("#fetchQuotes converts all the currencies passed") {}

  ignore("not testing #oneForgeConvertRate since this would only test http4s client which we do not want to test ourselves")

  ignore("not testing #oneForgeSymbols since this would only test http4s client which we do not want to test ourselves")

  test("#convertRateUri constructs URI from configuration passed") {}

  test("#convertRateUri returns CanNotParseConvertUri when unable to parse URI") {}

  test("#symbolsUri constructs URI from configuration passed") {}

  test("#symbolsUri returns CanNotParseSymbolsUri when unable to parse URI") {}

  test("#quoteToRate extracts Rate from QuoteDTO response object") {}

  test("#parseCurrencyPairFromCode extracts pair of currencies from string response") {}

  test("#handleHttpError converts network exceptions to errors") {}

}
