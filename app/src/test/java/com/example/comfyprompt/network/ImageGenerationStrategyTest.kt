package com.example.comfyprompt.network

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageGenerationStrategyTest {

    @Test
    fun testFindImageUrl_DirectPrimitive() {
        val url = "https://example.com/output.png"
        val element = JsonParser.parseString("\"$url\"")
        assertEquals(url, findImageUrl(element))
    }

    @Test
    fun testFindImageUrl_NonUrlPrimitive() {
        val nonUrl = "just a string"
        val element = JsonParser.parseString("\"$nonUrl\"")
        assertNull(findImageUrl(element))
    }

    @Test
    fun testFindImageUrl_NestedObjectWithKnownKey() {
        val json = """
            {
                "status": "success",
                "result": {
                    "download_url": "https://example.com/image.jpg"
                }
            }
        """.trimIndent()
        val element = JsonParser.parseString(json)
        assertEquals("https://example.com/image.jpg", findImageUrl(element))
    }

    @Test
    fun testFindImageUrl_NestedObjectWithUnknownKey() {
        val json = """
            {
                "status": "success",
                "output": {
                    "some_custom_key": "https://example.com/photo.jpeg"
                }
            }
        """.trimIndent()
        val element = JsonParser.parseString(json)
        assertEquals("https://example.com/photo.jpeg", findImageUrl(element))
    }

    @Test
    fun testFindImageUrl_NestedArray() {
        val json = """
            [
                "not_a_url",
                {
                    "info": "metadata"
                },
                [
                    "https://example.com/nested.webp"
                ]
            ]
        """.trimIndent()
        val element = JsonParser.parseString(json)
        assertEquals("https://example.com/nested.webp", findImageUrl(element))
    }

    @Test
    fun testFindImageUrl_NoMatch() {
        val json = """
            {
                "status": "error",
                "details": {
                    "code": 500,
                    "message": "Internal error"
                }
            }
        """.trimIndent()
        val element = JsonParser.parseString(json)
        assertNull(findImageUrl(element))
    }
}
