package com.martinseeler.dtf.stages

import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.util.ByteString
import com.martinseeler.dtf.{FactorizedDeltaTick, NonNegativeFactorizedDeltaTick}
import scodec.Attempt.{Failure, Successful}
import scodec.DecodeResult
import scodec.bits.BitVector

import scala.annotation.tailrec

class ByteStringToDeltaStage extends GraphStage[FlowShape[ByteString, FactorizedDeltaTick]] {

  val in = Inlet[ByteString]("ByteStringToDeltaStage.in")
  val out = Outlet[FactorizedDeltaTick]("ByteStringToDeltaStage.out")

  def shape: FlowShape[ByteString, FactorizedDeltaTick] = FlowShape(in, out)

  def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with OutHandler {

      def onPull(): Unit = if (!hasBeenPulled(in)) tryPull(in)
      setHandler(out, this)

      val inHandler = new InHandler {

        def decodeAllFromBits(bits: BitVector): (Vector[NonNegativeFactorizedDeltaTick], BitVector) = {

          @tailrec
          def compute(results: Vector[NonNegativeFactorizedDeltaTick], remainingBits: BitVector): (Vector[NonNegativeFactorizedDeltaTick], BitVector) = {
            NonNegativeFactorizedDeltaTick.nonNegFactorizedDeltaTickCodecV.decode(remainingBits) match {
              case Successful(DecodeResult(value, BitVector.empty)) =>
                (results :+ value, BitVector.empty)
              case Successful(DecodeResult(value, remainder)) if remainder.sizeGreaterThan(25) =>
                compute(results :+ value, remainder)
              case Successful(DecodeResult(value, remainder)) =>
                (results :+ value, remainder)
              case Failure(e) =>
                println("e = " + e)
                (results, BitVector.empty)
            }
          }

          compute(Vector.empty, bits)
        }

        private[this] var remainingBits = BitVector.empty

        def onPush(): Unit = {
          val bits = BitVector.view(grab(in).asByteBuffer)
          val (results, rest) = decodeAllFromBits(remainingBits ++ bits)
          emitMultiple(out, results.map(_.withNegatives))
          remainingBits = rest
        }
      }

      setHandler(in, inHandler)
    }

}
