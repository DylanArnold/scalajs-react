import sbt._
import Keys._

import com.typesafe.sbt.pgp.PgpKeys._

import scala.scalajs.sbtplugin.env.nodejs.NodeJSEnv
import scala.scalajs.sbtplugin.env.phantomjs.PhantomJSEnv
import scala.scalajs.sbtplugin.ScalaJSPlugin._
import scala.scalajs.sbtplugin.ScalaJSPlugin.ScalaJSKeys._

object ScalajsReact extends Build {

  val Scala211 = "2.11.2"

  type PE = Project => Project

  def commonSettings: PE =
    _.settings(scalaJSSettings: _*)
      .settings(
        organization       := "com.github.japgolly.scalajs-react",
        version            := "0.4.1-SNAPSHOT",
        homepage           := Some(url("https://github.com/japgolly/scalajs-react")),
        licenses           += ("Apache-2.0", url("http://opensource.org/licenses/Apache-2.0")),
        scalaVersion       := Scala211,
        crossScalaVersions := Seq("2.10.4", Scala211),
        scalacOptions     ++= Seq("-deprecation", "-unchecked", "-language:_"))

  def preventPublication: PE =
    _.settings(
      publishArtifact := false,
      publishLocalSigned := (),       // doesn't work
      publishSigned := (),            // doesn't work
      packagedArtifacts := Map.empty) // doesn't work - https://github.com/sbt/sbt-pgp/issues/42

  def publicationSettings: PE =
    _.settings(
      publishTo := {
        val nexus = "https://oss.sonatype.org/"
        if (version.value.trim endsWith "SNAPSHOT")
          Some("snapshots" at nexus + "content/repositories/snapshots")
        else
          Some("releases"  at nexus + "service/local/staging/deploy/maven2")
      },
      pomExtra :=
        <scm>
          <connection>scm:git:github.com/japgolly/scalajs-react</connection>
          <developerConnection>scm:git:git@github.com:japgolly/scalajs-react.git</developerConnection>
          <url>github.com:japgolly/scalajs-react.git</url>
        </scm>
        <developers>
          <developer>
            <id>japgolly</id>
            <name>David Barri</name>
          </developer>
        </developers>)

  def utestSettings: PE =
    _.settings(utest.jsrunner.Plugin.utestJsSettings: _*)
      .configure(useReact("test"))
      .settings(
        libraryDependencies += "com.lihaoyi" %%% "utest" % "0.1.8" % "test",
        requiresDOM := true,
        jsEnv in Test := customPhantomJSEnv)

  def customPhantomJSEnv: PhantomJSEnv = {
    import scala.scalajs.sbtplugin.env.ExternalJSEnv.RunJSArgs
    import scala.scalajs.tools.io._
    new PhantomJSEnv {
      override protected def initFiles(args: RunJSArgs): Seq[VirtualJSFile] = Seq(
        new MemVirtualJSFile("bindPolyfill.js").withContent(Polyfills.functionBind3)
      )
    }
  }

  def useReact(scope: String = "compile"): PE =
    _.settings(
      jsDependencies += "org.webjars" % "react" % "0.11.1" % scope / "react-with-addons.js" commonJSName "React",
      skip in packageJSDependencies := false)

  // ==============================================================================================
  lazy val root = Project("root", file("."))
    .aggregate(core, test, example, scalaz70, scalaz71)
    .configure(commonSettings, preventPublication)

  // ==============================================================================================
  lazy val core = project
    .configure(commonSettings, publicationSettings)
    .settings(
      name := "core",
      libraryDependencies ++= Seq(
        "org.scala-lang.modules.scalajs" %%% "scalajs-dom" % "0.6",
        "com.scalatags" %%% "scalatags" % "0.3.5"))

  lazy val test = project
    .configure(commonSettings, publicationSettings, utestSettings)
    .dependsOn(core)
    .settings(name := "test")

  // ==============================================================================================
  def scalazModule(name: String, version: String) = {
    val shortName = name.replaceAll("[^a-zA-Z0-9]+", "")
    Project(shortName, file(name))
      .configure(commonSettings, publicationSettings)
      .dependsOn(core)
      .settings(
        Keys.name := s"ext-$shortName",
        libraryDependencies += "com.github.japgolly.fork.scalaz" %%% "scalaz-effect" % version)
  }

  lazy val scalaz70 = scalazModule("scalaz-7.0", "7.0.6")
  lazy val scalaz71 = scalazModule("scalaz-7.1", "7.1.0")

  // ==============================================================================================
  lazy val example = project
    .dependsOn(core, scalaz71)
    .configure(commonSettings, useReact(), preventPublication)

}
