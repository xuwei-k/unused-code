package unused_code

import UnusedCodePlugin.*
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import org.scalatest.Assertions.*
import scala.concurrent.duration.Duration
import scalaprops.Gen
import scalaprops.Property
import scalaprops.Scalaprops

object UnusedCodeConfigTest extends Scalaprops {

  implicit val configGen: Gen[UnusedCodeConfig] = {
    implicit val s: Gen[String] = Gen.alphaNumString
    implicit val dialect: Gen[Dialect] = {
      val x +: xs = Dialect.all
      Gen.elements(x, xs*)
    }
    implicit val duration: Gen[Duration] = {
      val x +: xs = for {
        a <- 1 to 1000
        b <- Seq("days", "hours", "seconds", "millis")
      } yield Duration(s"${a}.${b}")
      Gen.elements(x, xs*)
    }
    implicit val zonedDateTime: Gen[ZonedDateTime] = {
      val zone = ZoneId.of("Asia/Tokyo")
      Gen
        .chooseLong(0L, (Int.MaxValue: Long) * 1000L)
        .map(Instant.ofEpochMilli)
        .map(
          ZonedDateTime.ofInstant(_, zone)
        )
    }
    Gen.from11(UnusedCodeConfig.apply)
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
