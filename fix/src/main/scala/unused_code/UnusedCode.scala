package unused_code

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import metaconfig.Conf
import metaconfig.ConfDecoder
import metaconfig.ConfEncoder
import metaconfig.Hocon
import metaconfig.generic.Surface
import scala.annotation.nowarn
import scala.concurrent.duration.*
import scala.meta.Defn
import scala.meta.Member
import scala.meta.Mod
import scala.meta.Name
import scala.meta.Pat
import scala.meta.Pkg
import scala.meta.Source
import scala.meta.Term
import scala.meta.Tree
import scala.meta.Type
import scala.meta.XtensionClassifiable
import scala.meta.XtensionCollectionLikeUI
import scala.meta.inputs.Input
import scala.meta.parsers.Parse
import scala.sys.process.Process

object UnusedCode {
  private[this] implicit val surface: Surface[UnusedCodeConfig] =
    metaconfig.generic.deriveSurface[UnusedCodeConfig]

  private[this] def defaultScalafixConfigFile = ".scalafix.conf"

  private[this] implicit val decoder: ConfDecoder[UnusedCodeConfig] = {
    implicit val durationDecoder: ConfDecoder[Duration] = {
      implicitly[ConfDecoder[String]].map(Duration.apply)
    }
    implicit val dialectDecoder: ConfDecoder[unused_code.Dialect] =
      implicitly[ConfDecoder[String]].map(unused_code.Dialect.map)
    implicit val dateTimeDecoder: ConfDecoder[ZonedDateTime] =
      implicitly[ConfDecoder[String]].map(ZonedDateTime.parse)
    val empty = UnusedCodeConfig(
      files = Nil,
      scalafixConfigPath = None,
      excludeNameRegex = Set.empty,
      excludePath = Set.empty,
      excludeGitLastCommit = None,
      excludeGitLastCommitDateTime = None,
      excludeMainMethod = true,
      excludeJEP512MainMethod = true,
      dialect = Dialect.Scala213Source3,
      excludeMethodRegex = Set.empty,
      baseDir = "",
    )
    metaconfig.generic.deriveDecoder[UnusedCodeConfig](empty)
  }

  private[unused_code] def jsonFileToConfig(json: File): UnusedCodeConfig = {
    val c = Conf.parseFile(json)(Hocon)
    implicitly[ConfDecoder[UnusedCodeConfig]].read(c).get
  }

  private[this] val convertDialect: Map[unused_code.Dialect, scala.meta.Dialect] = Map(
    unused_code.Dialect.Scala210 -> scala.meta.dialects.Scala210,
    unused_code.Dialect.Scala211 -> scala.meta.dialects.Scala211,
    unused_code.Dialect.Scala212 -> scala.meta.dialects.Scala212,
    unused_code.Dialect.Scala213 -> scala.meta.dialects.Scala213,
    unused_code.Dialect.Scala212Source3 -> scala.meta.dialects.Scala212Source3,
    unused_code.Dialect.Scala213Source3 -> scala.meta.dialects.Scala213Source3,
    unused_code.Dialect.Scala3 -> scala.meta.dialects.Scala3
  )

  def main(args: Array[String]): Unit = {
    val in = {
      val key = "--input="
      args.collectFirst { case arg if arg.startsWith(key) => arg.drop(key.length) }
        .getOrElse(sys.error("missing --input"))
    }
    val conf = jsonFileToConfig(new File(in))
    val config = getScalafixConfig(conf)
    val result = conf.files.flatMap { file =>
      val input = Input.File(new File(conf.baseDir, file))
      val dialect = applyDialectOverride(
        config.dialectOverride,
        convertDialect.getOrElse(conf.dialect, scala.meta.dialects.Scala213)
      )
      val tree = implicitly[Parse[Source]].apply(input, dialect).get
      run(tree, file, conf)
    }
    writeResult(result, conf)
  }

  private def getScalafixConfig(conf: UnusedCodeConfig): UnusedCodeScalafixConfig = {
    val scalafixConfig = new File(conf.baseDir, conf.scalafixConfigPath.getOrElse(defaultScalafixConfigFile))
    if (scalafixConfig.isFile) {
      Conf.parseFile(scalafixConfig)(Hocon).get.as[UnusedCodeScalafixConfig].get
    } else {
      UnusedCodeScalafixConfig.default
    }
  }

