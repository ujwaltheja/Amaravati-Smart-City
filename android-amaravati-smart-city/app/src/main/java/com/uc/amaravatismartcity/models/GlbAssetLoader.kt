package com.uc.amaravatismartcity.models

import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.node.ModelNode

/**
 * Loads GLB assets from the Android assets folder.
 *
 * Store models under `app/src/main/assets/`, then reference them with an
 * asset-relative path such as `models/residential/tower.glb`.
 */
object GlbAssetLoader {
    fun createModelNode(
        modelLoader: ModelLoader,
        assetPath: String,
        scaleToUnits: Float = 1f
    ): ModelNode {
        return ModelNode(
            modelInstance = modelLoader.createModelInstance(assetFileLocation = assetPath),
            scaleToUnits = scaleToUnits,
            centerOrigin = true
        )
    }
}
