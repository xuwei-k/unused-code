package unused_code

import scala.meta.Stat.WithMods
import scala.meta.Init
import scala.meta.Mod
import scala.meta.Name
import scala.meta.Term
import scala.meta.Tree
import scala.meta.Type
import scala.meta.contrib.XtensionTreeOps

private[unused_code] object CheckUnusedAnnotation {
  object UnusedAnnotation {
    def unapply(m: Mod): Boolean = PartialFunction.cond(m) {
      case Mod.Annot(
            Init.After_4_6_0(
              Type.Select(
                Term.Select(
                  Term.Select(
                    Term.Name("_root_"),
                    Term.Name("scala")
                  ),
                  Term.Name("annotation")
                ),
                Type.Name("unused")
              ) | Type.Select(
                Term.Select(
                  Term.Name("scala"),
                  Term.Name("annotation")
                ),
                Type.Name("unused")
              ) | Type.Select(
                Term.Name("annotation"),
                Type.Name("unused")
              ) | Type.Name("unused"),
              Name.Anonymous(),
              Nil
            )
          ) =>
        true
    }
  }

  def exists(x: Tree): Boolean = {
    x.collectFirst { case UnusedAnnotation() => () }.nonEmpty || loopParent(x)
  }

  private def loopParent(x: Tree): Boolean = {
    x match {
      case y: WithMods =>
        y.mods.exists(UnusedAnnotation.unapply) || x.parent.exists(loopParent)
      case _ =>
        x.parent.exists(loopParent)
    }
  }
}
