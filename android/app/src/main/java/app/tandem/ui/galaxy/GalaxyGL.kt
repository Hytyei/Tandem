package app.tandem.ui.galaxy

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.TextureView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * The galaxy in 3D: tens of thousands of point sprites, light adding up where they crowd, drawn on a
 * transparent TextureView so the app's dusk sky shows through. Drag to turn it (it follows your finger
 * and keeps a little momentum), pinch to come closer; left alone it turns slowly by itself.
 *
 * A TextureView (not a GLSurfaceView) composes like any other view: it fades, sits under the glass
 * panels and never leaves a black hole in the window. If GL can't start on a phone, [onFailed] fires
 * and the screen shows a painted galaxy instead.
 */
@SuppressLint("ViewConstructor")
class GalaxyView(context: Context, private val reduced: Boolean) : TextureView(context), TextureView.SurfaceTextureListener {
    private val renderer = GalaxyRenderer(reduced)
    private val gen = Executors.newSingleThreadExecutor()
    private var wanted: Pair<Long, Int>? = null
    private var thread: RenderThread? = null
    @Volatile private var paused = false
    /** Called on the main thread once the galaxy has formed, with how many stars it has. */
    var onReady: (Int) -> Unit = {}
    /** Called on the main thread if this phone can't draw it with GL. */
    var onFailed: () -> Unit = {}

    init {
        isOpaque = false
        surfaceTextureListener = this
    }

    /** Shows the galaxy for [seed], grown to [lifetime] stardust. Built off the main thread. */
    fun show(seed: Long, lifetime: Int, tilt: Boolean = true) {
        if (wanted == seed to lifetime) return
        wanted = seed to lifetime
        gen.execute {
            val layers = runCatching { GalaxyGen.build(seed, lifetime) }.getOrNull() ?: return@execute
            if (wanted != seed to lifetime) return@execute
            renderer.pending(layers, tilt)
            post { onReady(layers.starCount) }
        }
    }

