package com.runemate.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Compression
import org.gradle.api.tasks.bundling.Tar
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.com.google.gson.JsonParseException
import org.jetbrains.kotlin.com.google.gson.JsonParser
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import kotlin.io.path.relativeTo
import kotlin.io.path.writeText

private val Project.runeMateBuildDir get() = rootProject.layout.buildDirectory.dir("runemate")
private val Project.runeMateSourcesDir get() = runeMateBuildDir.map { it.dir("sources") }
private val Project.runeMateDistributionDir get() = runeMateBuildDir.map { it.dir("distribution") }
private val Project.runeMateManifestDir get() = runeMateBuildDir.map { it.dir("sources") }.map { it.dir(".runemate") }

private const val TASK_GENERATE_MANIFESTS = "generateManifests"
private const val TASK_VALIDATE_MANIFESTS = "validateManifests"
private const val TASK_COLLECT_SOURCES = "collectSubmissionSources"
private const val TASK_BUILD_SUBMISSION = "buildSubmission"

/*
  1. generate - create manifests from the declarations
  2. validate - check all manifests are valid
  3. gather sources/resources - copy sources into build directory
  4. create bundle - create distribution .tar.gz
  5. publish - submit bundle for review
*/
internal fun Project.createPublishTasks() {
    tasks.register<GenerateManifests>(TASK_GENERATE_MANIFESTS)
    tasks.register<ValidateManifests>(TASK_VALIDATE_MANIFESTS) {
        dependsOn(TASK_GENERATE_MANIFESTS)
    }

    tasks.register<Copy>(TASK_COLLECT_SOURCES) {
        dependsOn(TASK_VALIDATE_MANIFESTS)
        sourceRoots.forEach {
            from(it)
            into(runeMateSourcesDir)
        }
    }

    //These two tasks should only be registered to the root project
    if (rootProject.tasks.withType<Submit>().isEmpty()) {
        val buildSubmission = rootProject.tasks.register<Tar>(TASK_BUILD_SUBMISSION) {
            //Always run a clean and recollect sources to ensure bundle is up-to-date
            dependsOn("clean")
            allprojects.mapNotNull { it.tasks.findByName(TASK_COLLECT_SOURCES) }.forEach { dependsOn(it) }

            compression = Compression.GZIP
            destinationDirectory.set(runeMateDistributionDir)
            archiveFileName.set("runemate-publish.tar.gz")
            from(runeMateSourcesDir)
        }

        rootProject.tasks.register<Submit>("submitForReview") {
            dependsOn(buildSubmission)
        }
    }
}

open class GenerateManifests : DefaultTask() {

    @TaskAction
    fun run() {
        val ext = project.extensions.getByType<RuneMateExtension>()
        val manifests = ext.manifests
            .filter { it.publish }
            .map { validateManifest("generate ${it.name}", it.toManifest()) }
            .associateBy { it.name.lowercase().replace(Regex("[^\\\\dA-Za-z0-9 ]"), "").replace(" ", "-") }
            .mapValues { jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(it.value) }

        if (manifests.isNotEmpty()) {
            val directory = project.runeMateManifestDir.get()
            manifests.forEach { (key, manifest) ->
                try {
                    val file = directory.file("$key.manifest.json").asFile.toPath()
                    logger.debug("Writing manifest {}", file.relativeTo(project.rootDir.toPath()))
                    Files.createDirectories(directory.asFile.toPath())
                    file.writeText(manifest)
                } catch (e: Exception) {
                    logger.warn("Failed to write manifest '$key'", e)
                }
            }

            logger.debug("Generated {} manifests: {}", manifests.size, manifests.keys.map { "$it.manifest.json" })
        }
    }

}

open class ValidateManifests : DefaultTask() {

    init {
        group = "runemate publish"
        description = "Scans project source for manifests and validates them"
    }

    @TaskAction
    fun run() {
        //Validate manifests located in the project source (not including generated manifests)
        val manifests = project.sourceRoots
            .flatMap { project.fileTree(it).files }
            .filter { isManifest(it) }
            .associate { f -> "file ${f.toPath().relativeTo(project.rootDir.toPath())}" to project.parseManifest(f) }
            .filterValues { it != null }
            .onEach { (key, manifest) -> validateManifest(key, manifest!!) }

        logger.debug("Validated contents of ${manifests.size} manifests")

        //Check for duplicate internalIds
        for (manifest in manifests.values.distinctBy { it?.internalId }) {
            val count = manifests.count { it.value?.internalId == manifest?.internalId }
            if (count > 1) {
                val duplicates = manifests.filterValues { it?.internalId == manifest?.internalId }.keys
                throw GradleException("$count manifests have the same internalId '${manifest?.internalId}': $duplicates")
            }
        }
    }

}

open class Submit : DefaultTask() {

    init {
        group = "runemate publish"
        description = "Submits all source code to the store"
    }

    @TaskAction
    fun run() {
        val ext = project.extensions.getByType<RuneMateExtension>()
        val file = project.tasks.getByName(TASK_BUILD_SUBMISSION).outputs.files.singleFile

        logger.warn("w: Submission via Gradle is an incubating feature")
        logger.lifecycle("Submitting project '${project.name}' for review")

        val submissionKey = ext.submissionKey.get()
        val request = HttpRequest.newBuilder()
            .header("Authentication", "Private-Token $submissionKey")
            .POST(HttpRequest.BodyPublishers.ofFile(file.toPath()))
            .uri(URI.create("https://www20230922135246.runemate.com/developer/submit")) //TODO replace when beta period ends
            .build()

        val client = HttpClient.newHttpClient()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() == 200) {
            logger.lifecycle("Submission successful - you will receive a forum message when your submission has been reviewed.")
        } else if (response.statusCode() == 404) {
            throw GradleException("The submission system is currently offline - please try again later.")
        } else {
            try {
                val json = JsonParser.parseString(response.body()).asJsonObject
                if (json.has("error")) {
                    throw GradleException("Submission rejected: ${json.get("error").asString}")
                } else {
                    throw GradleException("Submission rejected: Unknown reason. Please contact the RuneMate team.")
                }
            } catch (e: JsonParseException) {
                throw GradleException("Invalid response: ${response.statusCode()}", e)
            }
        }
    }

}