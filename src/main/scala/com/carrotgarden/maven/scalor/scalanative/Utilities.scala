package com.carrotgarden.maven.scalor.scalanative

import java.lang.System.{ lineSeparator => nl }

import scala.language.postfixOps

import scala.sys.process.Process
import scala.sys.process.ProcessLogger

import sbt.io.syntax._

import java.io.File

import scala.scalanative.nir
import scala.scalanative.linker
import scala.scalanative.tools
import scala.scalanative.optimizer

/**
 * https://github.com/scala-native/scala-native/issues/1177
 *
 * https://github.com/scala-native/scala-native/blob/master/sbt-scala-native/src/main/scala/scala/scalanative/sbtplugin/Utilities.scala
 */

object Utilities {

  /**
   * @param dir directory name of the gc
   * @param links dependencies of the gc
   */
  sealed abstract class GarbageCollector(
    val name :  String,
    val links : Seq[ String ] = Nil
  )
  object GarbageCollector {
    object None extends GarbageCollector( "none" )
    object Boehm extends GarbageCollector( "boehm", Seq( "gc" ) )
    object Immix extends GarbageCollector( "immix" )
  }

  class MessageOnlyException( message : String ) extends RuntimeException( message )

  object SilentLogger extends ProcessLogger {
    def out( s : => String ) : Unit = ()
    def err( s : => String ) : Unit = ()
    def buffer[ T ]( f : => T ) : T = f
  }

  /**
   * Discover concrete binary path using command name and
   *  a sequence of potential supported versions.
   */
  def discover(
    binaryName :     String,
    binaryVersions : Seq[ ( String, String ) ]
  ) : File = {
    val docSetup =
      "http://www.scala-native.org/en/latest/user/setup.html"

    val envName =
      if ( binaryName == "clang" ) "CLANG"
      else if ( binaryName == "clang++" ) "CLANGPP"
      else binaryName

    sys.env.get( s"${envName}_PATH" ) match {
      case Some( path ) => file( path )
      case None => {
        val binaryNames = binaryVersions.flatMap {
          case ( major, minor ) =>
            Seq( s"$binaryName$major$minor", s"$binaryName-$major.$minor" )
        } :+ binaryName

        Process( "which" +: binaryNames )
          .lineStream_!( SilentLogger )
          .map( file( _ ) )
          .headOption
          .getOrElse {
            throw new MessageOnlyException(
              s"no ${binaryNames.mkString( ", " )} found in $$PATH. Install clang ($docSetup)"
            )
          }
      }
    }
  }

  /** Versions of clang which are known to work with Scala Native. */
  val clangVersions =
    Seq( ( "5", "0" ), ( "4", "0" ), ( "3", "9" ), ( "3", "8" ), ( "3", "7" ) )

  /**
   * Tests whether the clang compiler is recent enough.
   * <p/>
   * This is determined through looking up a built-in #define which is
   * more reliable than testing for a specific version.
   * <p/>
   * It might be better to use feature checking macros:
   * http://clang.llvm.org/docs/LanguageExtensions.html#feature-checking-macros
   */
  def checkThatClangIsRecentEnough( pathToClangBinary : File ) : Unit = {
    def maybeFile( f : File ) = f match {
      case file if file.exists => Some( file.abs )
      case none                => None
    }

    def definesBuiltIn(
      pathToClangBinary : Option[ String ]
    ) : Option[ Seq[ String ] ] = {
      def commandLineToListBuiltInDefines( clang : String ) =
        Process( Seq( "echo", "" ) ) #| Process( Seq( clang, "-dM", "-E", "-" ) )
      def splitIntoLines( s : String ) : Array[ String ] =
        s.split( f"%n" )
      def removeLeadingDefine( s : String ) : String =
        s.substring( s.indexOf( ' ' ) + 1 )

      for {
        clang <- pathToClangBinary
        output = commandLineToListBuiltInDefines( clang ).!!
        lines = splitIntoLines( output )
      } yield lines map removeLeadingDefine
    }

    val clang = maybeFile( pathToClangBinary )
    val defines : Seq[ String ] = definesBuiltIn( clang ).to[ Seq ].flatten
    val clangIsRecentEnough =
      defines.contains( "__DECIMAL_DIG__ __LDBL_DECIMAL_DIG__" )

    if ( !clangIsRecentEnough ) {
      throw new MessageOnlyException(
        s"No recent installation of clang found " +
          s"at $pathToClangBinary.\nSee http://scala-native.readthedocs.io" +
          s"/en/latest/user/setup.html for details."
      )
    }
  }

  def mode( mode : String ) = mode match {
    case "debug"   => tools.Mode.Debug
    case "release" => tools.Mode.Release
    case value =>
      throw new MessageOnlyException(
        "nativeMode can be either \"debug\" or \"release\", not: " + value
      )
  }

  def garbageCollector( gcType : String ) = gcType match {
    case "none"  => GarbageCollector.None
    case "boehm" => GarbageCollector.Boehm
    case "immix" => GarbageCollector.Immix
    case value =>
      throw new MessageOnlyException(
        "nativeGC can be either \"none\", \"boehm\" or \"immix\", not: " + value
      )
  }

  implicit class RichFile( file : File ) {
    def abs : String = file.getAbsolutePath
  }

  //  implicit class RichLogger( logger : Logger ) {
  //    def time[ T ]( msg : String )( f : => T ) : T = {
  //      import java.lang.System.nanoTime
  //      val start = nanoTime()
  //      val res = f
  //      val end = nanoTime()
  //      logger.info( s"$msg (${( end - start ) / 1000000} ms)" )
  //      res
  //    }
  //
  //    def running( command : Seq[ String ] ) : Unit =
  //      logger.debug( "running" + nl + command.mkString( nl + "\t" ) )
  //  }

}
