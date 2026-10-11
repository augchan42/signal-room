package dev.digitalrain.synthwave.core.data

import android.content.res.AssetManager

fun loadAndroidAssetCatalog(assets: AssetManager): AssetCatalog {
    val manifest = assets.open("demo/manifest.json").bufferedReader().use { it.readText() }
    return AssetCatalog(loadAssetManifest(manifest), AssetOpener { path ->
        runCatching { assets.open("demo/$path").use { it.read() } }.isSuccess
    })
}
