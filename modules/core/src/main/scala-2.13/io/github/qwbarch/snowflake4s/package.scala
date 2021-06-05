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
package io.github.qwbarch

import io.estatico.newtype.macros.newtype
import scala.util.Try
import cats.Show
import cats.kernel.Eq

package object snowflake4s {

  /**
   * A 64-bit unique identifier with a timestamp.
   */
  @newtype case class Snowflake(value: Long)

  object Snowflake {
    implicit val showSnowflake: Show[Snowflake] = Show.fromToString
    implicit val eqSnowflake: Eq[Snowflake] = Eq.fromUniversalEquals

    /**
     * Destructure the snowflake for pattern-matching.
     *
     * @param snowflake The snowflake id to destructure.
     * @return An option containing the underlying value.
     */
    def unapply(snowflake: Snowflake): Option[Long] = Some(snowflake.value)

    /**
     * Constructs a new [[Snowflake]] from a string.
     *
     * @param string The string to parse into a snowflake.
     * @return The snowflake, if the string is a valid long.
     */
    def fromString(string: String): Option[Snowflake] = Try(string.toLong).toOption.map(Snowflake.apply)
  }
}
