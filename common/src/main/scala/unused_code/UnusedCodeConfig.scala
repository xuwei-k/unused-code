package unused_code

import java.nio.file.FileSystems
import java.nio.file.PathMatcher
import java.time.LocalDate
import java.util.regex.Pattern
import scala.concurrent.duration.*

/**
 * @param files target files for find unused code
 * @param scalafixConfigPath `.scalafix.conf` file path
 * @param excludePath [[https://docs.oracle.com/javase/8/docs/api/java/nio/file/FileSystem.html#getPathMatcher-java.lang.String-]]
 * @param excludeGitLastCommit skip recently added or changed sources
 * @param excludeMainMethod don't remove main methods if true
 */
final case class UnusedCodeConfig(
  files: List[String],
  scalafixConfigPath: Option[String],
  excludeNameRegex: Set[String],
  excludePath: Set[String],
  excludeGitLastCommit: Option[Duration],
  excludeGitLastDate: Option[LocalDate],
  excludeMainMethod: Boolean,
  dialect: Dialect,
  excludeMethodRegex: Set[String],
  baseDir: String,
) {
  def pathMatchers: Seq[PathMatcher] = {
    val fs = FileSystems.getDefault
    excludePath.map(fs.getPathMatcher).toSeq
  }

  private[this] val regex: Seq[Pattern] = excludeNameRegex.map(Pattern.compile).toList
  private[this] val regexMethod: Seq[Pattern] = excludeMethodRegex.map(Pattern.compile).toList

  def isExcludeMethod(name: String): Boolean = regexMethod.exists { p =>
    p.matcher(name).matches
  }

  def isExcludeName(name: String): Boolean = regex.exists { p =>
    p.matcher(name).matches
  }
}
