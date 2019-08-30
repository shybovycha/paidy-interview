package forex.services.rates.oneforge.cache

import cats._
import cats.data._
import cats.effect._
import cats.implicits._
import cats.kernel.laws._
import cats.laws.discipline.FunctorTests
import org.scalacheck.ScalacheckShapeless._
import org.scalatest.FunSuite
import org.typelevel.discipline.scalatest.Discipline

//import cats.laws.discipline.FunctorTests

//class SelfRefreshingCacheLaws extends FunSuite with Discipline {
//  checkAll("SelfRefreshingCache.FunctorLaws", FunctorTests[SelfRefreshingCache].fun)
//}

trait SelfRefreshingCacheLaws[F[_], K, V] { //extends FunSuite with Discipline {
  // checkAll("SelfRefreshingCache.FunctorLaws", FunctorTests[SelfRefreshingCache[]].functor[])

  def algebra: SelfRefreshingCache[F, K, V]

  implicit def M: Monad[F]
  implicit def C: Concurrent[F]
  implicit def T: Timer[F]

  import cats.syntax.apply._
  import cats.syntax.flatMap._
  import cats.syntax.functor._

  def initialStateSetter(state: Map[K, V], k: K, v: V) = {
    val refresher = (state: Map[K, V]) => state.pure[F]
    val trigger = ().pure[F]

    val cache = SelfRefreshingCache.createCache[F, K, V](state, refresher, trigger)

    (cache >>= (c => c.get(k))) <-> M.pure(Some(v))
  }

}

object SelfRefreshingCacheLaws {

  def apply[F[_]: Concurrent: Timer, K, V](instance: SelfRefreshingCache[F, K, V])(implicit c: Concurrent[F], t: Timer[F], m: Monad[F]): SelfRefreshingCacheLaws[F, K, V] =
    new SelfRefreshingCacheLaws[F, K, V] {
      override val algebra: SelfRefreshingCache[F, K, V] = instance

      override implicit val M: Monad[F] = m
      override implicit val C: Concurrent[F] = c
      override implicit val T: Timer[F] = t
    }

}
