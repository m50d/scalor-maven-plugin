package com.carrotgarden.maven.scalor.scalanative

import java.io.File

import com.carrotgarden.maven.scalor.util.Java8
import com.carrotgarden.maven.scalor.meta.Macro.nameOf

import scala.scalanative.nir
import scala.scalanative.linker
import scala.scalanative.tools
import scala.scalanative.optimizer

import scala.language.implicitConversions
import scala.util.Try

import sbt.io.IO
import sbt.io.Hash
import sbt.io.syntax._

import scala.sys.process.Process
import scala.collection.concurrent.TrieMap

import Utilities._
import Native._
import Logging._
import Java8._

/**
 * https://github.com/scala-native/scala-native/issues/1177
 *
 * https://github.com/scala-native/scala-native/blob/master/sbt-scala-native/src/main/scala/scala/scalanative/sbtplugin/ScalaNativePluginInternal.scala
 */
case class Native(
  context :     Context,
  nativeCache : TrieMap[ String, AnyRef ]
) {

  import context._

  def cached[ T <: AnyRef ]( key : String )( value : => T ) : T = {
    nativeCache.getOrElseUpdate( key, value ).asInstanceOf[ T ]
  }

  lazy val nativeOutput : File = {
    params.runtime
  }

  lazy val nativeClang : File = cached( nameOf( nativeClang ) ) {
    logger.info( s"Detecting LLVM clang" )
    val clang = discover( "clang", clangVersions )
    checkThatClangIsRecentEnough( clang )
    logger.info( s"   clang: ${clang}" )
    clang
  }

  lazy val nativeClangPP : File = cached( nameOf( nativeClangPP ) ) {
    logger.info( s"Detecting LLVM clang++" )
    val clang = discover( "clang++", clangVersions )
    checkThatClangIsRecentEnough( clang )
    logger.info( s"   clang++: ${clang}" )
    clang
  }

  lazy val nativeCrossDir : File = {
    val folder = params.workdir / options.mode
    folder
  }

  lazy val nativeLibDir : File = {
    val folder = nativeMakekDir / "lib"
    folder
  }

  // Working directory for intermediate build files
  lazy val nativeMakekDir : File = cached( nameOf( nativeMakekDir ) + params.workdir + options.mode ) {
    val folder = nativeCrossDir / "native"
    logger.info( s"Creating makedir" )
    IO.delete( folder )
    IO.createDirectory( folder )
    logger.info( s"   makedir: ${folder}" )
    folder
  }

  lazy val nativeLinkStubs : Boolean = {
    options.linkStubs
  }

  lazy val nativeEntry : nir.Global = {
    val mainClass = params.entryClass
    nir.Global.Top( mainClass + "$" )
  }

  lazy val nativeClasspath : Seq[ File ] = {
    params.classpath.toSeq
  }

  // Aggregate config object that's used for tools
  lazy val nativeConfig = {
    val config = tools.Config.empty
      .withEntry( nativeEntry )
      .withPaths( nativeClasspath )
      .withWorkdir( nativeMakekDir )
      .withTarget( nativeTriple )
      .withMode( mode( nativeMode ) )
      .withLinkStubs( nativeLinkStubs )
    config
  }

  // Pass manager for the optimizer
  lazy val nativeOptimizerDriver : tools.OptimizerDriver = {
    val config = nativeConfig
    tools.OptimizerDriver( config )
  }

  // Link NIR using Scala Native linker
  lazy val nativeLinkNIR : tools.LinkerResult = {
    val config = nativeConfig
    val driver = nativeOptimizerDriver
    val result = logger.time( "Linking NIR" ) {
      tools.link( config, driver, linkerReporter )
    }
    if ( result.unresolved.nonEmpty ) {
      result.unresolved.map( _.show ).sorted.foreach { signature =>
        logger.error( s"Failed to resolve: $signature" )
      }
      sys.error( s"Failed to link ${result.unresolved.size} nir files" )
    }
    val classCount = result.defns.count {
      case _ : nir.Defn.Class | _ : nir.Defn.Module | _ : nir.Defn.Trait => true
      case _ => false
    }
    val methodCount = result.defns.count( _.isInstanceOf[ nir.Defn.Define ] )
    logger.info(
      s"   discovered ${classCount} classes and ${methodCount} methods"
    )
    result
  }

  // Optimize NIR produced after linking
  lazy val nativeOptimizeNIR : Seq[ nir.Defn ] = {
    val config = nativeConfig
    val driver = nativeOptimizerDriver
    val result = nativeLinkNIR
    val optimized = logger.time( s"Optimizing NIR" ) {
      tools.optimize( config, driver, result.defns, result.dyns, optimizerReporter )
    }
    logger.info(
      s"   optimized ${optimized.size} entries"
    )
    optimized
  }

  // Generate LLVM IR based on the optimized NIR
  lazy val nativeGenerateLL : Seq[ File ] = {
    val config = nativeConfig
    val workDir = nativeMakekDir
    val optimized = nativeOptimizeNIR
    logger.time( "Generating LLVM LL" ) {
      tools.codegen( config, optimized )
    }
    val moduleList = ( workDir ** "*.ll" ).get
    logger.info( s"   produced ${moduleList.length} modules" )
    moduleList.toSeq
  }

  lazy val nativeCompileOptions : Seq[ String ] = cached( nameOf( nativeCompileOptions ) ) {
    logger.info( s"Detecting LLVM compile options" )
    val includes = {
      val includedir =
        Try( Process( "llvm-config --includedir" ).lines_!.toSeq )
          .getOrElse( Seq.empty )
      ( "/usr/local/include" +: includedir ).map( s => s"-I$s" )
    }
    val options = includes :+ "-Qunused-arguments"
    logger.info( s"   compile options: ${options.mkString( " " )}" )
    options
  }

  lazy val nativeOptimizeOpt : String = {
    mode( nativeMode ) match {
      case tools.Mode.Debug   => "-O0"
      case tools.Mode.Release => "-O2"
    }
  }

  // Compile LLVM IR to native object files
  lazy val nativeCompileLL : Seq[ File ] = {

    val workDir = nativeMakekDir
    val clangpp = nativeClangPP
    val compileOpts = nativeCompileOptions
    val generated = nativeGenerateLL

    val optimizeOpt = nativeOptimizeOpt

    val opts = optimizeOpt +: compileOpts

    val objectList = logger.time( "Compiling LL to native O" ) {
      generated.par
        .map { ll =>
          val source = ll.abs
          val target = source + ".o"
          val command = Seq(
            clangpp.abs, "-c", source, "-o", target
          ) ++ opts
          logger.running( command )
          val result = Process( command, workDir ) ! logger
          if ( result != 0 ) {
            sys.error( s"Failed to invoke ${command}" )
          }
          new File( target ).getAbsoluteFile
        }
        .seq
        .toSeq
    }

    logger.info( s"   produced ${objectList.size} objects" )

    objectList
  }

  lazy val nativeLibJar : File = {
    nativeClasspath
      .map( entry => entry.abs )
      .collectFirst {
        case path if path.contains( "scala-native" ) && path.contains( "nativelib" ) =>
          file( path )
      }
      .getOrElse( sys.error( s"Missing dependency: scala-native:nativelib" ) )
  }

  // Unpack native lib
  lazy val nativeUnpackLib : File = {
    logger.info( s"Unpacking native lib" )
    val workDir = nativeMakekDir
    val libDir = nativeLibDir
    val libFile = nativeLibJar
    val libHashCode = Hash( libFile ).toSeq
    val libHashFile = libDir / "jarhash"
    def hasUnpack =
      libDir.exists &&
        libHashFile.exists &&
        libHashCode == IO.readBytes( libHashFile ).toSeq
    if ( !hasUnpack ) {
      IO.delete( libDir )
      IO.unzip( libFile, libDir )
      IO.write( libHashFile, Hash( libFile ) )
    }
    val sourceList = nativeSourceList( libDir )
    logger.info( s"   produced ${sourceList.size} sources in folder ${libDir}" )
    libDir
  }

  lazy val nativeGC : String = {
    //    Option( System.getenv.get( "SCALANATIVE_GC" ) ).getOrElse( "boehm" )
    params.gcType
  }

  lazy val nativeMode : String = {
    //    Option( System.getenv.get( "SCALANATIVE_MODE" ) ).getOrElse( "debug" )
    options.mode
  }

  // Precompile C/C++ code in native lib
  lazy val nativeCompileLib : File = {

    val workDir = nativeMakekDir // XXX

    val gcType = nativeGC
    val clang = nativeClang
    val clangpp = nativeClangPP
    val compileOpts = nativeOptimizeOpt +: nativeCompileOptions

    val libDir = nativeUnpackLib

    val linkerResult = nativeLinkNIR

    logger.info( s"Compiling native lib" )

    val sourceList = nativeSourceList( libDir )

    // predicate to check if given file path shall be compiled
    // we only include sources of the current gc and exclude
    // all optional dependencies if they are not necessary
    val sep = java.io.File.separator
    val optPath = libDir + sep + "optional"
    val gcPath = libDir + sep + "gc"
    val gcSelPath = gcPath + sep + gcType

    val linkNameList = linkerResult.links.map( _.name )

    def hasInclude( path : String ) = {
      if ( path.contains( optPath ) ) {
        val name = file( path ).getName.split( "\\." ).head
        linkNameList.contains( name )
      } else if ( path.contains( gcPath ) ) {
        path.contains( gcSelPath )
      } else {
        true
      }
    }

    // delete .o files for all excluded source files
    sourceList.foreach { source =>
      if ( !hasInclude( source ) ) {
        val target = file( source + ".o" )
        if ( target.exists ) {
          IO.delete( target )
        }
      }
    }

    // generate .o files for all included source files in parallel
    sourceList.par.foreach { source =>
      val target = source + ".o"
      if ( hasInclude( source ) && !file( target ).exists ) {
        val hasCpp = source.endsWith( ".cpp" )
        val compiler = if ( hasCpp ) clangpp.abs else clang.abs
        val flags = ( if ( hasCpp ) Seq( "-std=c++11" ) else Seq() ) ++ compileOpts
        val command = Seq( compiler ) ++ flags ++ Seq(
          "-c", source, "-o", target
        )
        logger.running( command )
        val result = Process( command, workDir ) ! logger
        if ( result != 0 ) {
          sys.error( s"Failed to compile native library ${command}" )
        }
      }
    }

    logger.info( s"   compiled ${sourceList.size} sources" )

    libDir

  }

  lazy val nativeTriple : String = cached( nameOf( nativeTriple ) ) {
    val workDir = nativeMakekDir
    val clang = nativeClang
    logger.info( s"Probing target triple" )
    val source = workDir / "probe" / "probe.c.file"
    val target = workDir / "probe" / "probe.ll.file"
    val probe = "int probe;"
    IO.write( source, probe )
    val command = Seq(
      clang.abs, "-S", "-xc", "-emit-llvm", "-o", target.abs, source.abs
    )
    logger.running( command )
    val result = Process( command, workDir ) ! logger
    if ( result != 0 ) {
      sys.error( s"Failed to invoke probe: ${command}" )
    }
    val triple = IO.readLines( target ).collectFirst {
      case line if line.contains( "target triple" ) =>
        line.split( "\"" ).apply( 1 )
    }.getOrElse( sys.error( "Failed to parse probe: ${command}" ) )
    logger.info( s"   triple: ${triple}" )
    triple
  }

  lazy val nativeLinkingOptions : Seq[ String ] = cached( nameOf( nativeLinkingOptions ) ) {
    logger.info( s"Detecting LLVM linking options" )
    val libs = {
      val libdir =
        Try( Process( "llvm-config --libdir" ).lines_!.toSeq )
          .getOrElse( Seq.empty )
      ( "/usr/local/lib" +: libdir ).map( s => s"-L$s" )
    }
    val options = libs :+ "-Qunused-arguments"
    logger.info( s"   linking options: ${options.mkString( " " )}" )
    options
  }

  // OS-dependent required runtime libraries
  lazy val nativeRuntimeLinks : Seq[ String ] = cached( nameOf( nativeRuntimeLinks ) ) {
    val target = nativeTriple
    val os = Option( sys.props( "os.name" ) ).getOrElse( "" )
    val arch = target.split( "-" ).head
    val librt = os match {
      case "Linux" => Seq( "rt" )
      case _       => Seq.empty
    }
    val libunwind = os match {
      case "Mac OS X" => Seq.empty
      case _          => Seq( "unwind", "unwind-" + arch )
    }
    librt ++ libunwind
  }

  // Link native object files into the final binary
  lazy val nativeLinkLL : File = {

    val workDir = nativeMakekDir
    val gcType = nativeGC
    val clang = nativeClang
    val clangpp = nativeClangPP
    val compileOpts = nativeCompileOptions
    val linkingOpts = nativeLinkingOptions
    val targetTriple = nativeTriple
    val outputFile = nativeOutput

    val nativeDir = nativeCompileLib
    val linkerResult = nativeLinkNIR
    val moduleList = nativeCompileLL

    val nativeList = ( nativeDir ** "*.o" ).get

    val links = nativeRuntimeLinks ++
      linkerResult.links.map( _.name ) ++ garbageCollector( gcType ).links

    val linkerOpts = links.map( lib => s"-l${lib}" ) ++ linkingOpts

    val tripleOpts = Seq( "-target", targetTriple )

    val outputOpts = Seq( "-o", outputFile.abs )

    val buildOpts = outputOpts ++ linkerOpts ++ tripleOpts

    val objectList = moduleList.map( _.abs ) ++ nativeList.map( _.abs )

    val command = Seq( clangpp.abs ) ++ buildOpts ++ objectList

    logger.time( s"Linking output binary" ) {
      logger.running( command )
      val result = Process( command, workDir ) ! logger
      if ( result != 0 ) {
        sys.error( s"Failed to invoke ${command}" )
      }
    }

    logger.info( s"   output binary: ${outputFile}" )

    outputFile

  }

}

object Native {

  case class Context(
    params :            Linker.Params,
    options :           Linker.Options,
    logger :            LinkerLogger,
    linkerReporter :    linker.Reporter,
    optimizerReporter : optimizer.Reporter
  )

  def nativeSourceList( dir : File ) : Seq[ String ] = {
    val listC = ( dir ** "*.c" ).get
    val listCPP = ( dir ** "*.cpp" ).get
    ( listC ++ listCPP ).map( _.abs )
  }

}
