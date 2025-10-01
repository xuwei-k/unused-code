package unused_code

import scalaprops.Gen
import scalaprops.Property
import scalaprops.Scalaprops
import UnusedCodePlugin.*
import org.scalatest.Assertions.*
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import scala.concurrent.duration.Duration

object UnusedCodeConfigTest extends Scalaprops {

  implicit val configGen: Gen[UnusedCodeConfig] = {
    implicit val s: Gen[String] = Gen.alphaNumString
    implicit val dialect: Gen[Dialect] = {
      val x +: xs = Dialect.all
      Gen.elements(x, xs *)
    }
    implicit val duration: Gen[Duration] = {
      val x +: xs = for {
        a <- 1 to 1000
        b <- Seq("days", "hours", "seconds", "millis")
      } yield Duration(s"${a}.${b}")
      Gen.elements(x, xs *)
    }
    implicit val localDate: Gen[java.time.LocalDate] = {
      for {
        y <- Gen.choose(1900, 2100)
        m <- Gen.choose(1, 12)
        d <- Gen.choose(1, 28)
      } yield java.time.LocalDate.of(y, m, d)
    }
    Gen.from10(UnusedCodeConfig.apply)
  }

  val test = Property.forAll { (c1: UnusedCodeConfig) =>
    val tmp = Files.createTempFile("", ".json")
    try {
      Files.write(tmp, c1.toJsonString.getBytes(StandardCharsets.UTF_8))
      val c2 = UnusedCode.jsonFileToConfig(tmp.toFile)
      assert(c1 == c2)
      true
    } finally {
      Files.deleteIfExists(tmp)
    }
  }

}
