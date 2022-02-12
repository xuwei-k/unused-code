sealed trait B
case class B1(value: Int) extends B

object C {
  def main(args: Array[String]): Unit = {
    println(B1(3))
  }
}
