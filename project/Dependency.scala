import sbt._

object Dependency {

   private def dependency(group: String)(version: String)(useScalaVersion: Boolean)(artifact: String) =
      (if (useScalaVersion) group %% _ else group % _)(artifact) % version
   
   private val typeLevel = dependency("org.typelevel") _
   val catsCore = typeLevel(Version.catsCore)(true)("cats-core")
   val catsKernel = typeLevel(Version.catsKernel)(true)("cats-kernel")

   private val catsEffect = typeLevel(Version.catsEffect)(true)
   val catsEffectCore = catsEffect("cats-effect")
   val catsEffectStd = catsEffect("cats-effect-std")
   val catsEffectKernel = catsEffect("cats-effect-kernel")

   private val log4Cats = typeLevel(Version.log4Cats)(true)
   val log4CatsCore = log4Cats("log4cats-core")
   val log4CatsNoOp = log4Cats("log4cats-noop")

   private val weaver = dependency("com.disneystreaming")(Version.weaver)(true) _
   val weaverCats = weaver("weaver-cats")
   val weaverScalaCheck = weaver("weaver-scalacheck")
}
