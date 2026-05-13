ThisBuild / tlBaseVersion := "1.1"
ThisBuild / organization := "org.polyvariant.smithy-transformations"
ThisBuild / organizationName := "Polyvariant"
ThisBuild / startYear := Some(2026)
ThisBuild / licenses := Seq(License.Apache2)
ThisBuild / developers := List(tlGitHubDev("kubukoz", "Jakub Kozłowski"))

ThisBuild / githubWorkflowPublishTargetBranches := Seq(
  RefPredicate.Equals(Ref.Branch("main")),
  RefPredicate.StartsWith(Ref.Tag("v")),
)

ThisBuild / scalaVersion := "3.8.3"
ThisBuild / tlJdkRelease := Some(17)
ThisBuild / tlFatalWarnings := false
ThisBuild / resolvers += Resolver.sonatypeCentralSnapshots

ThisBuild / mergifyStewardConfig ~= (_.map(_.withMergeMinors(true)))

val smithyVersion = "1.70.0"

val commonSettings = Seq(
  scalacOptions -= "-Xkind-projector:underscores",
  scalacOptions ++= Seq(
    "-Xkind-projector",
    "-deprecation",
    "-Wunused:all",
    "-Wnonunit-statement",
  ),
  libraryDependencies ++= Seq(
    "org.scalameta" %%% "munit" % "1.3.0" % Test
  ),
)

lazy val transformation = project
  .settings(
    autoScalaLibrary := false,
    crossPaths := false,
    libraryDependencies ++= Seq(
      "software.amazon.smithy" % "smithy-build" % smithyVersion,
      "software.amazon.smithy" % "smithy-model" % smithyVersion,
    ),
    smithyTraitCodegenNamespace := "smithytransformations",
    smithyTraitCodegenJavaPackage := "smithytransformations",
    smithyTraitCodegenDependencies := Nil,
    javacOptions -= "-Xlint:all",
    Compile / doc / javacOptions ++= Seq(
      // skip "no comment" warnings in Javadoc, these Java files are just boilerplate
      "-Xdoclint:all,-missing"
    ),
  )
  .enablePlugins(SmithyTraitCodegenPlugin)

lazy val tests = project
  .settings(
    commonSettings,
    libraryDependencies ++= Seq(
      "software.amazon.smithy" % "smithy-diff" % smithyVersion % Test
    ),
    publish / skip := true,
  )
  .dependsOn(transformation)
  .enablePlugins(NoPublishPlugin)

lazy val smithy4sExample = project
  .settings(
    commonSettings,
    libraryDependencies ++= Seq(
      "com.disneystreaming.smithy4s" %%% "smithy4s-core" % smithy4sVersion.value
    ),
    Compile / smithy4sModelTransformers := List(
      "addOperations"
    ),
    Compile / smithy4sAllDependenciesAsJars += (transformation / Compile / packageBin).value,
  )
  .enablePlugins(Smithy4sCodegenPlugin)
  .enablePlugins(NoPublishPlugin)

lazy val root = project
  .in(file("."))
  .aggregate(transformation, tests, smithy4sExample)
  .enablePlugins(NoPublishPlugin)
