import Dependency._

lazy val root = (project in file("."))
   .settings(
      organization := "io.github.qwbarch",
      scalaVersion := "3.0.0",
      crossScalaVersions := Seq("3.0.0", "2.13.6", "2.12.14"),
      libraryDependencies ++= Seq(
         catsCore,
         catsKernel,
         catsEffectStd,
         catsEffectKernel,
         log4CatsCore,
      ),
   )
