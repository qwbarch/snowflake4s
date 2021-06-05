@@@ index

* [Integration](integration/index.md)

@@@

# snowflake4s

## Overview

[![GitHub Workflow Status](https://img.shields.io/github/workflow/status/qwbarch/snowflake4s/Scala%20CI?logo=github)](https://github.com/qwbarch/snowflake4s/actions/workflows/scala.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.qwbarch/snowflake4s_3)](https://search.maven.org/artifact/io.github.qwbarch/snowflake4s_3/1.0.0/jar)
[![scaladoc](https://javadoc.io/badge2/io.github.qwbarch/snowflake4s_3/scaladoc.svg)](https://javadoc.io/doc/io.github.qwbarch/snowflake4s_3)
[![license](https://img.shields.io/badge/license-MIT-green)](https://opensource.org/licenses/MIT)
[![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)

snowflake4s is a purely functional library used for generating unique ids, using Twitter's snowflake id format.

@@dependency[sbt,Maven,Gradle] {
  group="$organization$"
  artifact="snowflake4s_$scala.binary.version$"
  version="$version$"
}

## Quick example

Here is a minimal example to create a snowflake id (Scala 3):

```scala
import cats.syntax.all.given
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.std.Console
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import io.qwbarch.snowflake4s.IdWorkerBuilder

object Main extends IOApp.Simple:

   override def run: IO[Unit] =
      given Logger[IO] = Slf4jLogger.getLogger[IO]
      for
         idWorker <- IdWorkerBuilder.default[IO].build
         id <- idWorker.nextId
         _ <- Console[IO].println(id)
      yield ()
```

## Customizing id workers

A custom epoch, sequence, worker id, and data center id can be provided through the ``IdWorkerBuilder``.

```scala
IdWorkerBuilder.default[IO]
   .withWorkerId(0)
   .withDataCenterId(0)
   .withEpoch(IdWorker.TwitterEpoch)
   .withSequence(0)
   .build
```

## Running tests

To run snowflake4s' unit tests, simply enter the following command:

```
sbt test
```

## Alternative libraries
- [scala-id-generator](https://github.com/softwaremill/scala-id-generator) - If you're looking to generate snowflake ids imperatively, try out this library from lightbend!
- [fuuid](https://github.com/davenverse/fuuid) - Looking for something more lightweight than snowflakes? This is a purely functional library for generating UUID's.

## Credits

Full credits goes to [Twitter](https://about.twitter.com/). Implementation is from Twitter's [archives](https://github.com/twitter-archive/snowflake/blob/updated_deps/src/main/scala/com/twitter/service/snowflake/IdWorker.scala).
