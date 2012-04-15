name := "shapeless"

organization := "com.chuusai"

version := "1.2.0"

scalaVersion := "2.10.0-SNAPSHOT"

crossScalaVersions <<= (version) {
  v => Seq("2.9.1", "2.9.1-1", "2.9.2") ++ (if (v.endsWith("-SNAPSHOT")) Seq("2.10.0-SNAPSHOT") else Seq())
}

scalacOptions ++= Seq("-unchecked", "-deprecation")

scalacOptions <++= scalaVersion map { version =>
  val Some((major, minor)) = CrossVersion.partialVersion(version)
  if (major < 2 || (major == 2 && minor < 10)) 
    Seq("-Ydependent-method-types")
 	else Nil
}

resolvers += ScalaToolsSnapshots

libraryDependencies ++= Seq(
  "com.novocode" % "junit-interface" % "0.7" % "test"
)