  /**
   * [[https://github.com/scalacenter/scalafix/commit/2529c4d42ef25511c6576d17c1cc287a5515d9d2]]
   */
  private def applyDialectOverride(
    dialectOverride: Map[String, Boolean],
    dialect: scala.meta.Dialect
  ): scala.meta.Dialect = {
    dialectOverride.foldLeft(dialect) {
      case (cur, (k, v)) if k.nonEmpty =>
        val upper = s"${k.head.toUpper}${k.drop(1)}"
        cur.getClass.getMethods
          .find(method =>
            (
              method.getName == s"with${upper}"
            ) && (
              method.getParameterTypes.toSeq == Seq(classOf[Boolean])
            ) && (
              method.getReturnType == classOf[scala.meta.Dialect]
            )
          )
          .fold(cur)(
            _.invoke(cur, java.lang.Boolean.valueOf(v)).asInstanceOf[scala.meta.Dialect]
          )
      case (cur, _) =>
        cur
    }
  }

  private[unused_code] val extractDefineValue: PartialFunction[Tree, (Tree, List[Mod], Name)] = {
    case x: Defn.Trait =>
      (x, x.mods, x.name)
    case x: Defn.Class =>
      (x, x.mods, x.name)
    case x: Defn.Object =>
      (x, x.mods, x.name)
    case x: Pkg.Object =>
      (x, x.mods, x.name)
    case x: Defn.Def =>
      (x, x.mods, x.name)
    case x: Defn.Enum =>
      (x, x.mods, x.name)
    case x: Defn.EnumCase =>
      (x, x.mods, x.name)
    case x @ Defn.Val(_, List(Pat.Var(name)), _, _) =>
      (x, x.mods, name)
    case x @ Defn.Var.After_4_7_2(_, List(Pat.Var(name)), _, _) =>
      (x, x.mods, name)
  }

  private[this] object DefineValue {
    def unapply(t: Tree): Option[(Tree, List[Mod], Name)] = extractDefineValue.lift.apply(t)
  }

  @nowarn("msg=lineStream")
  private[this] def lastGitCommitMilliSeconds(base: String, path: String): Long = {
    val default = 0L
    // https://git-scm.com/docs/git-log
    if (new File(base, ".git").isDirectory) {
      Process(
        command = Seq("git", "log", "-1", "--format=%ct", path),
        cwd = Some(new File(base))
      ).lineStream_!.headOption.map(_.toLong * 1000L).getOrElse(default)
    } else {
      default
    }
  }

