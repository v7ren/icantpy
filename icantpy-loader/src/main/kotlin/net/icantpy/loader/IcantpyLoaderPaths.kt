package net.icantpy.loader

import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Path

object IcantpyLoaderPaths {
    const val PAYLOAD_MOD_ID: String = "icantpy"
    const val LOADER_MOD_ID: String = "icantpy_loader"
    const val PAYLOAD_ENTRYPOINT: String = "net.icantpy.Icantpy"

    fun payloadFileName(version: String): String = "icantpy-$version.jar"

    fun root(): Path = FabricLoader.getInstance().gameDir.resolve("icantpy")

    fun runtime(): Path = root().resolve("runtime")

    fun payload(): Path = runtime().resolve("icantpy.jar")

    fun payloadCache(): Path = runtime().resolve("cache")

    fun libraryCache(): Path = runtime().resolve("libs")

    fun installedManifest(): Path = runtime().resolve("installed.json")
}
