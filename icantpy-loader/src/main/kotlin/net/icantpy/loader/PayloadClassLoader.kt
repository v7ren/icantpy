package net.icantpy.loader

import java.net.URL
import java.net.URLClassLoader

/**
 * Payload classloader: parent-first like a plain [URLClassLoader], except for libraries the payload bundles and
 * other mods may also inject into the game classloader with an incompatible version (Skia/Skiko: NebulaClient ships
 * an older Skia whose FontStyle lacks a constructor the payload calls). Those packages resolve from the payload jar
 * first and fall back to the parent only when the payload does not contain them.
 */
class PayloadClassLoader(urls: Array<URL>, parent: ClassLoader) : URLClassLoader(urls, parent) {
    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        if (!isChildFirst(name)) return super.loadClass(name, resolve)
        synchronized(getClassLoadingLock(name)) {
            val loaded = findLoadedClass(name) ?: try {
                findClass(name)
            } catch (_: ClassNotFoundException) {
                return super.loadClass(name, resolve)
            }
            if (resolve) resolveClass(loaded)
            return loaded
        }
    }

    override fun getResource(name: String): URL? {
        if (!isChildFirstResource(name)) return super.getResource(name)
        return findResource(name) ?: super.getResource(name)
    }

    companion object {
        private val CHILD_FIRST_PACKAGES = listOf("org.jetbrains.skia.", "org.jetbrains.skiko.")

        fun isChildFirst(className: String): Boolean = CHILD_FIRST_PACKAGES.any { className.startsWith(it) }

        /** Resources of the same libraries (e.g. Skiko's native library and version files). */
        fun isChildFirstResource(resourceName: String): Boolean =
            resourceName.startsWith("org/jetbrains/skia/") ||
                resourceName.startsWith("org/jetbrains/skiko/") ||
                resourceName.startsWith("skiko-")
    }
}
