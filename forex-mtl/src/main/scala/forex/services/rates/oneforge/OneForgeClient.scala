package forex.services.rates.oneforge

import forex.domain.Rate
import org.http4s.Uri

case class QuoteDTO(symbol: String, price: Double)

trait OneForgeClient[F[_]] {

  def fetchKnownSymbols(fetcher: Uri => F[List[String]]): F[List[Rate.Pair]]

  def fetchQuotes(currencyPairs: List[Rate.Pair], fetcher: Uri => F[List[QuoteDTO]]): F[List[Rate]]

  def knownSymbols(uri: Uri): F[List[String]]

  def quotes(uri: Uri): F[List[QuoteDTO]]

}
