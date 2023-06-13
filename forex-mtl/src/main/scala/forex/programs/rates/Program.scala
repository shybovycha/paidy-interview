package forex.programs.rates

import cats.Functor
import cats.data.EitherT
import forex.domain._
import forex.programs.rates.Errors._
import forex.services.RatesService

class Program[F[_]: Functor](ratesService: RatesService[F]) extends Algebra[F] {

  override def get(request: Protocol.GetRatesRequest): F[Error Either Rate] = {
    val symbol = Rate.Pair(request.from, request.to)
    val rate = ratesService.get(symbol)

    EitherT(rate).leftMap(toProgramError).value // convert service-level error to program-level error
  }

}

object Program {

  def apply[F[_]: Functor](ratesService: RatesService[F]): Algebra[F] = new Program[F](ratesService)

}
