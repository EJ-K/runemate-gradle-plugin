package com.runemate.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.Compression
import org.gradle.api.tasks.bundling.Tar
import org.gradle.kotlin.dsl.register

class RuneMatePublishPlugin : Plugin<Project> {

    override fun apply(target: Project): Unit = with(target) {
        createTasks()
    }

    /*
      1. generate - create manifests from the declarations
      2. validate - check all manifests are valid
      3. gather sources/resources - copy sources into build directory
      4. create bundle - create distribution .tar.gz
      5. publish - submit bundle for review
    */
    private fun Project.createTasks() {
        tasks.register<GenerateManifests>("generateRuneMateManifests")
        tasks.register<ValidateManifests>("validateRuneMateManifests") {
            dependsOn("generateRuneMateManifests")
        }

        tasks.register<Copy>("prepareDistributionSource") {
            dependsOn("validateRuneMateManifests")
            sourceRoots.forEach {
                from(it)
                into(rootProject.layout.buildDirectory.dir("runemate/sources"))
            }
        }

        tasks.register<Tar>("createSubmissionBundle") {
            dependsOn("prepareDistributionSource")
            compression = Compression.GZIP
            destinationDirectory.set(rootProject.layout.buildDirectory.dir("runemate/distribution"))
            archiveFileName.set("runemate-publish.tar.gz")
            from(rootProject.layout.buildDirectory.dir("runemate/sources").get())
        }

        tasks.register<Submit>("submitForReview") {
            dependsOn("createSubmissionBundle")
        }
    }
}

