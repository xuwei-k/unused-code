# Unused Code

[![maven](https://img.shields.io/maven-central/v/com.github.xuwei-k/unused-code-scalafix_2.13)](https://search.maven.org/artifact/com.github.xuwei-k/unused-code-scalafix_2.13)
[![scaladoc](https://javadoc.io/badge2/com.github.xuwei-k/unused-code-scalafix_2.13/javadoc.svg)](https://javadoc.io/doc/com.github.xuwei-k/unused-code-scalafix_2.13/latest/unused_code/index.html)

find and warn, remove unused public classes, methods by scalafix SyntacticRule.

## setup

### `project/plugins.sbt`

```scala
addSbtPlugin("com.github.xuwei-k" % "unused-code-plugin" % "version")
```

### sbt shell

```
> unusedCode
```

and then

```
> scalafix WarnUnusedCode
```

or

```
> scalafix ErrorUnusedCode
```

or

```
> scalafix RemoveUnusedCode
```

### config example

`build.sbt`

```scala
import scala.concurrent.duration.*

ThisBuild / unusedCodeConfig ~= { c =>
  c.copy(
    excludeNameRegex = Set(
      ".*Server"
    ),
    excludePath = c.excludePath ++ Set(
      "glob:some-project/**"
    ),
    excludeGitLastCommit = Some(
      365.days
    ),
    excludeMainMethod = false,
    dialect = unused_code.Dialect.Scala3,
  )
}
```
