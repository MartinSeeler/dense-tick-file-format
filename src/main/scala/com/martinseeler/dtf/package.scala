package com.martinseeler

package object dtf {

  implicit final class TickOps(private val wrappedTick: Tick) extends AnyVal {
    def factorize: FactorizedTick =
      FactorizedTick(
          wrappedTick.time,
          (wrappedTick.bid * 100000).toInt,
          (wrappedTick.ask * 100000).toInt
      )

    def asCSVString: String = s"${wrappedTick.time},${wrappedTick.bid},${wrappedTick.ask}"
  }

  implicit final class FactorizedTickOps(private val wrappedFactorizedTick: FactorizedTick) extends AnyVal {
    def normalize: Tick =
      Tick(
        wrappedFactorizedTick.time,
        wrappedFactorizedTick.bid / 100000.0,
        wrappedFactorizedTick.ask / 100000.0
      )

    def deltaTo(prevFactorizedTick: FactorizedTick): FactorizedDeltaTick =
      FactorizedDeltaTick(
        wrappedFactorizedTick.time - prevFactorizedTick.time,
        wrappedFactorizedTick.bid - prevFactorizedTick.bid,
        wrappedFactorizedTick.ask - prevFactorizedTick.ask
      )

    def withDelta(deltaTick: FactorizedDeltaTick): FactorizedTick = {
      FactorizedTick(
        wrappedFactorizedTick.time + deltaTick.timeDelta,
        wrappedFactorizedTick.bid + deltaTick.bidDelta,
        wrappedFactorizedTick.ask + deltaTick.askDelta
      )
    }
  }

  implicit class FactorizedDeltaTickOps(private val wrappedFactorizedDeltaTick: FactorizedDeltaTick) extends AnyVal {

    def nonNegative: NonNegativeFactorizedDeltaTick = NonNegativeFactorizedDeltaTick(
      wrappedFactorizedDeltaTick.timeDelta,
      wrappedFactorizedDeltaTick.bidDelta < 0,
      wrappedFactorizedDeltaTick.bidDelta.abs,
      wrappedFactorizedDeltaTick.askDelta < 0,
      wrappedFactorizedDeltaTick.askDelta.abs
    )

  }

  implicit class nonNegativeFactorizedDeltaTickOps(private val wrappedNonNegFactorizedDeltaTick: NonNegativeFactorizedDeltaTick) extends AnyVal {

    def withNegatives: FactorizedDeltaTick = FactorizedDeltaTick(
      wrappedNonNegFactorizedDeltaTick.timeDelta,
      wrappedNonNegFactorizedDeltaTick.bidDelta * (if (wrappedNonNegFactorizedDeltaTick.bidDeltaNeg) -1 else 1),
      wrappedNonNegFactorizedDeltaTick.askDelta * (if (wrappedNonNegFactorizedDeltaTick.askDeltaNeg) -1 else 1)
    )


  }
}
