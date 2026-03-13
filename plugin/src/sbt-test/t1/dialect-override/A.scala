package example

import scala.language.experimental.captureChecking

object Main {
  def main(args: Array[String]): Unit = {
    println("dialect override test")
  }

  def foo[A, B](f: A^ => B): B =
    ???
}
