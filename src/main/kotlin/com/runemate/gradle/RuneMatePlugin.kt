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
import java.nio.file.Files

class RuneMatePlugin : Plugin<Project> {

    override fun apply(target: Project): Unit = with(target) {
        configurePlugins()
        configureDependencies()
        configureTasks()
    }

    private fun Project.configurePlugins() {
        configurations.create(RUNEMATE) {
            configurations["implementation"].extendsFrom(this)
        }

        extensions.create<RuneMatePluginExtension>(RUNEMATE).apply {
            autoLogin.convention(false)
            devMode.convention(true)
            apiVersion.convention("+")
            clientVersion.convention("+")
            botDirectories.convention(setOf(layout.buildDirectory.dir("libs")))
            validateExternalDependencies.convention(true)
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
                        val rm = project.extensions.getByType<RuneMatePluginExtension>()
                        when (requested.name) {
                            "runemate-client" -> useVersion(rm.clientVersion.get())
                            "runemate-game-api" -> useVersion(rm.apiVersion.get())
                        }
                    }
                }
            }
        }

        val validate = extensions.getByType<RuneMatePluginExtension>().validateExternalDependencies.get()
        configurations.getByName("runtimeClasspath") {
            resolutionStrategy.eachDependency {
                val key = "${requested.group}:${requested.name}"
                if (dependencyAllowList.none { it.matches(key) }) {
                    if (validate) {
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
        tasks.register(RUNEMATE, JavaExec::class) {
            group = RUNEMATE
            classpath = configurations.getByName("runtimeClasspath")
            mainClass.set("com.runemate.client.boot.Boot")

            logger.lifecycle("Classpath {}", configurations.getByName(RUNEMATE).joinToString { it.name })

            arrayOf("java.base/java.lang.reflect", "java.base/java.nio", "java.base/sun.nio.ch", "java.base/java.util.regex").forEach {
                jvmArgs("--add-opens=${it}=ALL-UNNAMED")
            }

            //Handle arguments
            val ext = project.extensions.getByType<RuneMatePluginExtension>()
            ext.autoLogin.ifTrue { args("--login") }
            ext.devMode.ifTrue { args("--dev") }
            ext.botDirectories.ifPresent { args("-d", it.map { p -> file(p) }.joinToString(File.pathSeparator)) }

            logger.lifecycle("Launching RuneMate client with args {}", args)
        }

        val runemateDir = layout.projectDirectory.dir(".runemate")
        tasks.findByName("processResources")?.doLast {
            val sourceListFile = runemateDir.file("sources").asFile
            Files.createDirectories(runemateDir.asFile.toPath())
            sourceListFile.writeText(project.sourceFiles.joinToString(System.lineSeparator()))
        }

        tasks.findByName("clean")?.doLast {
            delete(runemateDir)
        }

        tasks.register("validateManifests") {
            group = RUNEMATE
            description = "Scans source code for manifests and reports on validation warnings"
            doLast {
                val manifests = project.sourceFiles
                    .map { it.toFile() }
                    .filter { isMaybeManifest(it) }
                    .mapNotNull { validate(it) }

                logger.lifecycle("Validated contents of {} manifests", manifests.size)

                for (internalId in manifests.map { it.internalId }.distinct()) {
                    val count = manifests.count { it.internalId == internalId }
                    if (count > 1) {
                        throw GradleException("$count manifests have the same internalId: $internalId")
                    }
                }
            }
        }
    }
}