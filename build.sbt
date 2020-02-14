import java.util.Properties

val Scalaversion = "2.12.10"
val Scalatraversion = "2.6.5"
val ScalaLoggingVersion = "3.9.0"
val ScalaTestVersion = "3.0.5"
val Log4JVersion = "2.11.1"
val Jettyversion = "9.4.18.v20190429"
val AwsSdkversion = "1.11.434"
val MockitoVersion = "2.23.0"
val Elastic4sVersion = "6.7.3"
val JacksonVersion = "2.9.10.2"
val ElasticsearchVersion = "6.8.2"
val Json4SVersion = "3.5.4"
val FlywayVersion = "5.2.0"
val PostgresVersion = "42.2.5"
val HikariConnectionPoolVersion = "3.2.0"
val CatsEffectVersion = "1.6.1"
val TestContainersVersion = "1.12.2"

val appProperties = settingKey[Properties]("The application properties")

appProperties := {
  val prop = new Properties()
  IO.load(prop, new File("build.properties"))
  prop
}

lazy val concept_api = (project in file("."))
  .settings(
    name := "concept-api",
    organization := appProperties.value.getProperty("NDLAOrganization"),
    version := appProperties.value.getProperty("NDLAComponentVersion"),
    scalaVersion := Scalaversion,
    javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),
    scalacOptions := Seq("-target:jvm-1.8", "-unchecked", "-deprecation", "-feature", "-Ypartial-unification"),
    libraryDependencies ++= Seq(
      "ndla" %% "network" % "0.42",
      "ndla" %% "mapping" % "0.11",
      "ndla" %% "validation" % "0.34",
      "org.scalatra" %% "scalatra" % Scalatraversion,
      "org.eclipse.jetty" % "jetty-webapp" % Jettyversion % "container;compile",
      "org.eclipse.jetty" % "jetty-plus" % Jettyversion % "container",
      "javax.servlet" % "javax.servlet-api" % "4.0.1" % "container;provided;test",
      "org.scalatra" %% "scalatra-json" % Scalatraversion,
      "org.scalatra" %% "scalatra-scalatest" % Scalatraversion % "test",
      "org.json4s" %% "json4s-native" % Json4SVersion,
      "org.scalatra" %% "scalatra-swagger" % Scalatraversion,
      "com.typesafe.scala-logging" %% "scala-logging" % ScalaLoggingVersion,
      "org.apache.logging.log4j" % "log4j-api" % Log4JVersion,
      "org.apache.logging.log4j" % "log4j-core" % Log4JVersion,
      "org.apache.logging.log4j" % "log4j-slf4j-impl" % Log4JVersion,
      "com.fasterxml.jackson.core" % "jackson-databind" % JacksonVersion, // Overriding jackson-databind used in dependencies because of https://app.snyk.io/vuln/SNYK-JAVA-COMFASTERXMLJACKSONCORE-72884
      "org.scalatest" %% "scalatest" % ScalaTestVersion % "test",
      "log4j" % "log4j" % "1.2.16",
      "net.bull.javamelody" % "javamelody-core" % "1.74.0",
      "org.jrobin" % "jrobin" % "1.5.9",
      "com.amazonaws" % "aws-java-sdk-cloudwatch" % AwsSdkversion,
      "org.mockito" % "mockito-core" % MockitoVersion % "test",
      "org.flywaydb" % "flyway-core" % FlywayVersion,
      "org.scalikejdbc" %% "scalikejdbc" % "3.3.1",
      "com.zaxxer" % "HikariCP" % HikariConnectionPoolVersion,
      "org.postgresql" % "postgresql" % PostgresVersion,
      "org.elasticsearch" % "elasticsearch" % ElasticsearchVersion,
      "com.sksamuel.elastic4s" %% "elastic4s-core" % Elastic4sVersion,
      "com.sksamuel.elastic4s" %% "elastic4s-http" % Elastic4sVersion,
      "org.typelevel" %% "cats-core" % CatsEffectVersion,
      "org.testcontainers" % "elasticsearch" % TestContainersVersion % "test",
      "org.testcontainers" % "testcontainers" % TestContainersVersion % "test",
      "vc.inreach.aws" % "aws-signing-request-interceptor" % "0.0.22",
      "org.apache.httpcomponents" % "httpclient" % "4.5.10", // Overridden because vulnerability in request interceptor
      "com.google.guava" % "guava" % "28.1-jre" // Overridden because vulnerability in request interceptor
    )
  )
  .enablePlugins(DockerPlugin)
  .enablePlugins(JettyPlugin)

val checkfmt = taskKey[Boolean]("Check for code style errors")
checkfmt := {
  val noErrorsInMainFiles = (Compile / scalafmtCheck).value
  val noErrorsInTestFiles = (Test / scalafmtCheck).value
  val noErrorsInSbtConfigFiles = (Compile / scalafmtSbtCheck).value

  noErrorsInMainFiles && noErrorsInTestFiles && noErrorsInSbtConfigFiles
}

Test / test := (Test / test).dependsOn(Test / checkfmt).value

val fmt = taskKey[Unit]("Automatically apply code style fixes")
fmt := {
  (Compile / scalafmt).value
  (Test / scalafmt).value
  (Compile / scalafmtSbt).value
}

assembly / assemblyJarName := "concept-api.jar"
assembly / mainClass := Some("no.ndla.conceptapi.JettyLauncher")
assembly / assemblyMergeStrategy := {
  case "mime.types" => MergeStrategy.filterDistinctLines
  case PathList("org", "joda", "convert", "ToString.class") =>
    MergeStrategy.first
  case PathList("org", "joda", "convert", "FromString.class") =>
    MergeStrategy.first
  case PathList("org", "joda", "time", "base", "BaseDateTime.class") =>
    MergeStrategy.first
  case x =>
    val oldStrategy = (assembly / assemblyMergeStrategy).value
    oldStrategy(x)
}

// Make the docker task depend on the assembly task, which generates a fat JAR file
docker := (docker dependsOn assembly).value

docker / dockerfile := {
  val artifact = (assemblyOutputPath in assembly).value
  val artifactTargetPath = s"/app/${artifact.name}"
  new Dockerfile {
    from("adoptopenjdk/openjdk11:alpine-slim")
    run("apk", "--no-cache", "add", "ttf-dejavu")
    add(artifact, artifactTargetPath)
    entryPoint("java", "-Dorg.scalatra.environment=production", "-jar", artifactTargetPath)
  }
}

docker / imageNames := Seq(
  ImageName(namespace = Some(organization.value),
            repository = name.value,
            tag = Some(System.getProperty("docker.tag", "SNAPSHOT")))
)

Test / parallelExecution := false

resolvers ++= scala.util.Properties
  .envOrNone("NDLA_RELEASES")
  .map(repo => "Release Sonatype Nexus Repository Manager" at repo)
  .toSeq
