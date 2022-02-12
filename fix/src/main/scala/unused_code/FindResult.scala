package unused_code

import metaconfig.generic.Surface
import metaconfig.ConfDecoder
import metaconfig.ConfEncoder

sealed abstract class FindResult extends Product with Serializable

object FindResult {
  final case class Define(value: String) extends FindResult
  final case class Use(value: String, path: String) extends FindResult
  object Use {
    private[this] val empty = Use("invalid_value", "invalid_path")
    implicit val surface: Surface[Use] = metaconfig.generic.deriveSurface[Use]
    implicit val encoder: ConfEncoder[Use] = metaconfig.generic.deriveEncoder[Use]
    implicit val decoder: ConfDecoder[Use] = metaconfig.generic.deriveDecoder[Use](empty)
  }
}
