package com.ronin2022.matelinuxlauncher.data

import android.app.ActivityManager
import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.os.Build
import com.ronin2022.matelinuxlauncher.domain.DeviceProfile
import com.ronin2022.matelinuxlauncher.domain.GpuProfile

class DeviceProfiler(private val context: Context) {

    @Volatile
    private var cached: DeviceProfile? = null

    fun read(): DeviceProfile = cached ?: synchronized(this) {
        cached ?: collect().also { cached = it }
    }

    private fun collect(): DeviceProfile {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val memoryInfo = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        val metrics = context.resources.displayMetrics

        return DeviceProfile(
            manufacturer = Build.MANUFACTURER.orEmpty().ifBlank { "Bilinmiyor" },
            model = Build.MODEL.orEmpty().ifBlank { "Bilinmiyor" },
            androidVersion = Build.VERSION.RELEASE.orEmpty(),
            sdkInt = Build.VERSION.SDK_INT,
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            totalMemoryBytes = memoryInfo.totalMem,
            availableMemoryBytes = memoryInfo.availMem,
            screenWidthPx = metrics.widthPixels,
            screenHeightPx = metrics.heightPixels,
            densityDpi = metrics.densityDpi,
            glEsVersion = decodeGlEs(activityManager.deviceConfigurationInfo.reqGlEsVersion),
            vulkanVersion = findVulkanVersion(),
            gpu = EglGpuProbe.read(),
        )
    }

    private fun findVulkanVersion(): String? {
        val encoded = context.packageManager.systemAvailableFeatures
            .firstOrNull { it.name == "android.hardware.vulkan.version" }
            ?.version
            ?: return null
        val major = encoded ushr 22
        val minor = (encoded ushr 12) and 0x3ff
        val patch = encoded and 0xfff
        return "$major.$minor.$patch"
    }

    private fun decodeGlEs(encoded: Int): String =
        "${encoded ushr 16}.${encoded and 0xffff}"
}

private object EglGpuProbe {

    fun read(): GpuProfile {
        var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
        var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        var surface: EGLSurface = EGL14.EGL_NO_SURFACE

        return try {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            check(display != EGL14.EGL_NO_DISPLAY) { "EGL display alınamadı." }

            val versions = IntArray(2)
            check(EGL14.eglInitialize(display, versions, 0, versions, 1)) {
                "EGL başlatılamadı."
            }

            val es3Config = chooseConfig(display, useEs3 = true)
            val config = es3Config ?: chooseConfig(display, useEs3 = false)
                ?: error("Uygun EGL yapılandırması bulunamadı.")
            val contextVersion = if (es3Config != null) 3 else 2

            eglContext = EGL14.eglCreateContext(
                display,
                config,
                EGL14.EGL_NO_CONTEXT,
                intArrayOf(
                    EGL14.EGL_CONTEXT_CLIENT_VERSION,
                    contextVersion,
                    EGL14.EGL_NONE,
                ),
                0,
            )
            check(eglContext != EGL14.EGL_NO_CONTEXT) { "EGL context oluşturulamadı." }

            surface = EGL14.eglCreatePbufferSurface(
                display,
                config,
                intArrayOf(
                    EGL14.EGL_WIDTH,
                    1,
                    EGL14.EGL_HEIGHT,
                    1,
                    EGL14.EGL_NONE,
                ),
                0,
            )
            check(surface != EGL14.EGL_NO_SURFACE) { "EGL yüzeyi oluşturulamadı." }
            check(EGL14.eglMakeCurrent(display, surface, surface, eglContext)) {
                "EGL context etkinleştirilemedi."
            }

            GpuProfile(
                vendor = GLES20.glGetString(GLES20.GL_VENDOR),
                renderer = GLES20.glGetString(GLES20.GL_RENDERER),
                version = GLES20.glGetString(GLES20.GL_VERSION),
            )
        } catch (error: Throwable) {
            GpuProfile(
                vendor = null,
                renderer = null,
                version = null,
                probeError = error.message ?: error::class.java.simpleName,
            )
        } finally {
            if (display != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(
                    display,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_CONTEXT,
                )
                if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
                if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, eglContext)
                EGL14.eglTerminate(display)
            }
        }
    }

    private fun chooseConfig(display: EGLDisplay, useEs3: Boolean): EGLConfig? {
        val attributes = intArrayOf(
            EGL14.EGL_SURFACE_TYPE,
            EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RENDERABLE_TYPE,
            if (useEs3) EGLExt.EGL_OPENGL_ES3_BIT_KHR else EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_RED_SIZE,
            8,
            EGL14.EGL_GREEN_SIZE,
            8,
            EGL14.EGL_BLUE_SIZE,
            8,
            EGL14.EGL_ALPHA_SIZE,
            8,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        val success = EGL14.eglChooseConfig(
            display,
            attributes,
            0,
            configs,
            0,
            configs.size,
            count,
            0,
        )
        return if (success && count[0] > 0) configs[0] else null
    }
}
