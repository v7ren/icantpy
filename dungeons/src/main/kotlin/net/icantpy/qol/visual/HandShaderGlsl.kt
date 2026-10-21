package net.icantpy.qol.visual

import java.util.Locale
import java.util.Objects

/**
 * Builds the GLSL `main()` body for the first-person hand shader. Pure text generation: no Minecraft types.
 *
 * The version adapter supplies the header: `#version`, the `MainSampler` / `DepthSampler` uniforms,
 * `in vec2 texCoord`, `out vec4 fragColor`, `float icantpyTime()` (seconds, already scaled by the speed
 * setting on the CPU side) and `bool icantpyIsHand(vec2 uv)`. The generated text declares its own constants
 * and helpers (all prefixed `hs_`) and then `void main()`.
 */
object HandShaderGlsl {
    private const val DEFAULT_SPEED = 1f
    private const val DEFAULT_OPACITY = 0.85f

    /** GLSL source for the shader body. Colors, speed and opacity are baked in as constants. */
    fun body(mode: HandShaderMode, color1: Int, color2: Int, speed: Float, opacity: Float): String {
        val prelude = prelude(color1, color2, speed, opacity)
        val main = when (mode) {
            HandShaderMode.SOLID -> SOLID
            HandShaderMode.CHROMA -> CHROMA
            HandShaderMode.STRIPES -> STRIPES
            HandShaderMode.GLOW -> GLOW
            HandShaderMode.SNOW -> SNOW
            HandShaderMode.SMOKE -> SMOKE
            HandShaderMode.BALATRO -> BALATRO
            HandShaderMode.GLASS -> GLASS
        }
        return prelude + "\n" + main.trimIndent() + "\n"
    }

    /** Stable hash of every input that changes the generated body. Equal inputs always give equal keys. */
    fun key(mode: HandShaderMode, color1: Int, color2: Int, speed: Float, opacity: Float): Int =
        Objects.hash(mode.id, color1 and RGB_MASK, color2 and RGB_MASK, sanitizeSpeed(speed).toBits(), sanitizeOpacity(opacity).toBits())

    private const val RGB_MASK = 0xFFFFFF

    private fun prelude(color1: Int, color2: Int, speed: Float, opacity: Float): String = buildString {
        // Speed is applied to icantpyTime() on the CPU side, so SPEED is informational only and is not used
        // in the shader; using it again here would scale the animation twice.
        appendLine("const vec3 C1 = ${rgb(color1)};")
        appendLine("const vec3 C2 = ${rgb(color2)};")
        appendLine("const float SPEED = ${fmt(sanitizeSpeed(speed))};")
        appendLine("const float OPACITY = ${fmt(sanitizeOpacity(opacity))};")
        append(HELPERS)
    }

    private val HELPERS = """
        float hs_hash(vec2 p) {
            return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
        }
        float hs_noise(vec2 p) {
            vec2 i = floor(p);
            vec2 f = fract(p);
            vec2 u = f * f * (3.0 - 2.0 * f);
            float a = hs_hash(i);
            float b = hs_hash(i + vec2(1.0, 0.0));
            float c = hs_hash(i + vec2(0.0, 1.0));
            float d = hs_hash(i + vec2(1.0, 1.0));
            return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
        }
        float hs_fbm(vec2 p) {
            float v = 0.0;
            float amp = 0.5;
            for (int i = 0; i < 4; i++) {
                v += amp * hs_noise(p);
                p *= 2.0;
                amp *= 0.5;
            }
            return v;
        }
        vec3 hs_hsv(float h) {
            vec3 k = vec3(1.0, 2.0 / 3.0, 1.0 / 3.0);
            vec3 p = abs(fract(vec3(h) + k) * 6.0 - vec3(3.0));
            return clamp(p - vec3(1.0), 0.0, 1.0);
        }

    """.trimIndent() + "\n"

