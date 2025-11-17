import sjsonnew.support.scalajson.unsafe.Parser.parseFromFile
import scala.util.Success

val commonSettings = Def.settings(
  scalaVersion := "2.13.18",
)

lazy val a1 = project.settings(commonSettings)
lazy val a2 = project.settings(commonSettings)
lazy val a3 = project.settings(commonSettings).disablePlugins(ScalafixPlugin)

TaskKey[Unit]("check") := {
  val Success(x1) = parseFromFile(file("expect.json"))
  val Success(x2) = parseFromFile(file("target/unused-code/unused.json"))
  assert(x1 == x2)
}
