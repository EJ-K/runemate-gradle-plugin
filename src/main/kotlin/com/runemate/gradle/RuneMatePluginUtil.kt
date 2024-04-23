package com.runemate.gradle

import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.maven
import kotlin.io.path.relativeTo

internal const val RUNEMATE = "runemate"
internal const val JAVAFX_VERSION = "20"

fun RepositoryHandler.runemateGameApiRepository() = maven("https://gitlab.com/api/v4/projects/32972353/packages/maven") {
    content {
        includeModule("com.runemate", "runemate-game-api")
    }
}

fun RepositoryHandler.runemateClientRepository() = maven("https://gitlab.com/api/v4/projects/10471880/packages/maven") {
    content {
        includeModule("com.runemate", "runemate-client")
    }
}

internal val dependencyAllowList = arrayOf(
    "com.runemate:.*",
    "org.openjfx:.*",
    "org.json:json",
    "org.jblas:jblas",
    "org.jetbrains.kotlin:.*",
    "org.projectlombok:lombok",
    "org.jetbrains:annotations"
).map(::Regex)

internal val Project.sourceFiles
    get() = extensions.getByType<JavaPluginExtension>()
        .sourceSets
        .flatMap { it.allSource }
        .map { it.toPath().relativeTo(rootDir.toPath()) }

internal fun <T> SetProperty<T>.ifPresent(block: (Set<T>) -> Unit) = if (isPresent) block(get()) else Unit
internal fun Property<Boolean>.ifTrue(block: () -> Unit) = if (isPresent && get()) block() else Unit