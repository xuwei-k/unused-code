package unused_code

sealed abstract class WhenGitShallowRepository(val value: String) extends Product with Serializable
object WhenGitShallowRepository {
  case object Silent extends WhenGitShallowRepository("silent")
  case object Warning extends WhenGitShallowRepository("warning")
  case object Error extends WhenGitShallowRepository("error")

  private[unused_code] val all: Seq[WhenGitShallowRepository] = Seq(
    Silent,
    Warning,
    Error
  )

  val map: Map[String, WhenGitShallowRepository] = all.map(a => a.value -> a).toMap

  val default: WhenGitShallowRepository = Error
}
