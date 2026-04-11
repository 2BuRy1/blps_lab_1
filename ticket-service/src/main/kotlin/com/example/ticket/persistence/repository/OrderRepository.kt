package com.example.ticket.persistence.repository

import com.example.ticket.persistence.entity.OrderEntity
import com.example.ticket.persistence.entity.OrderStatus
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface OrderRepository : JpaRepository<OrderEntity, String> {

    fun existsByRouteRouteIdAndSeatAndStatusIn(
        routeId: String,
        seat: String,
        statuses: Collection<OrderStatus>,
    ): Boolean

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrderEntity o where o.orderId = :orderId")
    fun findByOrderIdForUpdate(@Param("orderId") orderId: String): OrderEntity?

    @Query(
        """
        select o.orderId
        from OrderEntity o
        where o.status = :status
          and o.statusUpdatedAt is not null
          and o.statusUpdatedAt <= :cutoff
        order by o.statusUpdatedAt asc, o.orderId asc
        """
    )
    fun findExpiredOrderIds(
        @Param("status") status: OrderStatus,
        @Param("cutoff") cutoff: Instant,
        pageable: Pageable,
    ): List<String>
}
