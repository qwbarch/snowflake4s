# snowflake4s

## Overview

snowflake4s is a purely functional library used for generating unique ids, using Twitter's snowflake id format.

@@dependency[sbt,Maven,Gradle] {
  group="$organization$"
  artifact="snowflake4s_$scala.binary.version$"
  version="$version$"
}

## Quick example

Here is a minimal example to create a snowflake id:

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
- [fuuid](https://github.com/davenverse/fuuid) - Looking for something more lightweight? This is a purely functional library for generating UUID's.

## Credits

Full credits goes to [Twitter](https://about.twitter.com/). Implementation is from Twitter's [archives](https://github.com/twitter-archive/snowflake/blob/updated_deps/src/main/scala/com/twitter/service/snowflake/IdWorker.scala).
