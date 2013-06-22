package com.rumblesan.patchwerk

import com.rumblesan.scalapd.{ PureDataManager, StartPD, KillPd, PDMessage, LogMessage, SendPDMessage }

import play.api.libs.concurrent._
import akka.actor._

import play.api.Play.current
import play.api.Logger

import scala.collection.JavaConversions._

object PatchWerk {

  lazy val patchwerk = Akka.system.actorOf(Props[Patchwerk])

  lazy val logFileName = current.configuration.getString("patchwerk.logfile").get

  def startPD() = {

    val pdExe = current.configuration.getString("patchwerk.puredata").get
    val port = current.configuration.getInt("patchwerk.port").get

    val masterpatch = current.configuration.getString("patchwerk.masterpatch").get

    val masterpatchfolder = current.configuration.getString("patchwerk.masterpatchfolder").get
    val patchfolder = current.configuration.getString("patchwerk.patchfolder").get
    val poemfolder = current.configuration.getString("patchwerk.poemfolder").get
    val extrapaths = current.configuration.getStringList("patchwerk.extrapaths").get.toList

    val paths = masterpatchfolder :: patchfolder :: poemfolder :: extrapaths

    val extras = List.empty[String]

    patchwerk ! StartPD(pdExe, port, masterpatch, paths.toList, extras, Some(patchwerk))
  }

  def stopPD() = {
    patchwerk ! KillPd()
  }

}


class PatchwerkListener(logFileName: String) extends Actor {

  import java.io.{BufferedWriter, FileWriter}

  val fileOut = new BufferedWriter(new FileWriter(logFileName))

  def receive = {

    case LogMessage(output) => {
      fileOut.write(output + "\n")
      fileOut.flush()
    }

  }

}


class Patchwerk extends Actor {

  lazy val listenerProps = Props(new PatchwerkListener(PatchWerk.logFileName))

  lazy val puredata = Akka.system.actorOf(Props(new PureDataManager(listenerProps)))

  lazy val statemanager = Akka.system.actorOf(Props(new StateManager(self)))

  lazy val patchloader = Akka.system.actorOf(Props[PatchLoader])

  lazy val poemloader = Akka.system.actorOf(Props[PoemLoader])

  def receive = {

    case PDMessage(message) => parsePDMessage(message)

    case message: SendPDMessage => {
      Logger.info("Message sent to PD")
      puredata ! message
    }

    case PatchLoad(message) => {
      Logger.info("Patch load sent to PD: " + message.tail.head.toString)
      puredata ! SendPDMessage(message)
    }

    case PoemLoad(message) => {
      Logger.info("Poem load sent to PD: " + message.tail.head.toString)
      puredata ! SendPDMessage(message)
    }

    case StateMessage(message) => {
      Logger.info("Sending state change message to PD")
      puredata ! SendPDMessage(message)
    }

    case start: StartPD => {
      Logger.info("Start message sent to PD")
      puredata ! start
    }

    case kill: KillPd => {
      Logger.info("Kill message sent to PD")
      puredata ! kill
    }

  }

  def parsePDMessage(message: List[String]) {

    Logger.info("Message received from PD")

    message match {
      case "started" :: Nil => {
        Logger.info("PD has started")
        statemanager ! StateChange(1)
      }
      case "patchload" :: Nil => {
        Logger.info("Patch requested")
        patchloader ! PatchRequest()
      }
      case "poemload" :: Nil => {
        Logger.info("Poem requested")
        poemloader ! PoemRequest()
      }
      case other => Logger.info("Unknown message: " + other.toString)
    }

  }

}

