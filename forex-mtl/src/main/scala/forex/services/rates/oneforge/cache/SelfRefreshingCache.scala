package forex.services.rates.oneforge.cache

import cats.Functor
import cats.effect.Timer
import cats.effect._
import cats.effect.concurrent.Ref
import cats.effect.syntax.concurrent._
import cats.implicits._

import scala.concurrent.duration.FiniteDuration

private class SelfRefreshingCache[F[_]: Functor, K, V](state: Ref[F, Map[K, V]]) extends Cache[F, K, V] {

  override def get(key: K): F[Option[V]] =
    state.get.map(_.get(key))

  override def put(key: K, value: V): F[Unit] =
    state.update(_.updated(key, value))

}

object SelfRefreshingCache {

  def create[F[_]: Concurrent: Timer, K, V](initialState: Map[K, V], refresher: Map[K, V] => F[Option[Map[K, V]]], timeout: FiniteDuration): F[Cache[F, K, V]] = {

    def refreshRoutine(state: Ref[F, Map[K, V]]): F[Unit] = {
      val process = state.get
        .flatMap(refresher)
        .flatMap(_.fold(state.get)(state.getAndSet))
      // here by using Option#foreach we potentially ignore the None() case, which might be due to for ex. a HTTP error

      process >> Timer[F].sleep(timeout) >> refreshRoutine(state)
    }

    Ref.of[F, Map[K, V]](initialState)
      .flatTap(refreshRoutine(_).start.void)
      .map(new SelfRefreshingCache[F, K, V](_))

  }

}
