package com.example.bank.persistence.repository

import com.example.bank.persistence.entity.PaymentAttemptEntity
import org.springframework.data.jpa.repository.JpaRepository

interface PaymentAttemptRepository : JpaRepository<PaymentAttemptEntity, Long>
