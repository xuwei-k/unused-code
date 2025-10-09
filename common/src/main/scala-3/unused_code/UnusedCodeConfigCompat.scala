package unused_code

trait UnusedCodeConfigCompat { self: UnusedCodeConfig =>
  def asTupleOption = Option(Tuple.fromProductTyped(self))
}
