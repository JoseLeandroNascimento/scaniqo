package com.joseleandro.scaniqo.data.pdf

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES30
import android.util.Log
import com.joseleandro.scaniqo.domain.model.PdfImageFilter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Convoluções locais em GPU, sem dependência nativa extra nem requisito GLES no manifesto.
 * EGL pertence somente à chamada: não há thread/contexto retido após fechar o documento.
 * O resultado só substitui os pixels após renderizar, ler e validar todas as passagens.
 */
internal class GlesPdfFilterAcceleration : PdfFilterAcceleration {
    private var unavailable = false

    override suspend fun tryApply(
        pixels: IntArray, filter: PdfImageFilter, width: Int, height: Int, intensity: Float,
    ): Boolean {
        val context = currentCoroutineContext()
        context.ensureActive()
        if (unavailable || !isEligible(filter, width, height)) return false
        return try {
            render(pixels, filter, width, height, intensity, context)
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: RuntimeException) {
            // Drivers sem GLES 3, perda de contexto e limites da GPU mantêm a via CPU íntegra.
            unavailable = true
            Log.w("PdfProcessing", "GPU indisponível; continuando o processamento na CPU.", failure)
            false
        } catch (_: OutOfMemoryError) {
            // A alocação temporária não tocou a imagem original. A CPU usa somente três linhas.
            unavailable = true
            false
        }
    }

