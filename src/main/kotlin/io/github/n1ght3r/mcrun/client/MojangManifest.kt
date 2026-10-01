package io.github.n1ght3r.mcrun.client

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** The parts of a Mojang version JSON that mc-run needs. Parsed by hand: `arguments` is polymorphic. */
data class VersionInfo(
    val id: String,
    val type: String,
    val mainClass: String,
    val javaMajor: Int?,
    val client: Artifact,
    val libraries: List<Library>,
    val assetIndex: AssetIndexRef,
    val jvmArguments: List<Argument>,
    val gameArguments: List<Argument>,
)

data class Artifact(val path: String?, val url: String, val sha1: String, val size: Long?)

data class AssetIndexRef(val id: String, val url: String, val sha1: String, val size: Long?)

/** `group:artifact:version[:classifier]` plus its download. [artifact] is null for legacy-only entries. */
data class Library(val name: String, val artifact: Artifact?, val rules: List<Rule>?) {
    val classifier: String? get() = name.split(':').getOrNull(3)

    /** `group:artifact:version`, used to group the per-arch variants of one native library. */
    val baseCoordinates: String get() = name.split(':').take(3).joinToString(":")

    val isNative: Boolean get() = classifier?.startsWith("natives-") == true
}

/** One entry of `arguments.game` / `arguments.jvm`: either a plain string or `{ rules, value }`. */
data class Argument(val values: List<String>, val rules: List<Rule>?)

data class Rule(val action: String, val os: OsRule?, val features: Map<String, Boolean>)

data class OsRule(val name: String?, val arch: String?, val version: String?, val versionMin: String?, val versionMax: String?)

/** One entry of `version_manifest_v2.json`. */
data class ManifestEntry(val id: String, val type: String, val url: String, val sha1: String)

object MojangManifest {
    const val URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"

    fun parseManifest(json: String): List<ManifestEntry> =
        JsonParser.parseString(json).asJsonObject.getAsJsonArray("versions").map {
            val obj = it.asJsonObject
            ManifestEntry(obj.str("id")!!, obj.str("type")!!, obj.str("url")!!, obj.str("sha1")!!)
        }

    fun parseVersion(json: String): VersionInfo {
        val root = JsonParser.parseString(json).asJsonObject
        val arguments = root.getAsJsonObject("arguments")
            ?: throw IllegalArgumentException(
                "Version ${root.str("id")} uses legacy `minecraftArguments`; mc-run supports 1.13 and newer only",
            )
        val assetIndex = root.getAsJsonObject("assetIndex")
        return VersionInfo(
            id = root.str("id")!!,
            type = root.str("type") ?: "release",
            mainClass = root.str("mainClass")!!,
            javaMajor = root.getAsJsonObject("javaVersion")?.get("majorVersion")?.asInt,
            client = parseArtifact(root.getAsJsonObject("downloads").getAsJsonObject("client")),
            libraries = root.getAsJsonArray("libraries").map { parseLibrary(it.asJsonObject) },
            assetIndex = AssetIndexRef(
                id = assetIndex.str("id")!!,
                url = assetIndex.str("url")!!,
                sha1 = assetIndex.str("sha1")!!,
                size = assetIndex.get("size")?.asLong,
            ),
            jvmArguments = parseArguments(arguments.getAsJsonArray("jvm")),
            gameArguments = parseArguments(arguments.getAsJsonArray("game")),
        )
    }

    private fun parseLibrary(obj: JsonObject) = Library(
        name = obj.str("name")!!,
        artifact = obj.getAsJsonObject("downloads")?.getAsJsonObject("artifact")?.let(::parseArtifact),
        rules = obj.getAsJsonArray("rules")?.let(::parseRules),
    )

    private fun parseArtifact(obj: JsonObject) = Artifact(
        path = obj.str("path"),
        url = obj.str("url")!!,
        sha1 = obj.str("sha1")!!,
        size = obj.get("size")?.asLong,
    )

    private fun parseArguments(array: JsonArray?): List<Argument> = array?.map { element ->
        if (element.isJsonPrimitive) {
            Argument(listOf(element.asString), null)
        } else {
            val obj = element.asJsonObject
            Argument(stringOrList(obj.get("value")), obj.getAsJsonArray("rules")?.let(::parseRules))
        }
    } ?: emptyList()

    private fun parseRules(array: JsonArray): List<Rule> = array.map { element ->
        val obj = element.asJsonObject
        val os = obj.getAsJsonObject("os")?.let { o ->
            val range = o.getAsJsonObject("versionRange")
            OsRule(o.str("name"), o.str("arch"), o.str("version"), range?.str("min"), range?.str("max"))
        }
        val features = obj.getAsJsonObject("features")?.entrySet()?.associate { it.key to it.value.asBoolean } ?: emptyMap()
        Rule(obj.str("action") ?: "allow", os, features)
    }

    private fun stringOrList(element: JsonElement?): List<String> = when {
        element == null || element.isJsonNull -> emptyList()
        element.isJsonArray -> element.asJsonArray.map { it.asString }
        else -> listOf(element.asString)
    }

    private fun JsonObject.str(key: String): String? = get(key)?.takeUnless { it.isJsonNull }?.asString
}
