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

import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger
import cats.effect.IO
import cats.effect.kernel.Ref
import cats.syntax.all._
import io.github.qwbarch.snowflake4s.arbitrary._
import cats.effect.kernel.Async

private class EasyTimeWorker[F[_]: Async: Logger](
    val nextMillis: Ref[F, F[Long]],
    state: Ref[F, WorkerState],
    val epoch: Long,
    dataCenterId: Long,
    workerId: Long,
) extends IdWorker[F](state, epoch, dataCenterId, workerId) {

  override protected def currentTimeMillis: F[Long] = nextMillis.get.flatten
}

private class WakingIdWorker[F[_]: Async: Logger](
    nextMillis: Ref[F, F[Long]],
    val slept: Ref[F, Int],
    val state: Ref[F, WorkerState],
    epoch: Long,
    dataCenterId: Long,
    workerId: Long,
) extends EasyTimeWorker[F](nextMillis, state, epoch, dataCenterId, workerId) {

  override protected def tilNextMillis(lastTimeStamp: Long): F[Long] =
    slept.update(_ + 1) *> super.tilNextMillis(lastTimeStamp)
}

private class StaticTimeWorker[F[_]: Async: Logger](
    val time: Ref[F, Long],
    val state: Ref[F, WorkerState],
    epoch: Long,
    dataCenterId: Long,
    workerId: Long,
) extends IdWorker[F](state, epoch, dataCenterId, workerId) {

  override protected def currentTimeMillis: F[Long] = time.get.map(_ + epoch)
}

object IdWorkerSuite extends SimpleIOSuite with Checkers {

  private final val WorkerMask = 0x000000000001f000L
  private final val DataCenterMask = 0x00000000003e0000L
  private final val TimeStampMask = 0xffffffffffc00000L

  private implicit val logger: Logger[IO] = NoOpLogger[IO]

  private def createEasyTimeWorker(workerId: Long, dataCenterId: Long) =
    for {
      state <- Ref.of(WorkerState(lastTimeStamp = -1, sequence = 0L))
      nextMillis <- Ref.of(IO(System.currentTimeMillis))
    } yield new EasyTimeWorker(
      nextMillis,
      state,
      IdWorker.TwitterEpoch,
      dataCenterId,
      workerId,
    )

  private def createWakingIdWorker(workerId: Long, dataCenterId: Long) =
    for {
      nextMillis <- Ref.of(IO(System.currentTimeMillis))
      state <- Ref.of(WorkerState(lastTimeStamp = -1, sequence = 0L))
      slept <- Ref.of(0)
    } yield new WakingIdWorker(
      nextMillis,
      slept,
      state,
      IdWorker.TwitterEpoch,
      dataCenterId,
      workerId,
    )

  private def createStaticTimeWorker(workerId: Long, dataCenterId: Long) =
    for {
      state <- Ref[F].of(WorkerState(lastTimeStamp = -1, sequence = 0L))
      time <- Ref.of(1L)
    } yield new StaticTimeWorker(
      time,
      state,
      IdWorker.TwitterEpoch,
      dataCenterId,
      workerId,
    )

  test("Generate id") {
    forall { (workerId: Long, dataCenterId: Long) =>
      for {
        worker <- IdWorkerBuilder
          .default[IO]
          .withWorkerId(workerId)
          .withDataCenterId(dataCenterId)
          .build
        id <- worker.nextId
      } yield expect(id.value > 0L)
    }
  }

  test("Mask worker id") {
    forall { (workerId: Long, dataCenterId: Long) =>
      for {
        worker <- IdWorkerBuilder
          .default[IO]
          .withWorkerId(workerId)
          .withDataCenterId(dataCenterId)
          .build
        id <- worker.nextId
      } yield expect.same((id.value & WorkerMask) >> 12, workerId)
    }
  }

  test("Mask datacenter id") {
    forall { (workerId: Long, dataCenterId: Long) =>
      for {
        worker <- IdWorkerBuilder
          .default[IO]
          .withWorkerId(workerId)
          .withDataCenterId(dataCenterId)
          .build
        id <- worker.nextId
      } yield expect((id.value & DataCenterMask) >> 17L == dataCenterId)
    }
  }

