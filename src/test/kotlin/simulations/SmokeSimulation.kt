package simulations

import io.gatling.javaapi.core.CoreDsl.atOnceUsers
import io.gatling.javaapi.core.CoreDsl.global
import io.gatling.javaapi.core.CoreDsl.scenario
import io.gatling.javaapi.core.Simulation

/** One user through every request. Fails on any error: a functional check before the load run. */
class SmokeSimulation : Simulation() {
    init {
        setUp(scenario("smoke").exec(Journeys.shopper).injectOpen(atOnceUsers(1)))
            .protocols(Journeys.protocol)
            .assertions(global().failedRequests().count().`is`(0L))
    }
}
