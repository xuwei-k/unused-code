trait Given1 {
  given g1: Given2 = new Given2{}
}

trait Given2 {
  given g2: Given1 = new Given1{}
}
