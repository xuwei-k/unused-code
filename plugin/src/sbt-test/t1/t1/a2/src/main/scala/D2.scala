class D2 {
  def +++(d: D2): D2 = this
}

object D2 {
  def main(args: Array[String]): Unit = {
    var d = new D2
    d +++= d
  }
}
