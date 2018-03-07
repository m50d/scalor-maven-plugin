package com.carrotgarden.maven.scalor.scalanative

import java.io.File

import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.plugins.annotations.Component

import com.carrotgarden.maven.scalor.base
import com.carrotgarden.maven.tools.Description
import com.carrotgarden.maven.scalor.util.Folder
import com.carrotgarden.maven.scalor.util.Error.Throw
import org.apache.maven.archiver.MavenArchiveConfiguration
import org.apache.maven.project.MavenProjectHelper
import org.apache.maven.project.MavenProject

trait ParamsLinkAny extends AnyRef
  with Build
  with ParamsRegex
  with ParamsLibrary
  with ParamsLogging
  with ParamsOptions
  with ParamsEntryClassMain
  with ParamsGarbageCollectorAny {

  def nativeWorkdir : File

}

trait ParamsOptions {

  @Description( """
  Linking behaviour for placeholder methods annotated with <code>@stub</code>.
  By default stubs are not linked and are shown as linking errors.
  """ )
  @Parameter(
    property     = "scalor.nativeOptionLinkStubs",
    defaultValue = "false"
  )
  var nativeOptionLinkStubs : Boolean = _

}

trait ParamsLogging {

  @Description( """
  Enable logging of linker options.
  Use to review actual Scala.native linker invocation configuration.
  """ )
  @Parameter(
    property     = "scalor.nativeLogOptions",
    defaultValue = "false"
  )
  var nativeLogOptions : Boolean = _

  @Description( """
  Enable logging of Scala.native linker runtime.
  Use to review actual generated output <code>runtime</code> location.
  """ )
  @Parameter(
    property     = "scalor.nativeLogRuntime",
    defaultValue = "true"
  )
  var nativeLogRuntime : Boolean = _

  @Description( """
  Enable logging of Scala.native linker class path.
  Use to review actual resources used for <code>*.nir</code> class discovery.
  """ )
  @Parameter(
    property     = "scalor.nativeLogClassPath",
    defaultValue = "false"
  )
  var nativeLogClassPath : Boolean = _

  @Description( """
  Enable logging of Scala.native linker build phase statistics, including phase durations.
  Use to review linker performance profile.
  """ )
  @Parameter(
    property     = "scalor.nativeLogBuildStats",
    defaultValue = "false"
  )
  var nativeLogBuildStats : Boolean = _

  @Description( """
  Enable logging of Scala.native linker update result of M2E incremental change detection.
  Use to review actual <code>*.nir</code> classes which triggered Eclipse linker build.
  """ )
  @Parameter(
    property     = "scalor.nativeLogUpdateResult",
    defaultValue = "false"
  )
  var nativeLogUpdateResult : Boolean = _

}

trait ParamsLibrary {

  @Description( """
  Regular expression used to detect when Scala.native library is present on class path.
  This regular expression is matched against resolved project depenencies in given scope.
  Regular expression in the form: <code>${groupId}:${artifactId}</code>.
  Enablement parameter: <a href="#nativeLibraryDetect"><b>nativeLibraryDetect</b></a>.
  """ )
  @Parameter(
    property     = "scalor.nativeLibraryRegex",
    defaultValue = "org.scala-native:scalalib_.+"
  )
  var nativeLibraryRegex : String = _

  @Description( """
  Invoke Scala.native linker only when Scala.native library is detected
  in project dependencies with given scope.
  Detection parameter: <a href="#nativeLibraryRegex"><b>nativeLibraryRegex</b></a>.
  """ )
  @Parameter(
    property     = "scalor.nativeLibraryDetect",
    defaultValue = "true"
  )
  var nativeLibraryDetect : Boolean = _

}

trait ParamsRegex {

  @Description( """
  Regular expression used to discover Scala.native IR classes from class path.
  """ )
  @Parameter(
    property     = "scalor.nativeClassRegex",
    defaultValue = ".+[.]nir"
  )
  var nativeClassRegex : String = _

}

