package app.tandem.data

import kotlinx.serialization.Serializable

/** The couple's cosmos, as the server keeps it (see backend/src/cosmos.ts): stardust, storages, pets and the galaxy seed. */
@Serializable data class CosmosState(
    val members: List<String> = emptyList(),
    val tz: Int = 0,
    val stardust: Int = 0,
    val lifetime: Int = 0,
    val comets: Int = 0,
    val seed: Long = 1,
    val candidates: List<Long> = emptyList(),
    val day: String = "",
    val streak: Int = 0,
    val milestones: List<String> = emptyList(),
    val stores: Map<String, PetStore> = emptyMap(),
    val pets: List<Pet> = emptyList(),
    val log: List<CosmosEntry> = emptyList(),
)

@Serializable data class PetStore(val food: Int = 0, val drink: Int = 0, val love: Int = 0)

@Serializable data class Pet(
    val id: String = "",
    val species: String = "cat",
    val breed: String = "ginger",
    val coat: Int = 0,
    val name: String = "",
    val born: Long = 0,
    val hunger: Double = 80.0,
    val thirst: Double = 80.0,
    val love: Double = 70.0,
    val bond: Map<String, Double> = emptyMap(),
    val mood: String = "happy",
    val patReadyAt: Long = 0,
    /** Growing up: growth points, the stage (0 baby, 1 young, 2 adult) and 0..1 across the whole way. */
    val xp: Int = 0,
    val stage: Int = 2,
    val growth: Double = 1.0,
    /** Per person: how they've cared (counts, care streak in days). Only in the full /cosmos answer. */
    val care: Map<String, PetCare> = emptyMap(),
    /** This pet's own small history, newest first. */
    val moments: List<PetMoment> = emptyList(),
) {
    fun bondWith(id: String?) = (bond[id ?: ""] ?: 30.0).toFloat()
    /** The person this pet likes best, when it clearly has a favourite. */
    fun favourite(): String? {
        val sorted = bond.entries.sortedByDescending { it.value }
        if (sorted.size < 2) return null
        return if (sorted[0].value - sorted[1].value >= 12) sorted[0].key else null
    }
}

@Serializable data class PetCare(val feed: Int = 0, val water: Int = 0, val cuddle: Int = 0, val pat: Int = 0, val day: String = "", val streak: Int = 0)
@Serializable data class PetMoment(val at: Long = 0, val kind: String = "", val text: String = "", val user: String? = null)

/** Bond levels, as the server counts them: from this bond on, this name, and what it brings. */
object Bond {
    val levels = listOf(0 to "Stranger", 15 to "Acquaintance", 30 to "Friend", 50 to "Close friend", 70 to "Best friend", 90 to "Soulmate")
    val perks = listOf(
        "Just getting to know each other",
        "Knows your voice and says your name",
        "Free pats every 20 minutes instead of 30",
        "Your food, water and cuddles go 20% further",
        "Shows you a trick when you tap it",
        "Brings you a gift once a day when you pat it",
    )
    fun level(b: Float): Int = levels.indexOfLast { b >= it.first }.coerceAtLeast(0)
    fun name(b: Float) = levels[level(b)].second
    /** 0..1 of the way to the next level (1 at the top). */
    fun progress(b: Float): Float { val l = level(b); if (l >= levels.lastIndex) return 1f; val a = levels[l].first; val z = levels[l + 1].first; return ((b - a) / (z - a)).coerceIn(0f, 1f) }
}

/** What each species is called as it grows. */
fun stageName(species: String, stage: Int): String = when (species) {
    "cat" -> listOf("Kitten", "Young cat", "Cat")
    "dog" -> listOf("Puppy", "Young dog", "Dog")
    "hamster" -> listOf("Hamster pup", "Young hamster", "Hamster")
    "rabbit" -> listOf("Bunny", "Young rabbit", "Rabbit")
    else -> listOf("Chick", "Fledgling", "Parrot")
}[stage.coerceIn(0, 2)]

@Serializable data class CosmosEntry(val at: Long = 0, val kind: String = "", val text: String = "")

@Serializable data class CosmosResponse(
    val cosmos: CosmosState = CosmosState(),
    val me: PetStore = PetStore(),
    val species: Map<String, List<String>> = emptyMap(),
    val adoptCost: Int? = null,
    val maxPets: Int = 3,
)

/** The small cosmos summary /sync carries, for Today, widgets and notifications. */
@Serializable data class CosmosSummary(
    val stardust: Int = 0,
    val lifetime: Int = 0,
    val comets: Int = 0,
    val seed: Long = 1,
    val pets: List<Pet> = emptyList(),
)

@Serializable data class AdoptBody(val species: String, val breed: String, val name: String, val coat: Int = 0)
@Serializable data class PetBody(val petId: String)
@Serializable data class PetNameBody(val petId: String, val name: String)
@Serializable data class SeedBody(val seed: Long)
@Serializable class CosmosEmptyBody

/** Names people read, for the species and breeds the server knows. */
object PetCatalog {
    val species = listOf("cat", "dog", "hamster", "rabbit", "parrot")
    fun speciesName(s: String) = when (s) { "cat" -> "Cat"; "dog" -> "Dog"; "hamster" -> "Hamster"; "rabbit" -> "Rabbit"; else -> "Parrot" }
    val breeds = mapOf(
        "cat" to listOf("persian", "scottish", "british", "siamese", "ginger", "tuxedo"),
        "dog" to listOf("golden", "shiba", "corgi", "pomeranian", "husky"),
        "hamster" to listOf("syrian", "winterwhite", "panda", "robo"),
        "rabbit" to listOf("hollandlop", "dwarf", "lionhead", "rex"),
        "parrot" to listOf("budgie", "cockatiel", "lovebird", "conure"),
    )
    fun breedName(b: String) = when (b) {
        "persian" -> "Persian"; "scottish" -> "Scottish Fold"; "british" -> "British Shorthair"; "siamese" -> "Siamese"
        "ginger" -> "Ginger Tabby"; "tuxedo" -> "Tuxedo"
        "golden" -> "Golden Retriever"; "shiba" -> "Shiba Inu"; "corgi" -> "Corgi"; "pomeranian" -> "Pomeranian"; "husky" -> "Husky"
        "syrian" -> "Syrian"; "winterwhite" -> "Winter White"; "panda" -> "Panda"; "robo" -> "Roborovski"
        "hollandlop" -> "Holland Lop"; "dwarf" -> "Netherland Dwarf"; "lionhead" -> "Lionhead"; "rex" -> "Rex"
        "budgie" -> "Budgie"; "cockatiel" -> "Cockatiel"; "lovebird" -> "Lovebird"; "conure" -> "Sun Conure"
        else -> b.replaceFirstChar { it.uppercase() }
    }
    fun moodLabel(m: String) = when (m) { "happy" -> "Happy"; "okay" -> "Doing okay"; "grumpy" -> "Grumpy"; else -> "Sulking" }
    val suggestedNames = listOf("Mochi", "Biscuit", "Pumpkin", "Tofu", "Noodle", "Peach", "Pudding", "Bean", "Miso", "Waffle", "Sesame", "Clover", "Pepper", "Kiwi", "Dumpling", "Olive")
}
