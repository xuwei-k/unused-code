package unused_code

import java.nio.file.Paths
import metaconfig.Configured
import scala.meta.Position
import scala.meta.XtensionCollectionLikeUI
import scalafix.Diagnostic
import scalafix.Patch
import scalafix.lint.LintSeverity
import scalafix.v1.Configuration
import scalafix.v1.Rule
import scalafix.v1.SyntacticDocument
import scalafix.v1.SyntacticRule
import scalafix.v1.XtensionSeqPatch

class WarnUnusedCode(config: UnusedCodeScalafixConfig) extends SyntacticRule("WarnUnusedCode") {
  def this() = this(UnusedCodeScalafixConfig.default)

  override def withConfiguration(config: Configuration): Configured[Rule] = {
    config.conf
      .getOrElse(UnusedCodeScalafixConfig.configKey)(this.config)
      .map(newConfig => new WarnUnusedCode(newConfig))
  }

  private[this] lazy val unusedNames: Set[String] = {
    FindResults.loadFromFile(Paths.get(config.outputPath)).values.map(_.value).toSet
  }

  override def fix(implicit doc: SyntacticDocument): Patch = {
    doc.tree
      .collect(UnusedCode.extractDefineValue)
      .collect {
        case (tree, _, name) if unusedNames.contains(name) && !CheckUnusedAnnotation.exists(tree) =>
          Patch.lint(UnusedClassWarning(tree.pos))
      }
      .asPatch
  }
}

final case class UnusedClassWarning(override val position: Position) extends Diagnostic {
  override def message = "maybe unused"
  override def severity: LintSeverity = LintSeverity.Warning
}
