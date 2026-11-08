package app.tandem.ui.pets

import app.tandem.data.Occasions
import app.tandem.data.Pet
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.random.Random

/**
 * What the pets say, in comic bubbles: a bit sarcastic, a lot cute. Every pet has two personality traits
 * (from its id, so they never change), and those colour what it says. Lines are picked by what is going on
 * (hungry, ignored by one of you, a favourite, just fed, late at night, a holiday) and sometimes speak to one
 * of you by name. {me} is the person holding the phone, {them} the partner, {pet} the pet.
 *
 * Easter eggs: a few names have opinions, 11:11 and 3:33 are wishing times, Fridays and Mondays have moods,
 * holidays get their own lines, a pet's adoption day is a big deal, and poking one enough times finds its
 * tickle spot.
 */
enum class Trait(val label: String) {
    Dramatic("Dramatic"), Foodie("Foodie"), Sleepy("Sleepyhead"), Clingy("Clingy"), Diva("Diva"),
    Philosopher("Philosopher"), Gremlin("Little gremlin"), Shy("Shy sweetheart"), Grumpy("Grumpy, secretly soft"), ShowOff("Show-off"),
}

object PetLines {
    /** This pet's two traits, the same forever. */
    fun traits(pet: Pet): List<Trait> {
        val r = Random(pet.id.hashCode().toLong() * 31 + 7)
        val all = Trait.entries.shuffled(r)
        return all.take(2)
    }

