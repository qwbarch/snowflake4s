import Dependency._
import ReleaseTransformations._

lazy val commonSettings = Seq(
  organization := "io.github.qwbarch",
  scalaVersion := "2.12.14",
  crossScalaVersions := Seq("3.0.0", "2.13.6", "2.12.14"),
)

lazy val root = (project in file("."))
  .settings(commonSettings ++ noPublishSettings ++ releaseSettings)
  .settings(
    Compile / unmanagedSourceDirectories := Nil,
    Test / unmanagedSourceDirectories := Nil,
  )
  .aggregate(core)

lazy val core = (project in file("modules/core"))
  .settings(commonSettings ++ publishSettings)
  .settings(
    name := "snowflake4s",
    testFrameworks += new TestFramework("weaver.framework.CatsEffect"),
    libraryDependencies ++= Seq(
      catsCore,
      catsKernel,
      catsEffectStd,
      catsEffectKernel,
      log4CatsCore,
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
    // Use newtypes for scala 2.13
    libraryDependencies ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, 13)) => newType :: Nil
        case _ => Nil
      }
    },
    // Enable some scala 3 syntax for scala 2.12 and 2.13
    scalacOptions ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, n)) if n >= 12 => "-Xsource:3" :: Nil
        case _ => Nil
      }
    },
  )

lazy val docs = (project in file("modules/docs"))
  .dependsOn(core)
  .enablePlugins(ParadoxPlugin)
  .enablePlugins(ParadoxSitePlugin)
  .enablePlugins(GhpagesPlugin)
  .settings(commonSettings ++ noPublishSettings)
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
    setNextVersion,
    commitNextVersion,
    releaseStepCommand("sonatypeRelease"),
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
