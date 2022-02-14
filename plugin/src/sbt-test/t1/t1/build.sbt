import sjsonnew.support.scalajson.unsafe.Parser.parseFromFile
import scala.util.Success

lazy val a1 = project
lazy val a2 = project
lazy val a3 = project.disablePlugins(ScalafixPlugin)

TaskKey[Unit]("check") := {
  val Success(x1) = parseFromFile(file("expect.json"))
  val Success(x2) = parseFromFile(file("target/unused-code/unused.json"))
  assert(x1 == x2)
}
