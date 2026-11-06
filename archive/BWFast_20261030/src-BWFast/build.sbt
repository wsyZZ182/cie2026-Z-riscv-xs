ThisBuild / scalaVersion := "2.13.17"
libraryDependencies += "org.chipsalliance" %% "chisel" % "7.2.0"
addCompilerPlugin("org.chipsalliance" % "chisel-plugin" % "7.2.0" cross CrossVersion.full)
scalacOptions += "-language:reflectiveCalls"

// ARCHIVE NOTE (2026-11-06): 原工程构建文件未随仓库存档，本文件为补齐件。
// 版本与 docs-BWFast/README.md 记录一致（Scala 2.13.17 / Chisel 7.2.0 / sbt 1.10.7）。
// 源文件直接位于本目录根（package vdot），非标准 sbt 布局，故显式声明源目录。
Compile / unmanagedSourceDirectories := Seq(baseDirectory.value)
