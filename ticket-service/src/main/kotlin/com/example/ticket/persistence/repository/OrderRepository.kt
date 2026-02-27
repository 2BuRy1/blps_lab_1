package com.example.ticket.persistence.repository

import com.example.ticket.persistence.entity.OrderEntity
import com.example.ticket.persistence.entity.OrderStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface OrderRepository : JpaRepository<OrderEntity, String> {

    fun existsByRouteRouteIdAndSeatAndStatusIn(
        routeId: String,
        seat: String,
        statuses: Collection<OrderStatus>,
    ): Boolean

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrderEntity o where o.orderId = :orderId")
    fun findByOrderIdForUpdate(@Param("orderId") orderId: String): OrderEntity?
}
