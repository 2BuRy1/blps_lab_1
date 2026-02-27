package com.example.ticket.bootstrap

import com.example.ticket.persistence.entity.RouteEntity
import com.example.ticket.persistence.repository.RouteRepository
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalTime

@Component
class RoutesDataInitializer(
    private val routeRepository: RouteRepository,
) : CommandLineRunner {

    override fun run(vararg args: String?) {
        if (routeRepository.count() > 0) {
            val normalized = routeRepository.findAll().onEach {
                it.freeSeats = (it.capacity - it.reservedSeats).coerceAtLeast(0)
            }
            routeRepository.saveAll(normalized)
            return
        }

        val travelDate = LocalDate.now().plusDays(2)
        val routes = listOf(
            RouteEntity(
                routeId = "r1",
                fromCity = "SPB",
                toCity = "MSK",
                travelDate = travelDate,
                fromTerminal = "Moskovsky",
                toTerminal = "Leningradsky",
                trainId = "t10",
                departureTime = LocalTime.of(8, 30),
                price = 1500,
                capacity = 60,
            ),
            RouteEntity(
                routeId = "r2",
                fromCity = "SPB",
                toCity = "MSK",
                travelDate = travelDate,
                fromTerminal = "Moskovsky",
                toTerminal = "Leningradsky",
                trainId = "t12",
                departureTime = LocalTime.of(12, 10),
                price = 2100,
                capacity = 45,
            ),
            RouteEntity(
                routeId = "r3",
                fromCity = "SPB",
                toCity = "MSK",
                travelDate = travelDate,
                fromTerminal = "Ladozhsky",
                toTerminal = "Vostok",
                trainId = "t14",
                departureTime = LocalTime.of(18, 20),
                price = 3200,
                capacity = 30,
            ),
            RouteEntity(
                routeId = "r4",
                fromCity = "MSK",
                toCity = "SPB",
                travelDate = travelDate,
                fromTerminal = "Leningradsky",
                toTerminal = "Moskovsky",
                trainId = "t16",
                departureTime = LocalTime.of(9, 40),
                price = 1900,
                capacity = 50,
            ),
        )

        routeRepository.saveAll(routes)
    }
}
