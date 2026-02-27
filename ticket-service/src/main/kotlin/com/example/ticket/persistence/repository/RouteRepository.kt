package com.example.ticket.persistence.repository

import com.example.ticket.persistence.entity.RouteEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import jakarta.persistence.LockModeType

interface RouteRepository : JpaRepository<RouteEntity, String>, JpaSpecificationExecutor<RouteEntity> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RouteEntity r where r.routeId = :routeId")
    fun findByRouteIdForUpdate(@Param("routeId") routeId: String): RouteEntity?
}
