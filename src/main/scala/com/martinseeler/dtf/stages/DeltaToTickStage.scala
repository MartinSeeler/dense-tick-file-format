package com.martinseeler.dtf.stages

import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import com.martinseeler.dtf.{FactorizedDeltaTick, FactorizedTick, Tick}

class DeltaToTickStage
  extends GraphStage[FlowShape[FactorizedDeltaTick, Tick]] {

    val in = Inlet[FactorizedDeltaTick]("FactorizeDeltaTickStage.in")
    val out = Outlet[Tick]("FactorizeDeltaTickStage.out")

    def shape: FlowShape[FactorizedDeltaTick, Tick] = FlowShape(in, out)

    def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) with OutHandler {

        def onPull(): Unit = if (!hasBeenPulled(in)) tryPull(in)
        setHandler(out, this)

        val firstDeltaInHandler: InHandler = new InHandler {
          def onPush(): Unit = {
            val delta = grab(in)
            println("delta = " + delta)
            val newFactorizedTick: FactorizedTick = Tick(0L, 0, 0).factorize.withDelta(delta)
            emit(out, newFactorizedTick.normalize)
            setHandler(in, withPrevInHandler(newFactorizedTick))
          }
        }

        def withPrevInHandler(initialFactorizedTick: FactorizedTick): InHandler = new InHandler {

          private[this] var prevFactorizedTick = initialFactorizedTick

          def onPush(): Unit = {
            val delta = grab(in)
            val newFactorizedTick: FactorizedTick = prevFactorizedTick.withDelta(delta)
            emit(out, newFactorizedTick.normalize)
            prevFactorizedTick = newFactorizedTick
          }
        }

        setHandler(in, firstDeltaInHandler)
      }
  }
