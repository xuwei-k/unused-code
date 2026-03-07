package pkg2

object ExtensionTest {
  extension(x: Int) {
    def y1: Int = x + 1
  }

  extension(x: Int) {
    def y2: Int = x + 2
  }
}