    private val hungry = listOf(
        "I'm not saying I'm starving. I'm saying I can see through time",
        "Food. Now. Please. In that order",
        "{me}, the bowl is empty. I checked. Twice. With my face",
        "My tummy just made a noise. It said your name",
        "Remember snacks? I do. Fondly",
        "I would do anything for a treat. Even be cute. Watch",
        "Is this what independence feels like? I hate it",
        "Feed me and I'll pretend you're my favourite",
    )
    private val thirsty = listOf(
        "My water bowl is a desert. I'm basically a tiny cactus now",
        "Hydration is a right. I looked it up. With my paws",
        "{them} would have given me water by now. Just saying",
        "I'm parched, {me}. Parched. It's a whole tragedy",
        "Water please. I'll say thank you. Probably",
    )
    private val lonely = listOf(
        "You two look busy. I'll just sit here. Being adorable. For nobody",
        "When was the last cuddle? I counted. It was one. One",
        "Pet me or I start a podcast about this",
        "I have so much love to give and nowhere to put it",
        "Notice me. I'm the small fluffy one",
    )
    private val sulking = listOf(
        "I'm not talking to you. This doesn't count",
        "Oh, you remembered me. How nice",
        "I've written a list of complaints. You're both on it. In ink",
        "Don't look at me. I'm sulking. It's a whole thing",
        "Fine. Everything's fine. I'm fine. Totally fine",
        "I forgive you. Not yet. But soon. Maybe after a snack",
    )
    private val happy = listOf(
        "Life is good. Don't tell anyone, I have a reputation",
        "You two are alright. Don't let it go to your heads",
        "Ten out of ten. Would be adopted again",
        "Full belly, warm humans. Peak existence",
        "I love you both. That's it. That's the message",
        "Today I decided to be happy. You're welcome",
        "If happiness had a shape, it would be me. Round",
    )
    private val okay = listOf(
        "I'm okay. Could be better. Could be snacks",
        "Not bad. Not great. Very medium",
        "Somebody say something nice to me",
        "I've been thinking about treats. That's it, that's the thought",
    )
    private val favourite = listOf(
        "{fav} is my favourite. Sorry {other}. Not sorry",
        "Between you and me, {fav} gives better cuddles",
        "{other}, take notes from {fav}",
        "I love you both equally. {fav} slightly more equally",
        "{fav} is my person. {other} is my backup person. With love",
    )
    private val ignoredByMe = listOf(
        "{me}, do I know you? You look familiar",
        "{them} feeds me, {them} pets me. What do you do, {me}",
        "Oh hi {me}. Didn't see you there. For days",
    )
    private val ignoredByThem = listOf(
        "Tell {them} I said hi. Since they never visit",
        "Has anyone seen {them}? Asking for a small friend. Me",
        "{them} owes me at least four cuddles. I keep a tab",
    )
    private val night = listOf(
        "It's late. Shouldn't you two be asleep. Or cuddling me",
        "Why are you up. Go to bed. Take me with you",
        "Night shift again? I'll supervise. From the pillow",
    )
    private val morning = listOf(
        "Good morning. Breakfast was supposed to be three minutes ago",
        "Rise and shine. Mostly shine. I'll do the rising later",
        "Morning hugs are the most important meal of the day",
    )
    private val byTrait: Map<Trait, List<String>> = mapOf(
        Trait.Dramatic to listOf(
            "Nobody understands me. Except snacks. Snacks understand me",
            "I tripped on air earlier. I need emotional support. And a treat",
            "This is the worst day of my life. Also I love you",
            "I sneezed. I may not survive. Tell my story",
            "Every time you leave the room I age ten years",
        ),
        Trait.Foodie to listOf(
            "Is that a crumb? On your face? Asking for me",
            "My love language is snacks. Fluent, actually",
            "I could eat. I could always eat. That's my thing",
            "Rate today: three out of five. Not enough food",
            "If you love me, the bowl should never see its bottom",
        ),
        Trait.Sleepy to listOf(
            "I just woke up from a nap to plan my next nap",
            "Five more minutes. Or hours. Whatever",
            "Is it bedtime? It feels like bedtime. It's always bedtime",
            "I yawned so big I saw my dreams",
            "Wake me when there's cuddles",
        ),
        Trait.Clingy to listOf(
            "Where are you going? Can I come? Can I come? Can I come",
            "I missed you. You were gone for four seconds",
            "Hold me. Not like that. Like you mean it",
            "I like it when you're both here. It feels like home",
            "Don't leave. Or do, but take me in your pocket",
        ),
        Trait.Diva to listOf(
            "I'd like my water at room temperature, thank you",
            "Photos from my left side only, please",
            "I'm not spoiled. I'm well loved. There's a difference",
            "You may pet me. Gently. Like a royal",
            "Did someone say compliments? Go on",
        ),
        Trait.Philosopher to listOf(
            "If a treat falls and no one eats it, did it happen? No. Because I ate it",
            "What is love? Probably cuddles. Possibly snacks",
            "I've been staring at the wall. The wall has been staring back",
            "Are we the pets, or are you? Think about it",
            "Every day is a gift. Mostly a gift of food",
        ),
        Trait.Gremlin to listOf(
            "I knocked something over. It was art",
            "I have zoomies and no regrets",
            "I didn't do it. Unless it was funny",
            "Chaos is just fun that hasn't been explained yet",
            "Something is about to fall off a table. I won't say what",
        ),
        Trait.Shy to listOf(
            "Um. Hi. I like you. That's all. Bye",
            "I made you a compliment. You're nice. Okay that's it",
            "You can pet me. If you want. No pressure",
            "I'm hiding. But I want you to find me",
            "Thank you for being gentle with me",
        ),
        Trait.Grumpy to listOf(
            "Ugh. Fine. One cuddle. Just one",
            "I'm grumpy. Don't ask why. Don't stop petting either",
            "I don't like anyone. Except you two. Don't tell",
            "Go away. Wait. Come back",
            "I'm not smiling. My face just looks like this",
        ),
        Trait.ShowOff to listOf(
            "Did you see that? I did a jump. A really good one",
            "Rate my sitting. It's a ten",
            "I'm basically famous in this house",
            "Watch me be cute. Watch closer",
            "I'd win best pet. Every year. Easily",
        ),
    )
    private val fed = listOf(
        "Acceptable. Barely. More please", "Delicious. I forgive you for everything", "Chef's kiss. I'd kiss you too but I'm busy eating",
        "Nom. Nom nom. Yes", "Okay, you're my favourite. For the next ten minutes", "This tastes like being loved",
    )
    private val watered = listOf("Refreshing. Like a spa. A tiny spa", "Ahh. I was getting crispy", "Water. A classic. Good call", "Sip sip hooray")
    private val cuddled = listOf(
        "Okay. I'll allow it. Keep going", "This is fine. This is very fine", "Don't stop. That's an order. A cute order",
        "I'm not purring. You're purring", "Warm. Safe. Home", "More please. I'm collecting them",
    )
    private val patted = listOf("A pat. Wow. Generous", "Yes, there. No, a bit left. Perfect", "I accept your offering", "That's the spot", "Head pats are my favourite currency")
    private val tooSoon = listOf("Patience. I'm a pet, not a vending machine", "Again? Clingy. I like it. Wait a bit", "One pat per half hour. I have a schedule")
    private val poked = listOf(
        "Yes? Can I help you", "Boop received", "Hey. I'm working here", "That tickles. Do it again. Actually don't",
        "You rang?", "I'm cute, I know. You don't have to keep checking", "Booped. Again. The audacity",
    )
    private val emptyFood = listOf("Your food store is empty. Write a note or play a game, I'll wait", "No snacks in your storage. Go do something nice and come back", "Out of food? Share a mood. I'll be here. Hungry")
    private val emptyDrink = listOf("No drinks left in your storage. Write a note or play a round", "Your drink store is dry. Like my bowl. Go play a game")
    private val emptyLove = listOf("You're out of love to give. Share a mood or a picture first", "Empty on affection? Share something with {them}, then come back to me")
    private val hello = listOf("So this is home? I'll take it", "Hi. I'm yours now. No refunds", "Nice place. Where's the food", "I've decided to love you both. Don't make it weird")
    private val siblings = listOf("{sib} keeps looking at me. Make it stop", "{sib} got more snacks than me. I counted", "I like {sib}. Don't tell them", "{sib} and I have a plan. You'll see")

