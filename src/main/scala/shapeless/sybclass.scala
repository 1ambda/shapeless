/*
 * Copyright (c) 2011 Miles Sabin 
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

object SybClass {
  import TypeOperators._
  import Poly._
  import HList._
  
  trait Data[HF <: HRFn, T] {
    def gmapQ(t : T) : List[HF#G[_]]
  }
  
  def gmapQ[HF <: HRFn, T](f : HF)(t : T)(implicit data : Data[HF, T]) = data.gmapQ(t)

  implicit def dfltData[HF <: HRFn, T] = new Data[HF, T] {
    def gmapQ(t : T) : List[HF#G[_]] = Nil
  }

  implicit def pairData[HF <: HRFn, T, U](implicit qt : Case[HF, T => HF#G[_]], qu : Case[HF, U => HF#G[_]]) = new Data[HF, (T, U)] {
    def gmapQ(t : (T, U)) = List(qt(t._1), qu(t._2))
  }

  implicit def eitherData[HF <: HRFn, T, U](implicit qt : Case[HF, T => HF#G[_]], qu : Case[HF, U => HF#G[_]]) = new Data[HF, Either[T, U]] {
    def gmapQ(t : Either[T, U]) = t match {
      case Left(t) => List(qt(t))
      case Right(u) => List(qu(u))
    }
  }

  implicit def optionData[HF <: HRFn, T](implicit qt : Case[HF, T => HF#G[_]]) = new Data[HF, Option[T]] {
    def gmapQ(t : Option[T]) = t.map(qt.value).toList
  }

  implicit def listData[HF <: HRFn, T](implicit qt : Case[HF, T => HF#G[_]]) = new Data[HF, List[T]] {
    def gmapQ(t : List[T]) = t.map(qt.value)
  }
  
  implicit def hlistData[HF <: HRFn, H, T <: HList](implicit qh : Case[HF, H => HF#G[_]], ct : Data[HF, T]) = new Data[HF, H :: T] {
    def gmapQ(t : H :: T) = qh(t.head) :: ct.gmapQ(t.tail)
  }
  
  trait DataT[HF, T] {
    def gmapT(t : T) : T
  }

  def gmapT[HF, T](f : HF)(t : T)(implicit data : DataT[HF, T]) = data.gmapT(t)

  implicit def dfltDataT[HF, T] : DataT[HF, T] = new DataT[HF, T] {
    def gmapT(t : T) = t
  }

  implicit def pairDataT[HF, T, U](implicit ft : Case[HF, T => T], fu : Case[HF, U => U]) = new DataT[HF, (T, U)] {
    def gmapT(t : (T, U)) = (ft(t._1), fu(t._2))
  }

  implicit def eitherDataT[HF, T, U](implicit ft : Case[HF, T => T], fu : Case[HF, U => U]) = new DataT[HF, Either[T, U]] {
    def gmapT(t : Either[T, U]) = t match {
      case Left(t) => Left(ft(t))
      case Right(u) => Right(fu(u))
    }
  }

  implicit def optionDataT[HF, T](implicit ft : Case[HF, T => T]) = new DataT[HF, Option[T]] {
    def gmapT(t : Option[T]) = t.map(ft.value)
  }

  implicit def listDataT[HF, T](implicit ft : Case[HF, T => T]) = new DataT[HF, List[T]] {
    def gmapT(t : List[T]) = t.map(ft.value)
  }
  
  implicit def hlistDataT[HF <: HRFn, H, T <: HList](implicit fh : Case[HF, H => H], ct : DataT[HF, T]) = new DataT[HF, H :: T] {
    def gmapT(t : H :: T) = fh(t.head) :: ct.gmapT(t.tail)
  }

  case class Node[T](t : T, c : List[Node[T]] = Nil) {
    def fold(f : (T, T) => T) : T = c.map(_.fold(f)).foldLeft(t)(f)
  }
  
  type Everything[HF <: HRFn, T] = Case[Everything0[HF], T => Node[HF#G[_]]]

  trait Everything0[HF <: HRFn] extends (Id ~> Const[Node[HF#G[_]]]#λ) with NoDefault
  
  def everything[HF <: HRFn, T](f : HF)(k : (HF#G[_], HF#G[_]) => HF#G[_])(t : T) 
    (implicit c : Case[Everything0[HF], T => Node[HF#G[_]]]) : HF#G[_] = c(t).fold(k)
    
  implicit def everythingDflt[HF <: HRFn, T](implicit data : Data[Everything0[HF], T], fT : Case[HF, T => HF#G[_]]) =
    new Case[Everything0[HF], T => Node[HF#G[_]]](t => Node(fT(t), data.gmapQ(t)))

  type Everywhere[HF, T] = Case[Everywhere0[HF], T => T]
    
  trait Everywhere0[HF] extends (Id ~> Id) with NoDefault
    
  def everywhere[HF <: HRFn](f : HF) = new Everywhere0[f.type] {}
  
  implicit def everywhereDflt[HF, T](implicit data : DataT[Everywhere0[HF], T], fT : Case[HF, T => T]) =
    new Case[Everywhere0[HF], T => T](t => fT(data.gmapT(t)))
}
