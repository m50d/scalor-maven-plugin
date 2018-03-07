package com.carrotgarden.maven.scalor.zinc

import java.io.File
import java.net.URLClassLoader
import java.util.Optional

import com.carrotgarden.maven.scalor.base
import com.carrotgarden.maven.scalor.meta
import com.carrotgarden.maven.scalor.util.Folder._

import sbt.internal.inc.AnalyzingCompiler
import sbt.internal.inc.FileAnalysisStore
import sbt.internal.inc.IncrementalCompilerImpl
import sbt.internal.inc.Locate
import sbt.internal.inc.ScalaInstance
import sbt.util.InterfaceUtil
import sbt.util.Level
import xsbti.compile.AnalysisContents
import xsbti.compile.AnalysisStore
import xsbti.compile.ClasspathOptionsUtil
import xsbti.compile.CompileAnalysis
import xsbti.compile.CompileOrder
import xsbti.compile.CompilerCache
import xsbti.compile.DefinesClass
import xsbti.compile.IncOptions
import xsbti.compile.MiniSetup
import xsbti.compile.PerClasspathEntryLookup
import xsbti.compile.PreviousResult
import xsbti.compile.ZincCompilerUtil
import com.carrotgarden.maven.scalor.util.Error.Throw
import com.carrotgarden.maven.scalor.util.Folder

/**
 * Compiler for scope=macro.
 */
trait CompilerMacro extends Compiler
  with base.BuildMacro
  with ParamsMacro {

  self : Resolve with base.Logging =>

  override def zincBuildCache = zincCacheMacro

}

/**
 * Compiler for scope=main.
 */
trait CompilerMain extends Compiler
  with base.BuildMain
  with ParamsMain {

  self : Resolve with base.Logging =>

  override def zincBuildCache = zincCacheMain

}

/**
 * Compiler for scope=test.
 */
trait CompilerTest extends Compiler
  with base.BuildTest
  with ParamsTest {

  self : Resolve with base.Logging =>

  override def zincBuildCache = zincCacheTest

}

/**
 * Shared compiler interface.
 */