    private fun render(
        pixels: IntArray, filter: PdfImageFilter, width: Int, height: Int,
        intensity: Float, coroutineContext: CoroutineContext,
    ) {
        require(pixels.size.toLong() == width.toLong() * height)
        require(intensity.isFinite() && intensity in 0f..1f)
        val egl = EglSession()
        try {
            egl.open()
            val maximum = IntArray(1)
            GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, maximum, 0)
            check(width <= maximum[0] && height <= maximum[0]) { "Imagem excede o limite da GPU." }
            // Ordem explícita: bytes B,G,R,A; o shader converte para canais RGBA ao ler.
            val staging = ByteBuffer.allocateDirect(pixels.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            staging.asIntBuffer().put(pixels)
            val textures = IntArray(if (filter == PdfImageFilter.DENOISE) 3 else 2)
            val framebuffer = IntArray(1)
            var program = 0
            try {
                GLES30.glGenTextures(textures.size, textures, 0)
                for ((index, texture) in textures.withIndex()) {
                    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
                    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
                    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
                    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
                    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
                    GLES30.glTexImage2D(
                        GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, width, height, 0,
                        GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, if (index == 0) staging else null,
                    )
                }
                checkGl()
                program = createProgram()
                GLES30.glUseProgram(program)
                GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "sourceImage"), 0)
                GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "originalImage"), 1)
                GLES30.glUniform2i(GLES30.glGetUniformLocation(program, "imageSize"), width, height)
                GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textures[0])
                GLES30.glGenFramebuffers(1, framebuffer, 0)
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer[0])
                GLES30.glViewport(0, 0, width, height)
                GLES30.glDisable(GLES30.GL_BLEND)
                GLES30.glDisable(GLES30.GL_DITHER)
                val operations = if (filter == PdfImageFilter.DENOISE) intArrayOf(0, 1) else intArrayOf(2)
                for ((index, operation) in operations.withIndex()) {
                    coroutineContext.ensureActive()
                    GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textures[index])
                    GLES30.glFramebufferTexture2D(
                        GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
                        GLES30.GL_TEXTURE_2D, textures[index + 1], 0,
                    )
                    check(GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) == GLES30.GL_FRAMEBUFFER_COMPLETE)
                    GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "operation"), operation)
                    GLES30.glUniform1f(
                        GLES30.glGetUniformLocation(program, "intensity"),
                        if (index == operations.lastIndex) intensity else 1f,
                    )
                    GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
                    checkGl()
                }
                // RGBA8 + UNSIGNED_BYTE é o caminho de leitura normalizado do GLES 3.
                staging.clear()
                GLES30.glReadPixels(0, 0, width, height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, staging)
                checkGl()
                coroutineContext.ensureActive()
                staging.asIntBuffer().get(pixels)
            } finally {
                if (program != 0) GLES30.glDeleteProgram(program)
                GLES30.glDeleteFramebuffers(1, framebuffer, 0)
                GLES30.glDeleteTextures(textures.size, textures, 0)
            }
        } finally {
            egl.close()
        }
    }

    private fun createProgram(): Int {
        val vertex = compileShader(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER)
        var fragment = 0
        var program = 0
        try {
            fragment = compileShader(GLES30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
            program = GLES30.glCreateProgram()
            GLES30.glAttachShader(program, vertex)
            GLES30.glAttachShader(program, fragment)
            GLES30.glLinkProgram(program)
            val status = IntArray(1)
            GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0)
            check(status[0] == GLES30.GL_TRUE) { GLES30.glGetProgramInfoLog(program) }
            return program
        } catch (failure: Throwable) {
            if (program != 0) GLES30.glDeleteProgram(program)
            throw failure
        } finally {
            GLES30.glDeleteShader(vertex)
            if (fragment != 0) GLES30.glDeleteShader(fragment)
        }
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        try {
            GLES30.glShaderSource(shader, source)
            GLES30.glCompileShader(shader)
            val status = IntArray(1)
            GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
            check(status[0] == GLES30.GL_TRUE) { GLES30.glGetShaderInfoLog(shader) }
            return shader
        } catch (failure: Throwable) {
            GLES30.glDeleteShader(shader)
            throw failure
        }
    }

    private fun checkGl() {
        val error = GLES30.glGetError()
        check(error == GLES30.GL_NO_ERROR) { "OpenGL ES: 0x${error.toString(16)}" }
    }

    private class EglSession {
        private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var context: EGLContext = EGL14.EGL_NO_CONTEXT
        private var surface: EGLSurface = EGL14.EGL_NO_SURFACE
        private val previousDisplay = EGL14.eglGetCurrentDisplay()
        private val previousContext = EGL14.eglGetCurrentContext()
        private val previousDraw = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW)
        private val previousRead = EGL14.eglGetCurrentSurface(EGL14.EGL_READ)

        fun open() {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            check(display != EGL14.EGL_NO_DISPLAY)
            // O binding Java exige arrays mesmo que o EGL nativo aceite ponteiros nulos.
            val version = IntArray(2)
            check(EGL14.eglInitialize(display, version, 0, version, 1))
            val configs = arrayOfNulls<EGLConfig>(1)
            val count = IntArray(1)
            check(EGL14.eglChooseConfig(
                display, intArrayOf(
                    EGL14.EGL_RENDERABLE_TYPE, 0x0040, // EGL_OPENGL_ES3_BIT_KHR
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                    EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_NONE,
                ), 0, configs, 0, 1, count, 0,
            ) && count[0] > 0)
            val config = requireNotNull(configs[0])
            context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0)
            check(context != EGL14.EGL_NO_CONTEXT)
            surface = EGL14.eglCreatePbufferSurface(display, config,
                intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
            check(surface != EGL14.EGL_NO_SURFACE)
            check(EGL14.eglMakeCurrent(display, surface, surface, context))
        }

        fun close() {
            if (display == EGL14.EGL_NO_DISPLAY) return
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            // Não chamamos eglTerminate: o display padrão também pertence ao renderizador da UI.
            if (previousDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(previousDisplay, previousDraw, previousRead, previousContext)
            }
        }
    }

    companion object {
        // Evita upload/readback de miniaturas e picos de memória nas páginas gigantes.
        internal fun isEligible(filter: PdfImageFilter, width: Int, height: Int): Boolean =
            filter in setOf(PdfImageFilter.DENOISE, PdfImageFilter.SHARPEN) &&
                width >= 3 && height >= 3 && width.toLong() * height in 262_144L..6_000_000L

        private const val VERTEX_SHADER = """#version 300 es
            void main() {
                vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
            }
        """

        // Inteiros reproduzem os kernels CPU, inclusive divisões, bordas e preservação do alfa.
        private const val FRAGMENT_SHADER = """#version 300 es
            precision highp float;
            precision highp int;
            uniform highp sampler2D sourceImage;
            uniform highp sampler2D originalImage;
            uniform ivec2 imageSize;
            uniform int operation;
            uniform float intensity;
            out vec4 result;
            ivec4 readPixel(ivec2 p) {
                return ivec4(floor(texelFetch(sourceImage, p, 0).bgra * 255.0 + 0.5));
            }
            int luminance(ivec4 p) { return (77 * p.r + 150 * p.g + 29 * p.b + 128) >> 8; }
            ivec4 isolated(ivec2 position, ivec4 center) {
                if (center.a == 0) return center;
                int light = luminance(center);
                int minimum = 255;
                int maximum = 0;
                ivec3 sum = ivec3(0);
                for (int y = -1; y <= 1; ++y) for (int x = -1; x <= 1; ++x) {
                    if (x == 0 && y == 0) continue;
                    ivec4 neighbor = readPixel(position + ivec2(x, y));
                    int value = luminance(neighbor);
                    if (neighbor.a != center.a || abs(value - light) <= 32) return center;
                    minimum = min(minimum, value);
                    maximum = max(maximum, value);
                    sum += neighbor.rgb;
                }
                if (maximum - minimum > 20) return center;
                ivec4 replacement = ivec4(sum / 8, center.a);
                return abs(light - luminance(replacement)) >= 48 ? replacement : center;
            }
            ivec4 bilateral(ivec2 position, ivec4 center) {
                int total = 160;
                ivec3 sum = center.rgb * total;
                for (int y = -1; y <= 1; ++y) for (int x = -1; x <= 1; ++x) {
                    if (x == 0 && y == 0) continue;
                    ivec4 neighbor = readPixel(position + ivec2(x, y));
                    if (neighbor.a != center.a) continue;
                    ivec3 delta = abs(center.rgb - neighbor.rgb);
                    int difference = max(delta.r, max(delta.g, delta.b));
                    if (difference >= 40) continue;
                    int weight = 40 - difference;
                    sum += neighbor.rgb * weight;
                    total += weight;
                }
                return ivec4(sum / total, center.a);
            }
            ivec4 sharpen(ivec2 position, ivec4 center) {
                int total = 0;
                for (int y = -1; y <= 1; ++y) for (int x = -1; x <= 1; ++x) {
                    total += luminance(readPixel(position + ivec2(x, y)));
                }
                int difference = luminance(center) - total / 9;
                int detail = abs(difference) < 3 ? 0 :
                    clamp(int(floor(float(difference) * 0.6 + 0.5)), -24, 24);
                return ivec4(clamp(center.rgb + detail, 0, 255), center.a);
            }
            void main() {
                ivec2 position = ivec2(gl_FragCoord.xy);
                ivec4 filtered = readPixel(position);
                if (position.x > 0 && position.y > 0 &&
                    position.x < imageSize.x - 1 && position.y < imageSize.y - 1) {
                    if (operation == 0) filtered = isolated(position, filtered);
                    else if (operation == 1) filtered = bilateral(position, filtered);
                    else filtered = sharpen(position, filtered);
                }
                if (intensity < 1.0) {
                    ivec4 original = ivec4(floor(texelFetch(originalImage, position, 0).bgra * 255.0 + 0.5));
                    filtered.rgb = ivec3(floor(vec3(original.rgb) +
                        vec3(filtered.rgb - original.rgb) * intensity + 0.5));
                    filtered.a = original.a;
                }
                result = vec4(filtered.bgra) / 255.0;
            }
        """
    }
}