    fun onPause() { paused = true }
    fun onResume() { paused = false }

    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
        thread = RenderThread(st, w, h).also { it.start() }
    }
    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) { thread?.resize(w, h) }
    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean { thread?.finish(); thread = null; return true }
    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}

    private inner class RenderThread(private val st: SurfaceTexture, @Volatile var w: Int, @Volatile var h: Int) : Thread("galaxy-gl") {
        @Volatile private var running = true
        @Volatile private var sized = true
        fun resize(nw: Int, nh: Int) { w = nw; h = nh; sized = true }
        fun finish() { running = false; join(500) }

        override fun run() {
            var dpy: EGLDisplay = EGL14.EGL_NO_DISPLAY; var ctx: EGLContext = EGL14.EGL_NO_CONTEXT; var surf: EGLSurface = EGL14.EGL_NO_SURFACE
            try {
                dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
                val v = IntArray(2); check(EGL14.eglInitialize(dpy, v, 0, v, 1)) { "eglInitialize" }
                val attrs = intArrayOf(EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT, EGL14.EGL_NONE)
                val cfgs = arrayOfNulls<EGLConfig>(1); val n = IntArray(1)
                check(EGL14.eglChooseConfig(dpy, attrs, 0, cfgs, 0, 1, n, 0) && n[0] > 0) { "no config" }
                ctx = EGL14.eglCreateContext(dpy, cfgs[0], EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
                check(ctx != EGL14.EGL_NO_CONTEXT) { "no context" }
                surf = EGL14.eglCreateWindowSurface(dpy, cfgs[0], st, intArrayOf(EGL14.EGL_NONE), 0)
                check(surf != EGL14.EGL_NO_SURFACE) { "no surface" }
                check(EGL14.eglMakeCurrent(dpy, surf, surf, ctx)) { "makeCurrent" }
                renderer.created()
                while (running) {
                    val t0 = SystemClock.uptimeMillis()
                    if (sized) { renderer.sized(w, h); sized = false }
                    if (!paused) {
                        renderer.frame()
                        EGL14.eglSwapBuffers(dpy, surf)
                    }
                    val spent = SystemClock.uptimeMillis() - t0
                    sleep(max(2L, (if (paused) 100L else 16L) - spent))
                }
            } catch (_: InterruptedException) {
            } catch (e: Throwable) {
                Log.w("Galaxy", "GL failed, falling back to a painted galaxy", e)
                post { onFailed() }
            } finally {
                runCatching {
                    if (dpy != EGL14.EGL_NO_DISPLAY) {
                        EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                        if (surf != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(dpy, surf)
                        if (ctx != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(dpy, ctx)
                        EGL14.eglTerminate(dpy)
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ touch
    private var lastX = 0f; private var lastY = 0f; private var lastT = 0L
    private var dragging = false
    private val scaler = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean { renderer.zoomBy(1f / d.scaleFactor); return true }
    })

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        scaler.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { lastX = e.x; lastY = e.y; lastT = e.eventTime; dragging = true; renderer.grab() }
            MotionEvent.ACTION_POINTER_DOWN -> dragging = false     // a second finger means a pinch, not a turn
            MotionEvent.ACTION_MOVE -> if (dragging && e.pointerCount == 1) {
                val dx = e.x - lastX; val dy = e.y - lastY; val dt = max(1L, e.eventTime - lastT)
                val k = 180f / max(1, width)
                renderer.turn(dx * k, dy * k, dx * k / dt * 1000f, dy * k / dt * 1000f)
                lastX = e.x; lastY = e.y; lastT = e.eventTime
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // a finger that stopped before lifting shouldn't fling
                if (e.eventTime - lastT > 80) renderer.turn(0f, 0f, 0f, 0f)
                dragging = false; renderer.release()
            }
        }
        parent?.requestDisallowInterceptTouchEvent(true)
        return true
    }
}

private class GalaxyRenderer(private val reduced: Boolean) {
    private var prog = 0
    private var aPos = 0; private var aCol = 0; private var aSize = 0
    private var uMvp = 0; private var uScale = 0; private var uTime = 0; private var uKind = 0; private var uKindF = 0; private var uMaxSize = 0; private var uFade = 0
    private val bufs = IntArray(4)
    private val counts = IntArray(4)
    private var loaded = false
    private var fadeStart = 0L
    private var maxPoint = 64f

    private val proj = FloatArray(16); private val view = FloatArray(16); private val model = FloatArray(16); private val mvp = FloatArray(16); private val tmp = FloatArray(16)
    private var w = 1; private var h = 1

    // the view: yaw spins the disk in its plane, pitch tilts it toward you
    @Volatile private var yaw = 20f
    @Volatile private var pitch = 55f
    @Volatile private var vYaw = 0f
    @Volatile private var vPitch = 0f
    @Volatile private var dist = 3.1f
    @Volatile private var held = false
    private var lastFrame = 0L
    private val start = SystemClock.uptimeMillis()

    // a galaxy waiting to be uploaded by the GL thread
    @Volatile private var incoming: Pair<GalaxyLayers, Boolean>? = null
    private var last: GalaxyLayers? = null

    fun grab() { held = true; vYaw = 0f; vPitch = 0f }
    fun release() { held = false }
    fun turn(dYaw: Float, dPitch: Float, vy: Float, vp: Float) {
        yaw += dYaw; pitch = (pitch - dPitch).coerceIn(0f, 178f)
        vYaw = vy.coerceIn(-600f, 600f); vPitch = -vp.coerceIn(-600f, 600f)
    }
    fun zoomBy(f: Float) { dist = (dist * f).coerceIn(1.5f, 6f) }
    fun pending(l: GalaxyLayers, tilt: Boolean) { incoming = l to tilt }

    private fun upload(l: GalaxyLayers, tilt: Boolean) {
        last = l
        if (tilt) pitch = l.look.tilt
        val layers = listOf(l.field, l.glow, l.dust, l.stars)
        if (bufs[0] != 0) GLES20.glDeleteBuffers(4, bufs, 0)
        GLES20.glGenBuffers(4, bufs, 0)
        layers.forEachIndexed { i, arr ->
            val fb = ByteBuffer.allocateDirect(max(4, arr.size * 4)).order(ByteOrder.nativeOrder()).asFloatBuffer().put(arr).also { it.position(0) }
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, bufs[i])
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, arr.size * 4, fb, GLES20.GL_STATIC_DRAW)
            counts[i] = arr.size / 8
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        loaded = true
        fadeStart = SystemClock.uptimeMillis()
    }

    fun created() {
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        prog = link(VERT, FRAG)
        aPos = GLES20.glGetAttribLocation(prog, "aPos"); aCol = GLES20.glGetAttribLocation(prog, "aCol"); aSize = GLES20.glGetAttribLocation(prog, "aSize")
        uMvp = GLES20.glGetUniformLocation(prog, "uMvp"); uScale = GLES20.glGetUniformLocation(prog, "uScale"); uTime = GLES20.glGetUniformLocation(prog, "uTime")
        uKind = GLES20.glGetUniformLocation(prog, "uKind"); uKindF = GLES20.glGetUniformLocation(prog, "uKindF")
        uMaxSize = GLES20.glGetUniformLocation(prog, "uMaxSize"); uFade = GLES20.glGetUniformLocation(prog, "uFade")
        val range = FloatArray(2); GLES20.glGetFloatv(GLES20.GL_ALIASED_POINT_SIZE_RANGE, range, 0); maxPoint = max(16f, range[1])
        loaded = false
        bufs.fill(0)
        last?.let { upload(it, false) }
    }

    fun sized(width: Int, height: Int) {
        w = max(1, width); h = max(1, height)
        GLES20.glViewport(0, 0, w, h)
        Matrix.perspectiveM(proj, 0, 42f, w.toFloat() / h, .05f, 40f)
    }

    fun frame() {
        incoming?.let { incoming = null; upload(it.first, it.second) }
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 0f else min(.05f, (now - lastFrame) / 1000f); lastFrame = now
        if (!held) {
            // momentum fades out; then the slow turn of its own
            val decay = exp(-3.2f * dt)
            vYaw *= decay; vPitch *= decay
            yaw += vYaw * dt; pitch = (pitch + vPitch * dt).coerceIn(0f, 178f)
            if (!reduced && abs(vYaw) < 4f) yaw += 2.2f * dt
        }
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (!loaded) return

        // portrait screens see the galaxy from a little further away, so it fits the width
        val aspect = w.toFloat() / h
        val fitDist = 1.06f / (.384f * min(1f, aspect))
        Matrix.setLookAtM(view, 0, 0f, 0f, dist / 3.1f * fitDist, 0f, 0f, 0f, 0f, 1f, 0f)
        Matrix.setIdentityM(model, 0)
        Matrix.rotateM(model, 0, -pitch, 1f, 0f, 0f)
        Matrix.rotateM(model, 0, yaw, 0f, 0f, 1f)
        Matrix.multiplyMM(tmp, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, proj, 0, tmp, 0)

        GLES20.glUseProgram(prog)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        GLES20.glUniform1f(uTime, if (reduced) 0f else ((now - start) % 3_600_000L) / 1000f)
        GLES20.glUniform1f(uMaxSize, maxPoint)
        GLES20.glUniform1f(uFade, min(1f, (now - fadeStart) / 900f).let { it * it * (3f - 2f * it) })
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        val px = h / 2f / Math.tan(Math.toRadians(21.0)).toFloat()      // pixels per unit at distance 1
        // light is premultiplied with little coverage, so it adds onto the sky behind; dust covers it
        draw(0, 0f, 2.2f * w / 1080f, GLES20.GL_ONE, GLES20.GL_ONE)
        draw(1, 1f, px, GLES20.GL_ONE, GLES20.GL_ONE)
        draw(2, 2f, px, GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        draw(3, 0f, 2.2f * w / 1080f, GLES20.GL_ONE, GLES20.GL_ONE)
    }

    private fun draw(i: Int, kind: Float, scale: Float, src: Int, dst: Int) {
        if (counts[i] == 0) return
        GLES20.glBlendFunc(src, dst)
        GLES20.glUniform1f(uKind, kind); GLES20.glUniform1f(uKindF, kind)
        GLES20.glUniform1f(uScale, scale)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, bufs[i])
        GLES20.glEnableVertexAttribArray(aPos); GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 32, 0)
        GLES20.glEnableVertexAttribArray(aCol); GLES20.glVertexAttribPointer(aCol, 4, GLES20.GL_FLOAT, false, 32, 12)
        GLES20.glEnableVertexAttribArray(aSize); GLES20.glVertexAttribPointer(aSize, 1, GLES20.GL_FLOAT, false, 32, 28)
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, counts[i])
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    private fun link(vs: String, fs: String): Int {
        fun shader(type: Int, src: String): Int {
            val s = GLES20.glCreateShader(type); GLES20.glShaderSource(s, src); GLES20.glCompileShader(s)
            val ok = IntArray(1); GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
            check(ok[0] != 0) { "shader: " + GLES20.glGetShaderInfoLog(s) }
            return s
        }
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, shader(GLES20.GL_VERTEX_SHADER, vs)); GLES20.glAttachShader(p, shader(GLES20.GL_FRAGMENT_SHADER, fs))
        GLES20.glLinkProgram(p)
        val ok = IntArray(1); GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
        check(ok[0] != 0) { "link: " + GLES20.glGetProgramInfoLog(p) }
        return p
    }

    companion object {
        // kind: 0 = stars (pixel sized, twinkling), 1 = glow (world sized, soft), 2 = dust (world sized, soft, dark).
        // The vertex and fragment shaders each get their own uniform (uKind, uKindF): a uniform shared between
        // them must have the same precision, and strict phone GPUs refuse to link when it doesn't.
        private const val VERT = """
            uniform mat4 uMvp; uniform float uScale; uniform float uTime; uniform float uKind; uniform float uMaxSize; uniform float uFade;
            attribute vec3 aPos; attribute vec4 aCol; attribute float aSize;
            varying vec4 vCol;
            void main() {
                vec4 p = uMvp * vec4(aPos, 1.0);
                gl_Position = p;
                float a = aCol.a * uFade;
                float s;
                if (uKind < 0.5) {
                    float f = fract(sin(dot(aPos.xy, vec2(12.9898, 78.233))) * 43758.5453);
                    a *= 0.78 + 0.22 * sin(uTime * (0.8 + f * 2.6) + f * 40.0);
                    s = aSize * uScale * clamp(4.0 / p.w, 0.7, 2.2);
                    // stars too small to cover a pixel get dimmer instead of vanishing
                    if (s < 1.6) { a *= s / 1.6; s = 1.6; }
                } else {
                    s = aSize * uScale / p.w;
                    if (s > uMaxSize) { a *= uMaxSize / s; s = uMaxSize; }
                }
                gl_PointSize = s;
                vCol = vec4(aCol.rgb, a);
            }
        """
        private const val FRAG = """
            precision mediump float;
            uniform float uKindF;
            varying vec4 vCol;
            void main() {
                vec2 d = gl_PointCoord - vec2(0.5);
                float r2 = dot(d, d) * 4.0;
                if (r2 > 1.0) discard;
                if (uKindF < 0.5) {
                    float k = exp(-r2 * 9.0) + 0.18 * exp(-r2 * 2.5);
                    vec3 c = vCol.rgb * k * vCol.a;
                    gl_FragColor = vec4(c, max(c.r, max(c.g, c.b)) * 0.35);
                } else if (uKindF < 1.5) {
                    float k = (1.0 - r2); k = k * k * k;
                    vec3 c = vCol.rgb * k * vCol.a;
                    gl_FragColor = vec4(c, max(c.r, max(c.g, c.b)) * 0.25);
                } else {
                    float k = (1.0 - r2); k = k * k;
                    float a = k * vCol.a;
                    gl_FragColor = vec4(vCol.rgb * a, a);
                }
            }
        """
    }
}
