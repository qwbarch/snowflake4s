package snowflake4s.arbitrary

import org.scalacheck.Arbitrary
import snowflake4s.generator.workerDataCenterIdGen

given Arbitrary[Long] = Arbitrary(workerDataCenterIdGen)
