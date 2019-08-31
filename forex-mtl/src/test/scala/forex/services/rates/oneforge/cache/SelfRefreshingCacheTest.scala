package forex.services.rates.oneforge.cache

import cats.effect.IO
import cats.effect.concurrent.Ref
import org.scalatest.FunSuite
import org.scalatest.Matchers._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.global

class SelfRefreshingCacheTest extends FunSuite {

  val initialState = Map[String, Int]("existing" -> 42)
  val refresher    = (state: Map[String, Int]) => IO { state.updated("burrito", -14) }

  implicit val cs    = IO.contextShift(global)
  implicit val timer = IO.timer(global)

  val refreshingRoutine =
    (state: Ref[IO, Map[String, Int]]) => state.get.flatMap(refresher).flatMap(state.getAndSet).flatMap(_ => IO.unit)

  val cacheIO: IO[Cache[IO, String, Int]] =
    SelfRefreshingCache.createCache[IO, String, Int](initialState, refreshingRoutine)

  test("#get returns Some for existing value") {
    /*
      Just a few notes on this line:

      1. `unsafeRunTimed(duration)` prevents a memory leak when the routines never stop, even after test finishes
      2. the outer `Some()` comes from p.1 - `unsafeRunTimed` returns an `Option`
     */

    cacheIO.flatMap(c => c.get("existing")).unsafeRunTimed(10.seconds) should be (Some(Some(42)))
  }

  test("#get returns None for non-existing value") {
    cacheIO.flatMap(c => c.get("incognito")).unsafeRunTimed(10.seconds) should be (Some(None))
  }

  test("#get after #put returns new value") {
    val newCacheIO = for {
      cache <- cacheIO
      _ <- cache.put("extravaganza", 7)
      newValue <- cache.get("extravaganza")
    } yield newValue

    newCacheIO.unsafeRunTimed(10.seconds) should be(Some(Some(7)))
  }

  test("refresher function updates the value") {
    cacheIO.flatMap(c => c.get("burrito")).unsafeRunTimed(10.seconds) should be (Some(Some(-14)))
  }

}
