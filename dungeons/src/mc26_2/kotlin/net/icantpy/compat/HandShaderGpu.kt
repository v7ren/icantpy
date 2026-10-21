package net.icantpy.compat

import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.pipeline.BindGroupLayout
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.shaders.ShaderSource
import com.mojang.blaze3d.shaders.ShaderType
import com.mojang.blaze3d.shaders.UniformType
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.resources.Identifier
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Optional

/**
 * Full-screen GPU pass that rewrites the main color target after the first-person hand is drawn.
 * Minecraft 26.2 adapter; the 26.1.2 twin in payload-2612 exposes the identical API.
 *
 * The hand is already flushed to the main target when [draw] runs. Depth is cleared to 0.0 (reversed-Z)
 * right before the hand, so any pixel whose depth differs from that is hand geometry.
 */
@Suppress("UNUSED_PARAMETER")
object HandShaderGpu {
    /** Shader pair vanilla uses for full-screen passes; generates the triangle from gl_VertexID, no vertex buffer. */
    private const val SCREEN_QUAD_VSH = """#version 330

out vec2 texCoord;

void main() {
    vec2 uv = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
    vec4 pos = vec4(uv * vec2(2, 2) + vec2(-1, -1), 0, 1);

    gl_Position = pos;
    texCoord = uv;
}
"""

    /**
     * `icantpyIsHand` compares against 0.0, the value vanilla clears the main depth to before the hand (reversed-Z).
     * The 26.1.2 twin compares against 1.0 instead.
     */
    private const val HEADER = """#version 330
uniform sampler2D MainSampler;
uniform sampler2D DepthSampler;
layout(std140) uniform IcantpyHand { vec4 TimeData; };
in vec2 texCoord;
out vec4 fragColor;
float icantpyTime() { return TimeData.x; }
bool icantpyIsHand(vec2 uv) { return abs(texture(DepthSampler, uv).r - 0.0) > 1e-6; }
"""

    private val vertexId = Identifier.fromNamespaceAndPath("minecraft", "core/screenquad")

    /** Fragment source per body path, so each key compiles its own shader (the GL shader cache is keyed by id). */
    private val bodies = HashMap<String, String>()
    private val shaderSource = ShaderSource { id, type ->
        if (id == vertexId && type == ShaderType.VERTEX) return@ShaderSource SCREEN_QUAD_VSH
        if (id.namespace != NAMESPACE || type != ShaderType.FRAGMENT) {
            error("icantpy hand shader: unexpected shader $id")
        }
        bodies[id.path] ?: error("icantpy hand shader body missing for ${id.path}")
    }

    private var pipeline: RenderPipeline? = null
    private var pipelineKey = 0
    private var pipelineFormat: GpuFormat? = null
    private var pipelinePath = ""

    private var copyTexture: GpuTexture? = null
    private var copyView: GpuTextureView? = null
    private var copyFormat: GpuFormat? = null
    private var copyWidth = 0
    private var copyHeight = 0

