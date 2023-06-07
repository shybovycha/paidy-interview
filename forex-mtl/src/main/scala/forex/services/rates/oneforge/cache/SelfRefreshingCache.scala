package forex.services.rates.oneforge.cache

import cats.Functor
import cats.Monad
import cats.effect.Timer
import cats.effect._
import cats.effect.concurrent.Ref
import cats.effect.syntax.concurrent._
import cats.implicits._

import scala.concurrent.duration.FiniteDuration

class SelfRefreshingCache[F[_]: Functor, K, V](stateRef: Ref[F, Map[K, V]]) extends Cache[F, K, V] {

  override def get(key: K): F[Option[V]] = {
    for {
      state <- stateRef.get
    } yield state.get(key)
  }

  override def put(key: K, value: V): F[Unit] =
    stateRef.update(_.updated(key, value))

}

object SelfRefreshingCache {

  def createCache[F[_]: Concurrent, K, V](initialState: Map[K, V], refreshRoutine: Ref[F, Map[K, V]] => F[Unit]): F[Cache[F, K, V]] =
    for {
      stateRef <- Ref.of[F, Map[K, V]](initialState)
      _ <- refreshRoutine(stateRef)
    } yield new SelfRefreshingCache[F, K, V](stateRef)

  def createAsyncRefresher[F[_]: Concurrent, K, V](refresher: Ref[F, Map[K, V]] => F[Unit]): Ref[F, Map[K, V]] => F[Unit] =
    stateRef => refresher(stateRef).start.void

  def createRecursiveRefresher[F[_]: Monad, K, V](refresher: Map[K, V] => F[Map[K, V]], trigger: F[Unit]): Ref[F, Map[K, V]] => F[Unit] =
    (stateRef: Ref[F, Map[K, V]]) =>
      Monad[F].tailRecM(stateRef) (refreshCache(refresher, trigger))

  private def refreshCache[V, K, F[_] : Monad](refresher: Map[K, V] => F[Map[K, V]], trigger: F[Unit]): Ref[F, Map[K, V]] => F[Either[Ref[F, Map[K, V]], Unit]] =
    (stateRef: Ref[F, Map[K, V]]) =>
      for {
        state <- stateRef.get
        newState <- refresher(state)
        _ <- stateRef.set(newState)
        _ <- trigger
      } yield Left(state)

  def createRepeatedTrigger[F[_]: Timer](timeout: FiniteDuration): F[Unit] =
    Timer[F].sleep(timeout)

}
