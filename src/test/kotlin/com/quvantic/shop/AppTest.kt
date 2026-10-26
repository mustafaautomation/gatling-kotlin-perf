package com.quvantic.shop

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.measureTime

private val idPattern = Regex("\"id\":\"([0-9a-f-]{36})\"")

private suspend fun ApplicationTestBuilder.newCart(): String =
    idPattern.find(client.post("/api/carts").bodyAsText())!!.groupValues[1]

private suspend fun ApplicationTestBuilder.add(cart: String, json: String) =
    client.post("/api/carts/$cart/items") {
        contentType(ContentType.Application.Json)
        setBody(json)
    }

class AppTest {
    @Test
    fun `full journey over HTTP`() = testApplication {
        application { shopModule() }
        assertEquals(HttpStatusCode.OK, client.get("/health").status)
        assertTrue(client.get("/api/products?page=2&size=5").bodyAsText().contains("\"id\":6"))
        val cart = newCart()
        assertEquals(HttpStatusCode.OK, add(cart, """{"productId":3,"quantity":2}""").status)
        val checkout = client.post("/api/carts/$cart/checkout")
        assertEquals(HttpStatusCode.Created, checkout.status)
        val body = checkout.bodyAsText()
        assertTrue(body.contains("\"total\":27.98"), body)
        val orderId = idPattern.find(body)!!.groupValues[1]
        assertEquals(HttpStatusCode.OK, client.get("/api/orders/$orderId").status)
    }

    @Test
    fun `errors map to status codes with a JSON body`() = testApplication {
        application { shopModule() }
        assertEquals(HttpStatusCode.NotFound, client.get("/api/products/999").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/products/abc").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/products?size=0").status)
        val cart = newCart()
        val bad = add(cart, """{"productId":1,"quantity":50}""")
        assertEquals(HttpStatusCode.BadRequest, bad.status)
        assertTrue(bad.bodyAsText().contains("quantity must be 1..10"))
        assertEquals(HttpStatusCode.BadRequest, add(cart, "not json").status)
        assertEquals(HttpStatusCode.Conflict, client.post("/api/carts/$cart/checkout").status)
    }

    @Test
    fun `fault injection fails every Nth checkout and adds latency`() = testApplication {
        application { shopModule(faults = Faults(checkoutDelayMs = 150, failEvery = 2)) }
        val statuses = (1..4).map {
            val cart = newCart()
            add(cart, """{"productId":1,"quantity":1}""")
            client.post("/api/carts/$cart/checkout").status
        }
        assertEquals(listOf(201, 503, 201, 503), statuses.map { it.value })
        val cart = newCart()
        add(cart, """{"productId":1,"quantity":1}""")
        val took = measureTime { client.post("/api/carts/$cart/checkout") }
        assertTrue(took.inWholeMilliseconds >= 150, "took $took")
    }

    @Test
    fun `faults are read from the environment`() {
        assertEquals(Faults(), Faults.fromEnv(emptyMap()))
        assertEquals(Faults(200, 10), Faults.fromEnv(mapOf("SHOP_CHECKOUT_DELAY_MS" to "200", "SHOP_FAIL_EVERY" to "10")))
    }
}
