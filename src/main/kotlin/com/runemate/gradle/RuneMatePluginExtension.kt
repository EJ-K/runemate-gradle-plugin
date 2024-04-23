package com.runemate.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.kotlin.dsl.getByType

interface RuneMatePluginExtension {

    /**
     * Tells the client to attempt automatic login
     *
     * Default: false
     */
    val autoLogin: Property<Boolean>

    /**
     * Tells the client to launch in developer mode
     *
     * Default: true
     */
    val devMode: Property<Boolean>

    /**
     * Tells Gradle which version of runemate-game-api to fetch from Maven
     *
     * Default: + (latest)
     */
    val apiVersion: Property<String>

    /**
     * Tells Gradle which version of runemate-client to fetch from Maven
     *
     * Default: +/LATEST
     */
    val clientVersion: Property<String>

    /**
     * Tells the client which directories to scan for bots
     *
     * Default: $project/build/libs
     */
    val botDirectories: SetProperty<Any>


    /**
     * Tells Gradle to fail dependency resolution for external dependencies. RuneMate does not support external dependencies
     * when building for the bot store. If you intend only to run bots locally then you can disable this check.
     *
     * If this check is disabled, you will be responsible for building the JAR containing the dependencies, the plugin will not handle this for you.
     *
     * Default: true
     */
    val validateExternalDependencies: Property<Boolean>

}

fun Project.runemate(block: RuneMatePluginExtension.() -> Unit) = extensions.getByType<RuneMatePluginExtension>().apply(block)