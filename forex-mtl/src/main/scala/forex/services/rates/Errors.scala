package forex.services.rates

object Errors {

  sealed trait Error

  object Error {
    final case class OneForgeLookupFailed(msg: String) extends Error
  }

}
