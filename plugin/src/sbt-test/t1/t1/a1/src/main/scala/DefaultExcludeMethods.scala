object DefaultExcludeMethods {
  object DefaultExcludeMethods1 {
    def update(x: Int, y: Int): Unit = ()
    def unapply(x: Int): Option[Int] = Some(x)
    def apply(x: Int) = x
  }

  def main(args: Array[String]): Unit = {
    DefaultExcludeMethods1(2) = 3
    val DefaultExcludeMethods1(a) = 4
    DefaultExcludeMethods1(5)
  }
}
