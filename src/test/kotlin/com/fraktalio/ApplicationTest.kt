package com.fraktalio

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.testing.*
import kotlin.test.*
import io.ktor.http.*
import com.fraktalio.routes.homeRouting

class ApplicationTest {
    @Test
    fun testRoot() = testApplication {
        application {
            homeRouting()
        }
        client.get("/").apply {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals("Hello World!Hello World Again!Hello World Again and Again!", bodyAsText())
        }
    }
}
