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

import sbt._
import Keys._

object ShapelessBuild extends Build {

  lazy val project = Project(
    id = "root", 
    base = file("."),
    settings = Defaults.defaultSettings ++ Seq(
      (sourceGenerators in Compile) <+= (sourceManaged in Compile) map { dir =>
        val tupleraux = dir / "shapeless" / "tupleraux.scala"
        IO.write(tupleraux, genTuplerAuxInstances)
        
        val hlisteraux = dir / "shapeless" / "hlisteraux.scala"
        IO.write(hlisteraux, genHListerAuxInstances)
        
        val fnhlisteraux = dir / "shapeless" / "fnhlisteraux.scala"
        IO.write(fnhlisteraux, genFnHListerAuxInstances)
        
        val fnunhlisteraux = dir / "shapeless" / "fnunhlisteraux.scala"
        IO.write(fnunhlisteraux, genFnUnHListerAuxInstances)

        val nats = dir / "shapeless" / "nats.scala"
        IO.write(nats, genNats)
        
        Seq(tupleraux, hlisteraux, fnhlisteraux, fnunhlisteraux, nats)
      }
    )
  )
  
  def genHeader = {
    ("""|/*
        | * Copyright (c) 2011 Miles Sabin 
        | *
        | * Licensed under the Apache License, Version 2.0 (the "License");
        | * you may not use this file except in compliance with the License.
        | * You may obtain a copy of the License at
        | *
        | *     http://www.apache.org/licenses/LICENSE-2.0
        | *
        | * Unless required by applicable law or agreed to in writing, software
        | * distributed under the License is distributed on an "AS IS" BASIS,
        | * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
        | * See the License for the specific language governing permissions and
        | * limitations under the License.
        | */
        |
        |package shapeless
        |""").stripMargin
  }
  
  def genTuplerAuxInstances = {
    def genInstance(arity : Int) = {
      val typeVars = (0 until arity) map (n => (n+'A').toChar)
      val typeArgs = typeVars.mkString("[", ", ", "]")
      val tupleType = if (arity == 1) "Tuple1[A]" else typeVars.mkString("(", ", ", ")")
      val hlistType = typeVars.mkString("", " :: ", " :: HNil")
      val hlistValue = ((1 to arity) map (n => "t._"+n)).mkString("", " :: ", " :: HNil")
      val pattern = ((0 until arity) map (n => (n+'a').toChar)).mkString("", " :: ", " :: HNil")
      val tupleValue = if (arity == 1) "Tuple1(a)" else ((0 until arity) map (n => (n+'a').toChar)).mkString("(", ", ", ")")
      
      ("""|
          |  implicit def hlistTupler"""+arity+typeArgs+""" = new TuplerAux["""+hlistType+""", """+tupleType+"""] {
          |    def apply(l : """+hlistType+""") = l match { case """+pattern+""" => """+tupleValue+""" }
          |  }
          |""").stripMargin
    }

    val instances = ((1 to 22) map genInstance).mkString
    
    genHeader+
    ("""|
        |trait TuplerAuxInstances {"""+instances+"""}
        |""").stripMargin
  }
  
  def genHListerAuxInstances = {
    def genInstance(arity : Int) = {
      val typeVars = (0 until arity) map (n => (n+'A').toChar)
      val typeArgs = typeVars.mkString("[", ", ", "]")
      val prodType = "Product"+arity+typeArgs
      val hlistType = typeVars.mkString("", " :: ", " :: HNil")
      val hlistValue = ((1 to arity) map (n => "t._"+n)).mkString("", " :: ", " :: HNil")
      
      ("""|
          |  implicit def tupleHLister"""+arity+typeArgs+""" = new HListerAux["""+prodType+""", """+hlistType+"""] {
          |    def apply(t : """+prodType+""") = """+hlistValue+"""
          |  }
          |""").stripMargin
    }

    val instances = ((1 to 22) map genInstance).mkString
    
    genHeader+
    ("""|
        |trait HListerAuxInstances {"""+instances+"""}
        |""").stripMargin
  }
  
  def genFnHListerAuxInstances = {
    def genInstance(arity : Int) = {
      val typeVars = (0 until arity) map (n => (n+'A').toChar)
      val typeArgs = (typeVars :+ "Res").mkString("[", ", ", "]")
      val fnType = typeVars.mkString("(", ", ", ")")+" => Res"
      val hlistType = (typeVars :+ "HNil").mkString(" :: ")
      val hlistFnType = "("+hlistType+") => Res"
      val pattern = ((0 until arity) map (n => (n+'a').toChar)).mkString("", " :: ", " :: HNil")
      val fnArgs = ((0 until arity) map (n => (n+'a').toChar)).mkString("(", ", ", ")")
      val fnBody = if (arity == 0) """fn()""" else """l match { case """+pattern+""" => fn"""+fnArgs+""" }""" 
      
      ("""|
          |  implicit def fnHLister"""+arity+typeArgs+""" = new FnHListerAux["""+fnType+""", """+hlistFnType+"""] {
          |    def apply(fn : """+fnType+""") = (l : """+hlistType+""") => """+fnBody+"""
          |  }
          |""").stripMargin
    }

    val instances = ((0 to 22) map genInstance).mkString
    
    genHeader+
    ("""|
        |trait FnHListerAuxInstances {"""+instances+"""}
        |""").stripMargin
  }
  
  def genFnUnHListerAuxInstances = {
    def genInstance(arity : Int) = {
      val typeVars = (0 until arity) map (n => (n+'A').toChar)
      val typeArgs = (typeVars :+ "Res").mkString("[", ", ", "]")
      val fnType = typeVars.mkString("(", ", ", ")")+" => Res"
      val hlistType = (typeVars :+ "HNil").mkString(" :: ")
      val hlistFnType = "("+hlistType+") => Res"
      val litArgs = ((0 until arity) map (n => (n+'a').toChar+" : "+(n+'A').toChar)).mkString("(", ", ", ")")
      val hlistFnArgs = (((0 until arity) map (n => (n+'a').toChar)) :+ "HNil").mkString("", " :: ", "")
      
      ("""|
          |  implicit def fnUnHLister"""+arity+typeArgs+""" = new FnUnHListerAux["""+hlistFnType+""", """+fnType+"""] {
          |    def apply(hf : """+hlistFnType+""") = """+litArgs+""" => hf("""+hlistFnArgs+""")
          |  }
          |""").stripMargin
    }

    val instances = ((0 to 22) map genInstance).mkString
    
    genHeader+
    ("""|
        |trait FnUnHListerAuxInstances {"""+instances+"""}
        |""").stripMargin
  }
  
  def genNats = {
    def genNat(n : Int) = {
      ("""|
          |  type _"""+n+""" = Succ[_"""+(n-1)+"""]
          |  val _"""+n+""" = new _"""+n+"""
          |""").stripMargin
    }
    
    val nats = ((1 to 22) map genNat).mkString
    
    genHeader+
    ("""|
        |trait Nats {
        |  import Nat._
        |"""+nats+"""}
        |""").stripMargin
  }
}
