package snowflake4s.generator

import org.scalacheck.Gen
import snowflake4s.IdWorker

val workerDataCenterIdGen: Gen[Long] = Gen.choose(0L, IdWorker.MaxWorkerId)
