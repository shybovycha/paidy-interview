package forex.services.rates.interpreters

import forex.services.rates.{Algebra, Errors}
import cats.Applicative
import cats.syntax.applicative._
import cats.syntax.either._
import forex.domain.{Price, Rate, Timestamp}

class OneFrameDummy[F[_]: Applicative] extends Algebra[F] {

  override def get(pair: Rate.Pair): F[Errors.Error Either Rate] =
    Rate(pair, Price(BigDecimal(100)), Timestamp.now).asRight[Errors.Error].pure[F]

}