  @nowarn("msg=lineStream")
  private[this] def lastGitCommitDateTime(base: String, path: String): ZonedDateTime = {
    val default = ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.of("UTC"))
    // https://git-scm.com/docs/git-log
    if (new File(base, ".git").isDirectory) {
      Process(
        command = Seq("git", "log", "-1", "--date=iso-strict", "--pretty=tformat:%cd", path),
        cwd = Some(new File(base))
      ).lineStream_!.headOption.map(ZonedDateTime.parse).getOrElse(default)
    } else {
      default
    }
  }

  private implicit val zonedDateTimeOrdering: Ordering[ZonedDateTime] = _.compareTo(_)

  private[this] def aggregate(values: Seq[FindResult], config: UnusedCodeConfig): List[FindResult.Use] = {
    val allDefineNames = values.collect { case a: FindResult.Define => a.value }.toSet
    val allNames = values.collect { case a: FindResult.Use => a }
    val currentMillis = System.currentTimeMillis()
    val pathMatchers = config.pathMatchers

    // TODO filter `def`, `val` and `var` if outer object/class/trait unused
    allNames
      .groupBy(_.value)
      .view
      .collect { case (_, Seq(v)) => v }
      .filter(x => allDefineNames(x.value))
      .filterNot(x => config.isExcludeName(x.value))
      .filter(x => pathMatchers.forall(matcher => !matcher.matches(Paths.get(x.path))))
      .filter(x =>
        config.excludeGitLastCommit match {
          case Some(duration) if duration.isFinite =>
            duration < (currentMillis - lastGitCommitMilliSeconds(config.baseDir, x.path)).millis
          case _ =>
            config.excludeGitLastCommitDateTime match {
              case Some(dateTime) =>
                import Ordering.Implicits.*
                dateTime > lastGitCommitDateTime(config.baseDir, x.path)
              case None =>
                true
            }
        }
      )
      .toList
      .sortBy(x => (x.path, x.value))
  }

  private[this] def writeResult(values: Seq[FindResult], config: UnusedCodeConfig): String = {
    val result = FindResults(aggregate(values = values, config = config))
    val json = implicitly[ConfEncoder[FindResults]].write(result).show
    val bytes = json.getBytes(StandardCharsets.UTF_8)
    val path = new File(config.baseDir, getScalafixConfig(config).outputPath).toPath
    Files.createDirectories(path.getParent)
    Files.write(path, bytes)
    json
  }

  private[unused_code] val isJEP512MainMethod: PartialFunction[Tree, Unit] = {
    case Defn.Def.After_4_7_3(
          _,
          Term.Name("main"),
          Nil | List(
            Member.ParamClauseGroup(
              Type.ParamClause(Nil),
              List(
                Term.ParamClause(Nil, None)
              )
            )
          ),
          Some(
            Type.Select(Term.Select(Term.Name("_root_"), Term.Name("scala")), Type.Name("Unit")) |
            Type.Select(Term.Name("scala"), Type.Name("Unit")) | Type.Name("Unit")
          ) | None,
          _
        ) =>
      ()
  }

  private[unused_code] val isMainMethod: PartialFunction[Tree, Unit] = {
    case x: Defn.Def if x.mods.collect { case a: Mod.Annot => a.init.tpe }.collectFirst { case Type.Name("main") =>
          ()
        }.isDefined =>
      ()
    case Defn.Def.After_4_7_3(
          _,
          Term.Name("main"),
          List(
            Member.ParamClauseGroup(
              Type.ParamClause(Nil),
              List(
                Term.ParamClause(
                  List(
                    Term.Param(
                      _,
                      _,
                      Some(
                        Type.Apply.After_4_6_0(
                          Type.Name("Array") | Type.Select(Term.Name("scala"), Type.Name("Array")) |
                          Type.Select(Term.Select(Term.Name("_root_"), Term.Name("scala")), Type.Name("Array")),
                          Type.ArgClause(
                            List(
                              Type.Name("String") | Type.Select(Term.Name("Predef"), Type.Name("String")) |
                              Type.Select(Term.Select(Term.Name("java"), Term.Name("lang")), Type.Name("String")),
                            )
                          )
                        )
                      ),
                      _
                    )
                  ),
                  None
                )
              )
            )
          ),
          Some(
            Type.Select(Term.Select(Term.Name("_root_"), Term.Name("scala")), Type.Name("Unit")) |
            Type.Select(Term.Name("scala"), Type.Name("Unit")) | Type.Name("Unit")
          ) | None,
          _
        ) =>
      ()
  }
  private[this] def hasMainMethod(tree: Tree): Boolean =
    tree.collect(isMainMethod).nonEmpty

  private[this] def hasJEP512MainMethod(tree: Tree): Boolean =
    tree.collect(isJEP512MainMethod).nonEmpty

  private[this] def filterMods(mods: List[Mod]): Boolean = {
    !mods.exists(m => m.is[Mod.Implicit] || m.is[Mod.Override] || m.is[Mod.Inline])
  }

  private[this] def run(tree: Tree, path: String, config: UnusedCodeConfig): List[FindResult] = {
    tree.collect {
      case x: Defn.Object if filterMods(x.mods) =>
        if (config.excludeMainMethod && hasMainMethod(x)) {
          Nil
        } else if (config.excludeJEP512MainMethod && hasJEP512MainMethod(x)) {
          Nil
        } else {
          FindResult.Define(
            value = x.name.value,
          ) :: Nil
        }
      case x: Defn.Def if filterMods(x.mods) && !config.isExcludeMethod(x.name.value) =>
        if (config.excludeMainMethod && isMainMethod.isDefinedAt(x)) {
          Nil
        } else if (config.excludeJEP512MainMethod && isJEP512MainMethod.isDefinedAt(x)) {
          Nil
        } else {
          // TODO unary methods https://github.com/xuwei-k/unused-code/issues/3
          val setterSuffix = "_="
          if (x.name.value.endsWith(setterSuffix)) {
            // TODO improvement
            FindResult.Define(
              value = x.name.value.dropRight(setterSuffix.length),
            ) :: Nil
          } else {
            FindResult.Define(
              value = x.name.value,
            ) :: Nil
          }
        }
      case DefineValue(t, mods, name) if filterMods(mods) =>
        t match {
          case _: Defn.Def if config.isExcludeMethod(name.value) =>
            Nil
          case _ =>
            FindResult.Define(
              value = name.value,
            ) :: Nil
        }
      case x: Term.Name =>
        FindResult.Use(
          value = x.value,
          path = path,
        ) :: Nil
      case x: Type.Name =>
        FindResult.Use(
          value = x.value,
          path = path,
        ) :: Nil
      case x: scala.meta.Name =>
        FindResult.Use(
          value = x.value,
          path = path,
        ) :: Nil
    }.flatten
  }
}
