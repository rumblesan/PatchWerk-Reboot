package com.rumblesan.patchwerk

import play.api.libs.concurrent._
import akka.actor._

import play.api.Play.current

import java.io.File

import scala.util.Random



class PatchLoader extends Actor {

  val patchfolder = new File(
    current.configuration.getString("patchwerk.patchfolder").get
  )

  def getPatch: String = Random.shuffle(patchfolder.list).head

  def receive = {

    case PatchRequest => sender ! PatchLoad(
      List(
        "load",
        getPatch
      )
    )

  }

}

case class PatchLoad(message: List[String])
case class PatchRequest()
