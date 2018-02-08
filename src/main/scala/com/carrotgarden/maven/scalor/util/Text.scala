package com.carrotgarden.maven.scalor.util

/**
 * Text support.
 */
object Text {

  /**
   * Truncate text string.
   */
  def preview( text : String, size : Int ) = {
    if ( text.length <= size ) {
      text
    } else {
      text.take( text.lastIndexWhere( _.isSpaceChar, size + 1 ) ).trim
    }
  }

  /**
   * Array as list with indent.
   */
  def reportArray[ T ]( array : Array[ T ] ) = {
    array.mkString( "   ", "\n   ", "" )
  }

}
