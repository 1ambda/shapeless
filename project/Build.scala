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
import Build.data

import com.typesafe.sbteclipse.plugin.EclipsePlugin.{ EclipseKeys, EclipseCreateSrc }
import com.typesafe.sbt.osgi.SbtOsgi._
import sbtbuildinfo.Plugin._
import com.typesafe.sbt.SbtGit._
import GitKeys._

object ShapelessBuild extends Build {
  
  override lazy val settings = super.settings :+ (
    EclipseKeys.skipParents := false
  )

  lazy val shapeless = Project(
    id = "shapeless", 
    base = file("."),
    aggregate = Seq(shapelessCore, shapelessExamples),
    settings = commonSettings ++ Seq(
      moduleName := "shapeless-root",
        
      (unmanagedSourceDirectories in Compile) := Nil,
      (unmanagedSourceDirectories in Test) := Nil,
      
      publish := (),
      publishLocal := ()
    )
  )

  lazy val shapelessCore =
    Project(
      id = "shapeless-core", 
      base = file("core"),
      settings = commonSettings ++ Publishing.settings ++ osgiSettings ++ buildInfoSettings ++ Seq(
        moduleName := "shapeless",
        
        managedSourceDirectories in Test := Nil,
        
        EclipseKeys.createSrc := EclipseCreateSrc.Default+EclipseCreateSrc.Managed,
        
        libraryDependencies <++= scalaVersion { sv =>
          Seq(
            "org.scala-lang" % "scala-compiler" % sv,
            "com.novocode" % "junit-interface" % "0.7" % "test"
        )},
        
        (sourceGenerators in Compile) <+= (sourceManaged in Compile) map Boilerplate.gen,
        (sourceGenerators in Compile) <+= buildInfo,

        initialCommands in console := """import shapeless._""",

        mappings in (Compile, packageSrc) <++=
          (sourceManaged in Compile, managedSources in Compile) map { (base, srcs) =>
            (srcs x (Path.relativeTo(base) | Path.flat))
          },
          
        mappings in (Compile, packageSrc) <++=
          (mappings in (Compile, packageSrc) in LocalProject("shapeless-examples")),

        OsgiKeys.exportPackage := Seq("shapeless.*;version=${Bundle-Version}"),
        OsgiKeys.importPackage := Seq("""scala.*;version="$<range;[==,=+);$<@>>""""),
        OsgiKeys.additionalHeaders := Map("-removeheaders" -> "Include-Resource,Private-Package"),

        buildInfoPackage := "shapeless",
        buildInfoKeys := Seq[BuildInfoKey](version, scalaVersion),
        buildInfoKeys ++= Seq[BuildInfoKey](
          version,
          scalaVersion,
          gitHeadCommit,
          BuildInfoKey.action("buildTime") {
            System.currentTimeMillis
          }
        )
      )
    )

  lazy val shapelessExamples = Project(
    id = "shapeless-examples",
    base = file("examples"),
    dependencies = Seq(shapelessCore),
    
    settings = commonSettings ++ Seq(
      libraryDependencies <++= scalaVersion { sv =>
        Seq(
          "org.scala-lang" % "scala-compiler" % sv,
          "com.novocode" % "junit-interface" % "0.7" % "test"
      )},

      runAllIn(Compile),

      publish := (),
      publishLocal := ()
    )
  )
  
  lazy val runAll = TaskKey[Unit]("run-all") 
  
  def runAllIn(config: Configuration) = {
    runAll in config <<= (discoveredMainClasses in config, runner in run, fullClasspath in config, streams) map { 
      (classes, runner, cp, s) => classes.foreach(c => runner.run(c, data(cp), Seq(), s.log))
    }
  }

  def commonSettings = Defaults.defaultSettings ++
    Seq(
      organization        := "com.chuusai",
      version             := "2.0.0-SNAPSHOT",
      scalaVersion        := "2.10.2",
      scalaBinaryVersion  := "2.10.2",

      (unmanagedSourceDirectories in Compile) <<= (scalaSource in Compile)(Seq(_)),
      (unmanagedSourceDirectories in Test) <<= (scalaSource in Test)(Seq(_)),

      scalacOptions       := Seq(
        "-feature",
        "-language:higherKinds",
        "-language:implicitConversions",
        "-deprecation",
        "-unchecked"),

      resolvers           ++= Seq(
        Classpaths.typesafeSnapshots,
        "snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"
      )
    )
}
