trait D1 {
  def ddd_=(x1: Int): Unit = ()
  def ddd: Int = 2
}

object D1 {
  def d1: D1 = ???

  d1.ddd = 3
}
