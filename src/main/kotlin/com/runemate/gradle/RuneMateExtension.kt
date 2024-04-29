package com.runemate.gradle

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.kotlin.dsl.container
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.setProperty
import javax.inject.Inject

open class RuneMateExtension(@Inject private val project: Project) {

    /**
     * Tells the client to attempt automatic login
     *
     * Default: false
     */
    val autoLogin: Property<Boolean> = project.objects.property()

    /**
     * Tells the client to launch in developer mode
     *
     * Default: true
     */
    val devMode: Property<Boolean> = project.objects.property()

    /**
     * Tells Gradle which version of runemate-game-api to fetch from Maven
     *
     * Default: + (latest)
     */
    val apiVersion: Property<String> = project.objects.property()

    /**
     * Tells Gradle which version of runemate-client to fetch from Maven
     *
     * Default: +/LATEST
     */
    val clientVersion: Property<String> = project.objects.property()

    /**
     * Tells the client which directories to scan for bots
     *
     * Default: $project/build/libs
     */
    val botDirectories: SetProperty<Any> = project.objects.setProperty()


    /**
     * Tells Gradle to allow dependency resolution for external dependencies. RuneMate does not support external dependencies
     * when building for the bot store. If you intend only to run bots locally then you can disable this check.
     *
     * If this check is enabled, you will be responsible for building the JAR containing the dependencies, the plugin will not handle this for you.
     *
     * Default: false
     */
    val allowExternalDependencies: Property<Boolean> = project.objects.property()


    val submissionKey = project.objects.property<String>()

    val manifests: NamedDomainObjectContainer<ManifestDeclaration> = project.container()

    fun manifests(action: Action<NamedDomainObjectContainer<ManifestDeclaration>>) {
        action.execute(manifests)
    }

    fun NamedDomainObjectContainer<ManifestDeclaration>.createManifest(name: String, block: ManifestDeclaration.() -> Unit) = create(name, block)
}