package com.runemate.gradle

import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.maven
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import java.nio.file.Path

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

internal val Project.kotlinSourceRoots
    get() = extensions.findByType<KotlinProjectExtension>()
        ?.sourceSets
        ?.flatMap { it.kotlin.srcDirs }
        ?.map { it.toPath() }

internal val Project.javaSourceRoots
    get() = extensions.findByType<JavaPluginExtension>()
        ?.sourceSets
        ?.flatMap { it.java.srcDirs }
        ?.map { it.toPath() }

internal val Project.resourceRoots
    get() = extensions.findByType<JavaPluginExtension>()
        ?.sourceSets
        ?.flatMap { it.resources.srcDirs }
        ?.map { it.toPath() }

internal val Project.sourceRoots: Set<Path>
    get() {
        val kotlin = kotlinSourceRoots ?: emptyList()
        val java = javaSourceRoots ?: emptyList()
        val resources = resourceRoots ?: emptyList()
        return (java + kotlin + resources).toSet()
    }

internal fun <T> SetProperty<T>.ifPresent(block: (Set<T>) -> Unit) = if (isPresent) block(get()) else Unit
internal fun Property<Boolean>.ifTrue(block: () -> Unit) = if (isPresent && get()) block() else Unit