package com.martinseeler.dtf

import scodec._
import codecs._
import com.martinseeler.dtf.NonNegativeFactorizedDeltaTick._
import com.martinseeler.dtf.FactorizedDeltaTick._
import com.martinseeler.dtf.FactorizedTick._
import com.martinseeler.dtf.Tick._

object EncodeDemo extends App {

  val tick      = Tick(1420148801108L, 1.20989, 1.21049)
  val otherTick = Tick(1420148801207L, 1.20999, 1.21053)

  val factorizedTick = tick.factorize
  val factorizedOtherTick = otherTick.factorize

  val res0 = utf8.encode(tick.asCSVString)
  println("res0 = " + res0)

  val res1 = tickCodec encode tick
  println("res1 = " + res1)

  val res2 = tickCodecV encode tick
  println("res2 = " + res2)

  val res3 = factorizedTickCodec encode factorizedTick
  println("res3 = " + res3)

  val res4 = factorizedTickCodecV encode factorizedTick
  println("res4 = " + res4)

  val res5 = factorizedDeltaTickCodec encode (factorizedOtherTick deltaTo factorizedTick)
  println("res5 = " + res5)

  val res6 = factorizedDeltaTickCodecV encode (factorizedOtherTick deltaTo factorizedTick)
  println("res6 = " + res6)

  val res7 = nonNegFactorizedDeltaTickCodec encode (factorizedOtherTick deltaTo factorizedTick).nonNegative
  println("res7 = " + res7)

  val res8 = nonNegFactorizedDeltaTickCodecV encode (factorizedOtherTick deltaTo factorizedTick).nonNegative
  println("res8 = " + res8)
}
