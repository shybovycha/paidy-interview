package forex.services.cache

import cats.Monad
import cats.effect.implicits.genSpawnOps
import cats.effect.{Concurrent, Ref, Temporal}
import cats.implicits._

import scala.concurrent.duration.FiniteDuration

class SelfRefreshingCache[F[_]: Concurrent, K, V](stateRef: Ref[F, Map[K, V]], refreshAction: F[Unit])
    extends Cache[F, K, V] {

  override def get(key: K): F[Option[V]] =
    for {
      state <- stateRef.get
    } yield state.get(key)

  override def put(key: K, value: V): F[Unit] =
    stateRef.update(_.updated(key, value))

  def start(): F[Unit] =
    refreshAction.start.void

}

object SelfRefreshingCache {

  def createCache[F[_]: Concurrent, K, V](initialState: Map[K, V],
                                          refreshFn: Map[K, V] => F[Map[K, V]],
                                          trigger: F[Unit]): F[SelfRefreshingCache[F, K, V]] =
    for {
      stateRef <- Ref.of[F, Map[K, V]](initialState)
      refreshAction <- Monad[F].tailRecM(stateRef)(refreshCache(refreshFn, trigger))
    } yield new SelfRefreshingCache[F, K, V](stateRef, refreshAction.pure[F])

  private def refreshCache[F[_]: Concurrent, K, V](
      refreshFn: Map[K, V] => F[Map[K, V]],
      trigger: F[Unit]
  ): Ref[F, Map[K, V]] => F[Either[Ref[F, Map[K, V]], Unit]] =
    (stateRef: Ref[F, Map[K, V]]) =>
      for {
        previousState <- stateRef.get
        newState <- refreshFn(previousState)
        _ <- stateRef.set(newState)
        _ <- trigger
      } yield stateRef.asLeft[Unit]

  def createRepeatedTrigger[F[_]: Temporal](timeout: FiniteDuration): F[Unit] =
    Temporal[F].sleep(timeout)

}
