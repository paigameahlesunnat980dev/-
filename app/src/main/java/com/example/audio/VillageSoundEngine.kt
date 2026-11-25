package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.model.WeatherMode
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Lightweight procedural village soundscape synthesizer using Android AudioTrack.
 * Generates gentle wind, birds/crickets depending on day/night, rain during monsoon,
 * and subtle footsteps when moving.
 */
class VillageSoundEngine {
    @Volatile
    private var running = false
    @Volatile
    private var enabled = false
    @Volatile
    private var currentWeather = WeatherMode.GOLDEN_HOUR
    @Volatile
    private var isMoving = false
    @Volatile
    private var isSprinting = false

    private var audioThread: Thread? = null
    private var track: AudioTrack? = null

    fun setEnabled(enable: Boolean) {
        enabled = enable
        if (enable && !running) {
            start()
        } else if (!enable && running) {
            stop()
        }
    }

    fun updateState(weather: WeatherMode, moving: Boolean, sprinting: Boolean) {
        currentWeather = weather
        isMoving = moving
        isSprinting = sprinting
    }

    private fun start() {
        if (running) return
        running = true
        audioThread = thread(start = true, isDaemon = true, name = "MurgiTolaAudio") {
            val sampleRate = 22050
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(2048)

            val audioTrack = try {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } catch (_: Exception) {
                running = false
                return@thread
            }

            track = audioTrack
            try {
                audioTrack.play()
                val buffer = ShortArray(1024)
                var sampleIndex = 0L
                var lpNoise = 0f
                var stepPhase = 0f

                while (running && enabled) {
                    val weather = currentWeather
                    val moving = isMoving
                    val sprint = isSprinting
                    for (i in buffer.indices) {
                        val t = sampleIndex / sampleRate.toFloat()
                        val white = Random.nextFloat() * 2f - 1f
                        lpNoise = lpNoise * 0.96f + white * 0.04f

                        // Gentle village breeze
                        var sample = lpNoise * (0.28f + 0.12f * sin(t * 0.6f).toFloat())

                        // Daytime bird chirp or night cricket pulse
                        if (weather == WeatherMode.NIGHT) {
                            val chirpGate = if ((sampleIndex / 1400) % 7 == 0L) 1f else 0f
                            sample += sin(2.0 * PI * 3800.0 * t).toFloat() * 0.035f * chirpGate
                        } else if (weather == WeatherMode.MONSOON) {
                            // Soft rain patter
                            sample += white * 0.08f
                        } else {
                            // Subtle morning/evening bird harmonic
                            val birdEnv = sin(t * 2.2f).toFloat().coerceAtLeast(0f)
                            if (((sampleIndex / 18000) % 11) == 0L) {
                                val freq = 2100f + 450f * sin(t * 18f).toFloat()
                                sample += sin(2.0 * PI * freq * t).toFloat() * 0.03f * birdEnv
                            }
                        }

                        // Soft rhythmic footstep thump when walking/sprinting
                        if (moving) {
                            val stepRate = if (sprint) 3.6f else 2.1f
                            stepPhase += stepRate / sampleRate
                            if (stepPhase >= 1f) stepPhase -= 1f
                            if (stepPhase < 0.08f) {
                                val env = 1f - (stepPhase / 0.08f)
                                sample += (lpNoise * 1.8f + sin(2.0 * PI * 95.0 * t).toFloat() * 0.4f) * env * 0.25f
                            }
                        }

                        val clamped = (sample * 11000f).toInt().coerceIn(-32767, 32767)
                        buffer[i] = clamped.toShort()
                        sampleIndex++
                    }
                    audioTrack.write(buffer, 0, buffer.size)
                }
            } catch (_: Exception) {
            } finally {
                try {
                    audioTrack.pause()
                    audioTrack.flush()
                    audioTrack.release()
                } catch (_: Exception) {
                }
                track = null
            }
        }
    }

    fun stop() {
        running = false
        try {
            audioThread?.join(200)
        } catch (_: Exception) {
        }
        audioThread = null
    }
}
