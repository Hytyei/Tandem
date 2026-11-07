package app.tandem.ui.pets

import app.tandem.data.Pet
import java.util.Calendar
import kotlin.random.Random

/**
 * What the pets say, in comic bubbles: dry, a little sarcastic, fond underneath. Lines are picked by what
 * is going on (hungry, ignored by one of you, a favourite, just fed, late at night) and sometimes speak to
 * one of you by name. {me} is the person holding the phone, {them} the partner, {pet} the pet.
 */
object PetLines {
    private val hungry = listOf(
        "I'm not saying I'm starving. I'm saying I can see through time",
        "Food. Now. Please. In that order",
        "{me}, the bowl is empty. I checked. Twice. With my face",
        "I have been fed exactly never today",
        "Is this what independence feels like? I hate it",
        "Remember snacks? I do. Fondly",
    )
    private val thirsty = listOf(
        "My water bowl is a desert. I'm basically a cactus now",
        "Hydration is a human right. Also a pet right. Look it up",
        "{them} would have given me water by now. Just saying",
        "I'm parched, {me}. Parched",
    )
    private val lonely = listOf(
        "You two look busy. I'll just sit here. Alone. Being cute for no one",
        "When was the last cuddle? I've lost count. It's one. I counted",
        "I'm emotionally available. Unlike some people",
        "Pet me or I start a podcast about this",
    )
    private val sulking = listOf(
        "I'm not talking to you. This doesn't count",
        "Oh, you remembered me. How nice",
        "I've written a list of complaints. It's long. You're both on it",
        "Don't look at me. I'm sulking. It's a whole thing",
        "Fine. Everything's fine. I'm fine",
    )
    private val happy = listOf(
        "Life is good. Don't tell anyone, I have a reputation",
        "You two are alright. Don't let it go to your heads",
        "Ten out of ten. Would be adopted again",
        "I'm in a great mood. Enjoy it while it lasts",
        "Full belly, warm humans. Peak existence",
        "Is it nap time? It feels like nap time",
    )
    private val okay = listOf(
        "I'm okay. Could be better. Could be snacks",
        "Not bad. Not great. Very medium",
        "I've been thinking about treats. That's it, that's the thought",
        "Somebody say something nice to me",
    )
    private val favourite = listOf(
        "{fav} is my favourite. Sorry {other}. Not sorry",
        "Between you and me, {fav} gives better cuddles",
        "{other}, take notes from {fav}",
        "I love you both equally. {fav} slightly more equally",
    )
    private val ignoredByMe = listOf(
        "{me}, do I know you? You look familiar",
        "{them} feeds me, {them} pets me. What do you do, {me}",
        "Oh, {me}. Didn't see you there. For days",
    )
    private val ignoredByThem = listOf(
        "Tell {them} I said hi. Since they never visit",
        "Has anyone seen {them}? Asking for me",
        "{them} owes me at least four cuddles",
    )
    private val night = listOf(
        "It's late. Shouldn't you two be asleep. Or cuddling me",
        "Why are you up. Go to bed. Take me with you",
        "Night shift again? I'll supervise",
    )
    private val morning = listOf(
        "Good morning. Breakfast was supposed to be three minutes ago",
        "Rise and shine. Mostly shine. I'll do the rising",
    )
    private val fed = listOf("Acceptable. Barely. More please", "Delicious. I forgive you", "Chef's kiss. I'd kiss you too but I'm busy eating", "Was that organic? Never mind, I don't care", "Okay, you're my favourite. For the next ten minutes")
    private val watered = listOf("Refreshing. Like a spa. A tiny spa", "Ahh. I was getting crispy", "Water. A classic. Good call")
    private val cuddled = listOf("Okay. I'll allow it. Keep going", "This is fine. This is very fine", "Don't stop. That's an order", "I'm not purring. You're purring")
    private val patted = listOf("A pat. Wow. Generous", "Yes, there. No, a bit left. Perfect", "I accept your offering", "That's the spot")
    private val tooSoon = listOf("Patience. I'm a pet, not a vending machine", "Again? Clingy. I like it. Wait a bit", "One pat per half hour. I have a schedule")
    private val poked = listOf("Yes? Can I help you", "Boop received", "Hey. I'm working here", "That tickles. Do it again. Actually don't", "You rang?", "I'm cute, I know. You don't have to keep checking")
    private val emptyFood = listOf("Your food store is empty. Write a note or play a game, I'll wait", "No snacks in your storage. Go do something nice and come back", "Out of food? Share a mood. I'll be here. Hungry")
    private val emptyDrink = listOf("No drinks left in your storage. Write a note or play a round", "Your drink store is dry. Like my bowl. Go play a game")
    private val emptyLove = listOf("You're out of love to give. Share a mood or a picture first", "Empty on affection? Share something with {them}, then come back to me")
    private val hello = listOf("So this is home? I'll take it", "Hi. I'm yours now. No refunds", "Nice place. Where's the food", "I've decided to love you both. Don't make it weird")
    private val siblings = listOf("{sib} keeps looking at me. Make it stop", "{sib} got more snacks than me. I counted", "I like {sib}. Don't tell them")

    /** What [pet] would say now, unprompted. Returns null when it has nothing to say. */
    fun idle(pet: Pet, me: String, them: String, meId: String, themId: String, sibling: String?, rnd: Random = Random): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val bMe = pet.bondWith(meId); val bThem = pet.bondWith(themId)
        val fav = pet.favourite()
        val pool = mutableListOf<List<String>>()
        when {
            pet.mood == "sulking" -> pool += sulking
            pet.hunger < 30 -> pool += hungry
            pet.thirst < 30 -> pool += thirsty
            pet.love < 30 -> pool += lonely
        }
        if (pool.isEmpty()) {
            if (fav != null && rnd.nextFloat() < .35f) pool += favourite
            if (bMe < 22 && bThem > bMe + 10) pool += ignoredByMe
            if (bThem < 22 && bMe > bThem + 10) pool += ignoredByThem
            if (hour >= 23 || hour < 5) pool += night
            if (hour in 6..9) pool += morning
            if (sibling != null && rnd.nextFloat() < .25f) pool += siblings
            pool += if (pet.mood == "happy") happy else okay
        }
        val line = pool[rnd.nextInt(pool.size)].let { it[rnd.nextInt(it.size)] }
        val favName = if (fav == meId) me else them
        val other = if (fav == meId) them else me
        return fill(line, pet, me, them, favName, other, sibling)
    }

    /** What the pet says after something happened: fed, watered, cuddled, patted, poked, a pat too soon, an empty store, a new home. */
    fun after(kind: String, pet: Pet, me: String, them: String, rnd: Random = Random): String {
        val list = when (kind) {
            "feed" -> fed; "water" -> watered; "cuddle" -> cuddled; "pat" -> patted; "soon" -> tooSoon; "empty_food" -> emptyFood; "empty_drink" -> emptyDrink; "empty_love" -> emptyLove; "hello" -> hello
            else -> poked
        }
        return fill(list[rnd.nextInt(list.size)], pet, me, them, me, them, null)
    }

    private fun fill(s: String, pet: Pet, me: String, them: String, fav: String, other: String, sib: String?) =
        s.replace("{me}", me).replace("{them}", them).replace("{pet}", pet.name).replace("{fav}", fav).replace("{other}", other).replace("{sib}", sib ?: "the other one")
}
