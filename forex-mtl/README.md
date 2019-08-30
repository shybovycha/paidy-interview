# Forex-MTL

## Description

This is my interpretation and implementation of forex-mtl.

The original task constraints 

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

### Rates interpreters

This project is not fully tagless, so it uses the classic dependency-injection-like approach of instantiating the
specific classes (in this case - `live` or `dummy`) and then passing them around.
