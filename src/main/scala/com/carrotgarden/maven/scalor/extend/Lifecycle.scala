package com.carrotgarden.maven.scalor.extend

import org.apache.maven.AbstractMavenLifecycleParticipant
import org.codehaus.plexus.component.annotations._
import org.apache.maven.execution.MavenSession
import org.codehaus.plexus.logging.Logger
import org.apache.maven.model.PluginExecution
import scala.collection.JavaConverters._
import org.apache.maven.model.Plugin
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.LifecyclePhase._

import com.carrotgarden.maven.scalor.A
import com.carrotgarden.maven.scalor.meta
import com.carrotgarden.maven.scalor.util.Error._
import com.carrotgarden.maven.scalor.base.Self

/**
 * Provide default Maven lifecycle for Scalor plugin.
 *
 * FIXME No M2E support https://bugs.eclipse.org/bugs/show_bug.cgi?id=486737
 */
@Component(
  role = Lifecycle.Role,
  hint = Lifecycle.Hint
)
class Lifecycle extends AbstractMavenLifecycleParticipant {

  /**
   * Use default logger.
   */
  @Requirement
  var logger : Logger = _

  /**
   * Locate a plugin in the current project.
   */
  def aquirePlugin( artifactId : String )( implicit session : MavenSession ) : Option[ Plugin ] = {
    session.getCurrentProject.getBuild.getPlugins.asScala.toList
      .filter( plugin => plugin.getArtifactId.startsWith( artifactId ) ) match {
        case List( plugin ) => Some( plugin )
        case result : Any   => None
      }
  }

  /**
   * Locate scalor-maven-plugin in the current project.
   */
  def scalorPlugin( implicit session : MavenSession ) = aquirePlugin( Self.mavenArtifactId )

  /**
   * Locate maven-compiler-plugin in the current project.
   */
  def compilerPlugin( implicit session : MavenSession ) = aquirePlugin( A.maven.compilerId )

  /**
   * Add phase:goal as default execution for the plugin.
   */
  def registerExecution( phase : LifecyclePhase, goal : String )( implicit plugin : Plugin ) = {
    val execution = new PluginExecution()
    execution.setPhase( phase.id );
    execution.getGoals().add( goal )
    execution.setConfiguration( plugin.getConfiguration() )
    plugin.getExecutions.add( execution )
  }

  /**
   * Provide scalor plugin lifecycle.
   */
  // FIXME use macro to extract executions
  override def afterProjectsRead( session : MavenSession ) = {
    logger.info( "Scalor: extenson lifecycle..." )
    implicit val MS = session

    // Disable default compiler.
    compilerPlugin match {
      case Some( compiler ) => compiler.getExecutions.clear()
      case None             =>
    }

    // Enable alternative compiler.
    scalorPlugin match {
      case Some( scalor ) =>
        implicit val SP = scalor
        import A.mojo._
        // Provide default goal executions.

        registerExecution( CLEAN, `clean-macro` )
        registerExecution( CLEAN, `clean-main` )
        registerExecution( CLEAN, `clean-test` )

        registerExecution( INITIALIZE, `setup-cross` )

        registerExecution( INITIALIZE, `eclipse-config` )

        registerExecution( INITIALIZE, `register-macro` )
        registerExecution( INITIALIZE, `register-main` )
        registerExecution( INITIALIZE, `register-test` )

        registerExecution( COMPILE, `compile-macro` )
        registerExecution( COMPILE, `compile-main` )

        registerExecution( GENERATE_TEST_RESOURCES, `scala-js-env-prov-nodejs` )
        registerExecution( GENERATE_TEST_RESOURCES, `scala-js-env-prov-phantomjs` )
        registerExecution( GENERATE_TEST_RESOURCES, `scala-js-env-prov-webjars` )

        registerExecution( PROCESS_TEST_RESOURCES, `scala-js-env-conf-nodejs` )
        registerExecution( PROCESS_TEST_RESOURCES, `scala-js-env-conf-phantomjs` )

        registerExecution( PROCESS_CLASSES, `scala-js-link-main` )
        registerExecution( PROCESS_CLASSES, `scala-native-link-main` )

        registerExecution( TEST_COMPILE, `compile-test` )

        registerExecution( PROCESS_TEST_CLASSES, `scala-js-link-test` )
        registerExecution( PROCESS_TEST_CLASSES, `scala-native-link-test` )

        registerExecution( TEST, `eclipse-restart` )
        registerExecution( TEST, `eclipse-prescomp` )

        registerExecution( PACKAGE, `scaladoc-macro` )
        registerExecution( PACKAGE, `scaladoc-main` )
        registerExecution( PACKAGE, `scaladoc-test` )

        registerExecution( PACKAGE, `report-main` )
        registerExecution( PACKAGE, `report-test` )

        registerExecution( PACKAGE, `scala-native-pack-main` )
        registerExecution( PACKAGE, `scala-native-pack-test` )

      case None =>
        Throw( "Missing scalor plugin." )
    }

  }

}

/**
 * TODO
 */
object Lifecycle {
  import meta.Macro
  final val Role = classOf[ AbstractMavenLifecycleParticipant ]
  final val Impl = classOf[ Lifecycle ]
  // Globally unique component identity, once per build.
  final val Hint = "scalor-cycle" // Macro.guidOf( "com.carrotgarden.maven.scalor.extend.Lifecycle" )
}
