package forex.domain

case class Price(value: BigDecimal) extends AnyVal

object Price {
  def apply(value: Integer): Price =
    Price(BigDecimal(value))

  def apply(value: Double): Price =
    Price(BigDecimal(value))
}
