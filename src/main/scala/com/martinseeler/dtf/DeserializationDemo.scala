package com.martinseeler.dtf

import java.io.File

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{FileIO, Source}
import com.martinseeler.dtf.stages.{ByteStringToDeltaStage, DeltaToTickStage}

object DeserializationDemo extends App {

  implicit val system = ActorSystem("deserialization")
  implicit val mat = ActorMaterializer()

  val dtffSource = FileIO.fromPath(new File("ticks_1MM.dtff").toPath)

  dtffSource
    .via(new ByteStringToDeltaStage())
    .via(new DeltaToTickStage())
    .runForeach(println)
    .onComplete(_ => system.terminate())(
        scala.concurrent.ExecutionContext.global)
}
