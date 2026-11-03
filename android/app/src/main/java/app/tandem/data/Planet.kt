package app.tandem.data

import kotlinx.serialization.Serializable

/** The couple's planet, as the server simulates it (see backend/src/planet.ts). */
@Serializable data class PlanetState(
    val name: String = "Our planet",
    val tz: Int = 0,
    val created: String = "",
    val day: String = "",
    val members: List<String> = emptyList(),
    val suns: Map<String, Double> = emptyMap(),
    val atmosphere: Double = 0.0,
    val ice: Double = 0.0,
    val life: Double = 0.0,
    val stardust: Int = 0,
    val biomes: Map<String, Double> = emptyMap(),
    val quiet: Int = 0,
    val conj: Int = 0,
    val conjStreak: Int = 0,
    val stage: String = "thriving",
    val needsRekindle: Boolean = false,
    val species: Map<String, PlanetSpecies> = emptyMap(),
    val moons: List<PlanetMoon> = emptyList(),
    val landmarks: List<PlanetLandmark> = emptyList(),
    val buildings: List<PlanetBuilding> = emptyList(),
    val hibernate: PlanetSleep? = null,
    val sparks: Map<String, PlanetSpark> = emptyMap(),
    val chronicle: List<PlanetEntry> = emptyList(),
) {
    fun sun(id: String?) = (suns[id ?: ""] ?: 0.0).toFloat()
    fun biome(key: String) = (biomes[key] ?: 25.0).toFloat()
    val warmth: Float get() = if (suns.isEmpty()) 0f else suns.values.average().toFloat()
    val aliveMoons: List<PlanetMoon> get() = moons.filter { it.lost == null }
}

@Serializable data class PlanetSpecies(val state: String = "dormant", val since: String = "")
@Serializable data class PlanetMoon(val id: String = "", val name: String = "", val born: String = "", val lost: String? = null)
@Serializable data class PlanetLandmark(val id: String = "", val name: String = "", val day: String = "", val buried: Boolean? = null)
@Serializable data class PlanetBuilding(val id: String = "", val kind: String = "", val built: String = "", val ruined: String? = null)
@Serializable data class PlanetSleep(val from: String = "", val until: String = "")
@Serializable data class PlanetSpark(val day: String = "", val at: Long = 0, val text: String = "")
@Serializable data class PlanetEntry(val day: String = "", val kind: String = "", val text: String = "")

@Serializable data class PlanetToday(val day: String = "", val shared: Map<String, Boolean> = emptyMap(), val canWarm: Boolean = false, val canHibernate: Boolean = false)
@Serializable data class SpeciesInfo(val key: String = "", val name: String = "", val needs: String = "")
@Serializable data class BuildingInfo(val kind: String = "", val name: String = "", val cost: Int = 0)

@Serializable data class PlanetResponse(
    val planet: PlanetState = PlanetState(),
    val today: PlanetToday = PlanetToday(),
    val catalog: List<SpeciesInfo> = emptyList(),
    val buildings: List<BuildingInfo> = emptyList(),
    val terraformCost: Int = 30,
)

/** The small planet summary /sync carries, for the widget and notifications. */
@Serializable data class PlanetSummary(
    val name: String = "",
    val created: String = "",
    val stage: String = "thriving",
    val quiet: Int = 0,
    val needsRekindle: Boolean = false,
    val atmosphere: Double = 0.0,
    val ice: Double = 0.0,
    val life: Double = 0.0,
    val suns: Map<String, Double> = emptyMap(),
    val sparks: List<String> = emptyList(),
    val biomes: Map<String, Double> = emptyMap(),
    val moons: Int = 0,
    val alive: Int = 0,
)

@Serializable data class PlanetNameBody(val name: String, val moonId: String? = null)
@Serializable data class PlanetBiomeBody(val biome: String)
@Serializable data class PlanetBuildBody(val kind: String)
@Serializable data class PlanetIdBody(val id: String)
@Serializable data class PlanetTextBody(val text: String)
@Serializable data class PlanetDaysBody(val days: Int)
@Serializable class PlanetEmptyBody

/** How the planet's stage reads in the app. */
fun planetStageLabel(stage: String, quiet: Int): String = when (stage) {
    "dimming" -> if (quiet >= 3) "Dimming · $quiet quiet days" else "Dimmed · needs both your sparks"
    "winter" -> "Winter · $quiet quiet days"
    "drift" -> "Drifting · $quiet quiet days"
    "sleeping" -> "Asleep"
    else -> "Thriving"
}
