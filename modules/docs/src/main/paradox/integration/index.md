# Library Integration

This section shows examples on how to integrate snowflake4s with other libraries.

## Circe

@@dependency[sbt,Maven,Gradle] {
  group="$organization$"
  artifact="snowflake4s-circe_$scala.binary.version$"
  version="$version$"
}

The circe module provides an encoder/decoder for snowflakes.

```scala
import io.circe.syntax.given
import io.github.qwbarch.snowflake4s.circe.syntax.given
import io.github.qwbarch.snowflake4s.Snowflake

val snowflake = Snowflake(123456789L)
val encoded = snowflake.asJson
val decoded = encoded.as[Snowflake]
```

## Skunk

@@dependency[sbt,Maven,Gradle] {
  group="$organization$"
  artifact="snowflake4s-skunk_$scala.binary.version$"
  version="$version$"
}

The skunk module provides a codec for snowflakes.

```scala
import skunk.Command
import skunk.syntax.all.sql
import io.github.qwbarch.snowflake4s.skunk.codec.snowflake

val command: Command[Snowflake] = sql"DELETE FROM messages WHERE id=$snowflake".command
```

## Http4s

@@dependency[sbt,Maven,Gradle] {
  group="$organization$"
  artifact="snowflake4s-http4s_$scala.binary.version$"
  version="$version$"
}

The http4s module provides an extractor for handling path parameters, as well as an implicit required
for a query parameter matcher.

```scala
import org.http4s.dsl.io.*
import org.http4s.HttpRoutes
import cats.effect.IO
import cats.syntax.all.given
import io.github.qwbarch.snowflake4s.http4s.syntax.given
import io.github.qwbarch.snowflake4s.Snowflake

val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
   case GET -> Root / SnowflakeVar(snowflake) => Ok(snowflake.show)
   case GET -> Root / "user" :? SnowflakeQueryParamMatcher(snowflake) => Ok(snowflake.show)
}
```
