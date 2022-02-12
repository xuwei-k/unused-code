package unused_code

import metaconfig.generic.Surface
import metaconfig.Conf
import metaconfig.ConfDecoder
import metaconfig.ConfEncoder
import metaconfig.Hocon
import java.nio.file.Files
import java.nio.file.Path

final case class FindResults(values: List[FindResult.Use])

object FindResults {
  private[this] val empty = FindResults(Nil)
  implicit val surface: Surface[FindResults] = metaconfig.generic.deriveSurface[FindResults]
  implicit val encoder: ConfEncoder[FindResults] = metaconfig.generic.deriveEncoder[FindResults]
  implicit val decoder: ConfDecoder[FindResults] = metaconfig.generic.deriveDecoder[FindResults](empty)

  def loadFromFile(file: Path): FindResults = {
    val str = new String(Files.readAllBytes(file), "UTF-8")
    Conf.parseString(str)(Hocon).get.as[FindResults].get
  }
}
