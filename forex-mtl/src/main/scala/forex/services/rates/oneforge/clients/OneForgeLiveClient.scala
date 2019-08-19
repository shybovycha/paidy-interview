package forex.services.rates.oneforge.clients

import cats.Applicative
import forex.domain.Rate
import forex.services.rates.Algebra
import forex.services.rates.Errors._

class OneForgeLiveClient[F[_]: Applicative] extends Algebra[F] {

  override def get(pair: Rate.Pair): F[Error Either Rate] = ???

}

object OneForgeLiveClient {

  def apply[F[_]: Applicative]: Algebra[F] = new OneForgeLiveClient[F]()

}
