package forex.services.rates.oneforge

import cats.effect.IO
import forex.domain._
import forex.services.rates.Errors.Error.CacheIsOutOfDate
import forex.services.rates.Errors._
import forex.services.rates.oneforge.cache.Cache
import org.scalatest.FunSuite
import org.scalatest.Matchers._

class OneForgeLiveServiceTest extends FunSuite {

  class DummyCache[K, V](storage: Map[K, V]) extends Cache[IO, K, V] {
    override def get(key: K): IO[Option[V]] = IO { storage.get(key) }

    // we do not use #put in these scenarios
    override def put(key: K, value: V): IO[Unit] = IO.unit
  }

  val storage: Map[Rate.Pair, Rate] = Map.empty

  val rate: Rate = Rate(Rate.Pair(Currency.AUD, Currency.JPY), Price(70.0), Timestamp.now)

  implicit val dummyCache: Cache[IO, Rate.Pair, Rate] =
    new DummyCache[Rate.Pair, Rate](Map(rate.pair -> rate))

  val service: OneForgeLiveService[IO] = new OneForgeLiveService[IO]

  test("#get returns Right(Rate) for existing value in the cache") {
    val valueIO: IO[Error Either Rate] = service.get(Rate.Pair(Currency.AUD, Currency.JPY))

    valueIO.unsafeRunSync() should be (Right(rate))
  }

  test("#get returns Left(CacheIsOutOfDate) for non-existent value in the cache") {
    val valueIO: IO[Error Either Rate] = service.get(Rate.Pair(Currency.EUR, Currency.AUD))

    valueIO.unsafeRunSync() should be (Left(CacheIsOutOfDate()))
  }

}
