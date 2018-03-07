package com.carrotgarden.maven.scalor.scalanative

import java.io.File

import com.carrotgarden.maven.scalor.base
import com.carrotgarden.maven.scalor.base.Context.UpdateResult

import scala.scalanative.tools
import scala.scalanative.tools.Mode
import scala.scalanative.linker
import scala.scalanative.optimizer

/**
 * Incremental caching Scala.native linker.
 */
trait Linker {

  self : base.Context with base.Logging =>

  import Linker._
  import Logging._

  lazy val linkerLogger = new LinkerLogger( logger )
  lazy val linkerReporter = new LinkerReporter( logger )
  lazy val optimizerReporter = new OptimizerReporter( logger )

  /**
   * Invoke single linker run.
   */
  def linkerPerform(
    context : Context
  ) : Unit = {
    import context._

    val cacherId = linkerCacherId()
    val linkerCacher = contextValue[ Cacher ]( cacherId ) {
      logger.dbug( s"Creating cacher: ${cacherId}" )
      newCacher()
    }

    val engineId = linkerEngineId( options )
    //    val linkerEngine = contextValue[ Engine ]( engineId ) {
    //      logger.dbug( s"Creating engine: ${engineId}" )
    //      newEngine( options )
    //    }

    //    val linkerLogger = if ( hasLogStats ) linkerTimeLogger else linkerBaseLogger
    //    linkerEngine.link( context, linkerLogger, linkerCacher )
    //    if ( hasLogStats ) {
    //      logger.info( s"Cacher stats: ${linkerCacher.report}" )
    //    }

    import Native._

    val nativeCache = linkerCacher.nativeCache
    val nativeContext = Context(
      params            = params,
      options           = options,
      logger            = linkerLogger,
      linkerReporter    = linker.Reporter.empty, // linkerReporter,
      optimizerReporter = optimizer.Reporter.empty // optimizerReporter
    )

    val nativePipeline = Native( nativeContext, nativeCache )

    nativePipeline.nativeLinkLL

  }

}

object Linker {

  import Utilities._

  /**
   * Linker invocation context.
   */
  case class Context(
    params :     Params,
    options :    Options,
    updateList : Array[ UpdateResult ]
  ) {
    def hasUpdate = updateList.count( _.hasUpdate ) > 0
  }

  case class Params(
    gcType :     String,
    entryClass : String,
    runtime :    File,
    workdir :    File,
    classpath :  Array[ File ]
  )

  /**
   * Linker engine configuration.
   */
  case class Options(
    mode :      String  = "debug",
    linkStubs : Boolean = false
  )

  /**
   * Linker configuration parser.
   */
  object Options {
    import upickle._
    import upickle.default._
    implicit def optionsCodec : ReadWriter[ Options ] = macroRW
    def parse( options : String ) : Options = read[ Options ]( options )
    def unparse( options : Options ) : String = write( options )
  }

  case class Engine() {

  }

  def linkerCacherId() : String = {
    s"scala-native-linker-cacher"
  }

  def linkerEngineId( options : Options ) : String = {
    s"scala-native-linker-engine@${options.toString}"
  }

  def newCacher() : Cacher = Cacher()

  def newEngine( options : Options ) : Engine = Engine()

  def newConfig( context : Context ) : tools.Config = {
    import context._
    tools.Config.empty
    //      .withEntry( ??? )
    //      .withPaths( classpath )
    //      .withWorkdir( workdir )
    //      .withTarget( "" )
    //      .withMode( mode( "debug" ) )
    //      .withLinkStubs( false )
  }

}
