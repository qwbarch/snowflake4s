import Dependency._
import ReleaseTransformations._

lazy val commonSettings: Seq[SettingsDefinition] = Seq(
  organization := "io.github.qwbarch",
  scalaVersion := "3.1.0",
  crossScalaVersions := Seq("3.1.0", "2.13.8", "2.12.15"),
  testFrameworks += new TestFramework("weaver.framework.CatsEffect"),
  libraryDependencies ++= Seq(
    log4CatsNoOp % Test,
    weaverCats % Test,
    weaverScalaCheck % Test,
  ),
  // Enable Ymacro-annotations for scala 2.13, required for newtypes
  scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 13)) => "-Ymacro-annotations" :: Nil
      case _ => Nil
    }
  },
  // Enable some scala 3 syntax for scala 2.12 and 2.13
  scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) => "-Xsource:3" :: Nil
      case _ => Nil
    }
  },
  // Enable better-monadic-for with non-dotty versions
  libraryDependencies ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) => compilerPlugin(betterMonadicFor) :: Nil
      case _ => Nil
    }
  },
)

lazy val root = (project in file("."))
  .settings(commonSettings ++ noPublishSettings ++ releaseSettings: _*)
  .settings(
    Compile / unmanagedSourceDirectories := Nil,
    Test / unmanagedSourceDirectories := Nil,
  )
  .aggregate(core, circe, skunk, http4s)

lazy val core = (project in file("modules/core"))
  .settings(commonSettings ++ publishSettings: _*)
  .settings(
    name := "snowflake4s",
    libraryDependencies ++= Seq(
      catsCore,
      catsKernel,
      catsEffectKernel,
      log4CatsCore,
    ),
    // Use newtypes for scala 2.13
    libraryDependencies ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, 13)) => newType :: Nil
        case _ => Nil
      }
    },
  )

lazy val circe = (project in file("modules/circe"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(commonSettings ++ publishSettings: _*)
  .settings(
    name := "snowflake4s-circe",
    libraryDependencies += circeCore,
  )

lazy val skunk = (project in file("modules/skunk"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(commonSettings ++ publishSettings: _*)
  .settings(
    name := "snowflake4s-skunk",
    libraryDependencies += skunkCore,
  )

lazy val http4s = (project in file("modules/http4s"))
  .dependsOn(core % "compile->compile;test->test")
  .dependsOn(circe % "test->test")
  .settings(commonSettings ++ publishSettings: _*)
  .settings(
    name := "snowflake4s-http4s",
    libraryDependencies ++= Seq(
      http4sCore,
      http4sDsl % Test,
      http4sCirce % Test,
    ),
  )

lazy val docs = (project in file("modules/docs"))
  .dependsOn(core)
  .enablePlugins(ParadoxPlugin)
  .enablePlugins(ParadoxSitePlugin)
  .enablePlugins(GhpagesPlugin)
  .settings(commonSettings ++ noPublishSettings: _*)
  .settings(
    scalacOptions := Nil,
    git.remoteRepo := "git@github.com:qwbarch/snowflake4s.git",
    ghpagesNoJekyll := true,
    paradoxTheme := Some(builtinParadoxTheme("generic")),
    paradoxProperties ++= Map(
      "organization" -> organization.value,
      "version" -> version.value,
    ),
  )

lazy val publishSettings =
  releaseSettings ++ sharedPublishSettings ++ credentialSettings ++ sharedReleaseProcess

lazy val credentialSettings = Seq(
  credentials ++=
    (for {
      username <- Option(System.getenv().get("SONATYPE_USERNAME"))
      password <- Option(System.getenv().get("SONATYPE_PASSWORD"))
    } yield Credentials(
      "Sonatype Nexus Repository Manager",
      "oss.sonatype.org",
      username,
      password,
    )).toSeq,
)

lazy val noPublishSettings = Seq(
  publish := (()),
  publishLocal := (()),
  publishArtifact := false,
  publish / skip := true,
)

lazy val sharedReleaseProcess = Seq(
  releaseProcess := Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    runClean,
    runTest,
    setReleaseVersion,
    commitReleaseVersion,
    tagRelease,
    releaseStepCommandAndRemaining("+publishSigned"),
    releaseStepCommand("sonatypeBundleRelease"),
    setNextVersion,
    commitNextVersion,
    pushChanges,
  ),
)

lazy val releaseSettings = Seq(
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/qwbarch/snowflake4s"),
      "scm:git:git@github.com:qwbarch/snowflake4s.git",
    ),
  ),
  homepage := Some(url("https://github.com/qwbarch/snowflake4s")),
  licenses := Seq(
    "MIT" -> url("https://opensource.org/licenses/MIT"),
  ),
  pomIncludeRepository := { _ =>
    false
  },
  developers := List(
    Developer(
      id = "qwbarch",
      name = "Edward Yang",
      email = "edwardyang0410@gmail.com",
      url = url("https://github.com/qwbarch"),
    ),
  ),
  publishMavenStyle := true,
  Test / publishArtifact := false,
  sonatypeCredentialHost := "s01.oss.sonatype.org",
  sonatypeRepository := "https://s01.oss.sonatype.org/service/local",
  versionScheme := Some("semver-spec"),
  releaseCrossBuild := true,
  usePgpKeyHex("32C27C5322CC8C7A353D1642E524CDF123A41CB7"),
)

lazy val sharedPublishSettings = Seq(publishTo := sonatypePublishToBundle.value)

addCommandAlias("ci", "+undeclaredCompileDependenciesTest; +unusedCompileDependenciesTest; scalafmtCheckAll; +test")
