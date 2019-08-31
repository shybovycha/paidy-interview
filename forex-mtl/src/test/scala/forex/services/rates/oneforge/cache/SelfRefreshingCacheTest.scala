package forex.services.rates.oneforge.cache

import cats.effect.IO
import cats.syntax.all._
import org.scalatest.FunSuite
import org.scalatest.Matchers._

import scala.concurrent.ExecutionContext.global

class SelfRefreshingCacheTest extends FunSuite {

  val initialState = Map[String, Int]("existing" -> 42)
  val refresher    = (state: Map[String, Int]) => IO { state.updated("burrito", -14) }
  val trigger      = IO.unit

  implicit val cs    = IO.contextShift(global)
  implicit val timer = IO.timer(global)

  val cacheIO: IO[Cache[IO, String, Int]] =
    SelfRefreshingCache.createCache[IO, String, Int](initialState, refresher, trigger)

  test("#get returns Some for existing value") {
    (cacheIO >>= (c => c.get("existing"))).unsafeRunSync() should be(Some(42))
  }

  test("#get returns None for non-existing value") {
    (cacheIO >>= (c => c.get("incognito"))).unsafeRunSync() should be(None)
  }

  // TODO: have to figure out how to trigger the `Concurrent#sync` execution
  ignore("#get after #put returns new value") {
    val newCacheIO = for {
      cache <- cacheIO
      _ <- cache.put("extravaganza", 7)
      newValue <- cache.get("extravaganza")
    } yield newValue

    newCacheIO.unsafeRunSync() should be(Some(42))
  }

  // TODO: have to figure out how to trigger the `Concurrent#sync` execution
  ignore("refresher function updates the value") {
    (cacheIO >>= (c => c.get("burrito"))).unsafeRunSync() should be(Some(-14))
  }

}
