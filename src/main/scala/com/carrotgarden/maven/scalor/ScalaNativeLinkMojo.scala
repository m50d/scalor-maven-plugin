package com.carrotgarden.maven.scalor

import scala.collection.JavaConverters._

import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.ResolutionScope

import com.carrotgarden.maven.scalor.base.Context.UpdateResult
import com.carrotgarden.maven.scalor.scalanative.Linker
import com.carrotgarden.maven.tools.Description

import java.io.File
import org.apache.maven.artifact.Artifact

trait ScalaNativeLinkAnyMojo extends AbstractMojo
  with base.Mojo
  with base.Params
  with base.Logging
  with base.SkipMojo
  with base.Context
  with scalanative.Linker
  with scalanative.ParamsLinkAny
  with scalanative.ParamsOperatingSystem {

  @Description( """
  Flag to skip this execution: <code>scala-native-link-*</code>.
  """ )
  @Parameter(
    property     = "scalor.skipNativeLink",
    defaultValue = "false"
  )
  var skipNativeLink : Boolean = _

  /**
   * Provide linker project build class path.
   */
  lazy val linkerClassPath : Array[ File ] = {
    buildDependencyFolders ++ projectClassPath( buildDependencyScopes )
  }

  /**
   * Discover Scala.native library on project class path.
   */
  lazy val libraryArtifactOption : Option[ Artifact ] = {
    util.Maven.locateArtifact( project, nativeLibraryRegex )
  }

  def linkerInvoke(
    nativeMode : String, runtime : File, updateList : Array[ UpdateResult ]
  ) : Unit = {

    val params = Linker.Params(
      gcType     = nativeGarbageCollector,
      entryClass = nativeEntryClass,
      runtime    = runtime,
      workdir    = nativeWorkdir,
      classpath  = linkerClassPath
    )

    val options = Linker.Options(
      mode      = nativeMode,
      linkStubs = nativeOptionLinkStubs
    )

    val context = Linker.Context(
      params     = params,
      options    = options,
      updateList = updateList
    )

    linkerPerform( context )

  }

  /**
   * Generate runtime for debug and release mode.
   */
  def performLinker( updateList : Array[ UpdateResult ] ) : Unit = {
    val hasBuildDebug = nativeHasBuildDebug( hasIncremental )
    if ( hasBuildDebug ) {
      linkerInvoke( "debug", nativeRuntimeDebugFile, updateList )
    }
    val hasBuildRelease = nativeHasBuildRelease( hasIncremental )
    if ( hasBuildRelease ) {
      linkerInvoke( "release", nativeRuntimeReleaseFile, updateList )
    }
  }

  def performInvocation() : Unit = {
    if ( nativeLibraryDetect && libraryArtifactOption.isEmpty ) {
      reportSkipReason( s"Skipping execution: Scala.native library missing: ${nativeLibraryRegex}." )
      return
    }
    val updateList = if ( hasIncremental ) {
      contextUpdateResult( buildDependencyFolders, nativeClassRegex.r )
    } else {
      Array.empty[ UpdateResult ]
    }
    if ( hasIncremental ) {
      logger.info( s"Incremental build request." )
      val hasUpdate = updateList.count( _.hasUpdate ) > 0
      if ( hasUpdate ) {
        performLinker( updateList )
      }
    } else {
      logger.info( s"Full linker build request." )
      performLinker( updateList )
    }
  }

  override def perform() : Unit = {
    if ( skipNativeLink || hasSkipMojo ) {
      reportSkipReason( "Skipping disabled goal execution." )
      return
    }
    if ( nativeSystemDetect && !nativeHasOperatingSystem ) {
      reportSkipReason( "Skipping unsupported operating system." )
      return
    }
    performInvocation()
  }

}

@Description( """
Generate Scala.native runtime binary for all scopes.
Invokes goals: scala-native-link-*.
""" )
@Mojo(
  name                         = A.mojo.`scala-native-link`,
  defaultPhase                 = LifecyclePhase.PROCESS_CLASSES,
  requiresDependencyResolution = ResolutionScope.TEST
)
class ScalaNativeLinkArkonMojo extends ScalaNativeLinkAnyMojo
  with scalanative.ParamsLinkMain
  with scalanative.ParamsLinkTest {

  override def mojoName = A.mojo.`scala-native-link`

  override def performInvocation() : Unit = {
    executeSelfMojo( A.mojo.`scala-native-link-main` )
    executeSelfMojo( A.mojo.`scala-native-link-test` )
  }

}

// Provides incremental linking in M2E.
@Description( """
Generate Scala.native runtime binary for scope=main.
A member of goal=scala-native-link.
""" )
@Mojo(
  name                         = A.mojo.`scala-native-link-main`,
  defaultPhase                 = LifecyclePhase.PROCESS_CLASSES,
  requiresDependencyResolution = ResolutionScope.COMPILE
)
class ScalaNativeLinkMainMojo extends ScalaNativeLinkAnyMojo
  with scalanative.ParamsLinkMain {

  override def mojoName = A.mojo.`scala-native-link-main`

  @Description( """
  Flag to skip this goal execution: <code>scala-native-link-main</code>.
  """ )
  @Parameter(
    property     = "scalor.skipNativeLinkMain",
    defaultValue = "false"
  )
  var skipNativeLinkMain : Boolean = _

  override def hasSkipMojo = skipNativeLinkMain

}

// Provides incremental linking in M2E.
@Description( """
Generate Scala.native runtime binary for scope=test.
A member of goal=scala-native-link.
""" )
@Mojo(
  name                         = A.mojo.`scala-native-link-test`,
  defaultPhase                 = LifecyclePhase.PROCESS_TEST_CLASSES,
  requiresDependencyResolution = ResolutionScope.TEST
)
class ScalaNativeLinkTestMojo extends ScalaNativeLinkAnyMojo
  with scalanative.ParamsLinkTest {

  override def mojoName = A.mojo.`scala-native-link-test`

  @Description( """
  Flag to skip this goal execution: <code>scala-native-link-test</code>.
  """ )
  @Parameter(
    property     = "scalor.skipNativeLinkTest",
    defaultValue = "false"
  )
  var skipNativeLinkTest : Boolean = _

  override def hasSkipMojo = skipNativeLinkTest

}
