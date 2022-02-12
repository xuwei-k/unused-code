package unused_code

import org.scalatest.funsuite.AnyFunSuite
import scala.meta.inputs.Input
import scala.meta.Stat
import scala.meta.parsers.Parse

class UnusedCodeTest extends AnyFunSuite {
  test("isMainMethod") {
    Seq(
      "@main def foo(a1: String) = {}",
      "def main(a1: Array[String]) = {}",
      "def main(a2: Array[String]): Unit = {}",
      "def main(a3: Array[String]): scala.Unit = {}",
      "def main(args: Array[String]): _root_.scala.Unit = {}",
      "final def main(b1: Array[String]): Unit = {}",
      "def main(args: scala.Array[String]): Unit = {}",
      "def main(args: _root_.scala.Array[String]): Unit = {}",
      "def main(args: Array[Predef.String]): Unit = {}",
      "def main(args: Array[java.lang.String]): Unit = {}",
    ).foreach { str =>
      val input = Input.String(str)
      val tree = implicitly[Parse[Stat]].apply(input, scala.meta.dialects.Scala213).get
      assert(UnusedCode.isMainMethod.isDefinedAt(tree), str)
    }

    Seq(
      "def test(args: Array[String]): Unit = {}",
      "def main(args: List[String]): Unit = {}",
      "def main(args: Array[String]): Int = {}",
      "def main(args: Array[String]): String = {}",
    ).foreach { str =>
      val input = Input.String(str)
      val tree = implicitly[Parse[Stat]].apply(input, scala.meta.dialects.Scala213).get
      assert(!UnusedCode.isMainMethod.isDefinedAt(tree), str)
    }
  }
}
