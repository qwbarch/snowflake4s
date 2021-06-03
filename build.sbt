import Dependency._

lazy val commonSettings = Seq(
   organization := "io.qwbarch",
   scalaVersion := "3.0.0",
   crossScalaVersions := Seq("3.0.0", "2.13.6", "2.12.14"),
)

lazy val root = (project in file("."))
   .settings(
      Compile / unmanagedSourceDirectories := Nil,
      Test / unmanagedSourceDirectories := Nil,
   )
   .aggregate(core)

lazy val core = (project in file("modules/core"))
   .settings(commonSettings)
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
   .settings(commonSettings)
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
