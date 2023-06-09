package forex.services.rates.oneforge

import forex.domain.Rate
import org.http4s.Uri

case class QuoteDTO(symbol: String, price: Double)

trait OneForgeClient[F[_]] {

  def fetchPossiblePairs(fetcher: Uri => F[List[String]]): F[List[Rate.Pair]]

  def fetchQuotes(currencyPairs: List[Rate.Pair], fetcher: Uri => F[List[QuoteDTO]]): F[List[Rate]]

  def oneForgeSymbols(uri: Uri): F[List[String]]

  def oneForgeConvertRate(uri: Uri): F[List[QuoteDTO]]

}
