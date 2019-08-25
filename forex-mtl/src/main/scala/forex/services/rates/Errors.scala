package forex.services.rates

object Errors {

  sealed trait Error

  object Error {

    final case class OneForgeLookupFailed(msg: String) extends Error

    final case class NetworkFailure(msg: String) extends Error
    final case class UnknownFailure(msg: String) extends Error
    final case class BadResponseFailure(msg: String) extends Error
    final case class CanNotRetrieveFromCache() extends Error

  }

}
