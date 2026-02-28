package com.example.bank.persistence.repository

import com.example.bank.persistence.entity.PaymentAttemptEntity
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PaymentAttemptRepository : JpaRepository<PaymentAttemptEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentAttemptEntity p where p.paymentId = :paymentId")
    fun findByPaymentIdForUpdate(@Param("paymentId") paymentId: String): PaymentAttemptEntity?
}
