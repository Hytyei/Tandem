package app.tandem.ui.screens

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.min as minDp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import app.tandem.ui.components.FxKind
import app.tandem.ui.components.LocalFx
import app.tandem.ui.components.pressable
import app.tandem.ui.theme.T
import app.tandem.ui.theme.Type
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

// ------------------------------------------------------------------ Whack-a-Mole
private class MoleSpawn(val t: Long, val hole: Int, val kind: Int, val life: Long)   // kind: 0 mole, 1 unicorn (bonus), 2 bomb

@Composable
internal fun WhackGame(seed: Long, onDone: (Int) -> Unit) {
    val total = 40_000L
    val spawns = remember(seed) {
        val r = Random(seed)
        val freeAt = LongArray(9)
        buildList {
            var t = 600L
            while (t < total - 400) {
                val life = (1150L - t / 55).coerceAtLeast(620L)
                val free = (0..8).filter { freeAt[it] <= t }
                if (free.isNotEmpty()) {
                    val h = free[r.nextInt(free.size)]
                    val roll = r.nextInt(100)
                    add(MoleSpawn(t, h, if (roll < 14) 2 else if (roll < 22) 1 else 0, life))
                    freeAt[h] = t + life + 120
                }
                t += (700L - t / 80).coerceAtLeast(380L)
            }
        }
    }
    var whacked by remember { mutableStateOf(setOf<Int>()) }
    var points by remember { mutableIntStateOf(0) }
    var streak by remember { mutableIntStateOf(0) }
    val perf = LocalPerf.current
    val fx = LocalFx.current
    val elapsed = rememberClock(true, total) {
        val good = spawns.indices.count { spawns[it].kind != 2 && spawns[it].t + spawns[it].life <= total }
        val got = whacked.count { spawns[it].kind != 2 }
        repeat((good - got).coerceAtLeast(0)) { perf.miss() }
        onDone(points.coerceIn(0, 1000))
    }
    val nowMs by rememberUpdatedState(elapsed)
    val c = T.colors

    fun activeAt(h: Int, t: Long): Int? = spawns.indices.firstOrNull {
        val s = spawns[it]
        s.hole == h && it !in whacked && t >= s.t && t < s.t + s.life
    }
    fun tap(h: Int) {
        val t = nowMs
        val i = activeAt(h, t)
        if (i == null) { streak = 0; perf.miss(); fx.burst(FxKind.Bad); return }
        val s = spawns[i]
        whacked = whacked + i
        when (s.kind) {
            2 -> { points = (points - 15).coerceAtLeast(0); streak = 0; perf.miss(); fx.burst(FxKind.Boom, "-15") }
            1 -> {
                val g = 30 + minOf(streak, 5) * 2
                points += g; streak++; perf.hit(); perf.react((t - s.t).toInt()); fx.burst(FxKind.Coins, "+$g")
            }
            else -> {
                val g = 8 + minOf(streak, 5) * 2
                points += g; streak++; perf.hit(); perf.react((t - s.t).toInt()); fx.burst(FxKind.Stars, "+$g")
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Hud(total - elapsed, total, "$points pts" + if (streak >= 2) " · 🔥$streak" else "")
        Text("Whack the moles · avoid the 💣", style = Type.callout, color = c.secondary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(20.dp)) {
            val gap = 12.dp
            val cell = minDp((maxWidth - gap * 2) / 3, (maxHeight - gap * 2) / 3)
            Column(Modifier.align(Alignment.Center), verticalArrangement = Arrangement.spacedBy(gap)) {
                repeat(3) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        repeat(3) { col ->
                            val h = row * 3 + col
                            val act = activeAt(h, elapsed)
                            val s = act?.let { spawns[it] }
                            Box(
                                Modifier.size(cell).clip(CircleShape).background(Color(0x40000000))
                                    .border(2.dp, if (s?.kind == 1) Color(0xFFFFC857) else Color(0x33FFFFFF), CircleShape)
                                    .pointerInput(h) { detectTapGestures(onPress = { tap(h) }) },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (s != null) {
                                    val age = elapsed - s.t
                                    val pop = (0.3f + age / 120f).coerceAtMost(1f)
                                    val fade = ((s.t + s.life - elapsed) / 180f).coerceIn(0f, 1f)
                                    Text(
                                        when (s.kind) { 2 -> "💣"; 1 -> "🦄"; else -> "🐹" }, fontSize = (cell.value * 0.55f).sp,
                                        modifier = Modifier.graphicsLayer { scaleX = pop; scaleY = pop; alpha = fade },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Stack
@Composable
internal fun StackGame(seed: Long, onDone: (Int) -> Unit) {
    val density = LocalDensity.current.density
    val perf = LocalPerf.current
    val fx = LocalFx.current
    val c = T.colors
    val scope = rememberCoroutineScope()
    var box by remember { mutableStateOf(IntSize.Zero) }
    val lefts = remember { mutableStateListOf<Float>() }
    val widths = remember { mutableStateListOf<Float>() }
    var pos by remember { mutableFloatStateOf(0f) }
    var dir by remember { mutableFloatStateOf(1f) }
    var perfects by remember { mutableIntStateOf(0) }
    var shift by remember { mutableFloatStateOf(0f) }
    var over by remember { mutableStateOf(false) }
    val sides = remember(seed) { val r = Random(seed); List(120) { r.nextBoolean() } }
    val floors = (lefts.size - 1).coerceAtLeast(0)
    val score = (floors * 10 + perfects * 5).coerceAtMost(1000)
    val lh = 34f * density
    fun scoreNow() = ((lefts.size - 1).coerceAtLeast(0) * 10 + perfects * 5).coerceAtMost(1000)

    LaunchedEffect(box) {
        if (box.width > 0 && lefts.isEmpty()) {
            val bw = box.width * 0.56f
            lefts.add((box.width - bw) / 2f); widths.add(bw)
            pos = 0f; dir = 1f
        }
    }
    LaunchedEffect(box, over) {
        if (over || box.width == 0) return@LaunchedEffect
        var last = 0L
        while (true) {
            val t = withFrameMillis { it }
            val dt = if (last == 0L) 0.016f else ((t - last) / 1000f).coerceIn(0f, .05f)
            last = t
            if (widths.isEmpty()) continue
            val w = box.width.toFloat()
            val speed = (w * (0.75f + 0.04f * (lefts.size - 1).coerceAtLeast(0))).coerceAtMost(w * 1.7f)
            pos += dir * speed * dt
            val maxPos = w - widths.last()
            if (pos > maxPos) { pos = maxPos; dir = -1f }
            if (pos < 0f) { pos = 0f; dir = 1f }
            shift *= exp(-10f * dt)
        }
    }
    fun drop() {
        if (over || lefts.isEmpty()) return
        val pl = lefts.last(); val pw = widths.last()
        val d = pos - pl
        val tol = box.width * 0.014f
        if (abs(d) <= tol) {
            lefts.add(pl); widths.add(pw); perfects++; perf.hit(); fx.burst(FxKind.Stars, "PERFECT")
        } else {
            val overlap = pw - abs(d)
            if (overlap <= 4f * density) {
                over = true; perf.miss(); fx.burst(FxKind.Boom)
                scope.launch { delay(800); onDone(scoreNow()) }
                return
            }
            lefts.add(max(pos, pl)); widths.add(overlap); perf.miss(); fx.burst(FxKind.Ring)
        }
        shift = lh
        val right = sides[lefts.size % sides.size]
        dir = if (right) -1f else 1f
        pos = if (dir > 0f) 0f else box.width - widths.last()
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🧱 $floors floors", style = Type.headline, color = c.label, modifier = Modifier.weight(1f))
            Text("✨ $perfects perfect · $score pts", style = Type.headline, color = c.accent)
        }
        Box(
            Modifier.weight(1f).fillMaxWidth().onSizeChanged { box = it }
                .pointerInput(Unit) { detectTapGestures(onPress = { drop() }) },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val h = size.height
                val yTop = h * 0.52f
                val n = lefts.size
                for (k in 0 until n) {
                    val y = yTop + (n - 1 - k) * lh - shift
                    if (y > h) continue
                    drawRoundRect(Color.hsv(((k * 16) % 360).toFloat(), .55f, .95f), Offset(lefts[k], y), Size(widths[k], lh - 2f * density), CornerRadius(7f * density))
                }
                if (!over && n > 0) {
                    drawRoundRect(Color.hsv(((n * 16) % 360).toFloat(), .55f, 1f), Offset(pos, yTop - lh - shift), Size(widths.last(), lh - 2f * density), CornerRadius(7f * density))
                }
            }
            if (floors == 0) Text("Tap to drop the block", style = Type.callout, color = c.secondary, modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp))
        }
    }
}

// ------------------------------------------------------------------ Flappy Heart
@Composable
internal fun FlappyGame(seed: Long, onDone: (Int) -> Unit) {
    val density = LocalDensity.current.density
    val perf = LocalPerf.current
    val fx = LocalFx.current
    val c = T.colors
    val scope = rememberCoroutineScope()
    var box by remember { mutableStateOf(IntSize.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var started by remember { mutableStateOf(false) }
    var dead by remember { mutableStateOf(false) }
    var y by remember { mutableFloatStateOf(0f) }
    var vy by remember { mutableFloatStateOf(0f) }
    var scroll by remember { mutableFloatStateOf(0f) }
    var passed by remember { mutableIntStateOf(0) }
    val gaps = remember(seed) {
        val r = Random(seed)
        var prev = .5f
        List(140) { prev = (prev + (r.nextFloat() - .5f) * .5f).coerceIn(.24f, .76f); prev }
    }
    val spacing = 200f * density
    val pipeW = 58f * density
    val radius = 14f * density
    val birdX = box.width * 0.28f
    val firstX = box.width * 0.9f

    LaunchedEffect(box) { if (box.height > 0 && !started) y = box.height * 0.45f }
    fun flap() {
        if (dead) return
        if (!started) started = true
        vy = -430f * density
    }
    LaunchedEffect(started, box) {
        if (!started || box.width == 0) return@LaunchedEffect
        var last = 0L
        while (!dead) {
            val t = withFrameMillis { it }
            val dt = if (last == 0L) 0.016f else ((t - last) / 1000f).coerceIn(0f, .04f)
            last = t
            val h = box.height.toFloat()
            vy += 1350f * density * dt
            y += vy * dt
            scroll += 165f * density * dt
            if (y < radius) { y = radius; if (vy < 0f) vy = 0f }
            // score: pipes the heart has fully passed
            val q = (scroll + birdX - firstX - pipeW) / spacing
            val nowPassed = (if (q < 0f) 0 else q.toInt() + 1).coerceIn(0, gaps.size)
            if (nowPassed > passed) { passed = nowPassed; perf.hit(); fx.burstAt(FxKind.Stars, origin.x + birdX, origin.y + y, "+10") }
            // crash?
            var crash = y > h - radius
            val gapH = (170f - min(passed, 30) * 1.6f) * density
            for (i in 0 until gaps.size) {
                val px = firstX + i * spacing - scroll
                if (px > birdX + radius) break
                if (px + pipeW < birdX - radius) continue
                val gc = gaps[i] * h
                if (y - radius < gc - gapH / 2f || y + radius > gc + gapH / 2f) crash = true
            }
            if (crash) {
                dead = true; perf.miss()
                fx.burstAt(FxKind.Boom, origin.x + birdX, origin.y + y)
                scope.launch { delay(900); onDone((passed * 10).coerceAtMost(1000)) }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Tap to flap", style = Type.headline, color = c.label, modifier = Modifier.weight(1f))
            Text("💗 $passed gaps · ${(passed * 10).coerceAtMost(1000)} pts", style = Type.headline, color = c.accent)
        }
        Box(
            Modifier.weight(1f).fillMaxWidth().onSizeChanged { box = it }.onGloballyPositioned { origin = it.positionInParent() }
                .pointerInput(Unit) { detectTapGestures(onPress = { flap() }) },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val h = size.height
                val gapH = (170f - min(passed, 30) * 1.6f) * density
                val brush = Brush.verticalGradient(listOf(Color(0xFFFF8FA3), Color(0xFF9C8CFF)))
                for (i in 0 until gaps.size) {
                    val px = firstX + i * spacing - scroll
                    if (px > size.width) break
                    if (px + pipeW < 0f) continue
                    val gc = gaps[i] * h
                    drawRoundRect(brush, Offset(px, -20f * density), Size(pipeW, gc - gapH / 2f + 20f * density), CornerRadius(10f * density))
                    drawRoundRect(brush, Offset(px, gc + gapH / 2f), Size(pipeW, h - (gc + gapH / 2f) + 20f * density), CornerRadius(10f * density))
                }
                val tilt = (vy / (900f * density)).coerceIn(-.5f, .8f) * 40f
                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                    paint.textAlign = android.graphics.Paint.Align.CENTER
                    paint.textSize = radius * 2.6f
                    save(); rotate(tilt, birdX, y)
                    drawText(if (dead) "💔" else "💗", birdX, y + radius * .9f, paint)
                    restore()
                }
            }
            if (!started) Text("Tap to start", style = Type.title, color = c.label, modifier = Modifier.align(Alignment.Center))
        }
    }
}

// ------------------------------------------------------------------ Colour Clash (Stroop)
private val STROOP_NAMES = listOf("RED", "BLUE", "GREEN", "YELLOW")
private val STROOP_COLORS = listOf(Color(0xFFEF4444), Color(0xFF3B82F6), Color(0xFF22C55E), Color(0xFFF5B301))

@Composable
internal fun StroopGame(seed: Long, onDone: (Int) -> Unit) {
    val total = 30_000L
    val rounds = remember(seed) {
        val r = Random(seed)
        List(300) {
            val word = r.nextInt(4)
            val ink = if (r.nextInt(100) < 75) (word + 1 + r.nextInt(3)) % 4 else word      // mostly a clash
            word to ink
        }
    }
    var idx by remember { mutableIntStateOf(0) }
    var points by remember { mutableIntStateOf(0) }
    var streak by remember { mutableIntStateOf(0) }
    var last by remember { mutableStateOf<Boolean?>(null) }
    val perf = LocalPerf.current
    val fx = LocalFx.current
    var shownAt by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val elapsed = rememberClock(true, total) { onDone(points.coerceIn(0, 1000)) }
    val c = T.colors
    val (word, ink) = rounds[idx.coerceAtMost(rounds.lastIndex)]
    Column(Modifier.fillMaxSize()) {
        Hud(total - elapsed, total, "$points pts" + if (streak >= 2) " · 🔥$streak" else "")
        Spacer(Modifier.weight(1f))
        Text("Tap the colour you SEE", style = Type.callout, color = c.secondary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Text(
            STROOP_NAMES[word], style = Type.numberHuge.copy(fontSize = 72.sp, lineHeight = 80.sp, fontWeight = FontWeight.ExtraBold), color = STROOP_COLORS[ink],
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp), textAlign = TextAlign.Center, maxLines = 1,
        )
        Text(
            when (last) { true -> "✓"; false -> "✗"; null -> " " },
            style = Type.title, color = if (last == true) c.accent else c.danger, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        Column(Modifier.padding(16.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            (0..3).chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { k ->
                        Box(
                            Modifier.weight(1f).height(80.dp).clip(RoundedCornerShape(22.dp)).background(c.cardRaised)
                                .border(1.dp, c.separator, RoundedCornerShape(22.dp))
                                .pointerInput(k, idx) {
                                    detectTapGestures(onPress = {
                                        val ms = (SystemClock.elapsedRealtime() - shownAt).toInt()
                                        if (k == ink) {
                                            val g = 10 + minOf(streak, 5) * 2
                                            points += g; streak++; last = true; perf.hit(); perf.react(ms)
                                            fx.burst(if (ms < 600) FxKind.Confetti else FxKind.Stars, "+$g")
                                        } else {
                                            points = (points - 5).coerceAtLeast(0); streak = 0; last = false; perf.miss(); fx.burst(FxKind.Bad, "-5")
                                        }
                                        shownAt = SystemClock.elapsedRealtime()
                                        idx++
                                    })
                                },
                            contentAlignment = Alignment.Center,
                        ) { Text(STROOP_NAMES[k], style = Type.title.copy(fontSize = 26.sp), color = c.label) }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Word Duel
private val WORDS = (
    "ABOUT ABOVE ACTOR ADAPT ADMIT ADOPT AFTER AGAIN AGREE AHEAD ALARM ALBUM ALIVE ALLOW ALONE ALONG ANGEL ANGER ANGLE ANKLE APART APPLE APPLY ARENA ARGUE ARISE ARROW ASIDE AUDIO AVOID AWAKE AWARD AWARE " +
    "BADGE BAKER BASIC BEACH BEARD BEAST BEGIN BEING BELOW BENCH BERRY BIRTH BLACK BLADE BLAME BLANK BLAZE BLEND BLESS BLIND BLOCK BLOOM BOARD BOAST BONUS BOOST BRAIN BRAND BRAVE BREAD BREAK BRICK BRIDE BRIEF BRING BROAD BROWN BRUSH BUILD BUNCH " +
    "CABIN CABLE CANDY CARGO CARRY CATCH CAUSE CHAIN CHAIR CHALK CHARM CHART CHASE CHEAP CHECK CHEEK CHEER CHEST CHIEF CHILD CHOIR CIVIL CLAIM CLASS CLEAN CLEAR CLERK CLICK CLIMB CLOCK CLOSE CLOUD COACH COAST COUCH COUNT COURT COVER CRAFT CRANE CRASH CRAZY CREAM CRISP CROWD CROWN CRUSH CURVE CYCLE " +
    "DAILY DAIRY DAISY DANCE DEALT DEATH DELAY DEPTH DIARY DIRTY DOUBT DOZEN DRAFT DRAMA DREAM DRESS DRIFT DRINK DRIVE EAGER EARLY EARTH EIGHT ELITE EMPTY ENEMY ENJOY ENTER ENTRY EQUAL ERROR EVENT EVERY EXACT EXTRA " +
    "FAIRY FAITH FALSE FANCY FEAST FENCE FIELD FIFTH FIGHT FINAL FIRST FLAME FLASH FLEET FLOAT FLOOD FLOOR FLOUR FLUTE FOCUS FORCE FORTH FOUND FRAME FRESH FRONT FROST FRUIT FUNNY " +
    "GHOST GIANT GLASS GLOBE GLORY GLOVE GRACE GRADE GRAIN GRAND GRANT GRAPE GRASS GREAT GREEN GREET GRILL GROUP GUARD GUESS GUEST GUIDE HAPPY HARSH HEART HEAVY HENCE HONEY HONOR HORSE HOTEL HOUSE HUMAN HUMOR " +
    "IDEAL IMAGE INDEX INNER INPUT IRONY JELLY JEWEL JOINT JUICE KNIFE KNOCK LABEL LARGE LASER LATER LAUGH LAYER LEARN LEASE LEAST LEAVE LEGAL LEMON LEVEL LIGHT LIMIT LINEN LOCAL LOGIC LOOSE LOVER LUCKY LUNCH MAGIC MAJOR MAKER MANGO MARCH MATCH MAYBE MAYOR MEDAL MERCY METAL METER MIGHT MINOR MIXED MODEL MONEY MONTH MORAL MOTOR MOUNT MOUSE MOUTH MOVIE MUSIC " +
    "NAIVE NERVE NEVER NIGHT NOBLE NOISE NORTH NOTED NOVEL NURSE OASIS OCEAN OFFER OFTEN OLIVE ONION OPERA ORBIT ORDER OTHER OUTER OWNER PAINT PANEL PAPER PARTY PASTA PATCH PAUSE PEACE PEACH PEARL PHASE PHONE PHOTO PIANO PILOT PITCH PIZZA PLACE PLAIN PLANE PLANT PLATE PLAZA POINT POUND POWER PRESS PRICE PRIDE PRIME PRINT PRIZE PROOF PROUD PUPIL PURSE " +
    "QUEEN QUEST QUICK QUIET QUILT QUOTE RADIO RAISE RALLY RANCH RANGE RAPID RATIO REACH READY REALM REBEL RELAX REPLY RIDER RIDGE RIGHT RIVER ROBOT ROCKY ROMAN ROUGH ROUND ROUTE ROYAL RURAL SALAD SCALE SCARF SCENE SCOUT SENSE SERVE SEVEN SHADE SHAKE SHAPE SHARE SHARP SHEEP SHEET SHELF SHELL SHINE SHIRT SHORE SHORT SIGHT SILLY SINCE SKILL SLEEP SLICE SLIDE SMALL SMART SMILE SMOKE SNACK SNAKE SOLAR SOLID SOUND SOUTH SPACE SPARE SPARK SPEAK SPEED SPELL SPEND SPICE SPOON SPORT SQUAD STAGE STAIR STAMP STAND STARE START STATE STEAM STEEL STICK STILL STONE STORE STORM STORY STOVE STRAW STUDY STUFF STYLE SUGAR SUNNY SUPER SWAMP SWEET SWIFT SWING SWORD " +
    "TABLE TASTE TEACH TEETH THANK THEME THICK THING THINK THIRD THOSE THREE THROW THUMB TIGER TIGHT TIMER TIRED TITLE TOAST TODAY TOOTH TOPIC TORCH TOTAL TOUCH TOUGH TOWEL TOWER TRACK TRADE TRAIL TRAIN TREAT TREND TRIAL TRIBE TRICK TRUCK TRULY TRUST TRUTH TULIP TWICE UNCLE UNDER UNION UNITY UNTIL UPPER UPSET URBAN USAGE USUAL VALUE VIDEO VIRUS VISIT VITAL VOCAL VOICE WAGON WASTE WATCH WATER WEARY WEAVE WHEAT WHEEL WHERE WHICH WHILE WHITE WHOLE WHOSE WOMAN WORLD WORRY WORTH WOULD WRITE WRONG YACHT YOUNG YOUTH ZEBRA"
    ).split(" ").filter { it.length == 5 }
private val KEY_ROWS = listOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM")
private val GREEN = Color(0xFF22C55E)
private val YELLOW = Color(0xFFF5B301)
private val GRAY = Color(0xFF4B4870)

/** 2 = right letter, right place; 1 = in the word elsewhere; 0 = not in the word (standard Wordle rules, repeats handled). */
private fun grade(guess: String, answer: String): List<Int> {
    val res = MutableList(5) { 0 }
    val left = answer.toMutableList()
    for (i in 0 until 5) if (guess[i] == answer[i]) { res[i] = 2; left[i] = '.' }
    for (i in 0 until 5) if (res[i] == 0) {
        val k = left.indexOf(guess[i])
        if (k >= 0) { res[i] = 1; left[k] = '.' }
    }
    return res
}

@Composable
internal fun WordGame(seed: Long, onDone: (Int) -> Unit) {
    val total = 180_000L
    val answer = remember(seed) { WORDS[Random(seed).nextInt(WORDS.size)] }
    var guesses by remember { mutableStateOf(listOf<String>()) }
    var current by remember { mutableStateOf("") }
    var shake by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    val perf = LocalPerf.current
    val fx = LocalFx.current
    val scope = rememberCoroutineScope()
    val c = T.colors
    val startedAt = remember { SystemClock.elapsedRealtime() }

    fun partial(): Int {
        var best = 0
        for (g in guesses) { val r = grade(g, answer); best = max(best, r.count { it == 2 } * 15 + r.count { it == 1 } * 5) }
        return best
    }
    fun finish(solved: Boolean) {
        if (finished) return
        finished = true
        val secs = ((SystemClock.elapsedRealtime() - startedAt) / 1000).toInt()
        val score = if (solved) 400 + (7 - guesses.size) * 80 + (120 - (secs - 30).coerceAtLeast(0)).coerceIn(0, 120) else partial()
        if (solved) { perf.hit(); perf.react(secs * 1000) } else perf.miss()
        scope.launch { delay(if (solved) 900 else 1200); onDone(score.coerceIn(0, 1000)) }
    }
    val elapsed = rememberClock(!finished, total) { if (!finished) { finish(false) } }

    val letterState = remember(guesses) {
        val m = HashMap<Char, Int>()
        for (g in guesses) { val r = grade(g, answer); for (i in 0 until 5) m[g[i]] = max(m[g[i]] ?: 0, r[i] + 1) }
        m
    }
    fun submit() {
        if (finished) return
        if (current.length < 5) { shake = true; fx.burst(FxKind.Bad, "5 letters"); return }
        val g = current
        guesses = guesses + g
        current = ""
        val r = grade(g, answer)
        if (r.all { it == 2 }) { fx.burst(FxKind.Confetti, "SOLVED!"); finish(true) }
        else {
            fx.burst(if (r.count { it == 2 } >= 3) FxKind.Stars else FxKind.Ring, "${r.count { it == 2 }} green")
            if (guesses.size >= 6) finish(false)
        }
    }
    LaunchedEffect(shake) { if (shake) { delay(300); shake = false } }

    Column(Modifier.fillMaxSize()) {
        Hud(total - elapsed, total, "Guess ${(guesses.size + 1).coerceAtMost(6)} of 6")
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp)) {
            val gap = 6.dp
            val tile = minDp((maxHeight - gap * 5) / 6, (maxWidth - gap * 4) / 5).coerceIn(30.dp, 56.dp)
            Column(Modifier.align(Alignment.Center), verticalArrangement = Arrangement.spacedBy(gap)) {
                repeat(6) { row ->
                    val word = guesses.getOrNull(row)
                    val typing = if (row == guesses.size && !finished) current else ""
                    val res = word?.let { grade(it, answer) }
                    Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.graphicsLayer { if (shake && row == guesses.size) translationX = 10f }) {
                        repeat(5) { col ->
                            val ch = word?.get(col) ?: typing.getOrNull(col)
                            val bg = when (res?.get(col)) { 2 -> GREEN; 1 -> YELLOW; 0 -> GRAY; else -> c.cardRaised }
                            Box(
                                Modifier.size(tile).clip(RoundedCornerShape(10.dp)).background(bg)
                                    .border(1.dp, if (res == null && ch != null) c.accent else c.separator, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center,
                            ) { if (ch != null) Text("$ch", style = Type.title.copy(fontSize = (tile.value * .5f).sp), color = Color.White) }
                        }
                    }
                }
            }
        }
        // keyboard
        Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp).padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            KEY_ROWS.forEachIndexed { r, keys ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)) {
                    if (r == 2) KeyCap("ENTER", Modifier.weight(1.5f), c.accent) { submit() }
                    keys.forEach { ch ->
                        val st = letterState[ch]
                        val bg = when (st) { 3 -> GREEN; 2 -> YELLOW; 1 -> GRAY; else -> c.cardRaised }
                        KeyCap("$ch", Modifier.weight(1f), bg) { if (!finished && current.length < 5) current += ch }
                    }
                    if (r == 2) KeyCap("⌫", Modifier.weight(1.5f), c.cardRaised) { if (!finished && current.isNotEmpty()) current = current.dropLast(1) }
                }
            }
            if (finished) Text(
                if (guesses.lastOrNull() == answer) "Solved in ${guesses.size}!" else "The word was $answer",
                style = Type.headline, color = c.label, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun KeyCap(label: String, modifier: Modifier, bg: Color, onTap: () -> Unit) {
    val c = T.colors
    Box(
        modifier.height(46.dp).clip(RoundedCornerShape(9.dp)).background(bg).pointerInput(label) { detectTapGestures(onPress = { onTap() }) },
        contentAlignment = Alignment.Center,
    ) { Text(label, style = Type.footnote.copy(fontWeight = FontWeight.SemiBold, fontSize = if (label.length > 1) 12.sp else 16.sp), color = if (bg == c.cardRaised) c.label else Color.White, maxLines = 1) }
}

// ------------------------------------------------------------------ Blackjack
private enum class BjPhase { Bet, Player, Dealer, Result, End }

private fun cardRank(id: Int) = id % 13 + 1                       // 1 = ace .. 13 = king
private fun cardSuit(id: Int) = id / 13 % 4
private fun cardText(id: Int): String {
    val r = when (val k = cardRank(id)) { 1 -> "A"; 11 -> "J"; 12 -> "Q"; 13 -> "K"; else -> "$k" }
    return r + listOf("♠", "♥", "♦", "♣")[cardSuit(id)]
}
/** Best total of a hand (aces count 11 while that does not bust). */
private fun handTotal(cards: List<Int>): Int {
    var t = 0; var aces = 0
    for (c in cards) { val r = cardRank(c); t += if (r == 1) { aces++; 11 } else min(r, 10) }
    while (t > 21 && aces > 0) { t -= 10; aces-- }
    return t
}
private fun isBlackjack(cards: List<Int>) = cards.size == 2 && handTotal(cards) == 21

@Composable
private fun CardView(id: Int, hidden: Boolean = false) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val sc by animateFloatAsState(if (shown) 1f else .4f, label = "card")
    val shape = RoundedCornerShape(10.dp)
    val red = cardSuit(id) == 1 || cardSuit(id) == 2
    Box(
        Modifier.size(width = 54.dp, height = 78.dp).graphicsLayer { scaleX = sc; scaleY = sc; alpha = sc.coerceIn(0f, 1f) }.clip(shape)
            .background(if (hidden) Brush.linearGradient(listOf(Color(0xFF6A5CF0), Color(0xFFEC4899))) else Brush.linearGradient(listOf(Color(0xFFFFFFFF), Color(0xFFF1EEFF))))
            .border(1.dp, Color(0x33000000), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (hidden) Text("✦", fontSize = 26.sp, color = Color.White.copy(alpha = .8f))
        else Text(cardText(id), style = Type.headline.copy(fontSize = 20.sp), color = if (red) Color(0xFFD62A4A) else Color(0xFF1F1B3D), maxLines = 1)
    }
}

@Composable
internal fun BlackjackGame(seed: Long, onDone: (Int) -> Unit) {
    val handsTotal = 6
    val startChips = 500
    val shoe = remember(seed) { (0 until 104).map { it % 52 }.shuffled(Random(seed)) }     // two decks, same order for both of you
    var deckPos by remember { mutableIntStateOf(0) }
    var chips by remember { mutableIntStateOf(startChips) }
    var handNo by remember { mutableIntStateOf(1) }
    var bet by remember { mutableIntStateOf(50) }
    var player by remember { mutableStateOf(listOf<Int>()) }
    var dealer by remember { mutableStateOf(listOf<Int>()) }
    var phase by remember { mutableStateOf(BjPhase.Bet) }
    var doubled by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Place your bet") }
    var delta by remember { mutableIntStateOf(0) }
    var revealed by remember { mutableStateOf(false) }
    val perf = LocalPerf.current
    val fx = LocalFx.current
    val beat = LocalBeat.current
    val c = T.colors
    val bets = listOf(25, 50, 100, 250)

    fun draw(): Int { val card = shoe[deckPos % shoe.size]; deckPos++; return card }
    fun settle(text: String, net: Int, kind: FxKind?) {
        delta = net; chips = (chips + net).coerceAtLeast(0); message = text; revealed = true
        if (net > 0) perf.hit() else if (net < 0) perf.miss()
        if (kind != null) fx.burst(kind, if (net > 0) "+$net" else if (net < 0) "$net" else "push")
        phase = if (handNo >= handsTotal || chips <= 0) BjPhase.End else BjPhase.Result
    }
    fun deal() {
        val b = min(bet, chips)
        bet = b
        val p = listOf(draw()); val d = listOf(draw())
        val p2 = p + draw(); val d2 = d + draw()
        player = p2; dealer = d2; doubled = false; revealed = false; delta = 0
        val pb = isBlackjack(p2); val db = isBlackjack(d2)
        when {
            pb && db -> settle("Both have blackjack. Push.", 0, null)
            pb -> settle("Blackjack! Pays 3 to 2", b * 3 / 2, FxKind.Confetti)
            db -> settle("Dealer has blackjack", -b, FxKind.Bad)
            else -> { phase = BjPhase.Player; message = "Hit, stand or double?" }
        }
    }
    fun standNow() { phase = BjPhase.Dealer; message = "Dealer plays…" }
    fun hit() {
        player = player + draw()
        val t = handTotal(player)
        if (t > 21) settle("Bust with $t", -(if (doubled) bet * 2 else bet), FxKind.Boom)
        else if (t == 21) standNow()
    }
    fun double() {
        doubled = true
        player = player + draw()
        val t = handTotal(player)
        if (t > 21) settle("Bust with $t", -bet * 2, FxKind.Boom) else standNow()
    }
    // the dealer reveals and draws to 17
    LaunchedEffect(phase) {
        if (phase != BjPhase.Dealer) return@LaunchedEffect
        revealed = true
        delay(600)
        while (handTotal(dealer) < 17) { delay(650); dealer = dealer + draw() }
        delay(450)
        val pt = handTotal(player); val dt = handTotal(dealer)
        val stake = if (doubled) bet * 2 else bet
        when {
            dt > 21 -> settle("Dealer busts with $dt. You win!", stake, FxKind.Coins)
            pt > dt -> settle("You win, $pt to $dt", stake, FxKind.Coins)
            pt < dt -> settle("Dealer wins, $dt to $pt", -stake, FxKind.Bad)
            else -> settle("Push at $pt", 0, null)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Hand ${handNo.coerceAtMost(handsTotal)} of $handsTotal", style = Type.headline, color = c.label, modifier = Modifier.weight(1f))
                Text("💰 $chips", style = Type.headline, color = c.accent)
            }
            if (beat != null) Text("Number to beat: $beat chips", style = Type.footnote, color = if (chips > beat) c.accent else c.secondary)
        }
        Spacer(Modifier.weight(1f))
        // dealer
        Text("Dealer" + if (dealer.isNotEmpty() && revealed) " · ${handTotal(dealer)}" else "", style = Type.footnote, color = c.secondary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Row(Modifier.fillMaxWidth().height(86.dp).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            dealer.forEachIndexed { i, card -> key(i, card) { CardView(card, hidden = i == 1 && !revealed) } }
        }
        Spacer(Modifier.height(10.dp))
        Text(message, style = Type.headline, color = if (delta > 0) c.accent else if (delta < 0) c.danger else c.label, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), textAlign = TextAlign.Center, maxLines = 2)
        Spacer(Modifier.height(10.dp))
        // player
        Row(Modifier.fillMaxWidth().height(86.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            player.forEachIndexed { i, card -> key(i, card) { CardView(card) } }
        }
        Text("You" + if (player.isNotEmpty()) " · ${handTotal(player)}" else "", style = Type.footnote, color = c.secondary, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (phase) {
                BjPhase.Bet -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        bets.forEach { b ->
                            val ok = b <= chips
                            val on = b == bet
                            Box(
                                Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(16.dp)).background(if (on && ok) c.accent else c.cardRaised)
                                    .border(1.dp, if (on && ok) c.accent else c.separator, RoundedCornerShape(16.dp))
                                    .then(if (ok) Modifier.pressable(haptic = false) { bet = b } else Modifier),
                                contentAlignment = Alignment.Center,
                            ) { Text("$b", style = Type.headline, color = if (!ok) c.tertiary else if (on) Color.White else c.label) }
                        }
                    }
                    BjButton("Deal  ·  bet ${min(bet, chips)}", c.accent) { deal() }
                }
                BjPhase.Player -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BjButton("Hit", c.accent, Modifier.weight(1f)) { hit() }
                    BjButton("Stand", c.me, Modifier.weight(1f)) { standNow() }
                    if (player.size == 2 && chips >= bet * 2) BjButton("Double", c.special, Modifier.weight(1f)) { double() }
                }
                BjPhase.Dealer -> Spacer(Modifier.height(48.dp))
                BjPhase.Result -> BjButton("Next hand", c.accent) { bet = bets.filter { it <= chips && it <= bet }.maxOrNull() ?: min(bet, chips); handNo++; player = emptyList(); dealer = emptyList(); revealed = false; delta = 0; message = "Place your bet"; phase = BjPhase.Bet }
                BjPhase.End -> BjButton("See my score  ·  $chips chips", c.accent) { onDone(chips.coerceIn(0, 5000)) }
            }
        }
    }
}

@Composable
private fun BjButton(label: String, color: Color, modifier: Modifier = Modifier.fillMaxWidth(), onClick: () -> Unit) {
    Box(
        modifier.height(50.dp).clip(RoundedCornerShape(18.dp)).background(color).pressable(haptic = false, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = Type.headline, color = Color.White, maxLines = 1) }
}
