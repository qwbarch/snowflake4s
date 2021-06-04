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
package io.github.qwbarch.snowflake4s

import scala.util.Try
import cats.Show
import cats.kernel.Eq

/**
 * A 64-bit unique identifier with a timestamp.
 */
opaque type Snowflake = Long

object Snowflake:
  given showSnowflake: Show[Snowflake] = Show.fromToString
  given eqSnowflake: Eq[Snowflake] = Eq.fromUniversalEquals

  /**
   * Constructs a new [[Snowflake]].
   *
   * @param value The underlying id as a [[Long]].
   * @return A snowflake type with zero run-time overhead.
   */
  def apply(value: Long): Snowflake = value

  /**
   * Destructure the snowflake for pattern-matching.
   *
   * @param snowflake The snowflake id to destructure.
   * @return An option containing the underlying [[Long]].
   */
  def unapply(snowflake: Snowflake): Option[Long] = Some(snowflake)

  /**
   * Constructs a new [[Snowflake]] from a string.
   *
   * @param string The string to parse into a snowflake.
   * @return The snowflake, if the string is a valid long.
   */
  def fromString(string: String): Option[Snowflake] = Try(string.toLong).toOption

extension (snowflake: Snowflake)
  /**
   * Retrieve the underlying value.
   *
   * @return The snowflake id as a [[Long]].
   */
  def value: Long = snowflake
