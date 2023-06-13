package forex.services.rates.oneforge

import forex.domain.Rate

trait Algebra[F[_]] {

  def knownSymbols(): F[List[Rate.Pair]]

  def rates(currencyPairs: List[Rate.Pair]): F[List[Rate]]

}
