package com.runemate.gradle

import org.gradle.api.*
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.provideDelegate
import java.math.BigDecimal
import java.time.Duration
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class ManifestDeclaration(@JvmField val name: String) : Named {

    var publish: Boolean = true
    var mainClass: String by required()
    var tagline: String by required()
    var description: String by required()
    var version: String by required()
    var internalId: String by required()
    var compatibility: Set<GameType> = mutableSetOf(GameType.OSRS)
    var categories: Set<Category> = mutableSetOf(Category.OTHER)
    var features: Set<Feature> = mutableSetOf()
    var access: Access = Access.PUBLIC
    var hidden: Boolean = false
    var openSource: Boolean = false

    var price: BigDecimal = BigDecimal.ZERO
    var trial: Trial? = null
    var resources: Set<String> = emptySet()
    var tags: Set<String> = emptySet()
    var obfuscation: Set<String> = emptySet()

    fun tags(vararg tags: String) {
        this.tags = tags.toSet()
    }

    fun categories(vararg category: Category) {
        this.categories = category.toSet()
    }

    fun obfuscation(block: ObfuscationSpec.() -> Unit) {
        val spec = ObfuscationSpec().apply(block)
        this.obfuscation = spec.exclusions
    }

    fun resources(block: ResourcesSpec.() -> Unit) {
        val spec = ResourcesSpec().apply(block)
        this.resources = spec.resources
    }

    fun pricing(block: PricingSpec.() -> Unit) {
        val spec = PricingSpec().apply(block)
        if (spec.price > 0) {
            price = spec.price.toBigDecimal()
            trial = spec.trial
        } else {
            price = BigDecimal.ZERO
            trial = null
        }
    }

    fun features(block: FeatureSpec.() -> Unit) {
        val spec = FeatureSpec().apply(block)
        features = spec.features
    }

    internal fun toManifest() = BotManifest(
        mainClass,
        name,
        tagline,
        description,
        version,
        internalId,
        compatibility,
        categories,
        features,
        access,
        hidden,
        openSource,
        price,
        trial,
        resources,
        tags,
        obfuscation
    )

    override fun getName(): String = name
}

class ObfuscationSpec {

    internal var exclusions: Set<String> = mutableSetOf()

    fun exclude(block: () -> String) {
        exclude(block())
    }

    fun exclude(exclusion: String) {
        exclusions += exclusion
    }

    operator fun String.unaryPlus() {
        exclude(this)
    }

}

class ResourcesSpec {

    internal var resources: Set<String> = mutableSetOf()

    fun include(block: () -> String) {
        include(block())
    }

    fun include(rule: String) {
        resources += rule
    }

    operator fun String.unaryPlus() {
        include(this)
    }

}

class PricingSpec {

    internal var trial: Trial? = null
    var price: Double = 0.00

    fun trial(block: TrialSpec.() -> Unit) {
        val spec = TrialSpec().apply(block)
        trial = Trial(spec.allowance, spec.window)
    }
}

class TrialSpec {

    var allowance: Duration = Duration.ZERO
    var window: Duration = Duration.ZERO
}

class FeatureSpec {
    internal val features: MutableSet<Feature> = mutableSetOf()

    fun required(feature: FeatureType) {
        features += Feature(feature, FeatureMode.REQUIRED)
    }

    fun optional(feature: FeatureType) {
        features += Feature(feature, FeatureMode.OPTIONAL)
    }

    fun required(feature: () -> FeatureType) {
        features += Feature(feature(), FeatureMode.REQUIRED)
    }

    fun optional(feature: () -> FeatureType) {
        features += Feature(feature(), FeatureMode.OPTIONAL)
    }
}

private inline fun <reified T> ManifestDeclaration.required() = RequiredManifestElement<T>(this)

private class RequiredManifestElement<T>(private val dec: ManifestDeclaration) : ReadWriteProperty<Any?, T> {

    private var value: T? = null

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return value ?: throw GradleException("Missing property '${property.name}' in ${dec.name}")
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        this.value = value
    }

    override fun toString(): String = "ManifestProperty(${if (value != null) "value=$value" else "value not initialized yet"})"
}