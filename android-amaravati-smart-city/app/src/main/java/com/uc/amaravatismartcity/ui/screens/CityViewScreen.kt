package com.uc.amaravatismartcity.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.sceneview.Scene
import io.github.sceneview.node.ModelNode
import io.github.sceneview.math.Position
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes

@Composable
fun CityViewScreen(modifier: Modifier = Modifier) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)

    val sceneLayout = listOf(
        Pair("models/City-Commercial/building-a.glb", Position(-2f, 0f, -4f)),
        Pair("models/City-Commercial/building-b.glb", Position(2f, 0f, -4f)),
        Pair("models/Roads and Bridges/road-straight.glb", Position(-2f, 0f, -2f)),
        Pair("models/Roads and Bridges/road-straight.glb", Position(0f, 0f, -2f)),
        Pair("models/Roads and Bridges/road-straight.glb", Position(2f, 0f, -2f)),
        Pair("models/Cars/suv.glb", Position(0f, 0f, -2f)),
        Pair("models/Cars/taxi.glb", Position(-1.5f, 0f, -1.5f))
    )

    Scene(
        modifier = modifier.fillMaxSize(),
        engine = engine,
        modelLoader = modelLoader,
        childNodes = rememberNodes {
            for ((path, position) in sceneLayout) {
                add(
                    ModelNode(
                        modelInstance = modelLoader.createModelInstance(path)
                    ).apply {
                        this.position = position
                    }
                )
            }
        }
    )
}
