package forex.services.rates.oneforge

import cats.Applicative
import forex.domain.Rate
import forex.services.rates.Algebra
import forex.services.rates.Errors._

class OneForgeInterpreter[F[_]](client: Algebra[F]) extends Algebra[F] {

  override def get(pair: Rate.Pair): F[Error Either Rate] = client.get(pair)

}

object OneForgeInterpreter {

  def apply[F[_]: Applicative](client: Algebra[F]): OneForgeInterpreter[F] = new OneForgeInterpreter[F](client)

}
