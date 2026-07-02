package com.example.ec.architecture

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.name
import kotlin.io.path.readLines
import kotlin.streams.toList

class LayerDependencyTest : FunSpec({

    val sourceRoot = Path.of("src/main/kotlin/com/example/ec")

    test("domain layer does not depend on outer layers") {
        findForbiddenImports(
            sourceRoot = sourceRoot,
            layer = "domain",
            forbiddenImports = listOf(
                "com.example.ec.application.",
                "com.example.ec.infrastructure.",
                "com.example.ec.presentation."
            )
        ).shouldBeEmpty()
    }

    test("application layer does not depend on infrastructure or presentation") {
        findForbiddenImports(
            sourceRoot = sourceRoot,
            layer = "application",
            forbiddenImports = listOf(
                "com.example.ec.infrastructure.",
                "com.example.ec.presentation."
            )
        ).shouldBeEmpty()
    }

    test("infrastructure layer does not depend on application or presentation") {
        findForbiddenImports(
            sourceRoot = sourceRoot,
            layer = "infrastructure",
            forbiddenImports = listOf(
                "com.example.ec.application.",
                "com.example.ec.presentation."
            )
        ).shouldBeEmpty()
    }
})

private fun findForbiddenImports(
    sourceRoot: Path,
    layer: String,
    forbiddenImports: List<String>
): List<String> {
    val files = Files.walk(sourceRoot.resolve(layer)).use { paths ->
        paths
            .filter { path -> path.extension == "kt" && path.name != "LayerDependencyTest.kt" }
            .toList()
    }

    return files.flatMap { path ->
        path.readLines()
            .filter { line -> line.trimStart().startsWith("import ") }
            .filter { line -> forbiddenImports.any { forbidden -> line.contains(forbidden) } }
            .map { line -> "${path.invariantSeparatorsPathString}: $line" }
    }
}
