/*
 * Copyright (c) 2012-15 Lars Hupel, Miles Sabin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package shapeless

import scala.language.experimental.macros

import scala.annotation.{ StaticAnnotation, tailrec }
import scala.reflect.api.Universe
import scala.reflect.macros.Context

trait Generic[T] {
  type Repr
  def to(t : T) : Repr
  def from(r : Repr) : T
}

object Generic {
  type Aux[T, Repr0] = Generic[T] { type Repr = Repr0 }

  def apply[T](implicit gen: Generic[T]): Aux[T, gen.Repr] = gen

  implicit def materialize[T, R]: Aux[T, R] = macro GenericMacros.materialize[T, R]
}

trait LabelledGeneric[T] extends Generic[T]

object LabelledGeneric {
  type Aux[T, Repr0] = LabelledGeneric[T]{ type Repr = Repr0 }

  def apply[T](implicit lgen: LabelledGeneric[T]): Aux[T, lgen.Repr] = lgen

  implicit def materialize[T, R]: Aux[T, R] = macro GenericMacros.materializeLabelled[T, R]
}

class nonGeneric extends StaticAnnotation

trait IsTuple[T]

object IsTuple {
  implicit def apply[T]: IsTuple[T] = macro GenericMacros.mkIsTuple[T]
}

trait HasProductGeneric[T]

object HasProductGeneric {
  implicit def apply[T]: HasProductGeneric[T] = macro GenericMacros.mkHasProductGeneric[T]
}

trait HasCoproductGeneric[T]

object HasCoproductGeneric {
  implicit def apply[T]: HasCoproductGeneric[T] = macro GenericMacros.mkHasCoproductGeneric[T]
}

trait CaseClassMacros {
  val c: Context

  import c.universe._
  import Flag._

  def hlistTpe = typeOf[HList]
  def hnilTpe = typeOf[HNil]
  def hconsTpe = typeOf[::[_, _]].typeConstructor
  def coproductTpe = typeOf[Coproduct]
  def cnilTpe = typeOf[CNil]
  def cconsTpe = typeOf[:+:[_, _]].typeConstructor

  def atatTpe = typeOf[tag.@@[_,_]].typeConstructor
  def fieldTypeTpe = typeOf[shapeless.labelled.FieldType[_, _]].typeConstructor

  def abort(msg: String) =
    c.abort(c.enclosingPosition, msg)

  def isReprType(tpe: Type): Boolean =
    tpe <:< hlistTpe || tpe <:< coproductTpe

  def isProduct(tpe: Type): Boolean =
    tpe =:= typeOf[Unit] || (tpe.typeSymbol.isClass && isCaseClassLike(classSym(tpe)))

  def isCoproduct(tpe: Type): Boolean = {
    val sym = tpe.typeSymbol
    if(!sym.isClass) false
    else {
      val sym = classSym(tpe)
      (sym.isTrait || sym.isAbstractClass) && sym.isSealed
    }
  }

  def ownerChain(sym: Symbol): List[Symbol] = {
    @tailrec
    def loop(sym: Symbol, acc: List[Symbol]): List[Symbol] =
      if(sym.owner == NoSymbol) acc
      else loop(sym.owner, sym :: acc)

    loop(sym, Nil)
  }

  def mkDependentRef(prefix: Type, path: List[Name]): (Type, Symbol) = {
    val (_, pre, sym) =
      path.foldLeft((prefix, NoType, NoSymbol)) {
        case ((pre, _, sym), nme) =>
          val sym0 = pre.member(nme)
          val pre0 = sym0.typeSignature
          (pre0, pre, sym0)
      }
    (pre, sym)
  }

  def fieldsOf(tpe: Type): List[(TermName, Type)] =
    tpe.declarations.toList collect {
      case sym: TermSymbol if isCaseAccessorLike(sym) =>
        val NullaryMethodType(restpe) = sym.typeSignatureIn(tpe)
        (sym.name.toTermName, restpe)
    }

  def ctorsOf(tpe: Type): List[Type] = {
    def collectCtors(classSym: ClassSymbol): List[ClassSymbol] = {
      classSym.knownDirectSubclasses.toList flatMap { child0 =>
        val child = child0.asClass
        child.typeSignature // Workaround for <https://issues.scala-lang.org/browse/SI-7755>
        if (isCaseClassLike(child))
          List(child)
        else if (child.isSealed)
          collectCtors(child)
        else
          abort(s"$child is not case class like or a sealed trait")
      }
    }

    if(isProduct(tpe))
      List(tpe)
    else if(isCoproduct(tpe)) {
      val ctors = collectCtors(classSym(tpe)).sortBy(_.fullName)
      if (ctors.isEmpty) abort(s"Sealed trait $tpe has no case class subtypes")

      // We're using an extremely optimistic strategy here, basically ignoring
      // the existence of any existential types.
      val baseTpe: TypeRef = tpe.normalize match {
        case tr: TypeRef => tr
        case _ => abort(s"bad type $tpe")
      }

      val tpePrefix = prefix(tpe)

      ctors map { sym =>
        val suffix = ownerChain(sym).dropWhile(_ != tpePrefix.typeSymbol)
        if(suffix.isEmpty) {
          if(sym.isModuleClass) {
            val moduleSym = sym.asClass.module
            val modulePre = prefix(moduleSym.typeSignature)
            singleType(modulePre, moduleSym)
          } else {
            val subTpeSym = sym.asType
            val subTpePre = prefix(subTpeSym.typeSignature)
            typeRef(subTpePre, subTpeSym, baseTpe.args)
          }
        } else {
          if(sym.isModuleClass) {
            val path = suffix.tail.map(_.name.toTermName)
            val (modulePre, moduleSym) = mkDependentRef(tpePrefix, path)
            singleType(modulePre, moduleSym)
          } else {
            val path = suffix.tail.init.map(_.name.toTermName) :+ suffix.last.name.toTypeName
            val (subTpePre, subTpeSym) = mkDependentRef(tpePrefix, path)
            typeRef(subTpePre, subTpeSym, baseTpe.args)
          }
        }
      }
    }
    else
      abort(s"$tpe is not a case class, case class-like, a sealed trait or Unit")
  }

  def nameAsValue(name: Name): Constant = Constant(name.decodedName.toString.trim)

  def nameOf(tpe: Type) = tpe.typeSymbol.name

  def mkCompoundTpe(nil: Type, cons: Type, items: List[Type]): Type =
    items.foldRight(nil) { case (tpe, acc) => appliedType(cons, List(tpe, acc)) }

  def mkFieldTpe(name: Name, valueTpe: Type): Type = {
    val keyTpe = appliedType(atatTpe, List(typeOf[scala.Symbol], ConstantType(nameAsValue(name))))
    appliedType(fieldTypeTpe, List(keyTpe, valueTpe))
  }

  def mkHListTpe(items: List[Type]): Type =
    mkCompoundTpe(hnilTpe, hconsTpe, items)

  def mkRecordTpe(fields: List[(TermName, Type)]): Type =
    mkCompoundTpe(hnilTpe, hconsTpe, fields.map((mkFieldTpe _).tupled))

  def mkCoproductTpe(items: List[Type]): Type =
    mkCompoundTpe(cnilTpe, cconsTpe, items)

  def mkUnionTpe(fields: List[(TermName, Type)]): Type =
    mkCompoundTpe(cnilTpe, cconsTpe, fields.map((mkFieldTpe _).tupled))

  def unfoldCompoundTpe(compoundTpe: Type, nil: Type, cons: Type): List[Type] = {
    @tailrec
    def loop(tpe: Type, acc: List[Type]): List[Type] =
      tpe.normalize match {
        case TypeRef(_, consSym, List(hd, tl))
          if consSym.asType.toType.typeConstructor =:= cons => loop(tl, hd :: acc)
        case `nil` => acc
        case other => abort(s"Bad compound type $compoundTpe")
      }
    loop(compoundTpe, Nil).reverse
  }

  def hlistElements(tpe: Type): List[Type] =
    unfoldCompoundTpe(tpe, hnilTpe, hconsTpe)

  def coproductElements(tpe: Type): List[Type] =
    unfoldCompoundTpe(tpe, cnilTpe, cconsTpe)

  def reprTpe(tpe: Type, labelled: Boolean): Type = {
    if(isProduct(tpe)) {
      val fields = fieldsOf(tpe)
      if(labelled)
        mkRecordTpe(fields)
      else
        mkHListTpe(fields.map(_._2))
    } else {
      val ctors = ctorsOf(tpe)
      if(labelled) {
        val labelledCases = ctors.map(tpe => (nameOf(tpe).toTermName, tpe))
        mkUnionTpe(labelledCases)
      } else
        mkCoproductTpe(ctors)
    }
  }

  def isCaseClassLike(sym: ClassSymbol): Boolean =
    sym.isCaseClass ||
    (!sym.isAbstractClass && !sym.isTrait && sym.knownDirectSubclasses.isEmpty && fieldsOf(sym.typeSignature).nonEmpty)

  def isCaseObjectLike(sym: ClassSymbol): Boolean = sym.isModuleClass && isCaseClassLike(sym)

  def isCaseAccessorLike(sym: TermSymbol): Boolean =
    !isNonGeneric(sym) && sym.isPublic && (if(sym.owner.asClass.isCaseClass) sym.isCaseAccessor else sym.isAccessor)

  def isSealedHierarchyClassSymbol(symbol: ClassSymbol): Boolean = {
    def helper(classSym: ClassSymbol): Boolean = {
      classSym.knownDirectSubclasses.toList forall { child0 =>
        val child = child0.asClass
        child.typeSignature // Workaround for <https://issues.scala-lang.org/browse/SI-7755>

        isCaseClassLike(child) || (child.isSealed && helper(child))
      }
    }

    symbol.isSealed && helper(symbol)
  }

  def classSym(tpe: Type): ClassSymbol = {
    val sym = tpe.typeSymbol
    if (!sym.isClass)
      abort(s"$sym is not a class or trait")

    val classSym = sym.asClass
    classSym.typeSignature // Workaround for <https://issues.scala-lang.org/browse/SI-7755>

    classSym
  }

  def mkAttributedRef(pre: Type, sym: Symbol): Tree = {
    val global = c.universe.asInstanceOf[scala.tools.nsc.Global]
    val gPre = pre.asInstanceOf[global.Type]
    val gSym = sym.asInstanceOf[global.Symbol]
    global.gen.mkAttributedRef(gPre, gSym).asInstanceOf[Tree]
  }

  // See https://github.com/milessabin/shapeless/issues/212
  def companionRef(tpe: Type): Tree = {
    val global = c.universe.asInstanceOf[scala.tools.nsc.Global]
    val gTpe = tpe.asInstanceOf[global.Type]
    val pre = gTpe.prefix
    val sym = gTpe.typeSymbol.companionSymbol
    global.gen.mkAttributedRef(pre, sym).asInstanceOf[Tree]
  }

  def prefix(tpe: Type): Type = {
    val global = c.universe.asInstanceOf[scala.tools.nsc.Global]
    val gTpe = tpe.asInstanceOf[global.Type]
    gTpe.prefix.asInstanceOf[Type]
  }

  def isNonGeneric(sym: Symbol): Boolean = {
    def check(sym: Symbol): Boolean = {
      // See https://issues.scala-lang.org/browse/SI-7424
      sym.typeSignature                   // force loading method's signature
      sym.annotations.foreach(_.tpe) // force loading all the annotations

      sym.annotations.exists(_.tpe =:= typeOf[nonGeneric])
    }

    // See https://issues.scala-lang.org/browse/SI-7561
    check(sym) ||
    (sym.isTerm && sym.asTerm.isAccessor && check(sym.asTerm.accessed)) ||
    sym.allOverriddenSymbols.exists(isNonGeneric)
  }

  def isTuple(tpe: Type): Boolean =
    tpe <:< typeOf[Unit] || definitions.TupleClass.seq.contains(tpe.typeSymbol)
}

class GenericMacros[C <: Context](val c: C) extends CaseClassMacros {
  import c.universe._
  import Flag._

  def materialize[T: WeakTypeTag, R: WeakTypeTag] =
    materializeAux(false, weakTypeOf[T])

  def materializeLabelled[T: WeakTypeTag, R: WeakTypeTag] =
    materializeAux(true, weakTypeOf[T])

  def materializeAux(labelled: Boolean, tpe: Type): Tree = {
    if(isReprType(tpe))
      abort("No Generic instance available for HList or Coproduct")

    def mkElem(elem: Tree, name: Name, tpe: Type): Tree =
      if(labelled) q"$elem.asInstanceOf[${mkFieldTpe(name, tpe)}]" else elem

    def mkCoproductCases(tpe: Type, index: Int): (CaseDef, CaseDef) = {
      val name = newTermName(c.fresh("pat"))

      def mkCoproductValue(tree: Tree): Tree =
        (0 until index).foldLeft(q"_root_.shapeless.Inl($tree)": Tree) {
          case (acc, _) => q"_root_.shapeless.Inr($acc)"
        }

      if(isCaseObjectLike(tpe.typeSymbol.asClass)) {
        val singleton =
          tpe match {
            case SingleType(pre, sym) =>
              mkAttributedRef(pre, sym)
            case TypeRef(pre, sym, List()) if sym.isModule =>
              mkAttributedRef(pre, sym.asModule)
            case TypeRef(pre, sym, List()) if sym.isModuleClass =>
              mkAttributedRef(pre, sym.asClass.module)
            case other =>
              abort(s"Bad case object-like type $tpe")
          }

        val body = mkCoproductValue(mkElem(q"$name.asInstanceOf[$tpe]", nameOf(tpe), tpe))
        val pat = mkCoproductValue(pq"$name")
        (
          cq"$name if $name eq $singleton => $body",
          cq"$pat => $singleton: $tpe"
        )
      } else {
        val body = mkCoproductValue(mkElem(q"$name: $tpe", nameOf(tpe), tpe))
        val pat = mkCoproductValue(pq"$name")
        (
          cq"$name: $tpe => $body",
          cq"$pat => $name"
        )
      }
    }

    def mkProductCases(tpe: Type): (CaseDef, CaseDef) = {
      def mkCase(lhs: Tree, rhs: Tree) = cq"$lhs => $rhs"

      if(tpe =:= typeOf[Unit])
        (
          cq"() => _root_.shapeless.HNil",
          cq"_root_.shapeless.HNil => ()"
        )
      else if(isCaseObjectLike(tpe.typeSymbol.asClass)) {
        val singleton =
          tpe match {
            case SingleType(pre, sym) =>
              mkAttributedRef(pre, sym)
            case TypeRef(pre, sym, List()) if sym.isModule =>
              mkAttributedRef(pre, sym.asModule)
            case TypeRef(pre, sym, List()) if sym.isModuleClass =>
              mkAttributedRef(pre, sym.asClass.module)
            case other =>
              abort(s"Bad case object-like type $tpe")
          }

        (
          cq"_: $tpe => _root_.shapeless.HNil",
          cq"_root_.shapeless.HNil => $singleton: $tpe"
        )
      } else {
        val sym = tpe.typeSymbol
        val isCaseClass = sym.asClass.isCaseClass
        def hasNonGenericCompanionMember(name: String): Boolean = {
          val mSym = sym.companionSymbol.typeSignature.member(newTermName(name))
          mSym != NoSymbol && !isNonGeneric(mSym)
        }

        val binders = fieldsOf(tpe).map { case (name, tpe) => (newTermName(c.fresh("pat")), name, tpe) }

        val to =
          if(isCaseClass || hasNonGenericCompanionMember("unapply")) {
            val lhs = pq"${companionRef(tpe)}(..${binders.map(x => pq"${x._1}")})"
            val rhs =
              binders.foldRight(q"_root_.shapeless.HNil": Tree) {
                case ((bound, name, tpe), acc) =>
                  val elem = mkElem(q"$bound", name, tpe)
                  q"_root_.shapeless.::($elem, $acc)"
              }
            cq"$lhs => $rhs"
          } else {
            val lhs = newTermName(c.fresh("pat"))
            val rhs =
              fieldsOf(tpe).foldRight(q"_root_.shapeless.HNil": Tree) {
                case ((name, tpe), acc) =>
                  val elem = mkElem(q"$lhs.$name", name, tpe)
                  q"_root_.shapeless.::($elem, $acc)"
              }
            cq"$lhs => $rhs"
          }

        val from = {
          val lhs =
            binders.foldRight(q"_root_.shapeless.HNil": Tree) {
              case ((bound, _, _), acc) => pq"_root_.shapeless.::($bound, $acc)"
            }

          val rhs = {
            val ctorArgs = binders.map { case (bound, name, tpe) => mkElem(Ident(bound), name, tpe) }
            if(isCaseClass || hasNonGenericCompanionMember("apply"))
              q"${companionRef(tpe)}(..$ctorArgs)"
            else
              q"new $tpe(..$ctorArgs)"
          }

          cq"$lhs => $rhs"
        }

        (to, from)
      }
    }

    val (toCases, fromCases) =
      if(isProduct(tpe)) {
        val (to, from) = mkProductCases(tpe)
        (List(to), List(from))
      } else {
        val (to, from) = (ctorsOf(tpe) zip (Stream from 0) map (mkCoproductCases _).tupled).unzip
        (to, from :+ cq"_ => _root_.scala.Predef.???")
      }

    val genericTypeConstructor =
      (if(labelled) typeOf[LabelledGeneric[_]].typeConstructor
       else typeOf[Generic[_]].typeConstructor).typeSymbol

    val clsName = newTypeName(c.fresh())
    q"""
      final class $clsName extends $genericTypeConstructor[$tpe] {
        type Repr = ${reprTpe(tpe, labelled)}
        def to(p: $tpe): Repr = p match { case ..$toCases }
        def from(p: Repr): $tpe = p match { case ..$fromCases }
      }
      new $clsName()
    """
  }

  def mkIsTuple[T: WeakTypeTag]: Tree = {
    val tTpe = weakTypeOf[T]
    if(!isTuple(tTpe))
      abort(s"Unable to materialize IsTuple for non-tuple type $tTpe")

    q"""new IsTuple[$tTpe] {}"""
  }

  def mkHasProductGeneric[T: WeakTypeTag]: Tree = {
    val tTpe = weakTypeOf[T]
    if(isReprType(tTpe) || !isProduct(tTpe))
      abort(s"Unable to materialize HasProductGeneric for $tTpe")

    q"""new HasProductGeneric[$tTpe] {}"""
  }

  def mkHasCoproductGeneric[T: WeakTypeTag]: Tree = {
    val tTpe = weakTypeOf[T]
    if(isReprType(tTpe) || !isCoproduct(tTpe))
      abort(s"Unable to materialize HasCoproductGeneric for $tTpe")

    q"""new HasCoproductGeneric[$tTpe] {}"""
  }
}

object GenericMacros {
  def inst(c: Context) = new GenericMacros[c.type](c)

  def materialize[T: c.WeakTypeTag, R: c.WeakTypeTag](c: Context): c.Expr[Generic.Aux[T, R]] =
    c.Expr[Generic.Aux[T, R]](inst(c).materialize[T, R])

  def materializeLabelled[T: c.WeakTypeTag, R: c.WeakTypeTag](c: Context): c.Expr[LabelledGeneric.Aux[T, R]] =
    c.Expr[LabelledGeneric.Aux[T, R]](inst(c).materializeLabelled[T, R])

  def mkIsTuple[T: c.WeakTypeTag](c: Context): c.Expr[IsTuple[T]] =
    c.Expr[IsTuple[T]](inst(c).mkIsTuple[T])

  def mkHasProductGeneric[T: c.WeakTypeTag](c: Context): c.Expr[HasProductGeneric[T]] = 
    c.Expr[HasProductGeneric[T]](inst(c).mkHasProductGeneric[T])

  def mkHasCoproductGeneric[T: c.WeakTypeTag](c: Context): c.Expr[HasCoproductGeneric[T]] =
    c.Expr[HasCoproductGeneric[T]](inst(c).mkHasCoproductGeneric[T])
}
