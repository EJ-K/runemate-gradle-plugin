@file:Suppress("unused")

package com.runemate.gradle

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.JavaExec
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.*
import org.openjfx.gradle.JavaFXOptions
import org.openjfx.gradle.JavaFXPlugin
import java.io.File

class RuneMatePlugin : Plugin<Project> {

    override fun apply(target: Project): Unit = with(target.rootProject) {
        logger.lifecycle("Root project has plugin applied: {}", pluginManager.hasPlugin("com.runemate.gradle-plugin"))


        configurePlugins()
        configureDependencies()
        configureTasks()
        apply<RuneMatePublishPlugin>()
    }

    private fun Project.configurePlugins() {
        configurations.create(RUNEMATE) {
            configurations["implementation"].extendsFrom(this)
        }

        extensions.create<RuneMateExtension>(RUNEMATE).apply {
            autoLogin.convention(false)
            devMode.convention(true)
            apiVersion.convention("+")
            clientVersion.convention("+")
            botDirectories.convention(setOf(layout.buildDirectory.dir("libs")))
            allowExternalDependencies.convention(false)
        }

        apply<JavaFXPlugin>()
        configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(17))
            }
        }

        configure<JavaFXOptions> {
            version = JAVAFX_VERSION
            modules(
                "javafx.base",
                "javafx.fxml",
                "javafx.controls",
                "javafx.media",
                "javafx.web",
                "javafx.graphics",
                "javafx.swing"
            )
        }

    }

    private fun Project.configureDependencies() {
        configurations.all {
            resolutionStrategy {
                eachDependency {
                    if (requested.group == "com.runemate") {
                        val rm = project.extensions.getByType<RuneMateExtension>()
                        when (requested.name) {
                            "runemate-client" -> useVersion(rm.clientVersion.get())
                            "runemate-game-api" -> useVersion(rm.apiVersion.get())
                        }
                    }
                }
            }
        }

        val validate = extensions.getByType<RuneMateExtension>().allowExternalDependencies.get()
        configurations.getByName("runtimeClasspath") {
            resolutionStrategy.eachDependency {
                val key = "${requested.group}:${requested.name}"
                if (dependencyAllowList.none { it.matches(key) }) {
                    if (!validate) {
                        throw GradleException("RuneMate does not support external dependencies, please remove: $key")
                    } else {
                        logger.warn("RuneMate does not support external dependencies, please remove: $key")
                    }
                }
            }
        }

        repositories {
            runemateGameApiRepository()
            runemateClientRepository()
            mavenCentral()

            //Snapshot testing
            mavenLocal {
                content {
                    includeVersionByRegex("com.runemate", "runemate-game-api", ".*-SNAPSHOT")
                    includeVersionByRegex("com.runemate", "runemate-client", ".*-SNAPSHOT")
                    includeVersionByRegex("com.runemate", "runemate-game-api", ".*-beta.*")
                    includeVersionByRegex("com.runemate", "runemate-client", ".*-beta.*")
                }
            }
        }

        dependencies {
            configurations.getByName(RUNEMATE)("com.runemate:runemate-client")
            configurations.getByName(RUNEMATE)("com.runemate:runemate-game-api")
            configurations.getByName("compileOnly")("org.projectlombok:lombok:1.18.32")
            configurations.getByName("annotationProcessor")("org.projectlombok:lombok:1.18.32")
        }
    }

    private fun Project.configureTasks() {
        tasks.register("runClient", JavaExec::class) {
            group = RUNEMATE
            classpath = configurations.getByName("runtimeClasspath")
            mainClass.set("com.runemate.client.boot.Boot")

            arrayOf("java.base/java.lang.reflect", "java.base/java.nio", "java.base/sun.nio.ch", "java.base/java.util.regex").forEach {
                jvmArgs("--add-opens=${it}=ALL-UNNAMED")
            }

            //Handle arguments
            val ext = project.extensions.getByType<RuneMateExtension>()
            ext.autoLogin.ifTrue { args("--login") }
            ext.devMode.ifTrue { args("--dev") }
            ext.botDirectories.ifPresent { args("-d", it.map { p -> file(p) }.joinToString(File.pathSeparator)) }

            doFirst {
                logger.lifecycle("Launching RuneMate client with args {}", args)
            }
        }
    }
}