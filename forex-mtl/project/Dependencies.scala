import sbt._

object Dependencies {

  object Versions {
    val cats                = "1.6.0"
    val catsEffect          = "1.2.0"
    val fs2                 = "1.0.4"
    val http4s              = "0.20.0-M1"
    val circe               = "0.11.1"
    val pureConfig          = "0.10.2"

    val kindProjector       = "0.9.9"
    val supersafe           = "1.1.3"
    val logback             = "1.2.3"
    val scalaCheck          = "1.14.0"
    val scalaTest           = "3.0.7"
    val catsScalaCheck      = "0.1.1"
    val catsLaws            = "1.1.0"
    val shapeless           = "1.1.6"
    val discipline          = "1.0.0"
  }

  object Libraries {
    def circe(artifact: String): ModuleID = "io.circe"    %% artifact % Versions.circe
    def http4s(artifact: String): ModuleID = "org.http4s" %% artifact % Versions.http4s

    lazy val cats                = "org.typelevel"         %% "cats-core"                  % Versions.cats
    lazy val catsEffect          = "org.typelevel"         %% "cats-effect"                % Versions.catsEffect
    lazy val fs2                 = "co.fs2"                %% "fs2-core"                   % Versions.fs2

    lazy val http4sDsl           = http4s("http4s-dsl")
    lazy val http4sServer        = http4s("http4s-blaze-server")
    lazy val http4sCirce         = http4s("http4s-circe")
    lazy val http4sClient        = http4s("http4s-client")
    lazy val http4sBlazeClient   = http4s("http4s-blaze-client")
    lazy val circeCore           = circe("circe-core")
    lazy val circeGeneric        = circe("circe-generic")
    lazy val circeGenericExt     = circe("circe-generic-extras")
    lazy val circeParser         = circe("circe-parser")
    lazy val circeJava8          = circe("circe-java8")
    lazy val pureConfig          = "com.github.pureconfig"        %% "pureconfig"                 % Versions.pureConfig

    // Compiler plugins
    lazy val kindProjector       = "org.spire-math"               %% "kind-projector"             % Versions.kindProjector
    lazy val supersafe           = "com.artima.supersafe"         %% "sbtplugin"                  % Versions.supersafe

    // Runtime
    lazy val logback             = "ch.qos.logback"               %  "logback-classic"            % Versions.logback

    // Test
    lazy val scalaTest           = "org.scalatest"                %% "scalatest"                  % Versions.scalaTest
    lazy val scalaCheck          = "org.scalacheck"               %% "scalacheck"                 % Versions.scalaCheck
    lazy val catsScalaCheck      = "io.chrisdavenport"            %% "cats-scalacheck"            % Versions.catsScalaCheck
    lazy val catsLaws            = "org.typelevel"                %% "cats-laws"                  % Versions.catsLaws
    lazy val shapeless           = "com.github.alexarchambault"   %% "scalacheck-shapeless_1.13"  % Versions.shapeless
//    lazy val discipline          = "org.typelevel"         %% "discipline-core"            % Versions.discipline
//    lazy val disciplineScalaTest = "org.typelevel"         %% "discipline-scalatest"       % Versions.discipline
  }

}
