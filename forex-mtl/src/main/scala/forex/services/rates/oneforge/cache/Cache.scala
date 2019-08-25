package forex.services.rates.oneforge.cache

trait Cache[F[_], K, V] {
  def get(key: K): F[Option[V]]

  def put(key: K, value: V): F[Unit]
}
