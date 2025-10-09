package unused_code

trait UnusedCodeConfigCompat { self: UnusedCodeConfig =>
  def asTupleOption = UnusedCodeConfig.unapply(self)
}
