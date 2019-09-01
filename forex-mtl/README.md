# Forex-MTL

## Description

This is my interpretation and implementation of forex-mtl.

The original task and constraints were:

> An internal user of the application should be able to ask for an exchange rate between 
> 2 given currencies, and get back a rate that is not older than 5 minutes.
> The application should at least support 10.000 requests per day.
>
> In practice, this should require the following 2 points:
>
> 1. Create a live interpreter for the oneforge service. This should consume the 1forge API, and do so using the free tier.
> 2. Adapt the rates processes (if necessary) to make sure you cover the requirements of the use case, and work around possible limitations of the third-party provider.
> 3. Make sure the service's own API gets updated to reflect the changes you made in point 1 & 2.

Current implementation relies strictly on cached data and refreshing the cache every 5 minutes
(in order to keep the data not older than 5 minutes). OneForge free 14-day trial (aka Starter tier)
allows for 5000 requests per day. Current implementation will make 12 calls per hour or 288 calls per day.
Retrieving data from cache is extremely cheap, so the amount of daily requests to the application
will only be limited by the server capabilities, not by the pricing plan of OneForge.

In order to make application as flexible as possible, one can fine-tune both the API base URL
and the frequency of updates by changing the corresponding params in the `application.conf` file.

If users desire to substitute OneForge API with some other implementation (like in my case - the mock API),
they mush ensure the API is aligned with OneForge's `/quotes` and `/symbols` endpoints' contracts. 

I am not 100% sure about the `rates processes` part of the requirements (is it `/quotes` endpoint of 1forge API?),
but other than that I feel the implementation covers everything else.

## Some decisions made

### `Cache` and `SelfRefreshingCache`

I had an idea to store the conversion data in the cache and only use the cache to process user requests.
`Cache` algebra is defined for that purpose.

Cache should be then refreshed by some kind of a worker or scheduled job, every 5 minutes, to keep data freshness requirement.

Since the rest of the project is all about monadic transformations, I decided to create a cache which will automatically
refresh its data. Since any cache has an effect of storing the data, the auto-refreshing functionality would be another one
hence I extracted it into a separate monad.

However, having this kind of functionality requires the instantiation of this monad to be done **before**
kicking off the API request handler to prevent the extract cache from request thread.

### `OneForgeLiveClient`

This class is not split into `Algebra` and `Interpreter`s, since it is too implementation-specific,
designed to be used in couple with `SelfRefreshingCache` exclusively. 

It is the provider of the `refresher` instance to the `SelfRefreshingCache`. It takes the base URL and the
cache expiration timeout from the config file, `application.conf`, the `forex` section.

One more extravagant solution which could have been applied in this class is using the `Nested` monad transformer.
However, it would not give ton of benefit (in fact, it will make things just worse - monad transformers are known to be
slow and using it will add more characters to the code for sake 🍶 of just one operation):

```scala
import cats.data.Nested
Nested(symbolsUri.flatMap(fetcher)).map(parseCurrencyPairFromCode).value
```

vs

```scala
symbolsUri .flatMap(fetcher) .map(_.map(parseCurrencyPairFromCode))
```

See how it is `29` chars less and achieves exactly the same goal.

### Rates interpreters

This project is not fully tagless, so it uses the classic dependency-injection-like approach of instantiating the
specific classes (in this case - `live` or `dummy`) and then passing them around.

### API

I've implemented a stub API in Ruby to not hit the real 1Forge API.
This way I can control the responses and the server status to check the application behaviour in those scenarios.

### Testing

I was considering testing with `Discipline`, but failed terribly. Hence I've decided to stay close to MTL, but test
with conventional Scala tools. This led to few test scenarios for core pieces of application 
(the ones which make sense to test, not the 3rd party libraries) - `SelfRefreshingCache`, `OneForgeLiveClient` and
the more integration-ish test for `OneForgeLiveService` (combining the above two).

Since `SelfRefreshingCache` originally had everything in it (check the commits history for reference, if you are keen),
it was hard to come up with a sane approach to testing it. Hence I've pulled out as much stuff as possible and parametrized
whatever made sense. That's where the APIs in the form they are now come from.

Decoupling the timer _trigger_ and cache _refreshing routine_, for instance, allowed me to stub / mock those behaviours
and thus test the cache itself, not the `Timer` from cats.

One more important note is: previously, since all the timer logic was stuffed into the `SelfRefreshingCache` itself,
every time I was running tests, my whole SBT console would go stuck and laptop would start taking off. It was most
likely caused by the fact that I've used a separate thread to start cache refreshing routine
(specifically important for tests), and it never stopped (even when tests were done).

In my desperate try to prevent that, I've used `unsafeRunTimed(10.seconds)` so that routines would stop themselves.
It caused my assertions look funny
(`subjectIO.unsafeRunTimed(10.seconds) should be (Some(Some(x)))` or even `should be (Some(None))`)
but it worked. Since I've extracted that routine to a parameter, the need for this suddenly disappeared - I was just
mocking the thing with immediate evaluation.

## What could be done differently

### Error handling

There could be lot of things done differently in this regards.
For instance, when an API is down, it might be worth reducing the cache refresh cooldown time
to pick up the data when API recovers.

It also might be worth logging errors whenever they occur in request processing time.

The other thing I was thinking about whilst implementing this solution was using `ApplicativeHandle`, but asking around
showed that it has no implementation for `IO` in `cats-effects` just yet, so I trashed that idea. Currently I am using
`MonadError` instead, which throws an error, so maybe it would be worth catching it at some point with `handleErrorWith`?

Also worth mentioning parsing `Currency` from `String` would not handle case when the currency code does not exist. 
Have to come up with a reasonable way to handle that. 

### Testing

Could add more integration tests to cover the API overall, not just core parts of logic.

### Chaining `Cache` methods

I was thinking about returning `F[Cache[F, K, V]]` from `Cache#put` so that I can write something like this
(in the test, for ex.):

```scala
cacheIO.flatMap(_.put("extravaganza", 7)).flatMap(_.get("extravaganza")).unsafeRunTimed(10.seconds) should be (Some(Some(7)))
```

That might be controversial in some sense, but would make `Cache` more functional rather than effectful. 

Alternatively, _(if `IO` monad would allow that)_ I would use `flatTap` _(which `IO` does not implement)_:

```scala
cacheIO.flatTap(_.put("extravaganza", 7)).flatMap(_.get("extravaganza")).unsafeRunTimed(10.seconds) should be (Some(Some(7)))
```
