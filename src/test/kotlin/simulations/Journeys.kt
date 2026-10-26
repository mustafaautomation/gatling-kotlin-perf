package simulations

import io.gatling.javaapi.core.ChainBuilder
import io.gatling.javaapi.core.CoreDsl.StringBody
import io.gatling.javaapi.core.CoreDsl.exec
import io.gatling.javaapi.core.CoreDsl.jmesPath
import io.gatling.javaapi.core.CoreDsl.repeat
import io.gatling.javaapi.http.HttpDsl.http
import io.gatling.javaapi.http.HttpDsl.status
import io.gatling.javaapi.http.HttpProtocolBuilder
import java.time.Duration
import java.util.concurrent.ThreadLocalRandom

/** Settings come from -D system properties so CI and local runs share one simulation. */
object Settings {
    val baseUrl: String = System.getProperty("baseUrl", "http://localhost:8080")
    val usersPerSec: Double = System.getProperty("usersPerSec", "10").toDouble()
    val rampSeconds: Long = System.getProperty("rampSeconds", "20").toLong()
    val holdSeconds: Long = System.getProperty("holdSeconds", "60").toLong()

    /** Service-level objectives the load test asserts. */
    val p95Ms: Int = System.getProperty("slo.p95Ms", "250").toInt()
    val checkoutP99Ms: Int = System.getProperty("slo.checkoutP99Ms", "500").toInt()
    val maxErrorPercent: Double = System.getProperty("slo.maxErrorPercent", "1").toDouble()
}

object Journeys {
    val protocol: HttpProtocolBuilder = http
        .baseUrl(Settings.baseUrl)
        .acceptHeader("application/json")
        .contentTypeHeader("application/json")
        .userAgentHeader("gatling-kotlin-perf")

    private val pickProducts = exec { session ->
        val rnd = ThreadLocalRandom.current()
        session.set("productId", rnd.nextInt(1, 51)).set("quantity", rnd.nextInt(1, 4))
    }

    val browse: ChainBuilder = exec(
        http("list products")
            .get("/api/products")
            .queryParam("page", "#{page}")
            .queryParam("size", "10")
            .check(status().`is`(200), jmesPath("length(@)").ofInt().`is`(10)),
    ).exec(
        http("product detail")
            .get("/api/products/#{productId}")
            .check(status().`is`(200), jmesPath("id").ofInt().isEL("#{productId}")),
    )

    /** Create a cart, add 1-3 lines, check out, then read the order back. */
    val purchase: ChainBuilder = exec(
        http("create cart")
            .post("/api/carts")
            .check(status().`is`(201), jmesPath("id").saveAs("cartId")),
    ).exec(
        repeat({ ThreadLocalRandom.current().nextInt(1, 4) }).on(
            pickProducts.exec(
                http("add item")
                    .post("/api/carts/#{cartId}/items")
                    .body(StringBody("""{"productId":#{productId},"quantity":#{quantity}}"""))
                    .check(status().`is`(200)),
            ),
        ),
    ).exec(
        http("checkout")
            .post("/api/carts/#{cartId}/checkout")
            .check(status().`is`(201), jmesPath("id").saveAs("orderId"), jmesPath("cartId").isEL("#{cartId}")),
    ).exitHereIfFailed().exec(
        http("get order")
            .get("/api/orders/#{orderId}")
            .check(status().`is`(200), jmesPath("id").isEL("#{orderId}")),
    )

    val shopper: ChainBuilder = exec { session ->
        val rnd = ThreadLocalRandom.current()
        session.set("page", rnd.nextInt(1, 6)).set("productId", rnd.nextInt(1, 51))
    }.exec(browse).pause(Duration.ofMillis(100), Duration.ofMillis(400)).exec(purchase)
}
