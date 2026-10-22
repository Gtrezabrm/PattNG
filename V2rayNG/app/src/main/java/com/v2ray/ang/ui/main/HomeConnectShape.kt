package com.v2ray.ang.ui.main

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape

/** The look of the round connect button on the home card; the only type the rest of the UI sees of the polygons. */
enum class HomeConnectShape {
    /** Not running, or still coming up. */
    Idle,

    /** Running. */
    Active,
}

/**
 * Project-owned stable entry to the Material 3 Expressive polygon shapes, so that no other file depends on them.
 *
 * Opt-in marker: [ExperimentalMaterial3ExpressiveApi] on `MaterialShapes` and `toShape`. No stable API draws a
 * scalloped (cookie) button. Nothing here is persisted or sent over IPC.
 * Reevaluate when `MaterialShapes` leaves the experimental API in the material3 version that `composeBom` in
 * `libs.versions.toml` pins; then drop the opt-in.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun homeConnectShape(kind: HomeConnectShape): Shape = when (kind) {
    HomeConnectShape.Idle -> MaterialShapes.Cookie9Sided.toShape()
    HomeConnectShape.Active -> MaterialShapes.SoftBurst.toShape()
}

/** Whether the shapes of [HomeConnectShape] still exist in the pinned material3; the regression test reads this. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun homeConnectShapesAvailable(): Boolean =
    MaterialShapes.Cookie9Sided.cubics.isNotEmpty() && MaterialShapes.SoftBurst.cubics.isNotEmpty()
