@file:JvmName("ManifestParser")

package com.runemate.gradle

import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.exc.InvalidFormatException
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.gradle.api.Project
import java.io.File
import java.lang.Exception
import java.math.BigDecimal
import java.time.Duration

internal fun Project.validate(file: File) : BotManifest? {
    val manifest = try {
        parseManifest(file)
    } catch (e: InvalidFormatException) {
        logger.error("Invalid value in {}: {}", file, e.message)
        return null
    } catch (e: Exception) {
        logger.lifecycle("{} was not parseable as a manifest", file, e)
        return null
    }

    if (Rule.INTERNAL_ID.rejects(manifest)) {
        reject(file, "An internal-id has not been set in the bot manifest")
        return null
    }

    if (Rule.DESCRIPTION.rejects(manifest)) {
        reject(file, "Descriptions must be between 1 and 110 characters in size")
        return null
    }

    if (Rule.TAG_LINE.rejects(manifest)) {
        reject(file, "Taglines must be between 1 and 50 characters in size")
        return null
    }

    if (Rule.PRICE_TOO_LOW.rejects(manifest)) {
        reject(file, "Price cannot be negative")
        return null
    }

    if (Rule.PRICE_BAD_ACCESS.rejects(manifest)) {
        reject(file, "Bots with a positive price must have an access level of PUBLIC")
        return null
    }

    if (Rule.TRIAL_INVALID.rejects(manifest)) {
        reject(file, "Trial window and allowance must both be positive durations")
        return null
    }

    if (Rule.TRIAL_NON_PREMIUM.rejects(manifest)) {
        reject(file, "Only bots with a positive price may have a trial")
        return null
    }

    if (Rule.TAGS.rejects(manifest)) {
        reject(file, "Bots may not have more than 50 tags")
        return null
    }

    return manifest
}

internal fun Project.reject(file: File, reason: String) {
    logger.error("Invalid manifest: $reason (${file})")
}

internal data class BotManifest(
    val mainClass: String,
    val name: String,
    val tagline: String,
    val description: String,
    val version: String,
    val internalId: String = mainClass.substring(mainClass.lastIndexOf("/") + 1),
    val compatibility: Set<GameType> = setOf(GameType.OSRS),
    val categories: List<Category> = listOf(Category.OTHER),
    val features: Set<Feature> = emptySet(),
    val access: Access = Access.PUBLIC,
    val hidden: Boolean = false,
    val openSource: Boolean = false,
    val price: BigDecimal = BigDecimal.ZERO,
    val trial: Trial = Trial(),
    val resources: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val obfuscation: Set<String> = emptySet(),
)

internal enum class Access {
    PUBLIC,
    SUPPORTER
}

internal enum class Category {
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

internal data class Feature(val type: FeatureType, val mode: FeatureMode)

internal enum class GameType {
    OSRS
}

internal enum class FeatureType {
    DIRECT_INPUT
}

internal enum class FeatureMode {
    REQUIRED,
    OPTIONAL,
    NONE
}

internal data class Trial(
    val allowance: Duration = Duration.ZERO,
    val window: Duration = Duration.ZERO,
)

private val yamlMapper = JsonMapper.builder(YAMLFactory())
    .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
    .build()
    .registerKotlinModule()
    .registerModule(JavaTimeModule())

private val jsonMapper = JsonMapper.builder()
    .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
    .build()
    .registerKotlinModule()
    .registerModule(JavaTimeModule())

internal enum class ManifestFormat(vararg val extensions: String) {
    YAML("yaml", "yml"),
    JSON("json");

    companion object {
        @JvmStatic fun of(file: File) = entries.first { file.extension in it.extensions }
    }
}

internal fun mapper(format: ManifestFormat) = when(format) {
    ManifestFormat.YAML -> yamlMapper
    ManifestFormat.JSON -> jsonMapper
}

internal fun isMaybeManifest(file: File) = ManifestFormat.entries.any { file.extension in it.extensions }
internal fun parseManifest(file: File, format: ManifestFormat = ManifestFormat.of(file)) = mapper(format).readValue<BotManifest>(file)

internal enum class Rule(private val filter: (BotManifest) -> Boolean) {
    INTERNAL_ID({ it.internalId.isEmpty() }),
    DESCRIPTION({ it.description.isEmpty() || it.description.length > 110 }),
    TAG_LINE({ it.tagline.isEmpty() || it.tagline.length > 50 }),
    PRICE_TOO_LOW({ it.price < BigDecimal.ZERO }),
    PRICE_BAD_ACCESS({ it.price > BigDecimal.ZERO && it.access != Access.PUBLIC }),
    TRIAL_INVALID({ it.trial.window.isNegative || it.trial.allowance.isNegative }),
    TRIAL_NON_PREMIUM({ (!it.trial.window.isZero || !it.trial.allowance.isZero) && it.price <= BigDecimal.ZERO }),
    TAGS({ it.tags.size > 50 });

    fun rejects(bot: BotManifest): Boolean = filter(bot)
}