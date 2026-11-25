package com.joseleandro.scaniqo.data.pdf

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.joseleandro.scaniqo.domain.model.PdfImageFilter
import kotlin.math.abs
import kotlin.system.measureNanoTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GlesPdfFilterAccelerationTest {
    @Test
    fun gpuMatchesCpuIncludingEdgesAlphaAndIntensity() = runBlocking {
        requireGles3()
        val width = 513
        val height = 517
        val source = image(width, height, varyingAlpha = true)
        val gpu = GlesPdfFilterAcceleration()
        for (filter in listOf(PdfImageFilter.DENOISE, PdfImageFilter.SHARPEN)) {
            for (intensity in listOf(1f, .37f)) {
                val expected = source.copyOf()
                PdfImageFilters.apply(expected, filter, width, height, intensity)
                val actual = source.copyOf()
                assertTrue("O aparelho anuncia GLES 3; o shader deve executar: $filter",
                    gpu.tryApply(actual, filter, width, height, intensity))
                if (intensity == 1f) {
                    assertArrayEquals("Pixels inteiros de $filter", expected, actual)
                } else {
                    // Mistura fracionária pode arredondar 1 nível diferente por fusão de floats na GPU.
                    for (index in actual.indices) {
                        assertEquals("Alfa $index", expected[index] ushr 24, actual[index] ushr 24)
                        for (shift in listOf(0, 8, 16)) {
                            assertTrue("$filter pixel $index canal $shift",
                                abs(((expected[index] ushr shift) and 255) -
                                    ((actual[index] ushr shift) and 255)) <= 1)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun reportsRepresentativeCpuAndGpuTimesWithoutAssumingUniversalSpeedup() = runBlocking {
        requireGles3()
        val width = 1600
        val height = 2200
        val source = image(width, height, varyingAlpha = false)
        val gpu = GlesPdfFilterAcceleration()
        for (filter in listOf(PdfImageFilter.DENOISE, PdfImageFilter.SHARPEN)) {
            // Inclui upload, compilação, readback e liberação; não mede só o shader.
            val cpuTimes = mutableListOf<Long>()
            val gpuTimes = mutableListOf<Long>()
            repeat(3) {
                val cpuPixels = source.copyOf()
                cpuTimes += measureNanoTime { PdfImageFilters.apply(cpuPixels, filter, width, height) }
                val gpuPixels = source.copyOf()
                gpuTimes += measureNanoTime {
                    assertTrue(gpu.tryApply(gpuPixels, filter, width, height, 1f))
                }
                assertArrayEquals("Paridade na imagem de desempenho", cpuPixels, gpuPixels)
            }
            Log.i("PdfGpuBenchmark", "$filter ${width}x$height CPU=${cpuTimes.sorted()[1] / 1_000_000}ms " +
                "GPU=${gpuTimes.sorted()[1] / 1_000_000}ms (mediana de 3; inclui transferências)")
        }
    }

    private fun requireGles3() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        assumeTrue("GLES 3 indisponível: o app utiliza CPU", manager.deviceConfigurationInfo.reqGlEsVersion >= 0x30000)
    }

    private fun image(width: Int, height: Int, varyingAlpha: Boolean): IntArray =
        IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            val value = when {
                x % 31 == 0 && y % 29 == 0 -> 0 // Impulsos isolados.
                x % 47 == 1 -> 24 // Traço fino conectado, que deve ser preservado.
                else -> 195 + (x * 7 + y * 11) % 27
            }
            val alpha = if (varyingAlpha && index % 17 == 0) 96 else 255
            (alpha shl 24) or (value shl 16) or ((value + x % 9).coerceAtMost(255) shl 8) or value
        }
}
