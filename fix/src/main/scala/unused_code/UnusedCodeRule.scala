package unused_code

import java.nio.file.Paths
import metaconfig.Configured
import scala.meta.XtensionCollectionLikeUI
import scalafix.Diagnostic
import scalafix.Patch
import scalafix.lint.LintSeverity
import scalafix.v1.Configuration
import scalafix.v1.Rule
import scalafix.v1.SyntacticDocument
import scalafix.v1.SyntacticRule
import scalafix.v1.XtensionSeqPatch

sealed abstract class UnusedCodeRule(config: UnusedCodeScalafixConfig, name: String) extends SyntacticRule(name) {

  override final def withConfiguration(config: Configuration): Configured[Rule] = {
    config.conf.getOrElse(UnusedCodeScalafixConfig.configKey)(this.config).map(newConfig => newInstance(newConfig))
  }

  private[this] lazy val unusedNames: Set[String] = {
    FindResults.loadFromFile(Paths.get(config.outputPath)).values.map(_.value).toSet
  }

  protected def severity: LintSeverity
  protected def newInstance(config: UnusedCodeScalafixConfig): UnusedCodeRule

  override def fix(implicit doc: SyntacticDocument): Patch = {
    doc.tree
      .collect(UnusedCode.extractDefineValue)
      .collect {
        case (tree, _, treeName) if unusedNames.contains(treeName) && !CheckUnusedAnnotation.exists(tree) =>
          Patch.lint(
            Diagnostic(
              id = "",
              message = "maybe unused",
              position = tree.pos,
              severity = severity
            )
          )
      }
      .asPatch
  }
}

class WarnUnusedCode(config: UnusedCodeScalafixConfig) extends UnusedCodeRule(config, "WarnUnusedCode") {
  def this() = this(UnusedCodeScalafixConfig.default)
  override protected def severity: LintSeverity = LintSeverity.Warning
  override protected def newInstance(config: UnusedCodeScalafixConfig): UnusedCodeRule = new WarnUnusedCode(config)
}

class ErrorUnusedCode(config: UnusedCodeScalafixConfig) extends UnusedCodeRule(config, "ErrorUnusedCode") {
  def this() = this(UnusedCodeScalafixConfig.default)
  override protected def severity: LintSeverity = LintSeverity.Error
  override protected def newInstance(config: UnusedCodeScalafixConfig): UnusedCodeRule = new ErrorUnusedCode(config)
}
