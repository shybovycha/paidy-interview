package forex.services.rates

import forex.domain.Rate
import forex.services.rates.Errors._

trait Algebra[F[_]] {

  def get(pair: Rate.Pair): F[Error Either Rate]

}
