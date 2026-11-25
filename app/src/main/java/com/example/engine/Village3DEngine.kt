package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import com.example.R
import com.example.model.CharacterPreset
import com.example.model.GraphicsQuality
import com.example.model.LocationCategory
import com.example.model.VillageLocation
import com.example.model.WeatherMode
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

enum class ExplorerRenderMode {
    WALK_3D,
    PANORAMA_360,
    CHARACTER_PREVIEW
}

data class EngineTelemetry(
    val fps: Int = 60,
    val playerX: Float = 0f,
    val playerZ: Float = 0f,
    val cameraYawDeg: Float = 0f,
    val nearbyLocationId: String? = null,
    val nearbyLocationName: String? = null,
    val activeDrawCalls: Int = 0
)

private class Mesh(
    vertices: FloatArray,
    indices: ShortArray? = null
) {
    val vertexCount: Int = vertices.size / 12 // x,y,z, nx,ny,nz, u,v, r,g,b,a
    val indexCount: Int = indices?.size ?: 0
    val vbo: FloatBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply { put(vertices); position(0) }

    val ibo: ShortBuffer? = indices?.let {
        ByteBuffer.allocateDirect(it.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .apply { put(it); position(0) }
    }

    fun draw(strideBytes: Int = 12 * 4) {
        vbo.position(0)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, strideBytes, vbo)

        vbo.position(3)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, strideBytes, vbo)

        vbo.position(6)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 2, GLES30.GL_FLOAT, false, strideBytes, vbo)

        vbo.position(8)
        GLES30.glEnableVertexAttribArray(3)
        GLES30.glVertexAttribPointer(3, 4, GLES30.GL_FLOAT, false, strideBytes, vbo)

        if (ibo != null) {
            ibo.position(0)
            GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCount, GLES30.GL_UNSIGNED_SHORT, ibo)
        } else {
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, vertexCount)
        }
    }
}

private class PanoSphereMesh(stacks: Int = 36, slices: Int = 72) {
    private val vbo: FloatBuffer
    private val ibo: ShortBuffer
    private val indexCount: Int

    init {
        val verts = ArrayList<Float>()
        val inds = ArrayList<Short>()
        for (i in 0..stacks) {
            val v = i.toFloat() / stacks
            val phi = v * PI
            for (j in 0..slices) {
                val u = j.toFloat() / slices
                val theta = u * 2.0 * PI
                val x = (sin(phi) * cos(theta)).toFloat() * 30f
                val y = cos(phi).toFloat() * 30f
                val z = (sin(phi) * sin(theta)).toFloat() * 30f
                verts.add(x); verts.add(y); verts.add(z)
                verts.add(1f - u); verts.add(v)
            }
        }
        val stride = slices + 1
        for (i in 0 until stacks) {
            for (j in 0 until slices) {
                val a = (i * stride + j).toShort()
                val b = ((i + 1) * stride + j).toShort()
                val c = ((i + 1) * stride + j + 1).toShort()
                val d = (i * stride + j + 1).toShort()
                inds.add(a); inds.add(d); inds.add(b)
                inds.add(b); inds.add(d); inds.add(c)
            }
        }
        indexCount = inds.size
        vbo = ByteBuffer.allocateDirect(verts.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { verts.forEach { put(it) }; position(0) }
        ibo = ByteBuffer.allocateDirect(inds.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .apply { inds.forEach { put(it) }; position(0) }
    }

    fun draw() {
        val strideBytes = 5 * 4
        vbo.position(0)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, strideBytes, vbo)

        vbo.position(3)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, strideBytes, vbo)

        ibo.position(0)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCount, GLES30.GL_UNSIGNED_SHORT, ibo)
    }
}

