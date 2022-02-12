package unused_code

import metaconfig.ConfDecoder
import metaconfig.generic.Surface

final case class UnusedCodeScalafixConfig(
  outputPath: String,
  removeFile: Boolean,
)

object UnusedCodeScalafixConfig {
  val default = UnusedCodeScalafixConfig(
    outputPath = "target/unused-code/unused.json",
    removeFile = true,
  )
  implicit val surface: Surface[UnusedCodeScalafixConfig] =
    metaconfig.generic.deriveSurface[UnusedCodeScalafixConfig]
  implicit val decoder: ConfDecoder[UnusedCodeScalafixConfig] =
    metaconfig.generic.deriveDecoder(default)

  def configKey: String = "UnusedCode"
}
