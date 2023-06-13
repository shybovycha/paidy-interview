package forex.services.rates

import cats.Applicative
import cats.implicits._
import forex.domain.{Price, Rate, Timestamp}
import forex.services.rates.Errors.Error

class DummyInterpreter[F[_]: Applicative] extends Algebra[F] {

  override def get(pair: Rate.Pair): F[Error Either Rate] =
    Rate(pair, Price(BigDecimal(100)), Timestamp.now).asRight[Error].pure[F]

}

object DummyInterpreter {

  def apply[F[_]: Applicative]: Algebra[F] = new DummyInterpreter[F]()

}