trait Compiler extends AnyRef
  with base.Build
  with base.BuildAnyRegex
  with base.Params
  with base.ParamsDefine
  with Params {

  self : Resolve with base.Logging =>

  import Compiler._

  /**
   *
   * Incremental compiler state for a scope.
   */
  def zincBuildCache : File

  /**
   * Compilation scope input source files.
   */
  def zincBuildSources : Array[ File ] = {
    buildSourceList( buildSourceFolders )
  }

  /**
   * Compilation scope classes output directory.
   */
  def zincBuildTarget : File = {
    buildTargetFolder
  }

  /**
   * Configured project build dependencies.
   */
  def zincBuildClassPath : Array[ File ] = {
    buildDependencyFolders ++ projectClassPath( buildDependencyScopes )
  }

  /**
   * Zinc logger level.
   */
  def zincLoggerLevel : Level.Value = {
    Level( zincLogActiveLevel ).getOrElse {
      val param = meta.Macro.nameOf( zincLogActiveLevel )
      val value = zincLogActiveLevel
      val default = Level.Debug
      logger.fail( s"Invalid ${param}=${value}, using ${default}" )
      default
    }
  }

  /**
   * Incremental compiler file analysis store.
   */
  def zincStateStore( cacheFile : File ) : AnalysisStore = {
    storeType( zincStateStoreType ) match {
      case Store.Text =>
        FileAnalysisStore.text( cacheFile )
      case Store.Binary =>
        FileAnalysisStore.binary( cacheFile )
      case _ =>
        val param = meta.Macro.nameOf( zincStateStoreType )
        val value = zincStateStoreType
        logger.fail( s"Unknown store type ${param}=${value}, using 'binary'." )
        FileAnalysisStore.binary( cacheFile )
    }
  }

  /**
   * Discover optional Java tool chain.
   */
  //  def zincToolchainOption() : Option[ File ] = {
  //    if ( zincToolchainEnable ) {
  //      val toolchain = toolchainManager.getToolchainFromBuildContext( zincToolchainType, session )
  //      if ( toolchain == null ) {
  //        val param = meta.Macro.nameOf( zincToolchainType )
  //        Throw( s"Toolchain failure: missing ${param}=${zincToolchainType}" )
  //      }
  //      val toolPath = toolchain.findTool( zincToolchainTool )
  //      if ( toolPath == null ) {
  //        val param = meta.Macro.nameOf( zincToolchainTool )
  //        Throw( s"Toolchain failure: missing ${param}=${zincToolchainTool}" )
  //      }
  //      val toolHome = toolPath match {
  //        case zincToolchainRegex.r( path ) => path
  //        case _ =>
  //          val param = meta.Macro.nameOf( zincToolchainRegex )
  //          Throw( s"Toolchain failure: ${toolPath} does not match ${param}=${zincToolchainRegex}" )
  //      }
  //      Some( new File( toolHome ).getCanonicalFile )
  //    } else {
  //      None
  //    }
  //  }

  /**
   * Provide compiler context.
   */
  case class Context() {

    // Optional tool chain.
    //    val optionJavaHome = zincToolchainOption
    //    if ( optionJavaHome.isDefined ) {
    //      logger.info( s"Using toolchain: ${optionJavaHome.get}" )
    //    }

    // Assemble required build context.
    val buildSources : Array[ File ] =
      zincBuildSources.map( _.getCanonicalFile )
    val buildClassPath : Array[ File ] =
      zincBuildClassPath.map( _.getCanonicalFile )
    val buildCacheFile : File =
      zincBuildCache.getCanonicalFile
    val buildOutputFolder : File =
      zincBuildTarget.getCanonicalFile

    // Ensure output locations.
    ensureParent( buildCacheFile )
    ensureFolder( buildOutputFolder )

    // Provide compiler installation.
    val compilerInstall =
      resolveCustomInstall()
    val bridgeClassPath : Array[ File ] =
      compilerInstall.bridgeList.map( Module.fileFrom( _ ) ).toArray
    val compilerClassPath : Array[ File ] =
      compilerInstall.zincJars.map( Module.fileFrom( _ ) ).toArray
    val compilerPluginList : Array[ File ] =
      compilerInstall.pluginDefineList.map( Module.fileFrom( _ ) ).toArray

    // Provide compiler class loader.
    val compilerLoader : ClassLoader = {
      val entryList = compilerClassPath.map( _.toURI.toURL )
      new URLClassLoader( entryList )
    }

    // Provide compiler options.
    val optionsConfig = Settings.extract(
      compilerInstall.version, parseOptionsScala, logger.fail
    )

    // Provide user reporting.
    if ( zincLogSourcesList ) {
      loggerReportFileList( "Sources list:", buildSources )
    }
    if ( zincLogProjectClassPath ) {
      loggerReportFileList( "Build class path:", buildClassPath )
    }
    if ( zincLogBridgeClassPath ) {
      loggerReportFileList( "Bridge class path:", bridgeClassPath )
    }
    if ( zincLogCompilerClassPath ) {
      loggerReportFileList( "Compiler class path:", compilerClassPath )
    }
    if ( zincLogCompilerPluginList ) {
      loggerReportFileList( "Compiler plugin list:", compilerPluginList )
    }
    if ( zincLogCompilerOptions ) {
      val reportFile = zincCompilerOptionsReport
      val reportText = optionsConfig.reportFun()
      ensureParent( reportFile )
      persistString( reportFile, reportText )
      logger.info( s"Compiler options report: ${reportFile}" )
    }

    // Verify dependency version consistency.
    if ( zincVerifyVersion ) {
      Version.assertVersion( compilerInstall )
    }

    // Assemble compilation options.
    val pluginOptions = compilerPluginList.flatMap( pluginStanza( _ ) )
    val scalacOptions = optionsConfig.standard ++ pluginOptions
    val javacOptions = parseOptionsJava
    val compileOrder = CompileOrder.valueOf( optionsConfig.compileOrder )
    val maxErrors = optionsConfig.maxErrors

    // Provide Zinc ScalaC instance.
    val scalaInstance = instanceFrom( compilerLoader, compilerInstall )
    val bridgeJar = Module.fileFrom( compilerInstall.bridge )

    // Provide static compiler-bridge jar.
    val bridgeProvider = ZincCompilerUtil.constantBridgeProvider( scalaInstance, bridgeJar )

    // Configured ScalaC instance.
    val scalaCompiler = new AnalyzingCompiler(
      scalaInstance    = scalaInstance,
      provider         = bridgeProvider,
      classpathOptions = ClasspathOptionsUtil.auto,
      onArgsHandler = _ => (),
      classLoaderCache = None // FIXME provide loader cache
    )

    // Compilation status logger.
    val cologger = Logging.Logger( logger, zincLoggerLevel )

    // Compilation problem reporter.
    val reporter = Logging.Reporter( maxErrors, cologger )

    // Compilation progress printer.
    val progress = Logging.Progress( logger, zincLogProgressUnit, zincLogProgressRate )

  }

  /**
   * Setup and invoke Zinc incremental compiler.
   */
  def zincPerformCompile() : Unit = {
    val context = Context(); import context._

    // Use zinc incremental compiler.
    val incremental = new IncrementalCompilerImpl

    val compilers = incremental.compilers(
      instance  = scalaInstance,
      cpOptions = ClasspathOptionsUtil.boot,
      javaHome  = None, // optionJavaHome,
      scalac    = scalaCompiler
    )

    // FIXME review
    val lookup = new PerClasspathEntryLookup {
      override def analysis( classpathEntry : File ) : Optional[ CompileAnalysis ] = Optional.empty[ CompileAnalysis ]
      override def definesClass( classpathEntry : File ) : DefinesClass = Locate.definesClass( classpathEntry )
    }

    // Incremental compiler setup.
    val setup = incremental.setup(
      lookup         = lookup,
      skip           = false,
      cacheFile      = buildCacheFile,
      cache          = CompilerCache.fresh,
      incOptions     = IncOptions.of(),
      reporter       = reporter,
      optionProgress = Some( progress ),
      extra          = Array.empty
    )

    // Extract past state.
    val storePast = zincStateStore( buildCacheFile )
    val storeNext = AnalysisStore.getCachedStore( storePast )

    //    val contentPast = storePast.get()
    //    val resultPast = PreviousResult
    //      .of( contentPast.map( _.getAnalysis ), contentPast.map( _.getMiniSetup ) )

    // Iterative inputs.
    val inputsPast = incremental.inputs(
      classpath             = buildClassPath,
      sources               = buildSources,
      classesDirectory      = buildOutputFolder,
      scalacOptions         = scalacOptions,
      javacOptions          = javacOptions,
      maxErrors             = maxErrors,
      sourcePositionMappers = Array.empty,
      order                 = compileOrder,
      compilers             = compilers,
      setup                 = setup,
      pr                    = incremental.emptyPreviousResult
    //      pr                    = resultPast
    )

    // Iterative inputs.
    val inputsNext = {
      InterfaceUtil.toOption( storeNext.get() ) match {
        case Some( contentPast ) =>
          val analysisPast = contentPast.getAnalysis
          val setupPast = contentPast.getMiniSetup
          val resultPast = PreviousResult.of(
            Optional.of[ CompileAnalysis ]( analysisPast ),
            Optional.of[ MiniSetup ]( setupPast )
          )
          inputsPast.withPreviousResult( resultPast )
        case _ =>
          inputsPast
      }
    }

    logger.info( s"Invoking Zinc compiler: ${compilerInstall.identity}" )

    // Run compiler invocation.
    val resultNext = incremental.compile( inputsNext, cologger )

    // Persist next state.
    val contentNext = AnalysisContents.create( resultNext.analysis, resultNext.setup )
    storeNext.set( contentNext )

  }

  /**
   * Setup and invoke Zinc document compiler.
   */
  def zincPerformDocument( outputDirectory : File, options : Seq[ String ] ) : Unit = {
    val context = Context(); import context._

    logger.info( s"Invoking Zinc compiler: ${compilerInstall.identity}" )

    // Generate scaladoc.
    scalaCompiler.doc(
      sources         = buildSources,
      classpath       = buildClassPath,
      outputDirectory = outputDirectory,
      options         = options,
      log             = cologger,
      reporter        = reporter
    )
  }

}

object Compiler {

  import Module._
  import com.carrotgarden.maven.scalor.util.Folder._

  /**
   * Scala compiler argument: plugin stanza: activate plugin by jar path.
   */
  def pluginStanza( file : File ) : Array[ String ] = {
    Array[ String ]( "-Xplugin", file.getCanonicalPath )
  }

  def pluginStanza( module : Module ) : Array[ String ] = {
    pluginStanza( Module.fileFrom( module ) )
  }

  /**
   * Convert into Zinc scala compiler installation format.
   */
  def instanceFrom(
    loader :  ClassLoader,
    install : ScalaInstall
  ) : ScalaInstance = {
    import install._
    new ScalaInstance(
      version        = version.unparse,
      loader         = loader,
      libraryJar     = fileFrom( library ),
      compilerJar    = fileFrom( compiler ),
      allJars        = zincJars.map( fileFrom( _ ) ).toArray,
      explicitActual = Some( version.unparse )
    )
  }

  object Store {
    sealed trait Type
    case object Text extends Type
    case object Binary extends Type
    case object Unknown extends Type
  }

  def storeType( name : String ) = name match {
    case "text"   => Store.Text
    case "binary" => Store.Binary
    case _        => Store.Unknown
  }

}
