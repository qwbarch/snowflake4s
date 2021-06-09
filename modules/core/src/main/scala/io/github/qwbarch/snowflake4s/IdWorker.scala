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
import cats.effect.std.Semaphore
import scala.annotation.tailrec

/**
 * Generates new snowflake ids. Use [[IdWorkerBuilder]] to create workers.
 */
class IdWorker[F[_]: Sync: Logger](
    state: Ref[F, WorkerState],
    epoch: Long,
    dataCenterId: Long,
    workerId: Long,
) {

  import IdWorker._

  /**
   * Calculates the current time in milliseconds.
   *
   * @return The current time in milliseconds.
   */
  protected def currentTimeMillis: F[Long] = Sync[F].delay(System.currentTimeMillis)

  /**
   * Busy-loops until the next millisecond, based on the given timestamp.<br>
   *
   * @param lastTimeStamp The timestamp to base the next millisecond off of.
   * @return The next timestamp after the busy-looping finishes.
   */
  protected def tilNextMillis(lastTimeStamp: Long): F[Long] = currentTimeMillis.iterateWhile(_ <= lastTimeStamp)

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
   * Updates the timestamp and sequence.
   */
  private def updateState(timeStamp: Long, lastTimeStamp: Long, sequence: Long): F[WorkerState] =
    if (timeStamp === lastTimeStamp) {
      val nextSequence = (sequence + 1) & SequenceMask
      val nextTimeStamp =
        if (nextSequence === 0L) tilNextMillis(lastTimeStamp)
        else timeStamp.pure[F]
      nextTimeStamp.map(WorkerState(_, nextSequence))
    } else WorkerState(timeStamp, 0L).pure[F]

  /**
   * Verifies the timestamp isn't going backwards. Raises an exception if so.
   */
  private def verifyTimeStamp(timeStamp: Long, lastTimeStamp: Long) =
    if (timeStamp < lastTimeStamp)
      Logger[F].error(show"Clock is moving backwards. Rejecting requests until $lastTimeStamp.") *>
        Sync[F].raiseError(
          new RuntimeException(
            show"Clock moved backwards. Refusing to generate id for ${lastTimeStamp - timeStamp} milliseconds.",
          ),
        )
    else Sync[F].unit

  /**
   * Generates a new snowflake id.
   *
   * @return A new snowflake id.
   */
  val nextId: F[Snowflake] =
    state.access.flatMap { case (WorkerState(lastTimeStamp, sequence), setState) =>
      for {
        timeStamp <- currentTimeMillis
        _ <- verifyTimeStamp(timeStamp, lastTimeStamp)
        nextState <- updateState(timeStamp, lastTimeStamp, sequence)
        successful <- setState(nextState)
        id <-
          if (successful) nextIdPure(nextState.lastTimeStamp, nextState.sequence).pure[F]
          else nextId
      } yield id
    }
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