    private val SOLID = """
        void main() {
            vec4 base = texture(MainSampler, texCoord);
            if (!icantpyIsHand(texCoord)) {
                fragColor = base;
                return;
            }
            float lum = dot(base.rgb, vec3(0.299, 0.587, 0.114));
            vec3 effect = C1 * (0.55 + 0.45 * lum);
            fragColor = vec4(mix(base.rgb, effect, OPACITY), base.a);
        }
    """

    private val CHROMA = """
        void main() {
            vec4 base = texture(MainSampler, texCoord);
            if (!icantpyIsHand(texCoord)) {
                fragColor = base;
                return;
            }
            float lum = dot(base.rgb, vec3(0.299, 0.587, 0.114));
            float hue = fract(texCoord.x * 0.5 + icantpyTime() * 0.1);
            vec3 effect = hs_hsv(hue) * (0.6 + 0.4 * lum);
            fragColor = vec4(mix(base.rgb, effect, OPACITY), base.a);
        }
    """

    private val STRIPES = """
        void main() {
            vec4 base = texture(MainSampler, texCoord);
            if (!icantpyIsHand(texCoord)) {
                fragColor = base;
                return;
            }
            float lum = dot(base.rgb, vec3(0.299, 0.587, 0.114));
            float s = (texCoord.x + texCoord.y) * 18.0 - icantpyTime() * 2.0;
            float band = smoothstep(0.45, 0.55, fract(s));
            vec3 effect = mix(C1, C2, band) * (0.6 + 0.4 * lum);
            fragColor = vec4(mix(base.rgb, effect, OPACITY), base.a);
        }
    """

    private val GLOW = """
        void main() {
            vec4 base = texture(MainSampler, texCoord);
            vec2 px = 1.0 / vec2(textureSize(MainSampler, 0));
            float t = icantpyTime();
            if (!icantpyIsHand(texCoord)) {
                // Outer glow: march outward from non-hand pixels and fade with distance to the nearest hand texel.
                float halo = 0.0;
                for (int i = 0; i < 8; i++) {
                    float a = float(i) * 0.785398;
                    vec2 dir = vec2(cos(a), sin(a));
                    for (int r = 1; r <= 4; r++) {
                        float fr = float(r);
                        if (icantpyIsHand(texCoord + dir * px * fr * 2.0)) {
                            halo = max(halo, 1.0 - (fr - 1.0) / 4.0);
                            break;
                        }
                    }
                }
                if (halo <= 0.0) {
                    fragColor = base;
                    return;
                }
                float pulse = 0.85 + 0.15 * sin(t * 2.0);
                vec3 glow = C1 * halo * pulse * OPACITY;
                fragColor = vec4(base.rgb + glow, base.a);
                return;
            }
            float lum = dot(base.rgb, vec3(0.299, 0.587, 0.114));
            float pulse = 0.85 + 0.15 * sin(t * 2.0);
            vec3 effect = C1 * (0.5 + 0.5 * lum) * pulse + C1 * 0.2;
            fragColor = vec4(mix(base.rgb, effect, OPACITY), base.a);
        }
    """

    private val SNOW = """
        void main() {
            vec4 base = texture(MainSampler, texCoord);
            if (!icantpyIsHand(texCoord)) {
                fragColor = base;
                return;
            }
            float lum = dot(base.rgb, vec3(0.299, 0.587, 0.114));
            float t = icantpyTime();
            float sparkle = 0.0;
            for (int i = 0; i < 3; i++) {
                float layer = float(i);
                vec2 p = texCoord * (24.0 + 16.0 * layer);
                p.y += t * (1.5 + layer);
                p.x += sin(t * 0.5 + layer) * 0.3;
                vec2 id = floor(p);
                vec2 f = fract(p) - 0.5;
                float h = hs_hash(id + layer * 17.0);
                vec2 center = (vec2(h, fract(h * 7.0)) - 0.5) * 0.6;
                sparkle += smoothstep(0.12, 0.0, length(f - center)) * step(0.5, h);
            }
            vec3 effect = C1 * (0.7 + 0.3 * lum) + vec3(sparkle);
            fragColor = vec4(mix(base.rgb, effect, OPACITY), base.a);
        }
    """

