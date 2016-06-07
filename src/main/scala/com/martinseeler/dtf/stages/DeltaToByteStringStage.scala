package com.martinseeler.dtf.stages

import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.util.ByteString
import com.martinseeler.dtf.NonNegativeFactorizedDeltaTick.nonNegFactorizedDeltaTickCodecV
import com.martinseeler.dtf.{FactorizedDeltaTick, NonNegativeFactorizedDeltaTick}
import scodec.Attempt.{Failure, Successful}

class DeltaToByteStringStage extends GraphStage[FlowShape[FactorizedDeltaTick, ByteString]] {

  val in = Inlet[FactorizedDeltaTick]("DeltaToByteStringStage.in")
  val out = Outlet[ByteString]("DeltaToByteStringStage.out")

  def shape: FlowShape[FactorizedDeltaTick, ByteString] = FlowShape(in, out)

  def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with InHandler with OutHandler {

      def onPull(): Unit = if (!hasBeenPulled(in)) tryPull(in)
      setHandler(out, this)


      def onPush(): Unit = {
        val delta = grab(in)
        nonNegFactorizedDeltaTickCodecV.encode(delta.nonNegative) match {
          case Successful(x) => emit(out, ByteString(x.toByteBuffer))
          case Failure(err) => fail(out, new Exception(err.messageWithContext))
        }
      }
      setHandler(in, this)
    }

}
