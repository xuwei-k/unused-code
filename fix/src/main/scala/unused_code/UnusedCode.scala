package unused_code

import metaconfig.generic.Surface
import metaconfig.Conf
import metaconfig.ConfDecoder
import metaconfig.ConfEncoder
import metaconfig.Hocon
import java.io.File
import java.nio.charset.StandardCharsets
import scala.sys.process.Process
import scala.meta.Defn
import scala.meta.Mod
import scala.meta.Pat
import scala.meta.Pkg
import scala.meta.Source
import scala.meta.Term
import scala.meta.Tree
import scala.meta.Type
import java.nio.file.Files
import java.nio.file.Paths
import scala.annotation.nowarn
import scala.concurrent.duration.*
import scala.meta.inputs.Input
import scala.meta.parsers.Parse

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
    val empty = UnusedCodeConfig(
      files = Nil,
      scalafixConfigPath = None,
      excludeNameRegex = Set.empty,
      excludePath = Set.empty,
      excludeGitLastCommit = None,
      excludeMainMethod = true,
      dialect = Dialect.Scala213Source3,
    )
    metaconfig.generic.deriveDecoder[UnusedCodeConfig](empty)
  }

  private[unused_code] def jsonToConfig(json: String): UnusedCodeConfig = {
    val c = Conf.parseString(json)(Hocon)
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

  def main(arg: String): String = {
    val conf = jsonToConfig(arg)
    val result = conf.files.flatMap { file =>
      val input = Input.File(new File(file))
      val dialect = convertDialect.getOrElse(conf.dialect, scala.meta.dialects.Scala213)
      val tree = implicitly[Parse[Source]].apply(input, dialect).get
      run(tree, file, conf.excludeMainMethod)
    }
    writeResult(result, conf)
  }

  private[unused_code] val extractDefineValue: PartialFunction[Tree, (Tree, List[Mod], String)] = {
    case x: Defn.Trait =>
      (x, x.mods, x.name.value)
    case x: Defn.Class =>
      (x, x.mods, x.name.value)
    case x: Defn.Object =>
      (x, x.mods, x.name.value)
    case x: Pkg.Object =>
      (x, x.mods, x.name.value)
    case x: Defn.Def =>
      (x, x.mods, x.name.value)
    case x: Defn.Enum =>
      (x, x.mods, x.name.value)
    case x: Defn.EnumCase =>
      (x, x.mods, x.name.value)
    case x @ Defn.Val(_, List(Pat.Var(name)), _, _) =>
      (x, x.mods, name.value)
    case x @ Defn.Var(_, List(Pat.Var(name)), _, _) =>
      (x, x.mods, name.value)
  }

  private[this] object DefineValue {
    def unapply(t: Tree): Option[(Tree, List[Mod], String)] = extractDefineValue.lift.apply(t)
  }

  @nowarn("msg=lineStream")
  private[this] def lastGitCommitMilliSeconds(path: String): Long = {
    val default = 0L
    // https://git-scm.com/docs/git-log
    if (new File(".git").isDirectory) {
      Process(s"git log -1 --format=%ct ${path}").lineStream_!.headOption.map(_.toLong * 1000L).getOrElse(default)
    } else {
      default
    }
  }

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
            duration < (currentMillis - lastGitCommitMilliSeconds(x.path)).millis
          case _ =>
            true
        }
      )
      .toList
      .sortBy(x => (x.path, x.value))
  }

  private[this] def writeResult(values: Seq[FindResult], config: UnusedCodeConfig): String = {
    val result = FindResults(aggregate(values = values, config = config))
    val json = implicitly[ConfEncoder[FindResults]].write(result).show
    val bytes = json.getBytes(StandardCharsets.UTF_8)
    val path = {
      val scalafixConfig = new File(config.scalafixConfigPath.getOrElse(defaultScalafixConfigFile))
      Paths.get {
        if (scalafixConfig.isFile) {
          Conf.parseFile(scalafixConfig)(Hocon).get.as[UnusedCodeScalafixConfig].get.outputPath
        } else {
          UnusedCodeScalafixConfig.default.outputPath
        }
      }
    }
    Files.createDirectories(path.getParent)
    Files.write(path, bytes)
    json
  }

  private[unused_code] val isMainMethod: PartialFunction[Tree, Unit] = {
    case x: Defn.Def if x.mods.collect { case a: Mod.Annot => a.init.tpe }.collectFirst { case Type.Name("main") =>
          ()
        }.isDefined =>
      ()
    case Defn.Def(
          _,
          Term.Name("main"),
          Nil,
          List(
            List(
              Term.Param(
                _,
                _,
                Some(
                  Type.Apply(
                    Type.Name("Array") | Type.Select(Term.Name("scala"), Type.Name("Array")) |
                    Type.Select(Term.Select(Term.Name("_root_"), Term.Name("scala")), Type.Name("Array")),
                    List(
                      Type.Name("String") | Type.Select(Term.Name("Predef"), Type.Name("String")) |
                      Type.Select(Term.Select(Term.Name("java"), Term.Name("lang")), Type.Name("String")),
                    )
                  )
                ),
                _
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

  private[this] def filterMods(mods: List[Mod]): Boolean = {
    !mods.exists(m => m.is[Mod.Implicit] || m.is[Mod.Override] || m.is[Mod.Inline])
  }

  private[this] def run(tree: Tree, path: String, excludeMainMethod: Boolean): List[FindResult] = {
    tree.collect {
      case x: Defn.Object if filterMods(x.mods) =>
        if (excludeMainMethod && hasMainMethod(x)) {
          Nil
        } else {
          FindResult.Define(
            value = x.name.value,
          ) :: Nil
        }
      case x: Defn.Def if filterMods(x.mods) =>
        if (excludeMainMethod && isMainMethod.isDefinedAt(x)) {
          Nil
        } else {
          FindResult.Define(
            value = x.name.value,
          ) :: Nil
        }
      case DefineValue(_, mods, name) if filterMods(mods) =>
        FindResult.Define(
          value = name,
        ) :: Nil
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
