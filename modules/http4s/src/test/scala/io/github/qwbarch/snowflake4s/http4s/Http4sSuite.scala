/**
 * Copyright (c) 2021 qwbarch
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.github.qwbarch.snowflake4s.http4s

import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers
import io.github.qwbarch.snowflake4s.http4s.syntax._
import io.github.qwbarch.snowflake4s.Snowflake
import io.github.qwbarch.snowflake4s.arbitrary._
import io.github.qwbarch.snowflake4s.circe.syntax._
import cats.effect.IO
import cats.syntax.all._
import org.http4s.dsl.io._
import org.http4s.Uri
import org.http4s.HttpRoutes
import org.http4s.Request
import org.http4s.dsl.impl.QueryParamDecoderMatcher
import org.http4s.circe.CirceEntityCodec._

object Http4sSuite extends SimpleIOSuite with Checkers {

  private object SnowflakeQueryParamMatcher extends QueryParamDecoderMatcher[Snowflake]("id")

  test("Snowflake var pattern matching") {
    forall { (snowflake: Snowflake) =>
      val routes = HttpRoutes.of[IO] { case GET -> Root / SnowflakeVar(snowflake) => Ok(snowflake) }
      routes
        .run(Request(method = GET, uri = Uri.unsafeFromString(show"/$snowflake")))
        .value
        .flatMap {
          case Some(response) => response.as[Snowflake].map(expect.same(snowflake, _))
          case None => failure("Unknown route.").pure
        }
    }
  }

  test("Snowflake query param matching") {
    forall { (snowflake: Snowflake) =>
      val routes = HttpRoutes.of[IO] { case GET -> Root / "entity" :? SnowflakeQueryParamMatcher(snowflake) =>
        Ok(snowflake)
      }
      routes
        .run(Request(method = GET, uri = Uri.unsafeFromString(show"/entity?id=$snowflake")))
        .value
        .flatMap {
          case Some(response) => response.as[Snowflake].map(expect.same(snowflake, _))
          case None => failure("Unknown route.").pure
        }
    }
  }
}
