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
package snowflake4s

import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger
import cats.effect.IO
import cats.effect.kernel.Sync
import cats.effect.kernel.Ref
import cats.effect.std.Semaphore
import cats.syntax.all.given
import snowflake4s.arbitrary.given
import snowflake4s.IdWorkerBuilder

private class EasyTimeWorker[F[_]: Sync: Logger](
      val nextMillis: Ref[F, F[Long]],
      lastTimeStamp: Ref[F, Long],
      sequence: Ref[F, Long],
      val epoch: Long,
      dataCenterId: Long,
      workerId: Long,
      semaphore: Semaphore[F],
) extends IdWorker[F](lastTimeStamp, sequence, epoch, dataCenterId, workerId, semaphore):
   override protected val currentTimeMillis: F[Long] = nextMillis.get.flatten

private class WakingIdWorker[F[_]: Sync: Logger](
      nextMillis: Ref[F, F[Long]],
      val slept: Ref[F, Int],
      lastTimeStamp: Ref[F, Long],
      val sequence: Ref[F, Long],
      epoch: Long,
      dataCenterId: Long,
      workerId: Long,
      semaphore: Semaphore[F],
) extends EasyTimeWorker[F](nextMillis, lastTimeStamp, sequence, epoch, dataCenterId, workerId, semaphore):
   override protected def tilNextMillis(lastTimeStamp: Long): F[Long] =
      slept.update(_ + 1) *> super.tilNextMillis(lastTimeStamp)

private class StaticTimeWorker[F[_]: Sync: Logger](
      val time: Ref[F, Long],
      lastTimeStamp: Ref[F, Long],
      val sequence: Ref[F, Long],
      epoch: Long,
      dataCenterId: Long,
      workerId: Long,
      semaphore: Semaphore[F],
) extends IdWorker[F](lastTimeStamp, sequence, epoch, dataCenterId, workerId, semaphore):
   override protected val currentTimeMillis: F[Long] = time.get.map(_ + epoch)

