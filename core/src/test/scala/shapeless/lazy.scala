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

import scala.language.experimental.macros

import org.junit.Test
import org.junit.Assert._

import scala.collection.mutable.ListBuffer

class LazyTests {

  val effects = ListBuffer[Int]()

  implicit def lazyInt = Lazy[Int]{ effects += 3 ; 23 }

  def summonLazyInt(implicit li: Lazy[Int]): Int = {
    effects += 2
    val i = li.value
    effects += 4
    i
  }

  @Test
  def testEffectOrder {
    effects += 1
    val i = summonLazyInt
    effects += 5

    assertEquals(23, i)
    assertEquals(List(1, 2, 3, 4, 5), effects.toList)
  }

  sealed trait List[+T]
  case class Cons[T](hd: T, tl: List[T]) extends List[T]
  sealed trait Nil extends List[Nothing]
  case object Nil extends Nil

  trait Show[T] {
    def apply(t: T): String
  }

  def show[T](t: T)(implicit s: Show[T]) = s(t)

  implicit def showInt: Show[Int] = new Show[Int] {
    def apply(t: Int) = t.toString
  }

  implicit def showNil: Show[Nil] = new Show[Nil] {
    def apply(t: Nil) = "Nil"
  }

  implicit def showCons[T](implicit st: Lazy[Show[T]], sl: Lazy[Show[List[T]]]): Show[Cons[T]] = new Show[Cons[T]] {
    def apply(t: Cons[T]) = s"Cons(${show(t.hd)(st.value)}, ${show(t.tl)(sl.value)})"
  }

  implicit def showList[T](implicit sc: Lazy[Show[Cons[T]]]): Show[List[T]] = new Show[List[T]] {
    def apply(t: List[T]) = t match {
      case n: Nil => show(n)
      case c: Cons[T] => show(c)(sc.value)
    }
  }

  @Test
  def testRecursive {
    val l: List[Int] = Cons(1, Cons(2, Cons(3, Nil)))

    val sl = show(l)
    assertEquals("Cons(1, Cons(2, Cons(3, Nil)))", sl)
  }
}
