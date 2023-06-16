package forex.services.cache

import cats.Monad
import cats.effect.implicits._
import cats.effect.{ Ref, Temporal }
import cats.implicits._

import scala.concurrent.duration.FiniteDuration

class SelfRefreshingCache[F[_]: Monad, K, V](stateRef: F[Ref[F, Map[K, V]]]) extends Cache[F, K, V] {

  override def get(key: K): F[Option[V]] =
    stateRef
      .flatMap(_.get)
      .map(_.get(key))

  override def put(key: K, value: V): F[Unit] =
    stateRef
      .flatMap(_.update(_.updated(key, value)))

}

object SelfRefreshingCache {

  def createCache[F[_]: Temporal, K, V](initialState: Map[K, V],
                                        refreshFn: Map[K, V] => F[Map[K, V]],
                                        ttl: FiniteDuration): SelfRefreshingCache[F, K, V] = {
    val stateRefM = Ref.of[F, Map[K, V]](initialState)

    val refreshCacheFn: F[Ref[F, Map[K, V]]] = for {
      stateRef <- stateRefM
      state <- stateRef.get
      newState <- refreshFn(state)
      _ <- stateRef.update(_ => newState)
    } yield stateRef

    val refreshAction: F[Ref[F, Map[K, V]]] = refreshCacheFn.delayBy(ttl).foreverM

    new SelfRefreshingCache[F, K, V](refreshAction)
  }

}
