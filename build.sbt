import Dependency._
import ReleaseTransformations._

lazy val commonSettings = Seq(
   organization := "io.github.qwbarch",
   scalaVersion := "3.0.0",
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
      publish / skip := true,
      paradoxTheme := Some(builtinParadoxTheme("generic")),
      paradoxProperties ++= Map(
         "organization" -> organization.value,
         "version" -> version.value,
      ),
   )

lazy val publishSettings =
   releaseSettings ++ sharedPublishSettings ++ credentialSettings ++ sharedReleaseProcess

lazy val credentialSettings = Seq(
   credentials ++= (for {
      username <- Option(System.getenv().get("SONATYPE_USERNAME"))
      password <- Option(System.getenv().get("SONATYPE_PASSWORD"))
   } yield Credentials(
      "Sonatype Nexus Repository Manager",
      "s01.oss.sonatype.org",
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
)

lazy val sharedPublishSettings = Seq(
   publishTo := Some(
      if (isSnapshot.value) Opts.resolver.sonatypeSnapshots
      else Opts.resolver.sonatypeStaging,
   ),
)
