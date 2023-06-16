package forex

package object services {
  type RatesService[F[_]] = rates.Algebra[F]
  final val RatesServices = rates.Interpreters

  type OneForgeClient[F[_]] = rates.oneforge.Algebra[F]
  final val OneForgeClients = rates.oneforge.Interpreters

  type RatesCache[F[_]] = cache.Cache[F, domain.Rate.Pair, domain.Rate]
  final val OneForgeCache = services.rates.OneForgeInterpreter
}
