package unused_code

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
    val unusedCode = taskKey[String]("analyze code and output intermediate file")
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

    caseClass7(UnusedCodeConfig, UnusedCodeConfig.unapply)(
      "files",
      "scalafixConfigPath",
      "excludeNameRegex",
      "excludePath",
      "excludeGitLastCommit",
      "excludeMainMethod",
      "dialect",
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

  private[this] def generateProject = {
    val id = "unused-code-runner"
    Project(id = id, base = file("target") / id).settings(
      libraryDependencies += {
        "com.github.xuwei-k" %% "unused-code-scalafix" % UnusedCodeBuildInfo.version cross CrossVersion.for3Use2_13
      },
    )
  }

  // avoid extraProjects https://github.com/sbt/sbt/issues/4947
  override def derivedProjects(proj: ProjectDefinition[?]): Seq[Project] = {
    if (proj.projectOrigin == ProjectOrigin.Organic) {
      Seq(generateProject)
    } else {
      Nil
    }
  }

  override def trigger: PluginTrigger = allRequirements

  override def requires: Plugins = JvmPlugin && ScalafixPlugin

  override def projectSettings: Seq[Def.Setting[?]] = Seq(
    unusedCode / sources := ((Compile / sources).value ** "*.scala").get,
  )

  override def globalSettings: Seq[Def.Setting[?]] = Seq(
    ScalafixPlugin.autoImport.scalafixDependencies += {
      "com.github.xuwei-k" %% "unused-code-scalafix" % UnusedCodeBuildInfo.version
    },
    unusedCodeConfig := Def.taskDyn {
      val s = state.value
      val dialect = (LocalRootProject / scalaBinaryVersion).value match {
        case "2.10" =>
          Dialect.Scala210
        case "2.11" =>
          Dialect.Scala211
        case "2.12" =>
          if (scalacOptions.value.contains("-Xsource:3")) {
            Dialect.Scala213Source3
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
        )
      }
    }.value,
    unusedCode := {
      val s = state.value
      val extracted = Project.extract(s)
      val conf = unusedCodeConfig.value
      val _ = conf.pathMatchers // check syntax error
      val jsonString = conf.toJsonString
      streams.value.log.debug(jsonString)
      val loader = extracted.runTask(generateProject / Test / testLoader, s)._2
      val clazz = loader.loadClass("unused_code.UnusedCode")
      val method = clazz.getMethod("main", classOf[String])
      method.invoke(null, jsonString).asInstanceOf[String]
    },
  )
}
