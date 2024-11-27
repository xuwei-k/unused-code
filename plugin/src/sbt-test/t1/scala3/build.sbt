import sjsonnew.support.scalajson.unsafe.Parser.parseFromFile
import scala.util.Success

TaskKey[Unit]("check") := {
  val Success(x1) = parseFromFile(file("expect.json"))
  val Success(x2) = parseFromFile(file("target/unused-code/unused.json"))
  assert(x1 == x2)
}

val commonSettings = Def.settings(
  scalaVersion := "3.6.1",
)

commonSettings

lazy val a1 = project.settings(commonSettings)
