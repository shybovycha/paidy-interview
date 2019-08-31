package forex.services.rates.oneforge.cache

import cats.Functor
import cats.Monad
import cats.effect.Timer
import cats.effect._
import cats.effect.concurrent.Ref
import cats.effect.syntax.concurrent._
import cats.implicits._

import scala.concurrent.duration.FiniteDuration

class SelfRefreshingCache[F[_]: Functor, K, V](state: Ref[F, Map[K, V]]) extends Cache[F, K, V] {

  override def get(key: K): F[Option[V]] =
    state.get.map(_.get(key))

  override def put(key: K, value: V): F[Unit] =
    state.update(_.updated(key, value))

}

object SelfRefreshingCache {

  def createCache[F[_]: Concurrent, K, V](initialState: Map[K, V], refreshRoutine: Ref[F, Map[K, V]] => F[Unit]): F[Cache[F, K, V]] =
    Ref.of[F, Map[K, V]](initialState)
      .flatTap(refreshRoutine(_).start.void)
      .map(new SelfRefreshingCache[F, K, V](_))

  def createRecursiveRefresher[F[_]: Monad, K, V](refresher: Map[K, V] => F[Map[K, V]], trigger: F[Unit]): Ref[F, Map[K, V]] => F[Unit] = {
    def refreshRoutine(state: Ref[F, Map[K, V]]): F[Unit] =
      state.get
        .flatMap(refresher)
        .flatMap(state.getAndSet)
        .flatMap(_ => trigger)
        .flatMap(_ => refreshRoutine(state))

    refreshRoutine
  }

  def createRepeatedTrigger[F[_]: Timer](timeout: FiniteDuration): F[Unit] =
    Timer[F].sleep(timeout)

}