    // ---------- easter eggs
    private val names = mapOf(
        "garfield" to "Is there lasagna? Asking for no reason",
        "simba" to "Everything the light touches is my food bowl",
        "nala" to "I'm the real queen around here",
        "luna" to "I glow a little at night. Probably",
        "mochi" to "I'm soft, round and sweet. Like my name",
        "tofu" to "Plain? No. Versatile",
        "biscuit" to "Please stop looking at me like I'm a snack",
        "pickle" to "I'm in a bit of one. A pickle. Get it",
        "kiwi" to "Small, fuzzy, full of surprises",
        "pudding" to "Wobbly and proud",
    )
    private val tickle = listOf("Okay okay you found my tickle spot. This is a secret", "Stop. Stop. I can't. Don't tell anyone I laughed", "That's 7 pokes. You've unlocked: one pet that loves you a lot")
    private val wish = listOf("11:11. Make a wish. I wished for snacks", "It's 11:11. I wished you'd both stay forever. And snacks")
    private val friday = listOf("It's Friday. I've worked very hard at doing nothing all week", "Friday night plans: you, me, the couch")
    private val monday = listOf("Mondays should be illegal. For pets at least", "It's Monday. Be gentle with me")
    private val holidays = mapOf(
        "nowruz" to listOf("Nowruz mobarak. Where's my eidi", "New spring, new fur. Happy Nowruz"),
        "yalda" to listOf("Longest night of the year means longest cuddle", "Pomegranate seeds are just tiny snacks. Share"),
        "valentine" to listOf("Happy Valentine's. I'm both of your Valentines. Deal with it", "Roses are red, I'm adorable, feed me"),
        "sepandarmazgan" to listOf("Old Persian love day. New Persian me. Love you both"),
        "halloween" to listOf("I'm dressed as a cat this year. Again", "Trick or treat. Mostly treat. Only treat"),
        "christmas" to listOf("Is this tree for me to climb? I'm told no"),
        "newyearseve" to listOf("New year, same cute face", "My resolution: more naps. Easy"),
        "newyear" to listOf("Happy New Year. I'm keeping my resolution: be adorable"),
        "girlfriendday" to listOf("It's Girlfriend Day. Spoil her. Spoil me too, while you're at it"),
        "girlsdayiran" to listOf("Happy Girls' Day. There's at least one wonderful girl in this house"),
        "chaharshanbesuri" to listOf("Jumping over fire? I'll watch from a safe distance. Inside"),
    )
    private val rare = listOf("I can see your screen time, by the way", "Sometimes I just look at you two and think: nice", "Plot twist: I was the one adopting you")