trait ParamsEntryClassAny {

  def nativeEntryClass : String

}

trait ParamsEntryClassMain extends ParamsEntryClassAny {

  @Description( """
  Entry point for native runtime in scope=main.
  Fully qualified class name which follows Java <code>main</code> contract.
  For example, Scala object in file <code>main/Main.scala</code>:
<pre>
package main
object Main {
  def main( args : Array[ String ] ) : Unit = {
    println( s"scala-native" )
  }
}
</pre>
  """ )
  @Parameter(
    property     = "scalor.nativeMainEntryClass",
    defaultValue = "main.Main"
  )
  var nativeMainEntryClass : String = _

  override def nativeEntryClass = nativeMainEntryClass

}

trait ParamsEntryClassTest extends ParamsEntryClassAny {

  @Description( """
  Entry point for native runtime in scope=test.
  Fully qualified class name which follows Java <code>main</code> contract.
  For example, Scala object in file <code>test/Main.scala</code>:
<pre>
package test
object Main {
  def main( args : Array[ String ] ) : Unit = {
    println( s"scala-native" )
  }
}
</pre>
  """ )
  @Parameter(
    property     = "scalor.nativeTestEntryClass",
    defaultValue = "test.Main"
  )
  var nativeTestEntryClass : String = _

  override def nativeEntryClass = nativeTestEntryClass

}

trait ParamsGarbageCollectorAny {

  def nativeGarbageCollector : String

}

trait ParamsGarbageCollectorMain extends ParamsGarbageCollectorAny {

  @Description( """
  Select garbage collector included with Scala.native runtime in scope=main.
  Garbage collector <a href="http://www.scala-native.org/en/latest/user/sbt.html#garbage-collectors">reference</a>.
  Available garbage collectors:
<pre>
  none 
  boehm 
  immix 
</pre>
  """ )
  @Parameter(
    property     = "scalor.nativeMainGarbageCollector",
    defaultValue = "boehm"
  )
  var nativeMainGarbageCollector : String = _

  override def nativeGarbageCollector = nativeMainGarbageCollector

}

trait ParamsGarbageCollectorTest extends ParamsGarbageCollectorAny {

  @Description( """
  Select garbage collector included with Scala.native runtime in scope=test.
  Garbage collector <a href="http://www.scala-native.org/en/latest/user/sbt.html#garbage-collectors">reference</a>.
  Available garbage collectors:
<pre>
  none 
  boehm 
  immix 
</pre>
  """ )
  @Parameter(
    property     = "scalor.nativeTestGarbageCollector",
    defaultValue = "boehm"
  )
  var nativeTestGarbageCollector : String = _

  override def nativeGarbageCollector = nativeTestGarbageCollector

}

trait ParamsOperatingSystem {

  import org.apache.commons.lang3.SystemUtils

  @Description( """
  Detect operating system and invoke native goals only when running on supported o/s.
  When <code>false</code>, force native goals invocation.
  """ )
  @Parameter(
    property     = "scalor.nativeSystemDetect",
    defaultValue = "true"
  )
  var nativeSystemDetect : Boolean = _

  /**
   * http://www.scala-native.org/en/latest/user/setup.html
   */
  def nativeHasOperatingSystem = {
    import SystemUtils._
    IS_OS_LINUX || IS_OS_MAC_OSX || IS_OS_FREE_BSD
  }

}

trait ParamsLinkMain extends ParamsLinkAny
  with BuildMain
  with ParamsEntryClassMain
  with ParamsGarbageCollectorMain {

  @Description( """
  Linker working directory for scope=main.
  """ )
  @Parameter(
    property     = "scalor.nativeMainWorkdir",
    defaultValue = "${project.build.directory}/scalor/native/workdir/main"
  )
  var nativeMainWorkdir : File = _

  override def nativeWorkdir = nativeMainWorkdir

}

