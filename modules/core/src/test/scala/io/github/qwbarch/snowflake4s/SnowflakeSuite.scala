package io.github.qwbarch.snowflake4s

import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers
import io.github.qwbarch.snowflake4s.arbitrary._
import cats.syntax.all._

object SnowflakeSuite extends SimpleIOSuite with Checkers {

  test("Snowflake.toString") {
    forall { (snowflake: Snowflake) =>
      expect.same(snowflake.value.toString, snowflake.toString)
    }
  }

  test("Snowflake.show") {
    forall { (snowflake: Snowflake) =>
      expect.same(snowflake.toString, snowflake.show)
    }
  }

  test("Snowflake.##") {
    forall { (snowflake: Snowflake) =>
      expect.same(snowflake.value.##, snowflake.##)
    }
  }

  test("Snowflake.equals") {
    forall { (snowflake: Snowflake) =>
      val copy = Snowflake(snowflake.value)
      expect.same(snowflake, copy)
    }
  }
}