    /** What [pet] would say now, unprompted. */
    fun idle(pet: Pet, me: String, them: String, meId: String, themId: String, sibling: String?, rnd: Random = Random): String {
        val now = LocalTime.now(); val today = LocalDate.now()
        val bMe = pet.bondWith(meId); val bThem = pet.bondWith(themId)
        val fav = pet.favourite()
        val favName = if (fav == meId) me else them
        val other = if (fav == meId) them else me
        fun out(s: String) = fill(s, pet, me, them, favName, other, sibling)

        // needs come first: a hungry pet talks about food
        when {
            pet.mood == "sulking" -> return out(sulking.random(rnd))
            pet.hunger < 30 -> return out(hungry.random(rnd))
            pet.thirst < 30 -> return out(thirsty.random(rnd))
            pet.love < 30 -> return out(lonely.random(rnd))
        }
        // easter eggs, now and then
        if ((now.hour == 11 || now.hour == 23) && now.minute == 11) return out(wish.random(rnd))
        if ((now.hour == 3 || now.hour == 15) && now.minute == 33 && rnd.nextFloat() < .7f) return out("3:33. A lucky minute. Spend it petting me")
        Occasions.today(today)?.let { o -> holidays[o.key]?.let { if (rnd.nextFloat() < .6f) return out(it.random(rnd)) } }
        val born = Instant.ofEpochMilli(pet.born).atZone(ZoneId.systemDefault()).toLocalDate()
        if (pet.born > 0 && born.monthValue == today.monthValue && born.dayOfMonth == today.dayOfMonth && born.year < today.year) return out("It's my adoption day. Presents go to the left. Cuddles to the right")
        names[pet.name.trim().lowercase()]?.let { if (rnd.nextFloat() < .12f) return out(it) }
        if (rnd.nextFloat() < .02f) return out(rare.random(rnd))

        val pool = mutableListOf<List<String>>()
        traits(pet).forEach { t -> byTrait[t]?.let { pool += it; pool += it } }      // personality speaks most
        if (fav != null && rnd.nextFloat() < .35f) pool += favourite
        if (bMe < 22 && bThem > bMe + 10) pool += ignoredByMe
        if (bThem < 22 && bMe > bThem + 10) pool += ignoredByThem
        if (now.hour >= 23 || now.hour < 5) pool += night
        if (now.hour in 6..9) pool += morning
        if (today.dayOfWeek == DayOfWeek.FRIDAY) pool += friday
        if (today.dayOfWeek == DayOfWeek.MONDAY) pool += monday
        if (sibling != null && rnd.nextFloat() < .25f) pool += siblings
        pool += if (pet.mood == "happy") happy else okay
        return out(pool.random(rnd).random(rnd))
    }

    /** What the pet says after something happened: fed, watered, cuddled, patted, poked, a pat too soon, an empty store, a new home, a tickle. */
    fun after(kind: String, pet: Pet, me: String, them: String, rnd: Random = Random): String {
        val list = when (kind) {
            "feed" -> fed; "water" -> watered; "cuddle" -> cuddled; "pat" -> patted; "soon" -> tooSoon
            "empty_food" -> emptyFood; "empty_drink" -> emptyDrink; "empty_love" -> emptyLove; "hello" -> hello; "tickle" -> tickle
            else -> poked
        }
        return fill(list.random(rnd), pet, me, them, me, them, null)
    }

    private fun fill(s: String, pet: Pet, me: String, them: String, fav: String, other: String, sib: String?) =
        s.replace("{me}", me).replace("{them}", them).replace("{pet}", pet.name).replace("{fav}", fav).replace("{other}", other).replace("{sib}", sib ?: "the other one")
}
