package com.runemate.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.com.google.gson.JsonParseException
import org.jetbrains.kotlin.com.google.gson.JsonParser
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import kotlin.io.path.relativeTo
import kotlin.io.path.writeText

open class GenerateManifests : DefaultTask() {

    @TaskAction
    fun run() {
        val ext = project.extensions.getByType<RuneMateExtension>()
        val manifests = ext.manifests
            .filter { it.publish }
            .map { validateManifest("generate ${it.name}", it.toManifest()) }
            .associateBy { it.name.lowercase().replace(Regex("[^\\\\dA-Za-z0-9 ]"), "").replace(" ", "-") }
            .mapValues { yamlMapper.writeValueAsString(it.value) }

        if (manifests.isNotEmpty()) {
            logger.lifecycle("Generating ${manifests.size} manifests")

            val directory = project.layout.buildDirectory.dir("runemate/sources").get()
            manifests.forEach { (key, manifest) ->
                try {
                    val file = directory.file("$key.manifest.yml").asFile.toPath()
                    Files.createDirectories(directory.asFile.toPath())
                    file.writeText(manifest)
                } catch (e: Exception) {
                    logger.warn("Failed to generate manifest", e)
                }
            }
        }
    }

}

open class ValidateManifests : DefaultTask() {

    @TaskAction
    fun run() {
        //Validate manifests located in the project source (not including generated manifests)
        val manifests = project.sourceRoots
            .flatMap { project.fileTree(it).files }
            .filter { isManifest(it) }
            .associate { f -> "file ${f.toPath().relativeTo(project.rootDir.toPath())}" to project.parseManifest(f) }
            .filterValues { it != null }
            .onEach { (key, manifest) -> validateManifest(key, manifest!!) }

        logger.lifecycle("Validated contents of ${manifests.size} manifests")

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
    }

    @TaskAction
    fun run() {
        val ext = project.extensions.getByType<RuneMateExtension>()
        val file = project.tasks.getByName("createSubmissionBundle").outputs.files.singleFile
        logger.lifecycle("Submitting project '${project.name}' for review")

//        val submissionKey = ext.submissionKey.get()
        val request = HttpRequest.newBuilder()
//            .headers("SubmissionKey: $submissionKey") //TODO authentication
            .POST(HttpRequest.BodyPublishers.ofFile(file.toPath()))
            .uri(URI.create("https://www.runemate.com"))
            .build()

        val client = HttpClient.newHttpClient()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() == 200) {
            logger.lifecycle("Submission successful - you will receive a forum message when your submission has been reviewed.")
        } else {
            try {
                val json = JsonParser.parseString(response.body()).asJsonObject
                if (json.has("error")) {
                    logger.warn("Submission rejected: ${json.get("error").asString}")
                }
            } catch (e: JsonParseException) {
                logger.error("Failed to parse response body: ${response.body()}")
            }
        }
    }

}