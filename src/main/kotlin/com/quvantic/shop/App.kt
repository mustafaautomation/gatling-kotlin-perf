package com.quvantic.shop

import com.fasterxml.jackson.databind.SerializationFeature
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.delay

data class AddItem(val productId: Int = 0, val quantity: Int = 0)

data class ErrorBody(val error: String)

/**
 * Fault injection, off by default. CI turns it on to prove the Gatling assertions fail when the
 * service gets slow or flaky.
 *
 * @property checkoutDelayMs extra latency added to every checkout
 * @property failEvery every Nth checkout returns 503 (0 = never)
 */
data class Faults(val checkoutDelayMs: Long = 0, val failEvery: Long = 0) {
    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()): Faults = Faults(
            checkoutDelayMs = env["SHOP_CHECKOUT_DELAY_MS"]?.toLong() ?: 0,
            failEvery = env["SHOP_FAIL_EVERY"]?.toLong() ?: 0,
        )
    }
}

fun Application.shopModule(shop: Shop = Shop(), faults: Faults = Faults()) {
    val checkouts = AtomicLong()
    install(ContentNegotiation) { jackson { disable(SerializationFeature.FAIL_ON_EMPTY_BEANS) } }
    install(StatusPages) {
        exception<ShopException> { call, e -> call.respond(HttpStatusCode.fromValue(e.status), ErrorBody(e.message ?: "error")) }
        exception<BadRequestException> { call, _ -> call.respond(HttpStatusCode.BadRequest, ErrorBody("malformed request")) }
        exception<NumberFormatException> { call, _ -> call.respond(HttpStatusCode.BadRequest, ErrorBody("malformed request")) }
    }
    routing {
        get("/health") { call.respond(mapOf("status" to "ok")) }
        get("/api/products") {
            val page = call.request.queryParameters["page"]?.toInt() ?: 1
            val size = call.request.queryParameters["size"]?.toInt() ?: 20
            call.respond(shop.page(page, size))
        }
        get("/api/products/{id}") { call.respond(shop.product(call.parameters["id"]!!.toInt())) }
        post("/api/carts") { call.respond(HttpStatusCode.Created, shop.createCart()) }
        get("/api/carts/{id}") { call.respond(shop.cart(call.parameters["id"]!!)) }
        post("/api/carts/{id}/items") {
            val body = call.receive<AddItem>()
            call.respond(shop.addItem(call.parameters["id"]!!, body.productId, body.quantity))
        }
        post("/api/carts/{id}/checkout") {
            val n = checkouts.incrementAndGet()
            if (faults.checkoutDelayMs > 0) delay(faults.checkoutDelayMs)
            if (faults.failEvery > 0 && n % faults.failEvery == 0L) {
                call.respond(HttpStatusCode.ServiceUnavailable, ErrorBody("injected failure"))
                return@post
            }
            call.respond(HttpStatusCode.Created, shop.checkout(call.parameters["id"]!!))
        }
        get("/api/orders/{id}") { call.respond(shop.order(call.parameters["id"]!!)) }
    }
}

fun main() {
    val port = System.getenv("SHOP_PORT")?.toInt() ?: 8080
    val faults = Faults.fromEnv()
    embeddedServer(Netty, port = port, host = System.getenv("SHOP_HOST") ?: "127.0.0.1") {
        shopModule(faults = faults)
    }.start(wait = true)
}
