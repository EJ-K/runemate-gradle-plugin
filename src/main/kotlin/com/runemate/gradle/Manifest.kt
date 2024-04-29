package com.runemate.gradle

import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.exc.InvalidFormatException
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.gradle.api.GradleException
import org.gradle.api.Project
import java.io.File
import java.lang.Exception
import java.math.BigDecimal
import java.time.Duration

internal data class BotManifest(
    val mainClass: String,
    val name: String,
    val tagline: String,
    val description: String,
    val version: String,
    val internalId: String = mainClass.substring(mainClass.lastIndexOf("/") + 1),
    val compatibility: Set<GameType> = setOf(GameType.OSRS),
    val categories: Set<Category> = setOf(Category.OTHER),
    val features: Set<Feature> = emptySet(),
    val access: Access = Access.PUBLIC,
    val hidden: Boolean = false,
    val openSource: Boolean = false,
    val price: BigDecimal = BigDecimal.ZERO,
    val trial: Trial? = null,
    val resources: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val obfuscation: Set<String> = emptySet(),
)

enum class Access {
    PUBLIC,
    SUPPORTER
}

enum class Category {
    AGILITY,
    COMBAT,
    CONSTRUCTION,
    COOKING,
    CRAFTING,
    DEVELOPER_TOOLS,
    DIVINATION,
    DUNGEONEERING,
    INVENTION,
    FARMING,
    FIREMAKING,
    FISHING,
    FLETCHING,
    HERBLORE,
    HUNTER,
    MAGIC,
    MINIGAMES,
    MINING,
    MONEYMAKING,
    OTHER,
    PRAYER,
    QUESTING,
    RUNECRAFTING,
    SLAYER,
    SMITHING,
    SUMMONING,
    THIEVING,
    WOODCUTTING,
    BOSSING
}

data class Feature(val type: FeatureType, val mode: FeatureMode)

enum class GameType {
    OSRS
}

enum class FeatureType {
    DIRECT_INPUT;

    fun optional() = Feature(this, FeatureMode.REQUIRED)

    fun required()  = Feature(this, FeatureMode.REQUIRED)
}

enum class FeatureMode {
    REQUIRED,
    OPTIONAL,
    NONE
}

data class Trial(
    val allowance: Duration = Duration.ZERO,
    val window: Duration = Duration.ZERO,
)

internal fun Project.parseManifest(file: File) : BotManifest? {
    try {
        return _parseManifest(file)
    } catch (e: InvalidFormatException) {
        logger.error("Invalid value in {}: {}", file, e.message)
    } catch (e: Exception) {
        logger.lifecycle("{} was not parseable as a manifest", file, e)
    }
    return null
}

internal fun validateManifest(source: String, manifest: BotManifest) : BotManifest {
    if (Rule.INTERNAL_ID.rejects(manifest)) {
        reject(source, "An internal-id has not been set in the bot manifest")
    }

    if (Rule.DESCRIPTION.rejects(manifest)) {
        reject(source, "Descriptions must be between 1 and 110 characters in size")
    }

    if (Rule.TAG_LINE.rejects(manifest)) {
        reject(source, "Taglines must be between 1 and 50 characters in size")
    }

    if (Rule.PRICE_TOO_LOW.rejects(manifest)) {
        reject(source, "Price cannot be negative")
    }

    if (Rule.PRICE_BAD_ACCESS.rejects(manifest)) {
        reject(source, "Bots with a positive price must have an access level of PUBLIC")
    }

    if (Rule.TRIAL_INVALID.rejects(manifest)) {
        reject(source, "Trial window and allowance must both be positive durations")
    }

    if (Rule.TRIAL_NON_PREMIUM.rejects(manifest)) {
        reject(source, "Only bots with a positive price may have a trial")
    }

    if (Rule.TAGS.rejects(manifest)) {
        reject(source, "Bots may not have more than 50 tags")
    }

    return manifest
}

internal fun reject(source: String? = null, reason: String) {
    throw GradleException("Invalid manifest: $reason ${source?.let { "($it)" }}")
}

internal enum class ManifestFormat(vararg val extensions: String) {
    YAML("yaml", "yml"),
    JSON("json");

    companion object {
        @JvmStatic fun of(file: File) = entries.first { file.extension in it.extensions }
    }
}

internal fun isManifest(file: File) = ManifestFormat.entries.any { file.extension in it.extensions } && "mainClass" in file.readText()

internal val yamlMapper = JsonMapper.builder(YAMLFactory())
    .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
    .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
    .build()
    .registerKotlinModule()
    .registerModule(JavaTimeModule())

internal val jsonMapper = JsonMapper.builder()
    .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
    .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
    .build()
    .registerKotlinModule()
    .registerModule(JavaTimeModule())

internal fun mapper(format: ManifestFormat) = when(format) {
    ManifestFormat.YAML -> yamlMapper
    ManifestFormat.JSON -> jsonMapper
}
private fun _parseManifest(file: File, format: ManifestFormat = ManifestFormat.of(file)) = mapper(format).readValue<BotManifest>(file)

internal enum class Rule(private val filter: (BotManifest) -> Boolean) {
    INTERNAL_ID({ it.internalId.isEmpty() }),
    DESCRIPTION({ it.description.isEmpty() || it.description.length > 110 }),
    TAG_LINE({ it.tagline.isEmpty() || it.tagline.length > 50 }),
    PRICE_TOO_LOW({ it.price < BigDecimal.ZERO }),
    PRICE_BAD_ACCESS({ it.price > BigDecimal.ZERO && it.access != Access.PUBLIC }),
    TRIAL_INVALID({ it.trial != null && (it.trial.window.isNegative || it.trial.allowance.isNegative) }),
    TRIAL_NON_PREMIUM({ it.trial != null && (!it.trial.window.isZero || !it.trial.allowance.isZero) && it.price <= BigDecimal.ZERO }),
    TAGS({ it.tags.size > 50 });

    fun rejects(bot: BotManifest): Boolean = filter(bot)
}