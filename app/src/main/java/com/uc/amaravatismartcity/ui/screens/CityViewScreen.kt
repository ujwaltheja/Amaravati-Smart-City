package com.uc.amaravatismartcity.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.sceneview.Scene
import io.github.sceneview.node.ModelNode
import io.github.sceneview.math.Position

@Composable
fun CityViewScreen(modifier: Modifier = Modifier) {
    Scene(
        modifier = modifier.fillMaxSize(),
        onViewCreated = { sceneView ->
            
            // Build a small street scene to make the game look outstanding
            val sceneLayout = listOf(
                Pair("models/City-Commercial/building-a.glb", Position(-2f, 0f, -4f)),
                Pair("models/City-Commercial/building-b.glb", Position(2f, 0f, -4f)),
                Pair("models/Roads and Bridges/road-straight.glb", Position(-2f, 0f, -2f)),
                Pair("models/Roads and Bridges/road-straight.glb", Position(0f, 0f, -2f)),
                Pair("models/Roads and Bridges/road-straight.glb", Position(2f, 0f, -2f)),
                Pair("models/Cars/suv.glb", Position(0f, 0f, -2f)),
                Pair("models/Cars/taxi.glb", Position(-1.5f, 0f, -1.5f))
            )

            for ((path, position) in sceneLayout) {
                val model = sceneView.modelLoader.createModelInstance(path)
                if (model != null) {
                    val node = ModelNode(
                        modelInstance = model
                    ).apply {
                        this.position = position
                    }
                    sceneView.addChildNode(node)
                }
            }
        }
    )
}