  test("Mask timestamp") {
    forall { (workerId: Long, dataCenterId: Long) =>
      for {
        worker <- createEasyTimeWorker(workerId, dataCenterId)
        timeStamp <- IO(System.currentTimeMillis)
        _ <- worker.nextMillis.set(IO.pure(timeStamp))
        id <- worker.nextId
      } yield expect((id.value & TimeStampMask) >> 22L == timeStamp - worker.epoch)
    }
  }

  test("Id always increases") {
    forall { (workerId: Long, dataCenterId: Long) =>
      for {
        worker <- IdWorkerBuilder
          .default[IO]
          .withWorkerId(workerId)
          .withDataCenterId(dataCenterId)
          .build
        ids <- (1 to 100).map(_ => worker.nextId).toList.sequence
        (compareIds, _) = ids
          .foldLeft(success -> 0L) { case ((accumulator, previousId), nextId) =>
            ((expect(nextId.value > previousId) && accumulator), nextId.value)
          }
      } yield compareIds
    }
  }

  test("Sleep if rollover twice in a millisecond") {
    forall { (workerId: Long, dataCenterId: Long) =>
      for {
        worker <- createWakingIdWorker(workerId, dataCenterId)
        iterator = List(2L, 2L, 3L).iterator
        _ <- worker.nextMillis.set(IO(iterator.next()))
        _ <- worker.state.update(_.copy(sequence = 4095))
        _ <- worker.nextId
        _ <- worker.state.update(_.copy(sequence = 4095))
        _ <- worker.nextId
        slept <- worker.slept.get
      } yield expect.same(1, slept)
    }
  }

  test("Ids must be unique generating sequentially") {
    forall { (workerId: Long, dataCenterId: Long) =>
      for {
        worker <- createWakingIdWorker(workerId, dataCenterId)
        ids <- (1 to 100).map(_ => worker.nextId).toList.sequence
      } yield expect.same(100, ids.distinct.size)
    }
  }

  test("Ids must be unique generating in parallel") {
    forall { (workerId: Long, dataCenterId: Long) =>
      for {
        worker <- IdWorkerBuilder
          .default[IO]
          .withWorkerId(workerId)
          .withDataCenterId(dataCenterId)
          .build
        ids <- (0 to 20000).toList.map(_ => worker.nextId).parSequence
        distinct = ids.distinct
      } yield expect.same(ids.length, distinct.length)
    }
  }

  test("Ids must be unique even when clock moves backwards") {
    forall { (workerId: Long, dataCenterId: Long) =>
      for {
        worker <- createStaticTimeWorker(workerId, dataCenterId)
        sequenceMask = -1L ^ (-1L << 12)

        // Reported at https://github.com/twitter/snowflake/issues/6
        // First we generate 2 ids with the same time, so that we get the sequence to 1
        a <- worker.state.get.map(it => expect.same(0, it.sequence))
        b <- worker.time.get.map(it => expect.same(1, it))
        id1 <- worker.nextId
        c = expect.same(1, id1.value >> 22) && expect.same(0, id1.value & sequenceMask)

        d <- worker.state.get.map(it => expect.same(0, it.sequence))
        e <- worker.time.get.map(it => expect.same(1, it))
        id2 <- worker.nextId
        f = expect.same(1, id2.value >> 22) && expect.same(1, id2.value & sequenceMask)

        // Set time backwards
        _ <- worker.time.set(0L)
        g <- worker.state.get.map(it => expect.same(1, it.sequence))
        h <- worker.nextId.attempt.map(it => expect(it.isLeft))
        i <- worker.state.get.map(it => expect.same(1, it.sequence))

        _ <- worker.time.set(1L)
        id3 <- worker.nextId
        j = expect.same(1, id3.value >> 22) && expect.same(2, id3.value & sequenceMask)
      } yield a && b && c && d && e && f && g && h && i && j
    }
  }

  test("Extract timestamp from id") {
    forall { (workerId: Long, dataCenterId: Long) =>
      for {
        worker <- IdWorkerBuilder
          .default[IO]
          .withWorkerId(workerId)
          .withDataCenterId(dataCenterId)
          .build
        timeStamp <- IO(System.currentTimeMillis)
        id = worker.nextIdPure(timeStamp, 0)
        timeStamp2 = worker.getTimeStamp(id)
      } yield expect.same(timeStamp, timeStamp2)
    }
  }
}
