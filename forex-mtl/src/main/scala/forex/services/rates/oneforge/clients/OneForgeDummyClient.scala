package forex.services.rates.oneforge.clients

import cats.Applicative
import cats.implicits._
import forex.domain.Price
import forex.domain.Rate
import forex.domain.Timestamp
import forex.services.rates.Algebra
import forex.services.rates.Errors.Error

class OneForgeDummyClient[F[_]: Applicative] extends Algebra[F] {

  override def get(pair: Rate.Pair): F[Error Either Rate] =
    Rate(pair, Price(BigDecimal(100)), Timestamp.now).asRight[Error].pure[F]

}

object OneForgeDummyClient {

  def apply[F[_]: Applicative]: Algebra[F] = new OneForgeDummyClient[F]()

}
