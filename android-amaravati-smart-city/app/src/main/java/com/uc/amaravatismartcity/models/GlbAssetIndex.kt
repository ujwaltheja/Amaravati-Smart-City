package com.uc.amaravatismartcity.models

import android.content.res.AssetManager
import java.io.IOException

object GlbAssetIndex {
    fun scan(assetManager: AssetManager, rootPath: String = "models"): List<String> {
        val results = mutableListOf<String>()
        scanRecursive(assetManager, rootPath, results)
        return results.distinct().sorted()
    }

    private fun scanRecursive(
        assetManager: AssetManager,
        path: String,
        results: MutableList<String>
    ) {
        val children = try {
            assetManager.list(path).orEmpty()
        } catch (exception: IOException) {
            emptyArray()
        }

        if (children.isEmpty()) {
            if (path.endsWith(".glb", ignoreCase = true)) {
                results += path
            }
            return
        }

        children.forEach { child ->
            val childPath = if (path.isEmpty()) child else "$path/$child"
            if (child.endsWith(".glb", ignoreCase = true)) {
                results += childPath
            } else {
                scanRecursive(assetManager, childPath, results)
            }
        }
    }
}
