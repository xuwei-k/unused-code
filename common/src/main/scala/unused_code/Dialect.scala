package unused_code

/**
 * [[https://github.com/scalameta/scalameta/blob/v4.11.0/scalameta/dialects/shared/src/main/scala/scala/meta/Dialect.scala]]
 */
sealed abstract class Dialect(val value: String) extends Product with Serializable

object Dialect {
  case object Scala210 extends Dialect("Scala210")
  case object Scala211 extends Dialect("Scala211")
  case object Scala212 extends Dialect("Scala212")
  case object Scala213 extends Dialect("Scala213")
  case object Scala212Source3 extends Dialect("Scala212Source3")
  case object Scala213Source3 extends Dialect("Scala213Source3")
  case object Scala3 extends Dialect("Scala3")

  val all: Seq[Dialect] = Seq(
    Scala210,
    Scala211,
    Scala212,
    Scala212Source3,
    Scala213,
    Scala213Source3,
    Scala3,
  )

  val map: Map[String, Dialect] = all.map(a => a.value -> a).toMap

  implicit val ordering: Ordering[Dialect] = Ordering.by(all.indexOf)
}
