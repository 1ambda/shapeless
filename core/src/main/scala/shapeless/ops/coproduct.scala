/*
 * Copyright (c) 2013 Miles Sabin 
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
package ops

import poly._

object coproduct {
  trait Inject[C <: Coproduct, I] {
    def apply(i: I): C
  }

  object Inject {
    def apply[C <: Coproduct, I](implicit inject: Inject[C, I]): Inject[C, I] = inject

    implicit def tlInject[H, T <: Coproduct, I](implicit tlInj : Inject[T, I]): Inject[H :+: T, I] = new Inject[H :+: T, I] {
      def apply(i: I): H :+: T = Inr(tlInj(i))
    }

    implicit def hdInject[H, T <: Coproduct]: Inject[H :+: T, H] = new Inject[H :+: T, H] {
      def apply(i: H): H :+: T = Inl(i)
    }
  }

  trait Selector[C <: Coproduct, T] {
    def apply(c: C): Option[T]
  }

  object Selector {
    def apply[C <: Coproduct, T](implicit select: Selector[C, T]): Selector[C, T] = select

    implicit def tlSelector1[H, T <: Coproduct, S](implicit st: Selector[T, S]): Selector[H :+: T, S] = new Selector[H :+: T, S] {
      def apply(c: H :+: T): Option[S] = c match {
        case Inl(h) => None
        case Inr(t) => st(t)
      }
    }

    implicit def hdSelector[H, T <: Coproduct](implicit st: Selector[T, H] = null): Selector[H :+: T, H] = new Selector[H :+: T, H] {
      def apply(c: H :+: T): Option[H] = c match {
        case Inl(h) => Some(h)
        case Inr(t) => if (st != null) st(t) else None
      }
    }
  }

  trait At[C <: Coproduct, N <: Nat] extends DepFn1[C] {
    type A
    type Out = Option[A]
  }

  object At {
    def apply[C <: Coproduct, N <: Nat](implicit at: At[C, N]): Aux[C, N, at.A] = at

    type Aux[C <: Coproduct, N <: Nat, A0] = At[C, N] { type A = A0 }

    implicit def coproductAt0[H, T <: Coproduct]: Aux[H :+: T, Nat._0, H] = new At[H :+: T, Nat._0] {
      type A = H

      def apply(c: H :+: T): Out = c match {
        case Inl(h) => Some(h)
        case _      => None
      }
    }

    implicit def coproductAtN[H, T <: Coproduct, N <: Nat](
      implicit att: At[T, N]
    ): Aux[H :+: T, Succ[N], att.A] = new At[H :+: T, Succ[N]] {
      type A = att.A

      def apply(c: H :+: T): Out = c match {
        case Inl(_)    => None
        case Inr(tail) => att(tail)
      }
    }
  }

  trait Mapper[F <: Poly, C <: Coproduct] extends DepFn1[C] { type Out <: Coproduct }

  object Mapper {
    def apply[F <: Poly, C <: Coproduct](implicit mapper: Mapper[F, C]): Aux[F, C, mapper.Out] = mapper
    def apply[C <: Coproduct](f: Poly)(implicit mapper: Mapper[f.type, C]): Aux[f.type, C, mapper.Out] = mapper

    type Aux[F <: Poly, C <: Coproduct, Out0 <: Coproduct] = Mapper[F, C] { type Out = Out0 }

    implicit def cnilMapper[F <: Poly]: Aux[F, CNil, CNil] = new Mapper[F, CNil] {
      type Out = CNil
      def apply(t: CNil): Out = t
    }

    implicit def cpMapper[F <: Poly, H, OutH, T <: Coproduct]
      (implicit fh: Case1.Aux[F, H, OutH], mt: Mapper[F, T]): Aux[F, H :+: T, OutH :+: mt.Out] =
        new Mapper[F, H :+: T] {
          type Out = OutH :+: mt.Out
          def apply(c: H :+: T): Out = c match {
            case Inl(h) => Inl(fh(h))
            case Inr(t) => Inr(mt(t))
          }
        }
  }

  trait Unifier[C <: Coproduct] extends DepFn1[C]

  object Unifier {
    def apply[C <: Coproduct](implicit unifier: Unifier[C]): Aux[C, unifier.Out] = unifier

    type Aux[C <: Coproduct, Out0] = Unifier[C] { type Out = Out0 }

    implicit def lstUnifier[H]: Aux[H :+: CNil, H] =
      new Unifier[H :+: CNil] {
        type Out = H
        def apply(c: H :+: CNil): Out = (c: @unchecked) match {
          case Inl(h) => h
        }
      }
    
    implicit def cpUnifier[H1, H2, T <: Coproduct, TL, L, Out0 >: L]
      (implicit u: Lub[H1, H2, L], lt: Aux[L :+: T, Out0]): Aux[H1 :+: H2 :+: T, Out0] =
        new Unifier[H1 :+: H2 :+: T] {
          type Out = Out0
          def apply(c: H1 :+: H2 :+: T): Out = c match {
            case Inl(h1) => u.left(h1)
            case Inr(Inl(h2)) => u.right(h2)
            case Inr(Inr(t)) => lt(Inr(t))
          }
        }
  }

  trait ZipWithKeys[K <: HList, V <: Coproduct] extends DepFn2[K, V] { type Out <: Coproduct }

  object ZipWithKeys {
    import shapeless.record._

    def apply[K <: HList, V <: Coproduct]
      (implicit zipWithKeys: ZipWithKeys[K, V]): Aux[K, V, zipWithKeys.Out] = zipWithKeys

    type Aux[K <: HList, V <: Coproduct, Out0 <: Coproduct] = ZipWithKeys[K, V] { type Out = Out0 }

    implicit val cnilZipWithKeys: Aux[HNil, CNil, CNil] = new ZipWithKeys[HNil, CNil] {
      type Out = CNil
      def apply(k: HNil, v: CNil) = v
    }

    implicit def cpZipWithKeys[KH, VH, KT <: HList, VT <: Coproduct] (implicit zipWithKeys: ZipWithKeys[KT, VT], wkh: Witness.Aux[KH])
        : Aux[KH :: KT, VH :+: VT, FieldType[KH, VH] :+: zipWithKeys.Out] =
          new ZipWithKeys[KH :: KT, VH :+: VT] {
            type Out = FieldType[KH, VH] :+: zipWithKeys.Out
            def apply(k: KH :: KT, v: VH :+: VT): Out = v match {
              case Inl(vh) => Inl(field[wkh.T](vh))
              case Inr(vt) => Inr(zipWithKeys(k.tail, vt))
            }
          }
  }

  /**
   * Type class supporting computing the type-level Nat corresponding to the length of this `Coproduct'.
   *
   * @author Stacy Curl
   */
  trait Length[C <: Coproduct] extends DepFn0 { type Out <: Nat }

  object Length {
    def apply[C <: Coproduct](implicit length: Length[C]): Aux[C, length.Out] = length

    type Aux[C <: Coproduct, Out0 <: Nat] = Length[C] { type Out = Out0 }

    implicit def cnilLength: Aux[CNil, Nat._0] = new Length[CNil] {
      type Out = Nat._0

      def apply(): Out = Nat._0
    }

    implicit def coproductLength[H, T <: Coproduct, N <: Nat]
      (implicit lt: Aux[T, N], sn: Witness.Aux[Succ[N]]): Aux[H :+: T, Succ[N]] = new Length[H :+: T] {
        type Out = Succ[N]

        def apply(): Out = sn.value
      }

  }

  /**
   * Type class supporting extending a coproduct on the right
   *
   * @author Stacy Curl
   */
  trait ExtendRight[C <: Coproduct, T] extends DepFn1[C] { type Out <: Coproduct }

  object ExtendRight {
    def apply[C <: Coproduct, T]
      (implicit extendRight: ExtendRight[C, T]): Aux[C, T, extendRight.Out] = extendRight

    type Aux[C <: Coproduct, T, Out0 <: Coproduct] = ExtendRight[C, T] { type Out = Out0 }

    implicit def extendRightSingleton[H, A]: Aux[H :+: CNil, A, H :+: A :+: CNil] =
      new ExtendRight[H :+: CNil, A] {
        type Out = H :+: A :+: CNil

        def apply(c: H :+: CNil): Out = c match {
          case Inl(h) => Inl(h)
          case Inr(t) => Inr(Inr(t))
        }
      }

    implicit def extendRightCoproduct[H, T <: Coproduct, A, AT <: Coproduct]
      (implicit extendRight: Aux[T, A, AT]): Aux[H :+: T, A, H :+: AT] =
        new ExtendRight[H :+: T, A] {
          type Out = H :+: AT

          def apply(c: H :+: T) = c match {
            case Inl(h) => Inl(h)
            case Inr(t) => Inr(extendRight(t))
          }
        }
  }

  /**
   * Type class supporting rotating a Coproduct left
   *
   * @author Stacy Curl
   */
  trait RotateLeft[C <: Coproduct, N <: Nat] extends DepFn1[C] { type Out <: Coproduct }

  object RotateLeft extends LowPriorityRotateLeft {
    def apply[C <: Coproduct, N <: Nat]
      (implicit rotateLeft: RotateLeft[C, N]): Aux[C, N, rotateLeft.Out] = rotateLeft

    implicit def implToRotateLeft[C <: Coproduct, N <: Nat, Size <: Nat, NModSize <: Succ[_]]
      (implicit
       length: Length.Aux[C, Size],
       mod: nat.Mod.Aux[N, Size, NModSize],
       impl: Impl[C, NModSize]
      ): Aux[C, N, impl.Out] = new RotateLeft[C, N] {
        type Out = impl.Out

        def apply(c: C): Out = impl(c)
      }

    trait Impl[C <: Coproduct, N <: Nat] extends DepFn1[C] { type Out <: Coproduct }

    object Impl {
      type Aux[C <: Coproduct, N <: Nat, Out0 <: Coproduct] = Impl[C, N] { type Out = Out0 }

      implicit def rotateCoproductOne[H, T <: Coproduct, TH <: Coproduct]
        (implicit extendRight: ExtendRight.Aux[T, H, TH], inject: Inject[TH, H]): Aux[H :+: T, Nat._1, TH] =
         new Impl[H :+: T, Nat._1] {
           type Out = TH

           def apply(c: H :+: T): Out = c match {
             case Inl(a)    => inject(a)
             case Inr(tail) => extendRight(tail)
           }
         }

      implicit def rotateCoproductN[C <: Coproduct, N <: Nat, CN <: Coproduct, CSN <: Coproduct]
        (implicit rotateN: Aux[C, N, CN], rotate1: Aux[CN, Nat._1, CSN]): Aux[C, Succ[N], CSN] =
          new Impl[C, Succ[N]] {
            type Out = CSN

            def apply(c: C): Out = rotate1(rotateN(c))
          }
    }
  }

  trait LowPriorityRotateLeft {
    type Aux[C <: Coproduct, N <: Nat, Out0 <: Coproduct] = RotateLeft[C, N] { type Out = Out0 }

    implicit def noopRotateLeftImpl[C <: Coproduct, N <: Nat]: Aux[C, N, C] = new RotateLeft[C, N] {
      type Out = C

      def apply(c: C): Out = c
    }
  }

  /**
   * Type class supporting rotating a Coproduct right
   *
   * @author Stacy Curl
   */
  trait RotateRight[C <: Coproduct, N <: Nat] extends DepFn1[C] { type Out <: Coproduct }

  object RotateRight extends LowPriorityRotateRight {
    def apply[C <: Coproduct, N <: Nat]
      (implicit rotateRight: RotateRight[C, N]): Aux[C, N, rotateRight.Out] = rotateRight

    implicit def hlistRotateRightt[
      C <: Coproduct, N <: Nat, Size <: Nat, NModSize <: Succ[_], Size_Diff_NModSize <: Nat
    ](implicit
      length: Length.Aux[C, Size],
      mod: nat.Mod.Aux[N, Size, NModSize],
      diff: nat.Diff.Aux[Size, NModSize, Size_Diff_NModSize],
      rotateLeft: RotateLeft.Impl[C, Size_Diff_NModSize]
    ): Aux[C, N, rotateLeft.Out] = new RotateRight[C, N] {
      type Out = rotateLeft.Out

      def apply(c: C): Out = rotateLeft(c)
    }
  }

  trait LowPriorityRotateRight {
    type Aux[C <: Coproduct, N <: Nat, Out0 <: Coproduct] = RotateRight[C, N] { type Out = Out0 }

    implicit def noopRotateRight[C <: Coproduct, N <: Nat]: Aux[C, N, C] = new RotateRight[C, N] {
      type Out = C

      def apply(c: C): Out = c
    }
  }

  /**
   * Type class providing access to head and tail of a Coproduct
   *
   * @author Stacy Curl
   */
  trait IsCCons[C <: Coproduct] {
    type H
    type T <: Coproduct

    def head(c: C): Option[H]
    def tail(c: C): Option[T]
  }

  object IsCCons {
    def apply[C <: Coproduct](implicit isCCons: IsCCons[C]): Aux[C, isCCons.H, isCCons.T] = isCCons

    type Aux[C <: Coproduct, H0, T0 <: Coproduct] = IsCCons[C] { type H = H0; type T = T0 }

    implicit def coproductCCons[H0, T0 <: Coproduct]: Aux[H0 :+: T0, H0, T0] = new IsCCons[H0 :+: T0] {
      type H = H0
      type T = T0

      def head(c: H0 :+: T0): Option[H0] = c match {
        case Inl(h) => Some(h)
        case _      => None
      }

      def tail(c: H0 :+: T0): Option[T0] = c match {
        case Inr(t) => Some(t)
        case _      => None
      }
    }
  }

  /**
   * Type class supporting reversing a Coproduct
   *
   * @author Stacy Curl
   */
  trait Reverse[C <: Coproduct] extends DepFn1[C] { type Out <: Coproduct }

  object Reverse {
    def apply[C <: Coproduct](implicit reverse: Reverse[C]): Aux[C, reverse.Out] = reverse

    type Aux[C <: Coproduct, Out0 <: Coproduct] = Reverse[C] { type Out = Out0 }

    implicit val reverseCNil: Aux[CNil, CNil] = new Reverse[CNil] {
      type Out = CNil

      def apply(c: CNil): Out = c
    }

    implicit def reverseCoproduct[
      H, T <: Coproduct, ReverseT <: Coproduct, RotateL_HReverseT <: Coproduct
    ](
      implicit
      reverse: Aux[T, ReverseT],
      rotateLeft: RotateLeft.Aux[H :+: ReverseT, Nat._1, RotateL_HReverseT],
      inject: Inject[RotateL_HReverseT, H]
    ): Aux[H :+: T, RotateL_HReverseT] = new Reverse[H :+: T] {
      type Out = RotateL_HReverseT

      def apply(c: H :+: T): Out = c match {
        case Inl(h) => inject(h)
        case Inr(t) => rotateLeft(Inr[H, ReverseT](reverse(t)))
      }
    }
  }

  /**
   * Type class providing access to init and last of a Coproduct
   *
   * @author Stacy Curl
   */
  trait InitLast[C <: Coproduct] {
    type I <: Coproduct
    type L

    def init(c: C): Option[I]
    def last(c: C): Option[L]
  }

  object InitLast {
    def apply[C <: Coproduct](implicit initLast: InitLast[C]): Aux[C, initLast.I, initLast.L] = initLast

    type Aux[C <: Coproduct, I0 <: Coproduct, L0] = InitLast[C] { type I = I0; type L = L0 }

    implicit def initLastCoproduct[C <: Coproduct, ReverseC <: Coproduct, H, T <: Coproduct](
      implicit reverse: Reverse.Aux[C, ReverseC], isCCons: IsCCons.Aux[ReverseC, H, T]
    ): Aux[C, T, H] = new InitLast[C] {
      type I = T
      type L = H

      def init(c: C): Option[I] = isCCons.tail(reverse(c))
      def last(c: C): Option[L] = isCCons.head(reverse(c))
    }
  }

  implicit object cnilOrdering extends Ordering[CNil] {
    def compare(x: CNil, y: CNil) = 0
  }

  implicit def coproductPartialOrdering[H, T <: Coproduct]
    (implicit ordering: Ordering[H], partialOrdering: PartialOrdering[T]): PartialOrdering[H :+: T] =
      new PartialOrdering[H :+: T] {
        def lteq(x: H :+: T, y: H :+: T): Boolean = (x, y) match {
          case (Inl(xh), Inl(yh)) => ordering.compare(xh, yh) <= 0
          case (Inr(xt), Inr(yt)) => partialOrdering.tryCompare(xt, yt).fold(false)(_ <= 0)
          case _                  => false
        }

        def tryCompare(x: H :+: T, y: H :+: T): Option[Int] = (x, y) match {
          case (Inl(xh), Inl(yh)) => Some(ordering.compare(xh, yh))
          case (Inr(xt), Inr(yt)) => partialOrdering.tryCompare(xt, yt)
          case _                  => None
        }
      }
}
