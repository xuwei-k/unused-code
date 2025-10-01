package unused_code

import scalafix.v1.XtensionSeqPatch
import scala.meta.XtensionCollectionLikeUI
import metaconfig.Configured
import scalafix.Patch
import scalafix.v1.Configuration
import scalafix.v1.Rule
import scalafix.v1.SyntacticDocument
import scalafix.v1.SyntacticRule
import scala.meta.Position
import scalafix.Diagnostic
import scalafix.lint.LintSeverity
import java.nio.file.Paths

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