trait ParamsLinkTest extends ParamsLinkAny
  with BuildTest
  with ParamsEntryClassTest
  with ParamsGarbageCollectorTest {

  @Description( """
  Linker working directory for scope=main.
  """ )
  @Parameter(
    property     = "scalor.nativeTestWorkdir",
    defaultValue = "${project.build.directory}/scalor/native/workdir/test"
  )
  var nativeTestWorkdir : File = _

  override def nativeWorkdir = nativeTestWorkdir

}

trait ParamsPackAny extends AnyRef
  with ParamsLibrary {

  @Description( """
  Configuration of Scala.native archive jar. 
  Normally used with provided default values.
  Component reference:
<a href="https://maven.apache.org/shared/maven-archiver/index.html">
  MavenArchiveConfiguration
</a>
  """ )
  @Parameter()
  var nativeArchiveConfig : MavenArchiveConfiguration = new MavenArchiveConfiguration()

  @Description( """
  Maven project helper.
  """ )
  @Component()
  var projectHelper : MavenProjectHelper = _

  //  @Description( """
  //  Contains the full list of projects in the build.
  //  """ )
  //  @Parameter( defaultValue = "${reactorProjects}", readonly = true )
  //  var reactorProjects : java.util.List[ MavenProject ] = _

  @Description( """
  Root name for the generated Scala.native jar file.
  Full name will include <code>classifier</code> suffix.
  """ )
  @Parameter(
    property     = "scalor.nativeFinalName",
    defaultValue = "${project.build.finalName}"
  )
  var nativeFinalName : String = _

  def nativeHasAttach : Boolean
  def nativeClassifier : String
  def nativeOutputFolder : File

  def nativeArchiveName = s"${nativeFinalName}-${nativeClassifier}.jar"

}

trait ParamsPackMain extends ParamsPackAny {

  @Description( """
  Artifact classifier for Scala.native with scope=main.
  Appended to <a href="#nativeFinalName"><b>nativeFinalName</b></a>.
  """ )
  @Parameter(
    property     = "scalor.nativeMainClassifier",
    defaultValue = "native"
  )
  var nativeMainClassifier : String = _

  @Description( """
  Enable to attach generated Scala.native jar 
  to the project as deployment artifact with scope=main.
  """ )
  @Parameter(
    property     = "scalor.nativeMainAttach",
    defaultValue = "true"
  )
  var nativeMainAttach : Boolean = _

  @Description( """
  Folder with generated Scala.native content with scope=main. 
  """ )
  @Parameter(
    property     = "scalor.nativeMainOutputFolder",
    defaultValue = "${project.build.directory}/scalor/native/output/main"
  )
  var nativeMainOutputFolder : File = _

  override def nativeHasAttach = nativeMainAttach
  override def nativeClassifier = nativeMainClassifier
  override def nativeOutputFolder = nativeMainOutputFolder

}

trait ParamsPackTest extends ParamsPackAny {

  @Description( """
  Artifact classifier for Scala.native with scope=test.
  Appended to <a href="#nativeFinalName"><b>nativeFinalName</b></a>.
  """ )
  @Parameter(
    property     = "scalor.nativeTestClassifier",
    defaultValue = "test-native"
  )
  var nativeTestClassifier : String = _

  @Description( """
  Enable to attach generated Scala.native jar
  to the project as deployment artifact with scope=test.
  """ )
  @Parameter(
    property     = "scalor.nativeTestAttach",
    defaultValue = "true"
  )
  var nativeTestAttach : Boolean = _

  @Description( """
  Folder with generated Scala.native content with scope=test. 
  """ )
  @Parameter(
    property     = "scalor.nativeTestOutputFolder",
    defaultValue = "${project.build.directory}/scalor/native/output/test"
  )
  var nativeTestOutputFolder : File = _

  override def nativeHasAttach = nativeTestAttach
  override def nativeClassifier = nativeTestClassifier
  override def nativeOutputFolder = nativeTestOutputFolder

}
