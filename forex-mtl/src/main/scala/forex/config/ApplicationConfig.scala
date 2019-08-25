package forex.config

import scala.concurrent.duration.FiniteDuration

case class ApplicationConfig(
    http: HttpConfig,
    forex: ForexConfig
)

case class ForexConfig(
    host: String,
    apiKey: String,
    dataExpiresIn: FiniteDuration
)

case class HttpConfig(
    host: String,
    port: Int,
    timeout: FiniteDuration
)
