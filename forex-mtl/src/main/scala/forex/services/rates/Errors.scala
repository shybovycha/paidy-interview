package forex.services.rates

object Errors {

  sealed trait Error extends Throwable

  object Error {

    final case class OneForgeLookupFailed(causedBy: Throwable) extends Error

    final case class NetworkFailure(causedBy: Throwable) extends Error
    final case class UnknownFailure(causedBy: Throwable) extends Error
    final case class BadResponseFailure(causedBy: Throwable) extends Error
    final case class CanNotRetrieveFromCache() extends Error
    final case class CacheIsOutOfDate() extends Error
    final case class BadConfiguration(causedBy: Throwable) extends Error
    final case class CanNotParseSymbolsUri(causedBy: Throwable) extends Error
    final case class CanNotParseConvertUri(causedBy: Throwable) extends Error

  }

}
