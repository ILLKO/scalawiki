name := "scalawiki"

organization := "org.scalawiki"

version := "0.3"

scalaVersion := "2.11.6"

resolvers := Seq("spray repo" at "http://repo.spray.io",
  "Typesafe Repo" at "https://repo.typesafe.com/typesafe/releases/",
  "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases")

libraryDependencies ++= {
  val akkaV = "2.3.9"
  val sprayV = "1.3.2"
  val kamonV = "0.4.0"
  val aspectJV = "1.8.5"
  Seq(
    "io.spray" %% "spray-client" % sprayV,
    "io.spray" %% "spray-caching" % sprayV,
    "com.typesafe.play" %% "play-json" % "2.3.7",
    "com.typesafe.akka" %% "akka-actor" % akkaV,
    "commons-codec" % "commons-codec" % "1.10",
    "com.github.nscala-time" %% "nscala-time" % "1.8.0",
    "org.xwiki.commons" % "xwiki-commons-blame-api" % "6.4.1",
    "com.typesafe.slick" %% "slick" % "2.1.0",
    "com.h2database" % "h2" % "1.3.176",
    "com.github.wookietreiber" %% "scala-chart" % "0.4.2",
    "org.jfree" % "jfreesvg" % "2.1",
    "com.fasterxml" % "aalto-xml" % "0.9.11",
    //    "org.codehaus.woodstox" % "woodstox-core-asl" % "4.4.1",
    "org.apache.commons" % "commons-compress" % "1.9",
    "ch.qos.logback" % "logback-classic" % "1.1.3",
    "org.sweble.wikitext" % "swc-engine" % "2.0.0",
    //          "org.sweble.engine" % "sweble-engine-serialization" % "2.0.0",
    //    "org.sweble.wom3" % "sweble-wom3-swc-adapter" % "2.0.0",
//    "io.kamon" %% "kamon-core" % kamonV,
//    "io.kamon" %% "kamon-log-reporter" % kamonV,
//    "io.kamon" %% "kamon-system-metrics" % kamonV,
//    "io.kamon" %% "kamon-spray" % "0.4.0",
//  //    "io.kamon" %% "kamon-statsd" % kamonV,
//    "io.kamon" %% "kamon-akka" % kamonV,
//    "io.kamon" %% "kamon-jdbc" % kamonV,
//    "org.aspectj" % "aspectjweaver" % aspectJV,
    "org.specs2" %% "specs2" % "2.4.17" % "test",
    "com.google.jimfs" % "jimfs" % "1.0" % "test"
  )
}

initialize := {
  val _ = initialize.value // run the previous initialization
  val required = VersionNumber("1.7")
  val curr = VersionNumber(sys.props("java.specification.version"))
  assert(CompatibleJavaVersion(curr, required), s"Java $required or above required")
}



    