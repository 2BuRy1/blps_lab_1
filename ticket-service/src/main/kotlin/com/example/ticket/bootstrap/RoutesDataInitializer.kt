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
        val seedRoutes = listOf(
            SeedRoute("r1001", "SPB", "MSK", LocalDate.of(2026, 4, 1), "Moskovsky", "Leningradsky", "t10", LocalTime.of(6, 45), 1490, 120),
            SeedRoute("r1002", "SPB", "MSK", LocalDate.of(2026, 4, 1), "Moskovsky", "Leningradsky", "t12", LocalTime.of(9, 10), 1790, 90),
            SeedRoute("r1003", "SPB", "MSK", LocalDate.of(2026, 4, 1), "Ladozhsky", "Vostok", "t14", LocalTime.of(13, 30), 2390, 80),
            SeedRoute("r1004", "SPB", "MSK", LocalDate.of(2026, 4, 1), "Moskovsky", "Leningradsky", "t16", LocalTime.of(18, 5), 2690, 70),
            SeedRoute("r1005", "SPB", "MSK", LocalDate.of(2026, 4, 2), "Moskovsky", "Leningradsky", "t18", LocalTime.of(7, 20), 1550, 120),
            SeedRoute("r1006", "SPB", "MSK", LocalDate.of(2026, 4, 2), "Ladozhsky", "Vostok", "t20", LocalTime.of(11, 40), 1990, 90),
            SeedRoute("r1007", "SPB", "MSK", LocalDate.of(2026, 4, 2), "Moskovsky", "Leningradsky", "t22", LocalTime.of(16, 25), 2490, 70),
            SeedRoute("r1008", "SPB", "MSK", LocalDate.of(2026, 4, 2), "Moskovsky", "Leningradsky", "t24", LocalTime.of(20, 10), 2890, 60),
            SeedRoute("r1009", "SPB", "MSK", LocalDate.of(2026, 4, 3), "Moskovsky", "Leningradsky", "t26", LocalTime.of(6, 55), 1590, 120),
            SeedRoute("r1010", "SPB", "MSK", LocalDate.of(2026, 4, 3), "Ladozhsky", "Vostok", "t28", LocalTime.of(10, 20), 2090, 90),
            SeedRoute("r1011", "SPB", "MSK", LocalDate.of(2026, 4, 3), "Moskovsky", "Leningradsky", "t30", LocalTime.of(15, 40), 2590, 75),
            SeedRoute("r1012", "SPB", "MSK", LocalDate.of(2026, 4, 3), "Moskovsky", "Leningradsky", "t32", LocalTime.of(19, 30), 2990, 60),
            SeedRoute("r2001", "MSK", "SPB", LocalDate.of(2026, 4, 1), "Leningradsky", "Moskovsky", "t11", LocalTime.of(7, 5), 1510, 120),
            SeedRoute("r2002", "MSK", "SPB", LocalDate.of(2026, 4, 1), "Vostok", "Ladozhsky", "t13", LocalTime.of(10, 35), 1810, 90),
            SeedRoute("r2003", "MSK", "SPB", LocalDate.of(2026, 4, 1), "Leningradsky", "Moskovsky", "t15", LocalTime.of(14, 50), 2410, 80),
            SeedRoute("r2004", "MSK", "SPB", LocalDate.of(2026, 4, 1), "Leningradsky", "Moskovsky", "t17", LocalTime.of(19, 15), 2710, 70),
            SeedRoute("r2005", "MSK", "SPB", LocalDate.of(2026, 4, 2), "Leningradsky", "Moskovsky", "t19", LocalTime.of(6, 35), 1570, 120),
            SeedRoute("r2006", "MSK", "SPB", LocalDate.of(2026, 4, 2), "Vostok", "Ladozhsky", "t21", LocalTime.of(11, 10), 2010, 90),
            SeedRoute("r2007", "MSK", "SPB", LocalDate.of(2026, 4, 2), "Leningradsky", "Moskovsky", "t23", LocalTime.of(16, 0), 2510, 70),
            SeedRoute("r2008", "MSK", "SPB", LocalDate.of(2026, 4, 2), "Leningradsky", "Moskovsky", "t25", LocalTime.of(20, 25), 2910, 60),
            SeedRoute("r2009", "MSK", "SPB", LocalDate.of(2026, 4, 3), "Leningradsky", "Moskovsky", "t27", LocalTime.of(7, 15), 1610, 120),
            SeedRoute("r2010", "MSK", "SPB", LocalDate.of(2026, 4, 3), "Vostok", "Ladozhsky", "t29", LocalTime.of(10, 55), 2110, 90),
            SeedRoute("r2011", "MSK", "SPB", LocalDate.of(2026, 4, 3), "Leningradsky", "Moskovsky", "t31", LocalTime.of(15, 30), 2610, 75),
            SeedRoute("r2012", "MSK", "SPB", LocalDate.of(2026, 4, 3), "Leningradsky", "Moskovsky", "t33", LocalTime.of(19, 50), 3010, 60),
        )

        val persisted = seedRoutes.map { seed ->
            val route = routeRepository.findById(seed.routeId).orElse(
                RouteEntity(
                    routeId = seed.routeId,
                    fromCity = seed.fromCity,
                    toCity = seed.toCity,
                    travelDate = seed.travelDate,
                    fromTerminal = seed.fromTerminal,
                    toTerminal = seed.toTerminal,
                    trainId = seed.trainId,
                    departureTime = seed.departureTime,
                    price = seed.price,
                    capacity = seed.capacity,
                )
            )

            route.fromCity = seed.fromCity
            route.toCity = seed.toCity
            route.travelDate = seed.travelDate
            route.fromTerminal = seed.fromTerminal
            route.toTerminal = seed.toTerminal
            route.trainId = seed.trainId
            route.departureTime = seed.departureTime
            route.price = seed.price
            route.capacity = seed.capacity
            route.reservedSeats = route.reservedSeats.coerceIn(0, route.capacity)
            route.freeSeats = (route.capacity - route.reservedSeats).coerceAtLeast(0)
            route
        }

        routeRepository.saveAll(persisted)
    }

    private data class SeedRoute(
        val routeId: String,
        val fromCity: String,
        val toCity: String,
        val travelDate: LocalDate,
        val fromTerminal: String?,
        val toTerminal: String?,
        val trainId: String,
        val departureTime: LocalTime,
        val price: Int,
        val capacity: Int,
    )
}
