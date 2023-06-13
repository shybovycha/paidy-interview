package forex.services.cache

import cats.Monad
import cats.effect.implicits.genSpawnOps
import cats.effect.{Concurrent, Ref, Temporal}
import cats.implicits._

import scala.concurrent.duration.FiniteDuration

class SelfRefreshingCache[F[_]: Concurrent, K, V](stateRefF: F[Ref[F, Map[K, V]]], refreshAction: F[Unit])
    extends Cache[F, K, V] {

  override def get(key: K): F[Option[V]] =
    for {
      stateRef <- stateRefF
      state <- stateRef.get
    } yield state.get(key)

  override def put(key: K, value: V): F[Unit] =
    for {
      stateRef <- stateRefF
      _ <- stateRef.update(_.updated(key, value))
    } yield ()

  def start(): F[Unit] =
    refreshAction.start.void

}

object SelfRefreshingCache {

  def createCache[F[_]: Concurrent, K, V](initialState: Map[K, V],
                                          refreshFn: Map[K, V] => F[Map[K, V]],
                                          trigger: F[Unit]): SelfRefreshingCache[F, K, V] =
  {
    val stateRefF = Ref.of[F, Map[K, V]](initialState)
    val refreshAction = stateRefF.flatMap(Monad[F].tailRecM(_)(refreshCache(refreshFn, trigger)))

    new SelfRefreshingCache[F, K, V](stateRefF, refreshAction)
  }


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
