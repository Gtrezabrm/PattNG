package com.v2ray.ang.ui.main

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeConnectShapeTest {
    /**
     * Regression test for the experimental Material 3 Expressive shapes behind [HomeConnectShape]: a material3 upgrade
     * that removes or renames them fails here, instead of at first run of the home screen.
     */
    @Test
    fun expressivePolygonsBehindTheButtonShapesExist() {
        assertTrue(homeConnectShapesAvailable())
    }
}