object IdWorkerSuite extends SimpleIOSuite with Checkers:

   private inline val WorkerMask = 0x000000000001f000L
   private inline val DataCenterMask = 0x00000000003e0000L
   private inline val TimeStampMask = 0xffffffffffc00000L

   private given Logger[IO] = NoOpLogger[IO]

   private def createEasyTimeWorker(workerId: Long, dataCenterId: Long) =
      for
         nextMillis <- Ref.of(IO(System.currentTimeMillis))
         lastTimeStamp <- Ref.of(-1L)
         sequence <- Ref.of(0L)
         semaphore <- Semaphore[IO](1)
      yield EasyTimeWorker(
         nextMillis,
         lastTimeStamp,
         sequence,
         IdWorker.TwitterEpoch,
         dataCenterId,
         workerId,
         semaphore,
      )

   private def createWakingIdWorker(workerId: Long, dataCenterId: Long) =
      for
         nextMillis <- Ref.of(IO(System.currentTimeMillis))
         slept <- Ref.of(0)
         lastTimeStamp <- Ref.of(-1L)
         sequence <- Ref.of(0L)
         semaphore <- Semaphore[IO](1)
      yield WakingIdWorker(
         nextMillis,
         slept,
         lastTimeStamp,
         sequence,
         IdWorker.TwitterEpoch,
         dataCenterId,
         workerId,
         semaphore,
      )

   private def createStaticTimeWorker(workerId: Long, dataCenterId: Long) =
      for
         time <- Ref.of(1L)
         lastTimeStamp <- Ref.of(-1L)
         sequence <- Ref.of(0L)
         semaphore <- Semaphore[IO](1)
      yield StaticTimeWorker(
         time,
         lastTimeStamp,
         sequence,
         IdWorker.TwitterEpoch,
         dataCenterId,
         workerId,
         semaphore,
      )

   test("Generate id") {
      forall { (workerId: Long, dataCenterId: Long) =>
         for
            worker <- IdWorkerBuilder
               .default[IO]
               .withWorkerId(workerId)
               .withDataCenterId(dataCenterId)
               .build
            id <- worker.nextId
         yield expect(id > 0L)
      }
   }

   test("Mask worker id") {
      forall { (workerId: Long, dataCenterId: Long) =>
         for
            worker <- IdWorkerBuilder
               .default[IO]
               .withWorkerId(workerId)
               .withDataCenterId(dataCenterId)
               .build
            id <- worker.nextId
         yield expect.same((id & WorkerMask) >> 12, workerId)
      }
   }

   test("Mask datacenter id") {
      forall { (workerId: Long, dataCenterId: Long) =>
         for
            worker <- IdWorkerBuilder
               .default[IO]
               .withWorkerId(workerId)
               .withDataCenterId(dataCenterId)
               .build
            id <- worker.nextId
         yield expect((id & DataCenterMask) >> 17L == dataCenterId)
      }
   }

   test("Mask timestamp") {
      forall { (workerId: Long, dataCenterId: Long) =>
         for
            worker <- createEasyTimeWorker(workerId, dataCenterId)
            currentTimeMillis <- IO(System.currentTimeMillis)
            _ <- worker.nextMillis.set(currentTimeMillis.pure[F])
            id <- worker.nextId
         yield expect((id & TimeStampMask) >> 22L == currentTimeMillis - worker.epoch)
      }
   }

   test("Id always increases") {
      forall { (workerId: Long, dataCenterId: Long) =>
         for
            worker <- IdWorkerBuilder
               .default[IO]
               .withWorkerId(workerId)
               .withDataCenterId(dataCenterId)
               .build
            ids <- (1 to 100).map(_ => worker.nextId).toList.sequence
            (compareIds, _) = ids
               .foldLeft(success -> 0L) { case (accumulator -> previousId, nextId) =>
                  (expect(nextId > previousId) && accumulator) -> nextId
               }
         yield compareIds
      }
   }

   test("Sleep if rollover twice in a millisecond") {
      forall { (workerId: Long, dataCenterId: Long) =>
         for
            worker <- createWakingIdWorker(workerId, dataCenterId)
            iterator = List(2L, 2L, 3L).iterator
            _ <- worker.nextMillis.set(IO(iterator.next))
            _ <- worker.sequence.set(4095)
            _ <- worker.nextId
            _ <- worker.sequence.set(4095)
            _ <- worker.nextId
            slept <- worker.slept.get
         yield expect.same(1, slept)
      }
   }

   test("Ids must be unique") {
      forall { (workerId: Long, dataCenterId: Long) =>
         for
            worker <- createWakingIdWorker(workerId, dataCenterId)
            ids <- (1 to 100).map(_ => worker.nextId).toList.sequence
         yield expect.same(100, ids.distinct.size)
      }
   }

   test("Ids must be unique even when clock moves backwards") {
      forall { (workerId: Long, dataCenterId: Long) =>
         for
            worker <- createStaticTimeWorker(workerId, dataCenterId)
            sequenceMask = -1L ^ (-1L << 12)

            // Reported at https://github.com/twitter/snowflake/issues/6
            // First we generate 2 ids with the same time, so that we get the sequence to 1
            a <- worker.sequence.get.map(it => expect.same(0, it))
            b <- worker.time.get.map(it => expect.same(1, it))
            id1 <- worker.nextId
            c = expect.same(1, id1 >> 22) && expect.same(0, id1 & sequenceMask)

            d <- worker.sequence.get.map(it => expect.same(0, it))
            e <- worker.time.get.map(it => expect.same(1, it))
            id2 <- worker.nextId
            f = expect.same(1, id2 >> 22) && expect.same(1, id2 & sequenceMask)

            // Set time backwards
            _ <- worker.time.set(0)
            g <- worker.sequence.get.map(it => expect.same(1, it))
            h <- worker.nextId.attempt.map(it => expect(it.isLeft))
            i <- worker.sequence.get.map(it => expect.same(1, it))

            _ <- worker.time.set(1)
            id3 <- worker.nextId
            j = expect.same(1, id3 >> 22) && expect.same(2, id3 & sequenceMask)
         yield a && b && c && d && e && f && g && h && i && j
      }
   }
