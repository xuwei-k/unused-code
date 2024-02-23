package unused_code

import java.nio.charset.StandardCharsets
import sbt.*
import sbt.Keys.*
import sbt.plugins.JvmPlugin
import sjsonnew.Builder
import sjsonnew.JsonFormat
import sjsonnew.Unbuilder
import sjsonnew.support.scalajson.unsafe.CompactPrinter
import scalafix.sbt.ScalafixPlugin
import scala.concurrent.duration.*

object UnusedCodePlugin extends AutoPlugin {
  object autoImport {
    val unusedCode = taskKey[Unit]("analyze code and output intermediate file")
    val unusedCodeConfig = taskKey[UnusedCodeConfig]("config for UnusedCode")
  }
  import autoImport.*

  private[this] implicit val instance: JsonFormat[UnusedCodeConfig] = {
    import sjsonnew.BasicJsonProtocol.*
    val strFormat = implicitly[JsonFormat[String]]
    def from[A, B](f: JsonFormat[A])(f1: A => B, f2: B => A): JsonFormat[B] =
      new JsonFormat[B] {
        override def write[J](obj: B, builder: Builder[J]) =
          f.write(f2(obj), builder)
        override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]) =
          f1(f.read(jsOpt, unbuilder))
      }
    implicit val durationInstance: JsonFormat[Duration] =
      from(strFormat)(Duration.apply, _.toString)
    implicit val dialectInstance: JsonFormat[Dialect] =
      from(strFormat)(Dialect.map, _.value)

    caseClass9(UnusedCodeConfig, UnusedCodeConfig.unapply)(
      "files",
      "scalafixConfigPath",
      "excludeNameRegex",
      "excludePath",
      "excludeGitLastCommit",
      "excludeMainMethod",
      "dialect",
      "excludeMethodRegex",
      "baseDir",
    )
  }

  private[unused_code] implicit class UnusedCodeConfigOps(private val self: UnusedCodeConfig) extends AnyVal {
    def toJsonString: String = {
      val builder = new Builder(sjsonnew.support.scalajson.unsafe.Converter.facade)
      implicitly[JsonFormat[UnusedCodeConfig]].write(self, builder)
      CompactPrinter.apply(
        builder.result.getOrElse(sys.error("invalid json"))
      )
    }
  }

  private def sbtLauncher: Def.Initialize[Task[File]] = Def.task {
    val Seq(launcher) = (LocalRootProject / dependencyResolution).value
      .retrieve(
        dependencyId = "org.scala-sbt" % "sbt-launch" % (unusedCode / sbtVersion).value,
        scalaModuleInfo = None,
        retrieveDirectory = (ThisBuild / csrCacheDirectory).value,
        log = streams.value.log
      )
      .left
      .map(e => throw e.resolveException)
      .merge
      .distinct
    launcher
  }

  // avoid extraProjects and derivedProjects
  // https://github.com/sbt/sbt/issues/6860
  // https://github.com/sbt/sbt/issues/4947
  private[this] def runUnusedCode(
    base: File,
    config: UnusedCodeConfig,
    launcher: File,
    forkOptions: ForkOptions,
  ): Either[Int, Unit] = {
    val buildSbt =
      s"""|name := "tmp-unused-code"
          |logLevel := Level.Warn
          |scalaVersion := "2.13.13"
          |libraryDependencies ++= Seq(
          |  "com.github.xuwei-k" %% "unused-code-scalafix" % "${UnusedCodeBuildInfo.version}"
          |)
          |Compile / sources := Nil
          |""".stripMargin

    IO.withTemporaryDirectory { dir =>
      val forkOpt = forkOptions.withWorkingDirectory(dir)
      val in = dir / "in.json"
      IO.write(dir / "build.sbt", buildSbt.getBytes(StandardCharsets.UTF_8))
      IO.write(in, config.toJsonString.getBytes(StandardCharsets.UTF_8))
      val ret = Fork.java.apply(
        forkOpt,
        Seq(
          "-jar",
          launcher.getCanonicalPath,
          Seq(
            "runMain",
            "unused_code.UnusedCode",
            s"--input=${in.getCanonicalPath}"
          ).mkString(" ")
        )
      )
      if (ret == 0) {
        Right(())
      } else {
        Left(ret)
      }
    }
  }

  override def trigger: PluginTrigger = allRequirements

  override def requires: Plugins = JvmPlugin && ScalafixPlugin

  override def projectSettings: Seq[Def.Setting[?]] = Seq(
    unusedCode / sources := ((Compile / sources).value ** "*.scala").get,
  )

  override def buildSettings: Seq[Def.Setting[?]] = Def.settings(
    ScalafixPlugin.autoImport.scalafixDependencies += {
      "com.github.xuwei-k" %% "unused-code-scalafix" % UnusedCodeBuildInfo.version
    },
    unusedCode / forkOptions := ForkOptions(),
    unusedCodeConfig := Def.taskDyn {
      val s = state.value
      val dialect = (LocalRootProject / scalaBinaryVersion).value match {
        case "2.10" =>
          Dialect.Scala210
        case "2.11" =>
          Dialect.Scala211
        case "2.12" =>
          if (scalacOptions.value.contains("-Xsource:3")) {
            Dialect.Scala212Source3
          } else {
            Dialect.Scala212
          }
        case "2.13" =>
          if (scalacOptions.value.contains("-Xsource:3")) {
            Dialect.Scala213Source3
          } else {
            Dialect.Scala213
          }
        case "3" =>
          Dialect.Scala3
        case _ =>
          Dialect.Scala213Source3
      }
      val extracted = Project.extract(s)
      val currentBuildUri = extracted.currentRef.build
      val projects = extracted.structure.units
        .apply(currentBuildUri)
        .defined
        .values
        .filter(
          _.autoPlugins.contains(UnusedCodePlugin)
        )
        .toList
      val baseDir = (LocalRootProject / baseDirectory).value
      val sourcesTask: Def.Initialize[Task[Seq[File]]] = projects.map { p =>
        LocalProject(p.id) / unusedCode / sources
      }.join.map(_.flatten)

      sourcesTask.map { files =>
        UnusedCodeConfig(
          files = files.map { f =>
            IO.relativize(baseDir, f).getOrElse(sys.error("invalid file " + f.getCanonicalFile))
          }.toList,
          scalafixConfigPath = ScalafixPlugin.autoImport.scalafixConfig.?.value.flatten.map { f =>
            IO.relativize(baseDir, f).getOrElse(sys.error("invalid file " + f.getCanonicalFile))
          },
          excludeNameRegex = Set.empty,
          excludePath = Set(
            "glob:**/target/**",
            "glob:**/src_managed/**",
          ),
          excludeGitLastCommit = Some(60.days),
          excludeMainMethod = true,
          dialect = dialect,
          excludeMethodRegex = Set(
            "apply",
            "unapply",
            "unapplySeq",
            "update",
          ),
          baseDir = (LocalRootProject / baseDirectory).value.getCanonicalPath
        )
      }
    }.value,
    unusedCode := {
      val conf = unusedCodeConfig.value
      val _ = conf.pathMatchers // check syntax error
      val jsonString = conf.toJsonString
      streams.value.log.debug(jsonString)
      runUnusedCode(
        base = (LocalRootProject / baseDirectory).value,
        config = conf,
        launcher = sbtLauncher.value,
        forkOptions = (unusedCode / forkOptions).value
      ).fold(e => sys.error(s"${unusedCode.key.label} failed ${e}"), x => x)
    },
  )
}
