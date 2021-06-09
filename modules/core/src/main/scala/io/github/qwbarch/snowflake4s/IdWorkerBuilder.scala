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

import scala.util.hashing.MurmurHash3
import org.typelevel.log4cats.Logger
import cats.syntax.all._
import cats.effect.kernel.Async
import cats.effect.kernel.Ref
import cats.effect.std.Semaphore

/**
 * A builder for creating instances of [[IdWorker]].
 */
final class IdWorkerBuilder[F[_]: Async: Logger](
    workerId: Long,
    dataCenterId: Long,
    epoch: Long,
    sequence: Long,
) {

  import IdWorker._

  private def copy(
      workerId: Long = this.workerId,
      dataCenterId: Long = this.dataCenterId,
      epoch: Long = this.epoch,
      sequence: Long = this.sequence,
  ): IdWorkerBuilder[F] = new IdWorkerBuilder(workerId, dataCenterId, epoch, sequence)

  override def hashCode: Int = {
    var hash = IdWorkerBuilder.hashSeed
    hash = MurmurHash3.mix(hash, workerId.##)
    hash = MurmurHash3.mix(hash, dataCenterId.##)
    hash = MurmurHash3.mix(hash, epoch.##)
    hash = MurmurHash3.mixLast(hash, sequence.##)
    hash
  }

  override def toString: String = show"IdWorkerBuilder($workerId, $dataCenterId, $epoch, $sequence)"

  /**
   * Sets the worker's id.
   *
   * @param workerId The worker id.
   * @return A new builder with the provided worker id.
   */
  def withWorkerId(workerId: Long): IdWorkerBuilder[F] = copy(workerId = workerId)

  /**
   * Sets the worker's data center id.
   *
   * @param dataCenterId The data center id.
   * @return A new builder with the provided data center id.
   */
  def withDataCenterId(dataCenterId: Long): IdWorkerBuilder[F] = copy(dataCenterId = dataCenterId)

  /**
   * Sets the epoch used for generating ids.
   *
   * @param epoch The epoch.
   * @return A new builder with the provided epoch.
   */
  def withEpoch(epoch: Long): IdWorkerBuilder[F] = copy(epoch = epoch)

  /**
   * Sets the sequence id.
   *
   * @param sequence The sequence.
   * @return A new sequence with the provided sequence.
   */
  def withSequence(sequence: Long): IdWorkerBuilder[F] = copy(sequence = sequence)

  /**
   * Creates a new id worker using the builder's arguments.
   *
   * @return An id worker with the provided builder arguments.
   */
  def build: F[IdWorker[F]] =
    for {
      _ <- Async[F].unit
        .ensure(
          new IllegalArgumentException(show"Worker id can't be greater than $MaxWorkerId or less than 0."),
        )(_ => workerId <= MaxWorkerId && workerId >= 0)
        .ensure(
          new IllegalArgumentException(show"Data center id can't be greater than $MaxDataCenterId or less than 0."),
        )(_ => dataCenterId <= MaxDataCenterId && dataCenterId >= 0)
      _ <- Logger[F].info(
        show"Worker starting. Timestamp left shift $TimeStampLeftShift, " +
          show"data center id bits $DataCenterIdBits, worker id bits $WorkerIdBits, " +
          show"sequence bits $SequenceBits, worker id $workerId.",
      )
      state <- Ref[F].of(WorkerState(lastTimeStamp = -1, sequence = sequence))
    } yield new IdWorker(state, epoch, dataCenterId, workerId)
}

object IdWorkerBuilder {
  private val hashSeed = MurmurHash3.stringHash("IdWorkerBuilder")

  /**
   * The default builder arguments.
   *
   * @return A new builder with the default arguments.
   */
  def default[F[_]: Async: Logger]: IdWorkerBuilder[F] = new IdWorkerBuilder(
    workerId = 0,
    dataCenterId = 0,
    sequence = 0,
    epoch = IdWorker.TwitterEpoch,
  )
}
