package unused_code

import scalafix.v1.XtensionSeqPatch
import metaconfig.Configured
import scalafix.Patch
import scalafix.v1.Configuration
import scalafix.v1.Rule
import scalafix.v1.SyntacticDocument
import scalafix.v1.SyntacticRule
import java.io.File
import java.nio.file.Paths
import scala.meta.XtensionCollectionLikeUI
import scala.meta.XtensionClassifiable
import scala.meta.inputs.Input
import scala.meta.Defn
import scala.meta.Pkg
import scala.meta.Source
import scala.meta.Tree

class RemoveUnusedCode(config: UnusedCodeScalafixConfig) extends SyntacticRule("RemoveUnusedCode") {

  def this() = this(UnusedCodeScalafixConfig.default)

  override def withConfiguration(config: Configuration): Configured[Rule] = {
    config.conf
      .getOrElse(UnusedCodeScalafixConfig.configKey)(this.config)
      .map(newConfig => new RemoveUnusedCode(newConfig))
  }

  private[this] lazy val unusedNames: Set[String] = {
    FindResults.loadFromFile(Paths.get(config.outputPath)).values.map(_.value).toSet
  }

  private[this] def isTopLevel(t: Tree) = {
    t.parent.exists(_.is[Pkg.Body]) || t.parent.exists(_.is[Source])
  }

  override def fix(implicit doc: SyntacticDocument): Patch = {
    val removeAll = doc.tree.collect { case src: Source =>
      src
    }.exists { src =>
      // https://github.com/scala/scala3/blob/93af7b8c7dde6b5a4c29/compiler/src/dotty/tools/dotc/parsing/Parsers.scala#L4601-L4605
      // https://github.com/scala/scala3/blob/93af7b8c7dde6b5a4c29/compiler/src/dotty/tools/dotc/parsing/Parsers.scala#L254-L258
      // https://github.com/scala/scala3/blob/93af7b8c7dde6b5a4c29/compiler/src/dotty/tools/dotc/parsing/Tokens.scala#L244-L248
      val topLevelValues = src.collect {
        case t: Defn.Class if isTopLevel(t) => t
        case t: Defn.Def if isTopLevel(t) => t
        case t: Defn.Enum if isTopLevel(t) => t
        case t: Defn.ExtensionGroup if isTopLevel(t) => t
        case t: Defn.Given if isTopLevel(t) => t
        case t: Defn.GivenAlias if isTopLevel(t) => t
        case t: Defn.Object if isTopLevel(t) => t
        case t: Defn.Trait if isTopLevel(t) => t
        case t: Defn.Type if isTopLevel(t) => t
        case t: Defn.Val if isTopLevel(t) => t
        case t: Defn.Var if isTopLevel(t) => t
        case t: Pkg.Object if isTopLevel(t) => t
      }
      val removeTrees = src.collect(UnusedCode.extractDefineValue).collect {
        case (tree, _, name) if unusedNames.contains(name) =>
          tree
      }
      topLevelValues == removeTrees
    }
    def patch = {
      doc.tree
        .collect(UnusedCode.extractDefineValue)
        .collect {
          case (tree, _, name) if unusedNames.contains(name) =>
            Patch.removeTokens(tree.tokens)
        }
        .asPatch
    }
    if (removeAll) {
      PartialFunction.condOpt(doc.input) {
        case Input.File(path, _) =>
          path.toNIO.toFile
        case Input.VirtualFile(path, _) =>
          new File(path)
      } match {
        case Some(file) if config.removeFile =>
          file.delete()
          Patch.empty
        case _ =>
          patch
      }
    } else {
      patch
    }
  }
}
