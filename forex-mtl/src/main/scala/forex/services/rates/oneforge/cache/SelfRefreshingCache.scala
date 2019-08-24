package forex.services.rates.oneforge.cache

import cats.Monad
import cats.effect.Sync
import cats.effect.Timer
import cats.effect.concurrent.Ref
import cats.syntax.all._

import scala.concurrent.duration.FiniteDuration

private class SelfRefreshingCache[F[_]: Monad: Sync, K, V]
(state: Ref[F, Map[K, V]], refresher: Map[K, V] => F[Map[K, V]], timeout: FiniteDuration) extends Algebra[F, K, V] {

  override def get(key: K): F[Option[V]] =
    state.get.map(_.get(key))

  override def put(key: K, value: V): F[Unit] =
    state.update(_.updated(key, value))

}

object SelfRefreshingCache {

  def create[F[_] : Monad : Sync, K, V]
  (refresher: Map[K, V] => F[Map[K, V]], timeout: FiniteDuration)
  (implicit timer: Timer[F]): F[Algebra[F, K, V]] = {

    def refreshRoutine(state: Ref[F, Map[K, V]]): F[Unit] = {
      val process = state.get.flatMap(refresher).map(state.set)

      timer.sleep(timeout) >> process >> refreshRoutine(state)
    }

    Ref.of[F, Map[K, V]](Map.empty)
      .flatTap(refreshRoutine)
      .map(ref => new SelfRefreshingCache[F, K, V](ref, refresher, timeout))

  }

}