class VillageGLRenderer(
    private val context: Context,
    private val onTelemetryUpdate: (EngineTelemetry) -> Unit
) : GLSurfaceView.Renderer {

    @Volatile var mode: ExplorerRenderMode = ExplorerRenderMode.WALK_3D
    @Volatile var graphicsQuality: GraphicsQuality = GraphicsQuality.HIGH
    @Volatile var lodEnabled: Boolean = true
    @Volatile var occlusionCulling: Boolean = true
    @Volatile var dynamicResolution: Boolean = true
    @Volatile var weatherMode: WeatherMode = WeatherMode.GOLDEN_HOUR
    @Volatile var timeOfDay: Float = 0.72f
    @Volatile var characterPreset: CharacterPreset = CharacterPreset.EXPLORER
    @Volatile var characterAccentHex: String = "#FFB020"
    @Volatile var walkSpeedMultiplier: Float = 1.0f
    @Volatile var cameraSensitivity: Float = 1.0f
    @Volatile var invertCameraY: Boolean = false

    @Volatile var joystickX: Float = 0f
    @Volatile var joystickY: Float = 0f
    @Volatile var isSprinting: Boolean = false
    @Volatile private var jumpVelocity: Float = 0f
    @Volatile private var playerY: Float = 0f
    @Volatile private var playerX: Float = 0f
    @Volatile private var playerZ: Float = 6f
    @Volatile private var playerFacingRad: Float = PI.toFloat()

    @Volatile var cameraYawRad: Float = 0f
    @Volatile var cameraPitchRad: Float = 0.25f
    @Volatile var panoFovDeg: Float = 72f

    @Volatile private var locations: List<VillageLocation> = emptyList()
    @Volatile private var activePanoramaPath: String? = null
    @Volatile private var panoramaNeedsReload: Boolean = true

    private var worldProgram = 0
    private var skyProgram = 0
    private var panoProgram = 0

    private var unitCubeMesh: Mesh? = null
    private var billboardMesh: Mesh? = null
    private var groundMesh: Mesh? = null
    private var roadMesh: Mesh? = null
    private var skySphere: PanoSphereMesh? = null
    private var panoSphere: PanoSphereMesh? = null

    private var panoTextureId: Int = 0
    private val locationTextures = mutableMapOf<String, Int>()
    private var pendingTextureSync = false

    private val projMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val vpMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    private var surfaceW = 1
    private var surfaceH = 1
    private var lastFrameNs = System.nanoTime()
    private var walkAnimPhase = 0f
    private var worldTime = 0f
    private var fpsAccumFrames = 0
    private var fpsAccumTime = 0f
    private var currentFps = 60

    fun setLocations(newLocations: List<VillageLocation>) {
        locations = newLocations
        pendingTextureSync = true
    }

    fun setActivePanorama(localPath: String?) {
        activePanoramaPath = localPath
        panoramaNeedsReload = true
    }

    fun teleportTo(x: Float, z: Float) {
        playerX = x
        playerZ = z + 4f
        playerY = 0f
        jumpVelocity = 0f
    }

    fun triggerJump() {
        if (playerY <= 0.02f) {
            jumpVelocity = 6.2f
        }
    }

    fun adjustCameraLook(dxPx: Float, dyPx: Float) {
        val sens = 0.0045f * cameraSensitivity
        cameraYawRad -= dxPx * sens
        val ySign = if (invertCameraY) -1f else 1f
        val maxPitch = if (mode == ExplorerRenderMode.PANORAMA_360) 1.38f else 1.05f
        val minPitch = if (mode == ExplorerRenderMode.PANORAMA_360) -1.38f else -0.25f
        cameraPitchRad = (cameraPitchRad + dyPx * sens * ySign).coerceIn(minPitch, maxPitch)
    }

    fun adjustPanoZoom(deltaFov: Float) {
        panoFovDeg = (panoFovDeg + deltaFov).coerceIn(38f, 105f)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        worldProgram = VillageShaders.buildProgram(
            VillageShaders.WORLD_VERTEX_SHADER,
            VillageShaders.WORLD_FRAGMENT_SHADER
        )
        skyProgram = VillageShaders.buildProgram(
            VillageShaders.SKY_VERTEX_SHADER,
            VillageShaders.SKY_FRAGMENT_SHADER
        )
        panoProgram = VillageShaders.buildProgram(
            VillageShaders.PANO_VERTEX_SHADER,
            VillageShaders.PANO_FRAGMENT_SHADER
        )

        unitCubeMesh = createCubeMesh()
        billboardMesh = createQuadMesh()
        groundMesh = createGroundMesh()
        roadMesh = createRoadMesh()
        skySphere = PanoSphereMesh(18, 36)
        panoSphere = PanoSphereMesh(36, 72)

        panoramaNeedsReload = true
        pendingTextureSync = true
        lastFrameNs = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceW = max(1, width)
        surfaceH = max(1, height)
        GLES30.glViewport(0, 0, surfaceW, surfaceH)
    }

    override fun onDrawFrame(gl: GL10?) {
        val nowNs = System.nanoTime()
        val dt = ((nowNs - lastFrameNs) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
        lastFrameNs = nowNs
        worldTime += dt

        fpsAccumFrames++
        fpsAccumTime += dt
        if (fpsAccumTime >= 0.4f) {
            currentFps = (fpsAccumFrames / fpsAccumTime).toInt().coerceIn(1, 120)
            fpsAccumFrames = 0
            fpsAccumTime = 0f
        }

        if (panoramaNeedsReload) {
            loadActivePanoramaTexture()
            panoramaNeedsReload = false
        }
        if (pendingTextureSync) {
            syncLocationTextures()
            pendingTextureSync = false
        }

        // Dynamic resolution viewport scaling
        val resScale = if (dynamicResolution) graphicsQuality.resolutionScale else 1.0f
        val vpW = max(1, (surfaceW * resScale).toInt())
        val vpH = max(1, (surfaceH * resScale).toInt())
        GLES30.glViewport(0, 0, surfaceW, surfaceH)

        when (mode) {
            ExplorerRenderMode.PANORAMA_360 -> renderPanoramaFrame(vpW, vpH)
            ExplorerRenderMode.CHARACTER_PREVIEW -> renderCharacterPreview(dt)
            ExplorerRenderMode.WALK_3D -> renderWalkWorldFrame(dt)
        }
    }

    private fun renderPanoramaFrame(vpW: Int, vpH: Int) {
        GLES30.glClearColor(0.04f, 0.06f, 0.05f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glDisable(GLES30.GL_CULL_FACE)

        val aspect = surfaceW.toFloat() / surfaceH.toFloat()
        Matrix.perspectiveM(projMatrix, 0, panoFovDeg, aspect, 0.1f, 100f)

        val lookX = (cos(cameraPitchRad.toDouble()) * sin(cameraYawRad.toDouble())).toFloat()
        val lookY = sin(cameraPitchRad.toDouble()).toFloat()
        val lookZ = (cos(cameraPitchRad.toDouble()) * cos(cameraYawRad.toDouble())).toFloat()
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 0f, lookX, lookY, lookZ, 0f, 1f, 0f)
        Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, viewMatrix, 0)

        GLES30.glUseProgram(panoProgram)
        val mvpLoc = GLES30.glGetUniformLocation(panoProgram, "uMvpMatrix")
        val texLoc = GLES30.glGetUniformLocation(panoProgram, "uTexture")
        val expLoc = GLES30.glGetUniformLocation(panoProgram, "uExposure")
        GLES30.glUniformMatrix4fv(mvpLoc, 1, false, mvpMatrix, 0)
        GLES30.glUniform1f(expLoc, if (weatherMode == WeatherMode.NIGHT) 0.72f else 1.05f)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, panoTextureId)
        GLES30.glUniform1i(texLoc, 0)

        panoSphere?.draw()

        onTelemetryUpdate(
            EngineTelemetry(
                fps = currentFps,
                playerX = playerX,
                playerZ = playerZ,
                cameraYawDeg = ((cameraYawRad * 180f / PI.toFloat()) % 360f + 360f) % 360f,
                activeDrawCalls = 1
            )
        )
    }

    private fun renderCharacterPreview(dt: Float) {
        walkAnimPhase += dt * 3.0f
        GLES30.glClearColor(0.05f, 0.08f, 0.07f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        val aspect = surfaceW.toFloat() / surfaceH.toFloat()
        Matrix.perspectiveM(projMatrix, 0, 46f, aspect, 0.1f, 50f)
        val orbitX = sin(worldTime * 0.65f) * 3.3f
        val orbitZ = cos(worldTime * 0.65f) * 3.3f
        Matrix.setLookAtM(viewMatrix, 0, orbitX, 1.85f, orbitZ, 0f, 1.0f, 0f, 0f, 1f, 0f)
        Matrix.multiplyMM(vpMatrix, 0, projMatrix, 0, viewMatrix, 0)

        GLES30.glUseProgram(worldProgram)
        bindWorldLighting(floatArrayOf(orbitX, 1.85f, orbitZ))
        // Pedestal
        drawBox(0f, -0.15f, 0f, 2.2f, 0.3f, 2.2f, 0f, 0.14f, 0.22f, 0.19f, 1f)
        drawCharacter(0f, 0f, 0f, 0f, 0.35f)
    }

    private fun renderWalkWorldFrame(dt: Float) {
        // 1. Update third-person character physics & controls
        val speedBase = if (isSprinting) 9.8f else 4.9f
        val moveSpeed = speedBase * walkSpeedMultiplier
        val inputMag = sqrt(joystickX * joystickX + joystickY * joystickY).coerceAtMost(1f)
        if (inputMag > 0.05f) {
            val forwardX = sin(cameraYawRad)
            val forwardZ = cos(cameraYawRad)
            val rightX = cos(cameraYawRad)
            val rightZ = -sin(cameraYawRad)

            // joystickY > 0 is forward (up on pad)
            val dirX = forwardX * joystickY + rightX * joystickX
            val dirZ = forwardZ * joystickY + rightZ * joystickX
            val norm = sqrt(dirX * dirX + dirZ * dirZ).coerceAtLeast(0.001f)
            val vx = (dirX / norm) * moveSpeed * inputMag
            val vz = (dirZ / norm) * moveSpeed * inputMag

            playerX = (playerX + vx * dt).coerceIn(-160f, 160f)
            playerZ = (playerZ + vz * dt).coerceIn(-160f, 160f)
            playerFacingRad = kotlin.math.atan2(vx, vz)
            walkAnimPhase += dt * (if (isSprinting) 13.5f else 8.0f) * inputMag
        } else {
            walkAnimPhase = 0f
        }

        if (playerY > 0f || jumpVelocity > 0f) {
            playerY += jumpVelocity * dt
            jumpVelocity -= 18.0f * dt
            if (playerY <= 0f) {
                playerY = 0f
                jumpVelocity = 0f
            }
        }

        // 2. Camera third-person orbit
        val aspect = surfaceW.toFloat() / surfaceH.toFloat()
        Matrix.perspectiveM(projMatrix, 0, 58f, aspect, 0.2f, 260f)

        val camDist = 5.4f
        val camHeight = 2.0f + sin(cameraPitchRad) * camDist
        val horizDist = cos(cameraPitchRad) * camDist
        val camX = playerX - sin(cameraYawRad) * horizDist
        val camZ = playerZ - cos(cameraYawRad) * horizDist
        val camY = max(0.65f, playerY + camHeight)

        Matrix.setLookAtM(
            viewMatrix, 0,
            camX, camY, camZ,
            playerX, playerY + 1.35f, playerZ,
            0f, 1f, 0f
        )
        Matrix.multiplyMM(vpMatrix, 0, projMatrix, 0, viewMatrix, 0)

        GLES30.glClearColor(0.05f, 0.08f, 0.1f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        // 3. Draw Sky Dome
        drawSkyDome(camX, camY, camZ)

        // 4. Draw 3D Village World
        var drawCalls = 0
        GLES30.glUseProgram(worldProgram)
        bindWorldLighting(floatArrayOf(camX, camY, camZ))

        // Terrain & Main Road
        drawMeshModel(groundMesh, 0f, -0.02f, 0f, 1f, 1f, 1f, 0f, 0)
        drawMeshModel(roadMesh, 0f, 0.01f, 0f, 1f, 1f, 1f, 0f, 0)
        drawCalls += 2

        val maxDrawDist = if (lodEnabled) graphicsQuality.lodDistance else 220f
        val forwardX = sin(cameraYawRad)
        val forwardZ = cos(cameraYawRad)

        // Draw connecting roads between linked locations
        val locById = locations.associateBy { it.id }
        locations.forEach { loc ->
            val lx = loc.posX.toFloat()
            val lz = loc.posZ.toFloat()
            loc.connectedIds.forEach { targetId ->
                val target = locById[targetId]
                if (target != null && loc.id < target.id) {
                    val tx = target.posX.toFloat()
                    val tz = target.posZ.toFloat()
                    val dx = tx - lx
                    val dz = tz - lz
                    val len = sqrt(dx * dx + dz * dz)
                    if (len > 0.5f) {
                        val midX = (lx + tx) * 0.5f
                        val midZ = (lz + tz) * 0.5f
                        val angle = kotlin.math.atan2(dx, dz)
                        drawBox(midX, 0.015f, midZ, 2.8f, 0.03f, len, angle, 0.56f, 0.45f, 0.34f, 1f)
                        drawCalls++
                    }
                }
            }
        }

        // Draw scanned village nodes + reconstructed structures + nearest portal detection
        var nearestLoc: VillageLocation? = null
        var nearestDistSq = 8.5f * 8.5f

        locations.forEach { loc ->
            val lx = loc.posX.toFloat()
            val lz = loc.posZ.toFloat()
            val dx = lx - playerX
            val dz = lz - playerZ
            val distSq = dx * dx + dz * dz
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq
                nearestLoc = loc
            }

            val camDx = lx - camX
            val camDz = lz - camZ
            val camDistSq = camDx * camDx + camDz * camDz
            if (camDistSq > maxDrawDist * maxDrawDist) return@forEach

            // Simple frustum culling behind camera
            if (occlusionCulling && camDistSq > 144f) {
                val dot = camDx * forwardX + camDz * forwardZ
                if (dot < -12f) return@forEach
            }

            // Glowing Portal Beacon Ring
            val pulse = 0.85f + 0.2f * sin(worldTime * 3.2f)
            val beaconR = if (loc.markedAsHouse) 1.0f else 0.98f
            val beaconG = if (loc.markedAsHouse) 0.24f else 0.70f
            val beaconB = if (loc.markedAsHouse) 0.36f else 0.14f
            drawBox(lx, 0.08f, lz, 2.4f * pulse, 0.12f, 2.4f * pulse, worldTime * 0.6f, beaconR, beaconG, beaconB, 0.85f)
            drawBox(lx, 1.5f, lz, 0.18f, 3.0f, 0.18f, worldTime * 1.2f, beaconR, beaconG, beaconB, 0.55f)
            drawCalls += 2

            // Real Scanned Photo Frame Billboard facing toward central road
            val texId = locationTextures[loc.id] ?: panoTextureId
            if (texId != 0) {
                drawMeshModel(billboardMesh, lx, 2.4f, lz - 3.6f, 4.6f, 2.7f, 1f, 0f, texId)
                // Support posts for photo frame
                drawBox(lx - 2.1f, 1.2f, lz - 3.6f, 0.16f, 2.4f, 0.16f, 0f, 0.32f, 0.24f, 0.18f, 1f)
                drawBox(lx + 2.1f, 1.2f, lz - 3.6f, 0.16f, 2.4f, 0.16f, 0f, 0.32f, 0.24f, 0.18f, 1f)
                drawCalls += 3
            }

            // Reconstructed village props strictly matching AI analysis counts
            val detailScale = if (camDistSq > 3600f) 0 else 1 // LOD reduction at far distance
            if (detailScale > 0) {
                val bCount = loc.aiBuildingsCount.toInt().coerceIn(0, 6)
                for (b in 0 until bCount) {
                    val side = if (b % 2 == 0) -1f else 1f
                    val bx = lx + side * (7.5f + (b / 2) * 5.2f)
                    val bz = lz + ((b % 3) - 1) * 4.5f
                    drawVillageHouse(bx, bz, loc.category == LocationCategory.SHOP.name)
                    drawCalls += 2
                }
                val tCount = loc.aiTreesCount.toInt().coerceIn(0, 8)
                for (t in 0 until tCount) {
                    val angle = t * (2f * PI.toFloat() / max(1, tCount))
                    val tx = lx + cos(angle) * 11.5f
                    val tz = lz + sin(angle) * 11.5f
                    drawVillageTree(tx, tz)
                    drawCalls += 2
                }
                val pCount = loc.aiPolesCount.toInt().coerceIn(0, 4)
                for (p in 0 until pCount) {
                    val px = lx + (if (p % 2 == 0) -4.2f else 4.2f)
                    val pz = lz + p * 7.0f
                    drawElectricPole(px, pz)
                    drawCalls += 2
                }
            }
        }

        // 5. Monsoon Rain Streaks if active
        if (weatherMode == WeatherMode.MONSOON) {
            val dropCount = when (graphicsQuality) {
                GraphicsQuality.LOW -> 20
                GraphicsQuality.MEDIUM -> 40
                GraphicsQuality.HIGH -> 65
                GraphicsQuality.ULTRA -> 90
            }
            for (i in 0 until dropCount) {
                val rx = playerX + (((i * 37) % 28) - 14f)
                val rz = playerZ + (((i * 53) % 28) - 14f)
                val ry = 10f - ((worldTime * 16f + i * 1.3f) % 10f)
                drawBox(rx, ry, rz, 0.03f, 0.75f, 0.03f, 0f, 0.7f, 0.85f, 1.0f, 0.55f)
                drawCalls++
            }
        }

        // 6. Third-Person Playable Character
        drawCharacter(playerX, playerY, playerZ, playerFacingRad, inputMag)
        drawCalls += 6

        onTelemetryUpdate(
            EngineTelemetry(
                fps = currentFps,
                playerX = playerX,
                playerZ = playerZ,
                cameraYawDeg = ((cameraYawRad * 180f / PI.toFloat()) % 360f + 360f) % 360f,
                nearbyLocationId = nearestLoc?.id,
                nearbyLocationName = nearestLoc?.name,
                activeDrawCalls = drawCalls
            )
        )
    }

    private fun drawSkyDome(camX: Float, camY: Float, camZ: Float) {
        GLES30.glDepthMask(false)
        GLES30.glUseProgram(skyProgram)

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, camX, camY, camZ)
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)

        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(skyProgram, "uVpMatrix"), 1, false, mvpMatrix, 0)

        val (zenith, horizon, sunDir, sunCol) = computeAtmosphere()
        GLES30.glUniform3fv(GLES30.glGetUniformLocation(skyProgram, "uZenithColor"), 1, zenith, 0)
        GLES30.glUniform3fv(GLES30.glGetUniformLocation(skyProgram, "uHorizonColor"), 1, horizon, 0)
        GLES30.glUniform3fv(GLES30.glGetUniformLocation(skyProgram, "uSunDir"), 1, sunDir, 0)
        GLES30.glUniform3fv(GLES30.glGetUniformLocation(skyProgram, "uSunColor"), 1, sunCol, 0)

        skySphere?.draw()
        GLES30.glDepthMask(true)
    }

    private fun bindWorldLighting(camPos: FloatArray) {
        val (_, horizon, sunDir, sunCol) = computeAtmosphere()
        val isNight = weatherMode == WeatherMode.NIGHT || timeOfDay < 0.22f || timeOfDay > 0.84f
        val nightFactor = if (isNight) 1.0f else 0.0f
        val ambient = when (weatherMode) {
            WeatherMode.NIGHT -> floatArrayOf(0.14f, 0.18f, 0.28f)
            WeatherMode.MONSOON -> floatArrayOf(0.38f, 0.43f, 0.48f)
            WeatherMode.GOLDEN_HOUR -> floatArrayOf(0.48f, 0.36f, 0.26f)
            WeatherMode.SUNNY -> floatArrayOf(0.52f, 0.56f, 0.54f)
        }
        val fogDensity = when (weatherMode) {
            WeatherMode.MONSOON -> 0.014f
            WeatherMode.NIGHT -> 0.011f
            WeatherMode.GOLDEN_HOUR -> 0.0065f
            WeatherMode.SUNNY -> 0.0045f
        }

        GLES30.glUniform3fv(GLES30.glGetUniformLocation(worldProgram, "uSunDir"), 1, sunDir, 0)
        GLES30.glUniform3fv(GLES30.glGetUniformLocation(worldProgram, "uSunColor"), 1, sunCol, 0)
        GLES30.glUniform3fv(GLES30.glGetUniformLocation(worldProgram, "uAmbientColor"), 1, ambient, 0)
        GLES30.glUniform3fv(GLES30.glGetUniformLocation(worldProgram, "uFogColor"), 1, horizon, 0)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(worldProgram, "uFogDensity"), fogDensity)
        GLES30.glUniform3fv(GLES30.glGetUniformLocation(worldProgram, "uCameraPos"), 1, camPos, 0)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(worldProgram, "uNightFactor"), nightFactor)
    }

    private fun computeAtmosphere(): Array<FloatArray> {
        val sunAngle = (timeOfDay - 0.25f) * PI.toFloat() * 2f
        val sunY = sin(sunAngle).coerceAtLeast(0.15f)
        val sunX = cos(sunAngle)
        val sunDir = floatArrayOf(sunX, sunY, -0.45f)

        return when (weatherMode) {
            WeatherMode.NIGHT -> arrayOf(
                floatArrayOf(0.03f, 0.05f, 0.12f),
                floatArrayOf(0.08f, 0.11f, 0.20f),
                floatArrayOf(-0.3f, 0.5f, -0.4f),
                floatArrayOf(0.28f, 0.34f, 0.52f)
            )
            WeatherMode.MONSOON -> arrayOf(
                floatArrayOf(0.22f, 0.28f, 0.34f),
                floatArrayOf(0.42f, 0.48f, 0.52f),
                sunDir,
                floatArrayOf(0.48f, 0.52f, 0.55f)
            )
            WeatherMode.GOLDEN_HOUR -> arrayOf(
                floatArrayOf(0.18f, 0.34f, 0.58f),
                floatArrayOf(0.95f, 0.60f, 0.32f),
                sunDir,
                floatArrayOf(1.0f, 0.74f, 0.42f)
            )
            WeatherMode.SUNNY -> arrayOf(
                floatArrayOf(0.14f, 0.48f, 0.86f),
                floatArrayOf(0.66f, 0.82f, 0.94f),
                sunDir,
                floatArrayOf(1.0f, 0.96f, 0.86f)
            )
        }
    }

    private fun drawVillageHouse(x: Float, z: Float, isShop: Boolean) {
        val wallR = if (isShop) 0.82f else 0.74f
        val wallG = if (isShop) 0.65f else 0.44f
        val wallB = if (isShop) 0.42f else 0.30f
        drawBox(x, 1.45f, z, 4.4f, 2.9f, 4.0f, 0f, wallR, wallG, wallB, 1f)
        // Pitched/overhang roof
        drawBox(x, 3.08f, z, 4.9f, 0.45f, 4.5f, 0f, 0.52f, 0.20f, 0.14f, 1f)
    }

    private fun drawVillageTree(x: Float, z: Float) {
        drawBox(x, 1.4f, z, 0.45f, 2.8f, 0.45f, 0f, 0.36f, 0.24f, 0.14f, 1f)
        drawBox(x, 3.3f, z, 2.6f, 2.0f, 2.6f, 0.4f, 0.16f, 0.50f, 0.20f, 1f)
    }

    private fun drawElectricPole(x: Float, z: Float) {
        drawBox(x, 2.6f, z, 0.2f, 5.2f, 0.2f, 0f, 0.45f, 0.45f, 0.48f, 1f)
        val isNight = weatherMode == WeatherMode.NIGHT
        drawBox(
            x + 0.35f, 4.9f, z, 0.7f, 0.16f, 0.22f, 0f,
            if (isNight) 1.0f else 0.65f,
            if (isNight) 0.82f else 0.65f,
            if (isNight) 0.35f else 0.60f,
            1f
        )
    }

    private fun drawCharacter(x: Float, y: Float, z: Float, facingRad: Float, moveAmount: Float) {
        val (tr, tg, tb) = parseHexRgb(characterPreset.torsoHex)
        val (lr, lg, lb) = parseHexRgb(characterPreset.legsHex)
        val (ar, ag, ab) = parseHexRgb(characterAccentHex)

        val swing = sin(walkAnimPhase) * 0.55f * moveAmount

        // Shadow disc
        drawBox(x, 0.02f, z, 0.85f, 0.02f, 0.85f, facingRad, 0.04f, 0.04f, 0.04f, 0.45f)

        // Torso
        drawBox(x, y + 1.15f, z, 0.52f, 0.72f, 0.30f, facingRad, tr, tg, tb, 1f)
        // Head
        drawBox(x, y + 1.72f, z, 0.34f, 0.34f, 0.34f, facingRad, 0.88f, 0.70f, 0.56f, 1f)
        // Accent cap/band
        drawBox(x, y + 1.92f, z, 0.36f, 0.12f, 0.38f, facingRad, ar, ag, ab, 1f)

        val cosF = cos(facingRad)
        val sinF = sin(facingRad)

        // Left & Right Legs
        drawBox(x - cosF * 0.14f + sinF * swing * 0.28f, y + 0.40f, z + sinF * 0.14f + cosF * swing * 0.28f, 0.20f, 0.78f, 0.22f, facingRad, lr, lg, lb, 1f)
        drawBox(x + cosF * 0.14f - sinF * swing * 0.28f, y + 0.40f, z - sinF * 0.14f - cosF * swing * 0.28f, 0.20f, 0.78f, 0.22f, facingRad, lr, lg, lb, 1f)

        // Left & Right Arms
        drawBox(x - cosF * 0.37f - sinF * swing * 0.24f, y + 1.12f, z + sinF * 0.37f - cosF * swing * 0.24f, 0.16f, 0.64f, 0.18f, facingRad, ar, ag, ab, 1f)
        drawBox(x + cosF * 0.37f + sinF * swing * 0.24f, y + 1.12f, z - sinF * 0.37f + cosF * swing * 0.24f, 0.16f, 0.64f, 0.18f, facingRad, ar, ag, ab, 1f)
    }

    private fun drawBox(
        x: Float, y: Float, z: Float,
        sx: Float, sy: Float, sz: Float,
        rotYRad: Float,
        r: Float, g: Float, b: Float, a: Float
    ) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, x, y, z)
        if (rotYRad != 0f) {
            Matrix.rotateM(modelMatrix, 0, rotYRad * 180f / PI.toFloat(), 0f, 1f, 0f)
        }
        Matrix.scaleM(modelMatrix, 0, sx, sy, sz)
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)

        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(worldProgram, "uModelMatrix"), 1, false, modelMatrix, 0)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(worldProgram, "uMvpMatrix"), 1, false, mvpMatrix, 0)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(worldProgram, "uUseTexture"), 0f)

        // Modulate vertex color via uniform? Wait: cube vertices have white (1,1,1,1),
        // or let's override vertex attribute 3 constant!
        GLES30.glDisableVertexAttribArray(3)
        GLES30.glVertexAttrib4f(3, r, g, b, a)
        drawMeshAttributesNoColor(unitCubeMesh)
    }

    private fun drawMeshAttributesNoColor(mesh: Mesh?) {
        mesh ?: return
        val strideBytes = 12 * 4
        mesh.vbo.position(0)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, strideBytes, mesh.vbo)
        mesh.vbo.position(3)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, strideBytes, mesh.vbo)
        mesh.vbo.position(6)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 2, GLES30.GL_FLOAT, false, strideBytes, mesh.vbo)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, mesh.vertexCount)
    }

    private fun drawMeshModel(
        mesh: Mesh?,
        x: Float, y: Float, z: Float,
        sx: Float, sy: Float, sz: Float,
        rotYRad: Float,
        textureId: Int
    ) {
        mesh ?: return
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, x, y, z)
        if (rotYRad != 0f) {
            Matrix.rotateM(modelMatrix, 0, rotYRad * 180f / PI.toFloat(), 0f, 1f, 0f)
        }
        Matrix.scaleM(modelMatrix, 0, sx, sy, sz)
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)

        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(worldProgram, "uModelMatrix"), 1, false, modelMatrix, 0)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(worldProgram, "uMvpMatrix"), 1, false, mvpMatrix, 0)

        if (textureId != 0) {
            GLES30.glUniform1f(GLES30.glGetUniformLocation(worldProgram, "uUseTexture"), 1f)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(worldProgram, "uTexture"), 0)
        } else {
            GLES30.glUniform1f(GLES30.glGetUniformLocation(worldProgram, "uUseTexture"), 0f)
        }
        mesh.draw()
    }

    private fun loadActivePanoramaTexture() {
        val maxDim = graphicsQuality.maxTextureSize.coerceAtMost(2048)
        val bmp = decodeBitmapForGl(activePanoramaPath, maxDim)
            ?: BitmapFactory.decodeResource(context.resources, R.drawable.img_village_panorama_sample_1795586109597)
        if (bmp != null) {
            if (panoTextureId != 0) {
                GLES30.glDeleteTextures(1, intArrayOf(panoTextureId), 0)
            }
            panoTextureId = uploadTexture(bmp)
            bmp.recycle()
        }
    }

    private fun syncLocationTextures() {
        val maxDim = min(512, graphicsQuality.maxTextureSize)
        locations.forEach { loc ->
            if (!locationTextures.containsKey(loc.id)) {
                val bmp = decodeBitmapForGl(loc.localImagePath, maxDim)
                    ?: BitmapFactory.decodeResource(context.resources, R.drawable.img_village_panorama_sample_1795586109597)
                if (bmp != null) {
                    locationTextures[loc.id] = uploadTexture(bmp)
                    bmp.recycle()
                }
            }
        }
    }

    private fun decodeBitmapForGl(path: String?, maxDim: Int): Bitmap? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.exists()) return null
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, opts)
            var sample = 1
            while (opts.outWidth / sample > maxDim || opts.outHeight / sample > maxDim) {
                sample *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(path, decodeOpts)
        } catch (_: Exception) {
            null
        }
    }

    private fun uploadTexture(bitmap: Bitmap): Int {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        return ids[0]
    }

    private fun parseHexRgb(hex: String): Triple<Float, Float, Float> {
        return try {
            val c = android.graphics.Color.parseColor(hex)
            Triple(
                android.graphics.Color.red(c) / 255f,
                android.graphics.Color.green(c) / 255f,
                android.graphics.Color.blue(c) / 255f
            )
        } catch (_: Exception) {
            Triple(0.85f, 0.55f, 0.15f)
        }
    }

    private fun createGroundMesh(): Mesh {
        val s = 180f
        val verts = floatArrayOf(
            -s, 0f, -s, 0f, 1f, 0f, 0f, 0f, 0.24f, 0.42f, 0.20f, 1f,
             s, 0f, -s, 0f, 1f, 0f, 1f, 0f, 0.26f, 0.44f, 0.21f, 1f,
             s, 0f,  s, 0f, 1f, 0f, 1f, 1f, 0.22f, 0.39f, 0.18f, 1f,
            -s, 0f, -s, 0f, 1f, 0f, 0f, 0f, 0.24f, 0.42f, 0.20f, 1f,
             s, 0f,  s, 0f, 1f, 0f, 1f, 1f, 0.22f, 0.39f, 0.18f, 1f,
            -s, 0f,  s, 0f, 1f, 0f, 0f, 1f, 0.25f, 0.41f, 0.19f, 1f
        )
        return Mesh(verts)
    }

    private fun createRoadMesh(): Mesh {
        val w = 2.6f
        val l = 160f
        val verts = floatArrayOf(
            -w, 0f, -l, 0f, 1f, 0f, 0f, 0f, 0.54f, 0.43f, 0.32f, 1f,
             w, 0f, -l, 0f, 1f, 0f, 1f, 0f, 0.54f, 0.43f, 0.32f, 1f,
             w, 0f,  l, 0f, 1f, 0f, 1f, 1f, 0.50f, 0.39f, 0.29f, 1f,
            -w, 0f, -l, 0f, 1f, 0f, 0f, 0f, 0.54f, 0.43f, 0.32f, 1f,
             w, 0f,  l, 0f, 1f, 0f, 1f, 1f, 0.50f, 0.39f, 0.29f, 1f,
            -w, 0f,  l, 0f, 1f, 0f, 0f, 1f, 0.50f, 0.39f, 0.29f, 1f
        )
        return Mesh(verts)
    }

    private fun createQuadMesh(): Mesh {
        val verts = floatArrayOf(
            -0.5f, -0.5f, 0f, 0f, 0f, 1f, 0f, 1f, 1f, 1f, 1f, 1f,
             0.5f, -0.5f, 0f, 0f, 0f, 1f, 1f, 1f, 1f, 1f, 1f, 1f,
             0.5f,  0.5f, 0f, 0f, 0f, 1f, 1f, 0f, 1f, 1f, 1f, 1f,
            -0.5f, -0.5f, 0f, 0f, 0f, 1f, 0f, 1f, 1f, 1f, 1f, 1f,
             0.5f,  0.5f, 0f, 0f, 0f, 1f, 1f, 0f, 1f, 1f, 1f, 1f,
            -0.5f,  0.5f, 0f, 0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f, 1f
        )
        return Mesh(verts)
    }

    private fun createCubeMesh(): Mesh {
        val p = 0.5f
        val faces = arrayOf(
            // Front
            floatArrayOf(-p,-p, p, 0f,0f,1f, 0f,1f,  p,-p, p, 0f,0f,1f, 1f,1f,  p, p, p, 0f,0f,1f, 1f,0f,
                       -p,-p, p, 0f,0f,1f, 0f,1f,  p, p, p, 0f,0f,1f, 1f,0f, -p, p, p, 0f,0f,1f, 0f,0f),
            // Back
            floatArrayOf( p,-p,-p, 0f,0f,-1f, 0f,1f, -p,-p,-p, 0f,0f,-1f, 1f,1f, -p, p,-p, 0f,0f,-1f, 1f,0f,
                        p,-p,-p, 0f,0f,-1f, 0f,1f, -p, p,-p, 0f,0f,-1f, 1f,0f,  p, p,-p, 0f,0f,-1f, 0f,0f),
            // Left
            floatArrayOf(-p,-p,-p, -1f,0f,0f, 0f,1f, -p,-p, p, -1f,0f,0f, 1f,1f, -p, p, p, -1f,0f,0f, 1f,0f,
                       -p,-p,-p, -1f,0f,0f, 0f,1f, -p, p, p, -1f,0f,0f, 1f,0f, -p, p,-p, -1f,0f,0f, 0f,0f),
            // Right
            floatArrayOf( p,-p, p, 1f,0f,0f, 0f,1f,  p,-p,-p, 1f,0f,0f, 1f,1f,  p, p,-p, 1f,0f,0f, 1f,0f,
                        p,-p, p, 1f,0f,0f, 0f,1f,  p, p,-p, 1f,0f,0f, 1f,0f,  p, p, p, 1f,0f,0f, 0f,0f),
            // Top
            floatArrayOf(-p, p, p, 0f,1f,0f, 0f,1f,  p, p, p, 0f,1f,0f, 1f,1f,  p, p,-p, 0f,1f,0f, 1f,0f,
                       -p, p, p, 0f,1f,0f, 0f,1f,  p, p,-p, 0f,1f,0f, 1f,0f, -p, p,-p, 0f,1f,0f, 0f,0f),
            // Bottom
            floatArrayOf(-p,-p,-p, 0f,-1f,0f, 0f,1f,  p,-p,-p, 0f,-1f,0f, 1f,1f,  p,-p, p, 0f,-1f,0f, 1f,0f,
                       -p,-p,-p, 0f,-1f,0f, 0f,1f,  p,-p, p, 0f,-1f,0f, 1f,0f, -p,-p, p, 0f,-1f,0f, 0f,0f)
        )
        val out = FloatArray(36 * 12)
        var idx = 0
        faces.forEach { f ->
            for (v in 0 until 6) {
                val base = v * 8
                for (k in 0 until 8) out[idx++] = f[base + k]
                out[idx++] = 1f; out[idx++] = 1f; out[idx++] = 1f; out[idx++] = 1f
            }
        }
        return Mesh(out)
    }
}

class VillageGLSurfaceView(
    context: Context,
    onTelemetryUpdate: (EngineTelemetry) -> Unit
) : GLSurfaceView(context) {

    val renderer: VillageGLRenderer

    init {
        setEGLContextClientVersion(3)
        renderer = VillageGLRenderer(context.applicationContext, onTelemetryUpdate)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }
}
