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
) {
    fun bondWith(id: String?) = (bond[id ?: ""] ?: 30.0).toFloat()
    /** The person this pet likes best, when it clearly has a favourite. */
    fun favourite(): String? {
        val sorted = bond.entries.sortedByDescending { it.value }
        if (sorted.size < 2) return null
        return if (sorted[0].value - sorted[1].value >= 12) sorted[0].key else null
    }
}

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
