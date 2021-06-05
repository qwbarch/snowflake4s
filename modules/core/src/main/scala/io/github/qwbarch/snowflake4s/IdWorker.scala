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
 *
 * This product includes software developed at Twitter (https://twitter.com/).
 */
package io.github.qwbarch.snowflake4s

import cats.syntax.all._
import cats.effect.kernel.Sync
import cats.effect.kernel.Ref
import org.typelevel.log4cats.Logger

/**
 * Generates new snowflake ids. Use [[IdWorkerBuilder]] to create workers.
 */
class IdWorker[F[_]: Sync: Logger](
    lastTimeStamp: Ref[F, Long],
    sequence: Ref[F, Long],
    epoch: Long,
    dataCenterId: Long,
    workerId: Long,
) {

  import IdWorker._

  /**
   * Calculates the current time in milliseconds.
   *
   * @return
   *   The current time in milliseconds.
   */
  protected def currentTimeMillis: F[Long] = Sync[F].delay(System.currentTimeMillis)

  /**
   * Busy-loops until the next millisecond, based on the given timestamp.
   *
   * @param lastTimeStamp
   *   The timestamp to base the next millisecond off of.
   * @return
   *   The next timestamp after the busy-looping finishes.
   */
  protected def tilNextMillis(lastTimeStamp: Long): F[Long] =
    currentTimeMillis.iterateWhile(_ <= lastTimeStamp)

  /**
   * Extracts the timestamp of a snowflake.
   *
   * @param snowflake The snowflake to extract from.
   * @return The timestamp of the snowflake.
   */
  def getTimeStamp(snowflake: Snowflake): Long = epoch + (snowflake.value >> TimeStampLeftShift)

  /**
   * Constructs a new [[Snowflake]] from the given timestamp and sequence.
   *
   * @param timeStamp The timestamp of the snowflake.
   * @param sequence The sequence of the snowflake.
   */
  def nextIdPure(timeStamp: Long, sequence: Long): Snowflake =
    Snowflake(
      ((timeStamp - epoch) << TimeStampLeftShift) |
        (dataCenterId << DataCenterIdShift) |
        (workerId << WorkerIdShift) |
        sequence,
    )

  /**
   * Checks if time is moving backwards. Will raise an exception if time is moving backwards.
   */
  private def verifyTimeMovingForward(currentTimeMillis: Long, lastTimeStamp: Long) =
    if (currentTimeMillis < lastTimeStamp)
      Logger[F].error(show"Clock is moving backwards. Rejecting requests until $lastTimeStamp.") *>
        Sync[F].raiseError(
          new RuntimeException(
            show"Clock moved backwards. Refusing to generate id for ${lastTimeStamp - currentTimeMillis} milliseconds.",
          ),
        )
    else Sync[F].unit

  /**
   * Updates the sequence and provides a new timestamp.
   */
  private def updateSequenceAndTimeStamp(currentTimeMillis: Long, lastTimeStamp: Long): F[(Long, Long)] =
    if (currentTimeMillis === lastTimeStamp)
      sequence
        .updateAndGet(it => (it + 1L) & SequenceMask)
        .flatMap(sequence =>
          (
            if (sequence === 0L) tilNextMillis(lastTimeStamp)
            else currentTimeMillis.pure[F]
          ).map(sequence -> _),
        )
    else sequence.set(0L).as(0L -> currentTimeMillis)

  /**
   * Generates a new snowflake id.
   *
   * @return A new snowflake id.
   */
  val nextId: F[Snowflake] =
    currentTimeMillis.flatMap(currentTimeMillis =>
      lastTimeStamp.access.flatMap { case (lastTimeStamp, setLastTimeStamp) =>
        for {
          _ <- verifyTimeMovingForward(currentTimeMillis, lastTimeStamp)
          sequenceTimeStamp <- updateSequenceAndTimeStamp(currentTimeMillis, lastTimeStamp)
          (sequence, timeStamp) = sequenceTimeStamp
          _ <- setLastTimeStamp(timeStamp)
        } yield nextIdPure(timeStamp, sequence),
      },
    )
}

object IdWorker {
  final val WorkerIdBits = 5L
  final val DataCenterIdBits = 5L
  final val MaxWorkerId = -1L ^ (-1L << WorkerIdBits)
  final val MaxDataCenterId = -1L ^ (-1L << DataCenterIdBits)
  final val SequenceBits = 12L
  final val WorkerIdShift = SequenceBits
  final val DataCenterIdShift = SequenceBits + WorkerIdBits
  final val TimeStampLeftShift = SequenceBits + WorkerIdBits + DataCenterIdBits
  final val SequenceMask = -1L ^ (-1L << SequenceBits)
  final val TwitterEpoch = 1288834974657L
}
