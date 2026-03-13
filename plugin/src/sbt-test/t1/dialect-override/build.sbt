scalaVersion := "3.8.2"

InputKey[Unit]("updateScalafixConfigDialectOverrride") := {
  IO.write(
    file(".scalafix.conf"),
    "dialectOverride.allowCaptureChecking = true"
  )
}

InputKey[Unit]("checkScalaFile") := {
  val actual = IO.read(file("A.scala"))
  val expect = IO.read(file("expect.txt"))
  assert(actual == expect, actual)
}
