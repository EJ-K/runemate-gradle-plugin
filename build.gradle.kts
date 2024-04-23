import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    kotlin("jvm") version "1.9.0"
    `kotlin-dsl`
    `java-gradle-plugin`
    id("com.gradle.plugin-publish") version "1.2.1"
}

group = "com.runemate"
version = "1.0"

gradlePlugin {
    website = "https://www.runemate.com"
    vcsUrl = "git@github.com:RuneMate/runemate-gradle-plugin.git"
    plugins {
        create("runemateGradlePlugin") {
            id = "com.runemate.gradle-plugin"
            implementationClass = "com.runemate.gradle.RuneMatePlugin"
            description = "Configures your Gradle project for use with RuneMate"
            displayName = "RuneMate Development Plugin"
            tags = setOf("runemate", "development", "plugin")
        }
    }
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(gradleApi())
    implementation("org.openjfx:javafx-plugin:0.1.0")

    implementation(platform("com.fasterxml.jackson:jackson-bom:2.14.0"))
    implementation("com.fasterxml.jackson.core:jackson-core")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.core:jackson-annotations")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.compileKotlin {
    compilerOptions {
        languageVersion = KotlinVersion.KOTLIN_1_9
        apiVersion = KotlinVersion.KOTLIN_1_9
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}


publishing {
    repositories {
        mavenLocal()
    }
}