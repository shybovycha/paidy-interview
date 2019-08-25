package forex.services.rates

import cats.Applicative
import cats.effect.ConcurrentEffect
import cats.effect.Timer
import forex.services.rates.oneforge.OneForgeInterpreter
import forex.services.rates.oneforge.clients._

object Interpreters {
  def dummy[F[_]: Applicative]: Algebra[F] = OneForgeInterpreter[F](OneForgeDummyClient[F])

  def live[F[_]: ConcurrentEffect: Timer]: Algebra[F] = OneForgeInterpreter[F](OneForgeLiveClient[F])
}
