package forex.services.rates.oneforge.cache

import cats.laws.discipline.FunctorTests
import org.scalatest.FunSuite
import org.typelevel.discipline.scalatest.Discipline

class QuoteCacheLaws extends FunSuite with Discipline {
  // MonadTests[Cache].monad[Int, Int, Int].all.check
  // checkAll("Cache.FunctorLaws", FunctorTests[Cache].fun)
}
