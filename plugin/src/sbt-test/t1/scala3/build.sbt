val commonSettings = Def.settings(
  scalaVersion := "3.1.1",
)

commonSettings

lazy val a1 = project.settings(commonSettings)
