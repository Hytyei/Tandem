package app.tandem.ui.galaxy

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.Executors
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * The galaxy in 3D: tens of thousands of point sprites on black, light adding up where they crowd.
 * Drag to turn it (it follows your finger and keeps a little momentum), pinch to come closer; left alone
 * it turns slowly by itself. Reduced motion keeps it still.
 */
@SuppressLint("ViewConstructor")
class GalaxyView(context: Context, private val reduced: Boolean) : GLSurfaceView(context) {
    private val renderer = GalaxyRenderer()
    private val gen = Executors.newSingleThreadExecutor()
    private var wanted: Pair<Long, Int>? = null
    /** Called on the main thread once the galaxy has formed, with how many stars it has. */
    var onReady: (Int) -> Unit = {}

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        renderer.reduced = reduced
    }

    /** Shows the galaxy for [seed], grown to [lifetime] stardust. Built off the main thread. */
    fun show(seed: Long, lifetime: Int, tilt: Boolean = true) {
        if (wanted == seed to lifetime) return
        wanted = seed to lifetime
        gen.execute {
            val layers = GalaxyGen.build(seed, lifetime)
            if (wanted != seed to lifetime) return@execute
            queueEvent { renderer.load(layers, tilt) }
            post { onReady(layers.starCount) }
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
                val k = 180f / width
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

private class GalaxyRenderer : GLSurfaceView.Renderer {
    var reduced = false
    private var prog = 0
    private var aPos = 0; private var aCol = 0; private var aSize = 0
    private var uMvp = 0; private var uScale = 0; private var uTime = 0; private var uKind = 0; private var uMaxSize = 0; private var uFade = 0
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

    fun grab() { held = true; vYaw = 0f; vPitch = 0f }
    fun release() { held = false }
    fun turn(dYaw: Float, dPitch: Float, vy: Float, vp: Float) {
        yaw += dYaw; pitch = (pitch - dPitch).coerceIn(0f, 178f)
        vYaw = vy.coerceIn(-600f, 600f); vPitch = -vp.coerceIn(-600f, 600f)
    }
    fun zoomBy(f: Float) { dist = (dist * f).coerceIn(1.5f, 6f) }

    private var last: GalaxyLayers? = null

    fun load(l: GalaxyLayers, tilt: Boolean) {
        last = l
        if (prog == 0) return
        if (tilt) pitch = l.look.tilt
        val layers = listOf(l.field, l.glow, l.dust, l.stars)
        GLES20.glDeleteBuffers(4, bufs, 0)
        GLES20.glGenBuffers(4, bufs, 0)
        layers.forEachIndexed { i, arr ->
            val fb: FloatBuffer = ByteBuffer.allocateDirect(max(4, arr.size * 4)).order(ByteOrder.nativeOrder()).asFloatBuffer().put(arr).also { it.position(0) }
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, bufs[i])
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, arr.size * 4, fb, GLES20.GL_STATIC_DRAW)
            counts[i] = arr.size / 8
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        loaded = true
        fadeStart = SystemClock.uptimeMillis()
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        prog = link(VERT, FRAG)
        aPos = GLES20.glGetAttribLocation(prog, "aPos"); aCol = GLES20.glGetAttribLocation(prog, "aCol"); aSize = GLES20.glGetAttribLocation(prog, "aSize")
        uMvp = GLES20.glGetUniformLocation(prog, "uMvp"); uScale = GLES20.glGetUniformLocation(prog, "uScale"); uTime = GLES20.glGetUniformLocation(prog, "uTime")
        uKind = GLES20.glGetUniformLocation(prog, "uKind"); uMaxSize = GLES20.glGetUniformLocation(prog, "uMaxSize"); uFade = GLES20.glGetUniformLocation(prog, "uFade")
        val range = FloatArray(2); GLES20.glGetFloatv(GLES20.GL_ALIASED_POINT_SIZE_RANGE, range, 0); maxPoint = max(16f, range[1])
        // a new context (first start, or back from the background): make the buffers again
        loaded = false
        last?.let { load(it, false) }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        w = width; h = max(1, height)
        GLES20.glViewport(0, 0, w, h)
        Matrix.perspectiveM(proj, 0, 42f, w.toFloat() / h, .05f, 40f)
    }

    override fun onDrawFrame(gl: GL10?) {
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
        GLES20.glUniform1f(uTime, if (reduced) 0f else (now - start) / 1000f)
        GLES20.glUniform1f(uMaxSize, maxPoint)
        GLES20.glUniform1f(uFade, min(1f, (now - fadeStart) / 900f).let { it * it * (3f - 2f * it) })
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        val px = h / 2f / Math.tan(Math.toRadians(21.0)).toFloat()      // pixels per unit at distance 1
        // field (additive), glow (additive), dust (darkening), stars (additive)
        draw(0, 0f, 2.2f * w / 1080f, GLES20.GL_ONE, GLES20.GL_ONE)
        draw(1, 1f, px, GLES20.GL_ONE, GLES20.GL_ONE)
        draw(2, 2f, px, GLES20.GL_ZERO, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        draw(3, 0f, 2.2f * w / 1080f, GLES20.GL_ONE, GLES20.GL_ONE)
    }

    private fun draw(i: Int, kind: Float, scale: Float, src: Int, dst: Int) {
        if (counts[i] == 0) return
        GLES20.glBlendFunc(src, dst)
        GLES20.glUniform1f(uKind, kind)
        // stars and field stars are sized in pixels; glow and dust in world units
        GLES20.glUniform1f(uScale, scale)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, bufs[i])
        GLES20.glEnableVertexAttribArray(aPos); GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 32, 0)
        GLES20.glEnableVertexAttribArray(aCol); GLES20.glVertexAttribPointer(aCol, 4, GLES20.GL_FLOAT, false, 32, 12)
        GLES20.glEnableVertexAttribArray(aSize); GLES20.glVertexAttribPointer(aSize, 1, GLES20.GL_FLOAT, false, 32, 28)
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, counts[i])
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    private fun link(vs: String, fs: String): Int {
        fun shader(type: Int, src: String) = GLES20.glCreateShader(type).also { GLES20.glShaderSource(it, src); GLES20.glCompileShader(it) }
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, shader(GLES20.GL_VERTEX_SHADER, vs)); GLES20.glAttachShader(p, shader(GLES20.GL_FRAGMENT_SHADER, fs))
        GLES20.glLinkProgram(p)
        return p
    }

    companion object {
        // uKind: 0 = stars (pixel sized, twinkling), 1 = glow (world sized, soft), 2 = dust (world sized, soft, dark)
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
            uniform float uKind;
            varying vec4 vCol;
            void main() {
                vec2 d = gl_PointCoord - vec2(0.5);
                float r2 = dot(d, d) * 4.0;
                if (r2 > 1.0) discard;
                if (uKind < 0.5) {
                    float k = exp(-r2 * 9.0) + 0.18 * exp(-r2 * 2.5);
                    gl_FragColor = vec4(vCol.rgb * k * vCol.a, 1.0);
                } else if (uKind < 1.5) {
                    float k = (1.0 - r2); k = k * k * k;
                    gl_FragColor = vec4(vCol.rgb * k * vCol.a, 1.0);
                } else {
                    float k = (1.0 - r2); k = k * k;
                    gl_FragColor = vec4(vCol.rgb, k * vCol.a);
                }
            }
        """
    }
}
