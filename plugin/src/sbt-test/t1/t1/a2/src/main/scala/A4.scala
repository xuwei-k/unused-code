import scala.annotation.unused

object A4 {
  @unused
  def aaaaaa1: Int = 1

  @annotation.unused
  def aaaaaa2: Int = 2

  @scala.annotation.unused
  val aaaaaa3: Int = 3

  @ _root_.scala.annotation.unused
  def aaaaaa4: Int = 4
}

@unused
object A5 {
  object A6 {
    def bbbbbb1: Int = 1

    def bbbbbb2: Int = 2

    val bbbbbb3: Int = 3
  }
}
