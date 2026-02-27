package com.example.ticket.persistence.repository

import com.example.ticket.persistence.entity.TicketEntity
import org.springframework.data.jpa.repository.JpaRepository

interface TicketRepository : JpaRepository<TicketEntity, String>