    private val SMOKE = """
        void main() {
            vec4 base = texture(MainSampler, texCoord);
            if (!icantpyIsHand(texCoord)) {
                fragColor = base;
                return;
            }
            float lum = dot(base.rgb, vec3(0.299, 0.587, 0.114));
            float t = icantpyTime();
            vec2 q = texCoord * 6.0;
            q.y -= t * 0.4;
            float warp = hs_fbm(q + vec2(t * 0.2, 0.0));
            float n = hs_fbm(q + vec2(warp, warp * 0.5));
            vec3 effect = mix(C1, C2, clamp(n, 0.0, 1.0)) * (0.6 + 0.4 * lum);
            fragColor = vec4(mix(base.rgb, effect, OPACITY), base.a);
        }
    """

    private val BALATRO = """
        void main() {
            vec4 base = texture(MainSampler, texCoord);
            if (!icantpyIsHand(texCoord)) {
                fragColor = base;
                return;
            }
            float lum = dot(base.rgb, vec3(0.299, 0.587, 0.114));
            float t = icantpyTime();
            vec2 p = texCoord * 2.0 - 1.0;
            float r = length(p);
            float a = atan(p.y, p.x);
            float w = sin(a * 3.0 + t * 0.8 + r * 6.0) + cos(r * 10.0 - t * 1.2);
            float band = 0.5 + 0.5 * sin(w * 2.0);
            vec3 effect = mix(C1, C2, band) * (0.6 + 0.4 * lum);
            fragColor = vec4(mix(base.rgb, effect, OPACITY), base.a);
        }
    """

    private val GLASS = """
        void main() {
            vec4 base = texture(MainSampler, texCoord);
            if (!icantpyIsHand(texCoord)) {
                fragColor = base;
                return;
            }
            vec2 texel = 1.0 / vec2(textureSize(MainSampler, 0));
            float t = icantpyTime();
            vec2 np = texCoord * 40.0 + vec2(0.0, t * 0.5);
            float nx = hs_noise(np) - hs_noise(np + vec2(5.2, 1.3));
            float ny = hs_noise(np + vec2(1.7, 9.2)) - hs_noise(np + vec2(8.3, 2.8));
            vec2 off = vec2(nx, ny) * texel * 6.0;
            vec3 refracted = texture(MainSampler, clamp(texCoord + off, vec2(0.0), vec2(1.0))).rgb;
            float edge = 0.0;
            if (!icantpyIsHand(texCoord + vec2(texel.x * 2.0, 0.0))) edge = 1.0;
            if (!icantpyIsHand(texCoord - vec2(texel.x * 2.0, 0.0))) edge = 1.0;
            if (!icantpyIsHand(texCoord + vec2(0.0, texel.y * 2.0))) edge = 1.0;
            if (!icantpyIsHand(texCoord - vec2(0.0, texel.y * 2.0))) edge = 1.0;
            vec3 effect = refracted * 1.25 + vec3(0.05);
            effect = mix(effect, C1, edge * 0.8);
            fragColor = vec4(mix(base.rgb, effect, OPACITY), base.a);
        }
    """

    private fun rgb(color: Int): String {
        val r = ((color shr 16) and 0xFF) / 255f
        val g = ((color shr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        return "vec3(${fmt(r)}, ${fmt(g)}, ${fmt(b)})"
    }

    /** Always three decimals with a '.', regardless of the default locale. */
    private fun fmt(value: Float): String = String.format(Locale.ROOT, "%.3f", value)

    private fun sanitizeSpeed(speed: Float): Float =
        if (speed.isFinite()) speed.coerceIn(0.1f, 4f) else DEFAULT_SPEED

    private fun sanitizeOpacity(opacity: Float): Float =
        if (opacity.isFinite()) opacity.coerceIn(0f, 1f) else DEFAULT_OPACITY
}
