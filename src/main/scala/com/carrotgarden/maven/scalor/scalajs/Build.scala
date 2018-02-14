package com.carrotgarden.maven.scalor.scalajs

import java.io.File

import org.apache.maven.plugins.annotations.Parameter

import com.carrotgarden.maven.scalor.base
import com.carrotgarden.maven.tools.Description

/**
 * Linker build resource definitions for any scope.
 */
trait Build extends AnyRef
  with base.BuildAnyTarget
  with base.BuildAnyDependency {

  /**
   * Name of the generated runtime JavaScript.
   */
  def linkerRuntimeJS : String

  /**
   * Name of the runtime dependency resolution report.
   */
  def linkerRuntimeDeps : String

  /**
   * Full path of the generated runtime JavaScript.
   */
  def linkerRuntimeFile : File = {
    if ( !buildTargetFolder.exists() ) {
      buildTargetFolder.mkdirs()
    }
    new File( buildTargetFolder, linkerRuntimeJS ).getCanonicalFile
  }

}

/**
 * Scala.js linker build parameters for scope=main.
 */
trait BuildMain extends Build
  with BuildMainDependency
  with BuildMainTarget {

  @Description( """
  Name of the generated runtime JavaScript file.
  File is packaged inside <a href="#linkerMainTargetFolder"><b>linkerMainTargetFolder</b></a>
  """ )
  @Parameter(
    property     = "scalor.linkerMainRuntimeJs",
    defaultValue = "runtime.js"
  )
  var linkerMainRuntimeJs : String = _

  @Description( """
  Name of the runtime dependency resolution report file.
  File is packaged inside <a href="#linkerMainTargetFolder"><b>linkerMainTargetFolder</b></a>
  """ )
  @Parameter(
    property     = "scalor.linkerMainRuntimeDeps",
    defaultValue = "runtime.deps"
  )
  var linkerMainRuntimeDeps : String = _

  override def linkerRuntimeJS = linkerMainRuntimeJs
  override def linkerRuntimeDeps = linkerMainRuntimeDeps

}

trait BuildMainDependency extends base.BuildAnyDependency {

  @Description( """
  Folders with classes generated by current project and included in linker class path.
  Normally includes build output from scope=[macro,main]
  (<code>target/classes</code>).
  """ )
  @Parameter(
    property     = "scalor.linkerMainDependencyFolders",
    defaultValue = "${project.build.outputDirectory}"
  )
  var linkerMainDependencyFolders : Array[ File ] = _

  @Description( """
  Provide linker class path from project dependency artifacts based on these scopes.
  Scopes <a href="https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html">reference</a>.
  """ )
  @Parameter(
    property     = "scalor.linkerMainDependencyScopes",
    defaultValue = "provided"
  )
  var linkerMainDependencyScopes : Array[ String ] = _

  override def buildDependencyFolders = linkerMainDependencyFolders
  override def buildDependencyScopes = linkerMainDependencyScopes

}

trait BuildMainTarget extends base.BuildAnyTarget {

  @Description( """
  Build target directory for the generated runtime JavaScript file with scope=main.
  Normally packaged inside <code>target/classes</code>.
  """ )
  @Parameter(
    property     = "scalor.linkerMainTargetFolder",
    defaultValue = "${project.build.outputDirectory}/META-INF/resources/script"
  )
  var linkerMainTargetFolder : File = _

  override def buildTargetFolder = linkerMainTargetFolder

}

/**
 * Scala.js linker build parameters for scope=test.
 */
trait BuildTest extends Build
  with BuildTestTarget
  with BuildTestDependency {

  @Description( """
  Name of the generated runtime JavaScript file.
  File is packaged inside <a href="#linkerTestTargetFolder"><b>linkerTestTargetFolder</b></a>
  """ )
  @Parameter(
    property     = "scalor.linkerTestRuntimeJs",
    defaultValue = "runtime-test.js"
  )
  var linkerTestRuntimeJs : String = _

  @Description( """
  Name of the runtime dependency resolution report file.
  File is packaged inside <a href="#linkerTestTargetFolder"><b>linkerTestTargetFolder</b></a>
  """ )
  @Parameter(
    property     = "scalor.linkerTestRuntimeDeps",
    defaultValue = "runtime-test.deps"
  )
  var linkerTestRuntimeDeps : String = _

  override def linkerRuntimeJS = linkerTestRuntimeJs
  override def linkerRuntimeDeps = linkerTestRuntimeDeps

}

trait BuildTestDependency extends base.BuildAnyDependency {

  @Description( """
  Folders with classes generated by current project and included in linker class path.
  Normally includes build output from scope=[macro,main,test] 
  (<code>target/test-classes</code>, <code>target/classes</code>).
  """ )
  @Parameter(
    property     = "scalor.linkerTestDependencyFolders",
    defaultValue = "${project.build.testOutputDirectory},${project.build.outputDirectory}"
  )
  var linkerTestDependencyFolders : Array[ File ] = _

  @Description( """
  Provide linker class path from project dependencies selected by these scopes.
  Scopes <a href="https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html">reference</a>.
  """ )
  @Parameter(
    property     = "scalor.linkerTestDependencyScopes",
    defaultValue = "provided,test"
  )
  var linkerTestDependencyScopes : Array[ String ] = _

  override def buildDependencyFolders = linkerTestDependencyFolders
  override def buildDependencyScopes = linkerTestDependencyScopes

}

trait BuildTestTarget extends base.BuildAnyTarget {

  @Description( """
  Build target directory for the generated runtime JavaScript file with scope=test.
  Normally packaged inside <code>target/test-classes</code>.
  """ )
  @Parameter(
    property     = "scalor.linkerTestTargetFolder",
    defaultValue = "${project.build.testOutputDirectory}/META-INF/resources/script-test"
  )
  var linkerTestTargetFolder : File = _

  override def buildTargetFolder = linkerTestTargetFolder

}
