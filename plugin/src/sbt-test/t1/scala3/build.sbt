import sjsonnew.support.scalajson.unsafe.Parser.parseFromFile
import scala.util.Success

TaskKey[Unit]("check1") := {
  val Success(x1) = parseFromFile(file("expect1.json"))
  val Success(x2) = parseFromFile(file("target/unused-code/unused.json"))
  assert(x1 == x2)
}

TaskKey[Unit]("check2") := {
  val Success(x1) = parseFromFile(file("expect2.json"))
  val Success(x2) = parseFromFile(file("target/unused-code/unused.json"))
  assert(x1 == x2)
}

val commonSettings = Def.settings(
  scalaVersion := "3.8.2",
)

commonSettings

lazy val a1 = project.settings(commonSettings)
