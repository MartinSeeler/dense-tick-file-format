package com.martinseeler.dtf.stages

import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import com.martinseeler.dtf.{FactorizedDeltaTick, FactorizedTick, Tick}

class TickToDeltaStage
    extends GraphStage[FlowShape[Tick, FactorizedDeltaTick]] {

  val in = Inlet[Tick]("FactorizeDeltaTickStage.in")
  val out = Outlet[FactorizedDeltaTick]("FactorizeDeltaTickStage.out")

  def shape: FlowShape[Tick, FactorizedDeltaTick] = FlowShape(in, out)

  def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with OutHandler {

      def onPull(): Unit = if (!hasBeenPulled(in)) tryPull(in)
      setHandler(out, this)

      val firstTickInHandler: InHandler = new InHandler {
        def onPush(): Unit = {
          val tick = grab(in)
          val factorizedTick: FactorizedTick = tick.factorize
          emit(out, FactorizedDeltaTick(factorizedTick.time, factorizedTick.bid, factorizedTick.ask))
          setHandler(in, withPrevInHandler(tick))
        }
      }

      def withPrevInHandler(initialTick: Tick): InHandler = new InHandler {

        private[this] var prevTick = initialTick

        def onPush(): Unit = {
          val tick = grab(in)
          emit(out, tick.factorize.deltaTo(prevTick.factorize))
          prevTick = tick
          setHandler(in, withPrevInHandler(tick))
        }
      }

      setHandler(in, firstTickInHandler)
    }
}
