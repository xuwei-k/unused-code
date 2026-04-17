import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._

def sbt2 = "2.0.0-RC12"
def sbt1 = "1.12.9"
def Scala212 = scala_version_from_sbt_version.ScalaVersionFromSbtVersion(sbt1)
def Scala213 = "2.13.18"
def Scala3 = scala_version_from_sbt_version.ScalaVersionFromSbtVersion(sbt2)

val commonSettings = Def.settings(
  publishTo := (if (isSnapshot.value) None else localStaging.value),
  libraryDependencies += "org.scalatest" %% "scalatest-funsuite" % "3.2.20" % Test,
  Compile / unmanagedResources += (LocalRootProject / baseDirectory).value / "LICENSE.txt",
  Compile / doc / scalacOptions ++= {
    val hash = sys.process.Process("git rev-parse HEAD").lazyLines_!.head
    if (scalaBinaryVersion.value != "3") {
      Seq(
        "-sourcepath",
        (LocalRootProject / baseDirectory).value.getAbsolutePath,
        "-doc-source-url",
        s"https://github.com/xuwei-k/unused-code/blob/${hash}€{FILE_PATH}.scala"
      )
    } else {
      Nil
    }
  },
  scalacOptions ++= {
    scalaBinaryVersion.value match {
      case "3" =>
        Nil
      case _ =>
        Seq(
          "-release:8",
        )
    }
  },
  scalacOptions ++= {
    scalaBinaryVersion.value match {
      case "3" =>
        Nil
      case "2.13" =>
        Seq(
          "-Xsource:3-cross",
        )
      case "2.12" =>
        Seq(
          "-Xsource:3",
        )
    }
  },
  scalacOptions ++= Seq(
    "-deprecation",
  ),
  pomExtra := (
    <developers>
    <developer>
      <id>xuwei-k</id>
      <name>Kenji Yoshida</name>
      <url>https://github.com/xuwei-k</url>
    </developer>
  </developers>
  <scm>
    <url>git@github.com:xuwei-k/unused-code.git</url>
    <connection>scm:git:git@github.com:xuwei-k/unused-code.git</connection>
  </scm>
  ),
  organization := "com.github.xuwei-k",
  homepage := Some(url("https://github.com/xuwei-k/unused-code")),
  licenses := List(
    "MIT License" -> url("https://opensource.org/licenses/mit-license")
  ),
)

lazy val root = rootProject.autoAggregate.settings(
  commonSettings,
  publish / skip := true,
  releaseProcess := Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    runClean,
    setReleaseVersion,
    commitReleaseVersion,
    tagRelease,
    releaseStepCommandAndRemaining("publishSigned"),
    releaseStepCommandAndRemaining("sonaRelease"),
    setNextVersion,
    commitNextVersion,
    pushChanges
  ),
)

lazy val plugin = projectMatrix
  .in(file("plugin"))
  .defaultAxes(VirtualAxis.jvm)
  .jvmPlatform(Seq(Scala212, Scala3))
  .enablePlugins(ScriptedPlugin)
  .dependsOn(common)
  .configure(p =>
    p.id match {
      case "plugin2_12" =>
        p.dependsOn(fix.jvm(Scala212) % Test)
      case _ =>
        p
    }
  )
  .settings(
    commonSettings,
    pluginCrossBuild / sbtVersion := {
      scalaBinaryVersion.value match {
        case "2.12" =>
          sbt1
        case _ =>
          sbt2
      }
    },
    description := "find unused code sbt plugin",
    scalapropsSettings,
    scalapropsVersion := "0.10.1",
    addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % _root_.scalafix.sbt.BuildInfo.scalafixVersion),
    scriptedLaunchOpts += "-Dplugin.version=" + version.value,
    scriptedBufferLog := false,
    sbtPlugin := true,
    name := "unused-code-plugin",
    moduleName := "unused-code-plugin",
    Test / dependencyClasspath := (Test / dependencyClasspath).value.reverse,
  )

lazy val common = projectMatrix
  .in(file("common"))
  .settings(
    commonSettings,
    Compile / sourceGenerators += task {
      val dir = (Compile / sourceManaged).value
      val className = "UnusedCodeBuildInfo"
      val f = dir / "unused_code" / s"${className}.scala"
      IO.write(
        f,
        Seq(
          "package unused_code",
          "",
          s"object $className {",
          s"""  def version: String = "${version.value}" """,
          "}",
        ).mkString("", "\n", "\n")
      )
      Seq(f)
    },
    name := "unused-code-common",
    description := "unused-code common sources",
  )
  .defaultAxes(VirtualAxis.jvm)
  .jvmPlatform(Seq(Scala212, Scala213, Scala3))

lazy val fix = projectMatrix
  .in(file("fix"))
  .enablePlugins(ScalafixRuleResourceGen)
  .settings(
    commonSettings,
    name := "unused-code-scalafix",
    description := "scalafix rules unused-code",
    libraryDependencies += "ch.epfl.scala" %% "scalafix-core" % "0.14.6",
  )
  .defaultAxes(VirtualAxis.jvm)
  .jvmPlatform(Seq(Scala212, Scala213))
  .dependsOn(common)

ThisBuild / scalafixDependencies += "com.github.xuwei-k" %% "scalafix-rules" % "0.6.24"
