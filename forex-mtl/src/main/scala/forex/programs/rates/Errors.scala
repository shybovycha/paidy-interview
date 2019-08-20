package forex.programs.rates

import forex.services.rates.Errors.{ Error => RatesServiceError }

object Errors {

  sealed trait Error extends Exception

  object Error {

    final case class RateLookupFailed(msg: String) extends Error

  }

  def toProgramError(error: RatesServiceError): Error = error match {
    case RatesServiceError.OneForgeLookupFailed(msg) => Error.RateLookupFailed(msg)
    case RatesServiceError.NetworkFailure(msg) => Error.RateLookupFailed(msg)
    case RatesServiceError.BadResponseFailure(msg) => Error.RateLookupFailed(msg)
    case _ => Error.RateLookupFailed("Unknown error")
  }

}
