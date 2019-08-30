package forex.services.rates.oneforge.cache

import cats.effect.Concurrent
import cats.effect.IO
import cats.syntax.all._
import org.scalatest.FunSuite
import org.scalatest.Ignore
import org.scalatest.Matchers._

@Ignore
class SelfRefreshingCacheTest extends FunSuite {

  val initialState = Map[String, Int]("existing" -> 42)
  val refresher = (state: Map[String, Int]) => IO { state.updated("new-existing", -14) }
  val trigger = IO.unit

  implicit val c: Concurrent[IO] = ???

  val cacheIO: IO[Cache[IO, String, Int]] = SelfRefreshingCache.createCache[IO, String, Int](initialState, refresher, trigger)

  test("#get returns Some for existing value") {
    (cacheIO >>= (c => c.get("existing"))).unsafeRunSync() should be (Some(42))
  }

  test("#get returns None for non-existing value") {
    (cacheIO >>= (c => c.get("inexistent"))).unsafeRunSync() should be (None)
  }

  test("#get after #put returns new value") {
    val newCacheIO = for {
      cache <- cacheIO
      _ <- cache.put("new", 7)
      newValue <- cache.get("new")
    } yield newValue

    newCacheIO.unsafeRunSync() should be (Some(42))
  }

   test("refresher function updates the value") {
     (cacheIO >>= (c => c.get("new-existing"))).unsafeRunSync() should be (Some(-14))
   }

}
