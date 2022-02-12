package hello

// https://docs.scala-lang.org/scala3/book/methods-main-methods.html

object Hello1 {
  @main def x1(args: String*): Unit = println("hello " + args.mkString(", "))
}

object Hello2 {
  @main def x2(a1: Int = 3): Int = a1 + 2
}
