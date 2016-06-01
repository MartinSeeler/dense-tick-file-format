package com.martinseeler.dtf

import scodec._
import scodec.bits._
import codecs._

/**
  * Represents a forex price at a given time.
  *
  * @param time The time this price was valid.
  * @param bid The price to sell for.
  * @param ask The price to buy for.
  */
case class Tick(time: Long, bid: Double, ask: Double)
object Tick {
  val tickCodec: Codec[Tick] = (long(64) :: double :: double).as[Tick]
  val tickCodecV: Codec[Tick] = (vlong :: double :: double).as[Tick]
}

case class FactorizedTick(time: Long, bid: Int, ask: Int)
object FactorizedTick {

  val factorizedTickCodec: Codec[FactorizedTick] = (long(64) :: int32 :: int32).as[FactorizedTick]
  val factorizedTickCodecV: Codec[FactorizedTick] = (vlong :: vint :: vint).as[FactorizedTick]
}

case class FactorizedDeltaTick(timeDelta: Long, bidDelta: Int, askDelta: Int)
object FactorizedDeltaTick {

  val factorizedDeltaTickCodec: Codec[FactorizedDeltaTick] = (long(64) :: int32 :: int32).as[FactorizedDeltaTick]
  val factorizedDeltaTickCodecV: Codec[FactorizedDeltaTick] = (vlong :: vint :: vint).as[FactorizedDeltaTick]
}

case class NonNegativeFactorizedDeltaTick(timeDelta: Long, bidDeltaNeg: Boolean, bidDelta: Int, askDeltaNeg: Boolean, askDelta: Int)
object NonNegativeFactorizedDeltaTick {

  val nonNegFactorizedDeltaTickCodec: Codec[NonNegativeFactorizedDeltaTick] = (long(64) :: bool :: int32 :: bool :: int32).as[NonNegativeFactorizedDeltaTick]
  val nonNegFactorizedDeltaTickCodecV: Codec[NonNegativeFactorizedDeltaTick] = (vlong :: bool :: vint :: bool :: vint).as[NonNegativeFactorizedDeltaTick]
}