    private var uniformBuffer: GpuBuffer? = null
    private val uniformData: ByteBuffer = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder())

    private const val NAMESPACE = "icantpy"

    /** GLSL placed before a body: `#version`, samplers, the IcantpyHand block, `texCoord`/`fragColor`, and helpers. */
    fun header(): String = HEADER

    /**
     * Draws one full-screen pass over the main target using `header() + body`. The pipeline is rebuilt only when
     * [key] or the target format changes; [body] must be the same text for the same [key].
     */
    fun draw(
        minecraft: Minecraft,
        gameRenderer: GameRenderer,
        key: Int,
        body: String,
        timeSeconds: Float,
    ) {
        val device = RenderSystem.getDevice()
        val main = gameRenderer.mainRenderTarget()
        val colorTexture = main.colorTexture ?: error("main color target is not available")
        val colorView = main.colorTextureView ?: error("main color view is not available")
        val depthView = main.depthTextureView ?: error("main depth view is not available")
        val width = colorTexture.getWidth(0)
        val height = colorTexture.getHeight(0)
        val format = colorTexture.format

        val pipeline = ensurePipeline(key, format, body)
        val copy = ensureCopy(format, width, height)
        val sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)

        // Vanilla blits the main target into the copy so the shader can read the frame while writing to it.
        device.createCommandEncoder()
            .copyTextureToTexture(colorTexture, copy, 0, 0, 0, 0, 0, width, height)

        val ubo = uniformBuffer ?: device.createBuffer(
            { "icantpy hand shader uniforms" }, GpuBuffer.USAGE_UNIFORM or GpuBuffer.USAGE_COPY_DST, 16L,
        ).also { uniformBuffer = it }
        uniformData.clear()
        uniformData.putFloat(timeSeconds).putFloat(0f).putFloat(0f).putFloat(0f)
        uniformData.flip()
        val encoder = device.createCommandEncoder()
        encoder.writeToBuffer(ubo.slice(), uniformData)

        // Re-register on every draw: a resource reload clears the pipeline cache, and the cached
        // entry is what setPipeline finds. Returns the existing compiled program when still cached.
        val compiled = device.precompilePipeline(pipeline, shaderSource)
        check(compiled.isValid) { "icantpy hand shader failed to compile" }

        val pass = encoder.createRenderPass({ "icantpy hand shader" }, colorView, Optional.empty())
        try {
            pass.setPipeline(pipeline)
            pass.bindTexture("MainSampler", copyView ?: error("copy view missing"), sampler)
            pass.bindTexture("DepthSampler", depthView, sampler)
            pass.setUniform("IcantpyHand", ubo.slice())
            pass.draw(3, 1, 0, 0)
        } finally {
            pass.close()
        }
    }

    /** Releases the pipeline references, copy texture and uniform buffer. Safe to call repeatedly. */
    fun close() {
        copyView?.close()
        copyTexture?.close()
        uniformBuffer?.close()
        copyView = null
        copyTexture = null
        copyFormat = null
        copyWidth = 0
        copyHeight = 0
        uniformBuffer = null
        pipeline = null
        pipelineFormat = null
        pipelinePath = ""
        bodies.clear()
    }

    private fun ensurePipeline(key: Int, format: GpuFormat, body: String): RenderPipeline {
        val path = "core/hand_pass_" + Integer.toHexString(key)
        val existing = pipeline
        if (existing != null && pipelineKey == key && pipelineFormat == format && pipelinePath == path) {
            return existing
        }
        bodies[path] = HEADER + body
        val layout = BindGroupLayout.builder()
            .withSampler("MainSampler")
            .withSampler("DepthSampler")
            .withUniform("IcantpyHand", UniformType.UNIFORM_BUFFER)
            .build()
        val built = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, path))
            .withVertexShader(vertexId)
            .withFragmentShader(Identifier.fromNamespaceAndPath(NAMESPACE, path))
            .withBindGroupLayout(layout)
            // No blend: the shader writes the final color for every pixel.
            .withColorTargetState(ColorTargetState(Optional.empty(), format, ColorTargetState.WRITE_ALL))
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .build()
        pipeline = built
        pipelineKey = key
        pipelineFormat = format
        pipelinePath = path
        return built
    }

    /** Returns the copy texture sized for the target, recreating (and closing the old one) on size or format change. */
    private fun ensureCopy(format: GpuFormat, width: Int, height: Int): GpuTexture {
        val existing = copyTexture
        if (existing != null && copyFormat == format && copyWidth == width && copyHeight == height) return existing
        copyView?.close()
        copyTexture?.close()
        val device = RenderSystem.getDevice()
        val created = device.createTexture(
            "icantpy hand shader source",
            GpuTexture.USAGE_COPY_DST or GpuTexture.USAGE_TEXTURE_BINDING,
            format,
            width,
            height,
            1,
            1,
        )
        copyTexture = created
        copyView = device.createTextureView(created)
        copyFormat = format
        copyWidth = width
        copyHeight = height
        return created
    }
}
