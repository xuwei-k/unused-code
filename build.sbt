import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._

def Scala212 = "2.12.15"
def Scala213 = "2.13.8"

val commonSettings = Def.settings(
  publishTo := sonatypePublishToBundle.value,
  libraryDependencies += "org.scalatest" %% "scalatest-funsuite" % "3.2.12" % Test,
  Compile / unmanagedResources += (LocalRootProject / baseDirectory).value / "LICENSE.txt",
  Compile / packageSrc / mappings ++= Compile / managedSources.value.map { f =>
    (f, f.relativeTo((Compile / sourceManaged).value).get.getPath)
  },
  Compile / doc / scalacOptions ++= {
    val hash = sys.process.Process("git rev-parse HEAD").lineStream_!.head
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
    if (scalaBinaryVersion.value == "3") {
      Nil
    } else {
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

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  releaseStepCommandAndRemaining("publishSigned"),
  releaseStepCommandAndRemaining("sonatypeBundleRelease"),
  setNextVersion,
  commitNextVersion,
  pushChanges
)

commonSettings

publish / skip := true

lazy val plugin = project
  .in(file("plugin"))
  .enablePlugins(ScriptedPlugin)
  .dependsOn(LocalProject("common2_12"), LocalProject("fix2_12") % Test)
  .settings(
    commonSettings,
    description := "find unused code sbt plugin",
    scalapropsSettings,
    scalapropsVersion := "0.9.0",
    addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % _root_.scalafix.sbt.BuildInfo.scalafixVersion),
    scriptedLaunchOpts += "-Dplugin.version=" + version.value,
    scriptedBufferLog := false,
    sbtPlugin := true,
    name := "unused-code-plugin",
    moduleName := "unused-code-plugin",
    Test / dependencyClasspath := Test / dependencyClasspath.value.reverse,
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
  .jvmPlatform(Seq(Scala212, Scala213))

lazy val fix = projectMatrix
  .in(file("fix"))
  .settings(
    commonSettings,
    name := "unused-code-scalafix",
    description := "scalafix rules unused-code",
    libraryDependencies += "ch.epfl.scala" %% "scalafix-core" % "0.10.0",
    Compile / resourceGenerators += Def.task {
      val rules = (Compile / compile).value
        .asInstanceOf[sbt.internal.inc.Analysis]
        .apis
        .internal
        .collect {
          case (className, analyzed) if analyzed.api.classApi.structure.parents.collect {
                case p: xsbti.api.Projection => p.id
              }.exists(Set("SyntacticRule", "SemanticRule")) =>
            className
        }
        .toList
        .sorted
      assert(rules.nonEmpty)
      val output = (Compile / resourceManaged).value / "META-INF" / "services" / "scalafix.v1.Rule"
      IO.writeLines(output, rules)
      Seq(output)
    }.taskValue,
  )
  .defaultAxes(VirtualAxis.jvm)
  .jvmPlatform(Seq(Scala212, Scala213))
  .dependsOn(common)

ThisBuild / scalafixDependencies += "com.github.xuwei-k" %% "scalafix-rules" % "0.2.1"
