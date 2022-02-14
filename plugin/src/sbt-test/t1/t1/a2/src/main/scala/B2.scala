trait B2 {
  def c2: C2
  implicit def implicit1: Int = ???
  implicit val implicit2: Int = ???
}

trait C2 {
  def b2: B2
}
