package simulations

import io.gatling.javaapi.core.CoreDsl.constantUsersPerSec
import io.gatling.javaapi.core.CoreDsl.details
import io.gatling.javaapi.core.CoreDsl.global
import io.gatling.javaapi.core.CoreDsl.rampUsersPerSec
import io.gatling.javaapi.core.CoreDsl.scenario
import io.gatling.javaapi.core.Simulation
import java.time.Duration

/**
 * Open-model load: arrivals ramp up to [Settings.usersPerSec] new shoppers per second, then hold.
 * An open model keeps sending arrivals even if the service slows down, which is how real traffic
 * behaves and why queueing shows up in the percentiles.
 */
class CheckoutLoadSimulation : Simulation() {
    init {
        setUp(
            scenario("shoppers").exec(Journeys.shopper).injectOpen(
                rampUsersPerSec(1.0).to(Settings.usersPerSec).during(Duration.ofSeconds(Settings.rampSeconds)),
                constantUsersPerSec(Settings.usersPerSec).during(Duration.ofSeconds(Settings.holdSeconds)),
            ),
        ).protocols(Journeys.protocol)
            .assertions(
                global().failedRequests().percent().lt(Settings.maxErrorPercent),
                global().responseTime().percentile(95.0).lt(Settings.p95Ms),
                details("checkout").responseTime().percentile(99.0).lt(Settings.checkoutP99Ms),
                details("checkout").failedRequests().percent().lt(Settings.maxErrorPercent),
            )
    }
}
