package com.example.comfyprompt.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComfyClientTest {

    @Test
    fun testIsPositivePrompt_StandardPositive() {
        val prompt = "A beautiful photo of a sunset over the mountains, 8k resolution, highly detailed."
        assertTrue(WorkflowTransformer.isPositivePrompt(prompt))
    }

    @Test
    fun testIsPositivePrompt_EmptyAndBlank() {
        assertTrue(WorkflowTransformer.isPositivePrompt(""))
        assertTrue(WorkflowTransformer.isPositivePrompt("   "))
    }

    @Test
    fun testIsPositivePrompt_ContainsNegativeKeywords() {
        val blurryPrompt = "A blurry photo of a cat."
        assertFalse(WorkflowTransformer.isPositivePrompt(blurryPrompt))

        val badAnatomyPrompt = "A person with bad anatomy."
        assertFalse(WorkflowTransformer.isPositivePrompt(badAnatomyPrompt))

        val nsfwPrompt = "An absolute masterpiece, nsfw artwork."
        assertFalse(WorkflowTransformer.isPositivePrompt(nsfwPrompt))
    }

    @Test
    fun testCalculateDimensions_SquareOneMegapixel() {
        val (width, height) = WorkflowTransformer.calculateDimensions("1.0", "1:1")
        // 1MP = 1000000 pixels. Square root is 1000.
        // 1000 is divisible by 16 (16 * 62 = 992, 16 * 63 = 1008). In Kotlin round(62.5) rounds to even (62.0).
        assertEquals(992, width)
        assertEquals(992, height)
    }

    @Test
    fun testCalculateDimensions_AspectRatios() {
        val (width, height) = WorkflowTransformer.calculateDimensions("1.0", "16:9 (Panorama)")
        // w * h = 1000000, w / h = 16 / 9 => w = 1.777 * h => h^2 * 1.777 = 1000000 => h = 750 => w = 1333
        // 750 rounds to 752 (divisible by 16: 16 * 47 = 752)
        // 1333 rounds to 1328 (divisible by 16: 16 * 83 = 1328)
        assertEquals(1328, width)
        assertEquals(752, height)
    }

    @Test
    fun testCalculateDimensions_Divisibility() {
        val aspectRatios = listOf("1:1", "16:9", "3:4", "2:3", "4:5")
        val megapixels = listOf("0.5", "1.0", "2.0")

        for (mp in megapixels) {
            for (ratio in aspectRatios) {
                val (w, h) = WorkflowTransformer.calculateDimensions(mp, ratio)
                assertEquals("Width $w must be divisible by 16", 0, w % 16)
                assertEquals("Height $h must be divisible by 16", 0, h % 16)
                assertTrue("Width $w must be at least 16", w >= 16)
                assertTrue("Height $h must be at least 16", h >= 16)
            }
        }
    }
}
