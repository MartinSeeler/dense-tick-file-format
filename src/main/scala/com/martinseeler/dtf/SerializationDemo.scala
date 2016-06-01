package com.martinseeler.dtf

import java.io.File
import java.nio.file.StandardOpenOption._

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{FileIO, Framing}
import akka.util.ByteString
import com.martinseeler.dtf.stages.{DeltaToByteStringStage, TickToDeltaStage}

object SerializationDemo extends App {

  implicit val system = ActorSystem("serialization")
  implicit val mat = ActorMaterializer()

  val csvSource = FileIO
    .fromPath(new File("ticks_1MM.csv").toPath)
    .via(Framing.delimiter(ByteString("\n"), 1024))
    .map(_.utf8String.split(','))
    .map(xs => Tick(xs(0).toLong, xs(1).toDouble, xs(2).toDouble))

  val dtffSink = FileIO
    .toPath(new File("ticks_1MM.dtff").toPath, options = Set(CREATE, WRITE, TRUNCATE_EXISTING))

  csvSource
    .via(new TickToDeltaStage())
    .via(new DeltaToByteStringStage())
    .runWith(dtffSink)
    .onComplete(_ => system.terminate())(scala.concurrent.ExecutionContext.global)
}
