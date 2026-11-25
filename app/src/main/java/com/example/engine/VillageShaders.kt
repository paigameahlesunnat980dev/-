package com.example.engine

import android.opengl.GLES30
import android.util.Log

private const val TAG = "VillageShaders"

object VillageShaders {

    const val WORLD_VERTEX_SHADER = """#version 300 es
        layout(location = 0) in vec3 aPosition;
        layout(location = 1) in vec3 aNormal;
        layout(location = 2) in vec2 aTexCoord;
        layout(location = 3) in vec4 aColor;

        uniform mat4 uMvpMatrix;
        uniform mat4 uModelMatrix;

        out vec3 vWorldPos;
        out vec3 vNormal;
        out vec2 vTexCoord;
        out vec4 vColor;

        void main() {
            vec4 worldPos = uModelMatrix * vec4(aPosition, 1.0);
            vWorldPos = worldPos.xyz;
            vNormal = mat3(uModelMatrix) * aNormal;
            vTexCoord = aTexCoord;
            vColor = aColor;
            gl_Position = uMvpMatrix * vec4(aPosition, 1.0);
        }
    """

    const val WORLD_FRAGMENT_SHADER = """#version 300 es
        precision mediump float;

        in vec3 vWorldPos;
        in vec3 vNormal;
        in vec2 vTexCoord;
        in vec4 vColor;

        uniform vec3 uSunDir;
        uniform vec3 uSunColor;
        uniform vec3 uAmbientColor;
        uniform vec3 uFogColor;
        uniform float uFogDensity;
        uniform vec3 uCameraPos;
        uniform float uUseTexture;
        uniform sampler2D uTexture;
        uniform float uNightFactor;

        out vec4 fragColor;

        void main() {
            vec3 n = normalize(vNormal);
            float ndl = max(dot(n, normalize(uSunDir)), 0.0);

            vec4 baseColor = vColor;
            if (uUseTexture > 0.5) {
                vec4 texCol = texture(uTexture, vTexCoord);
                baseColor = vec4(texCol.rgb * vColor.rgb, texCol.a * vColor.a);
            }

            // Subtle warm village street-lamp pool glow along the road at night
            float lampWave = 0.5 + 0.5 * cos(vWorldPos.z * 0.35);
            float roadDist = abs(vWorldPos.x);
            float lampGlow = uNightFactor * smoothstep(14.0, 0.0, roadDist) * lampWave * 0.55;

            vec3 lit = baseColor.rgb * (uAmbientColor + uSunColor * ndl + vec3(1.0, 0.72, 0.32) * lampGlow);

            float dist = length(uCameraPos - vWorldPos);
            float fogFactor = clamp(exp(-pow(dist * uFogDensity, 1.4)), 0.0, 1.0);
            vec3 finalRgb = mix(uFogColor, lit, fogFactor);

            fragColor = vec4(finalRgb, baseColor.a);
        }
    """

    const val SKY_VERTEX_SHADER = """#version 300 es
        layout(location = 0) in vec3 aPosition;
        uniform mat4 uVpMatrix;
        out vec3 vDir;
        void main() {
            vDir = aPosition;
            gl_Position = uVpMatrix * vec4(aPosition, 1.0);
        }
    """

    const val SKY_FRAGMENT_SHADER = """#version 300 es
        precision mediump float;
        in vec3 vDir;
        uniform vec3 uZenithColor;
        uniform vec3 uHorizonColor;
        uniform vec3 uSunDir;
        uniform vec3 uSunColor;
        out vec4 fragColor;
        void main() {
            vec3 d = normalize(vDir);
            float h = clamp(d.y * 0.5 + 0.5, 0.0, 1.0);
            float t = smoothstep(0.45, 0.95, h);
            vec3 sky = mix(uHorizonColor, uZenithColor, t);
            float sunDot = max(dot(d, normalize(uSunDir)), 0.0);
            float sunDisc = smoothstep(0.996, 0.9992, sunDot);
            float sunHalo = pow(sunDot, 14.0) * 0.45;
            sky += uSunColor * (sunDisc * 1.4 + sunHalo);
            fragColor = vec4(sky, 1.0);
        }
    """

    const val PANO_VERTEX_SHADER = """#version 300 es
        layout(location = 0) in vec3 aPosition;
        layout(location = 1) in vec2 aTexCoord;
        uniform mat4 uMvpMatrix;
        out vec2 vTexCoord;
        void main() {
            vTexCoord = aTexCoord;
            gl_Position = uMvpMatrix * vec4(aPosition, 1.0);
        }
    """

    const val PANO_FRAGMENT_SHADER = """#version 300 es
        precision mediump float;
        in vec2 vTexCoord;
        uniform sampler2D uTexture;
        uniform float uExposure;
        out vec4 fragColor;
        void main() {
            vec4 col = texture(uTexture, vTexCoord);
            fragColor = vec4(col.rgb * uExposure, 1.0);
        }
    """

    fun buildProgram(vertexSource: String, fragmentSource: String): Int {
        val vs = compileShader(GLES30.GL_VERTEX_SHADER, vertexSource)
        val fs = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vs)
        GLES30.glAttachShader(program, fs)
        GLES30.glLinkProgram(program)
        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            Log.e(TAG, "Program link error: ${GLES30.glGetProgramInfoLog(program)}")
            GLES30.glDeleteProgram(program)
            return 0
        }
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
        return program
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            Log.e(TAG, "Shader compile error: ${GLES30.glGetShaderInfoLog(shader)}")
            GLES30.glDeleteShader(shader)
            return 0
        }
        return shader
    }
}
