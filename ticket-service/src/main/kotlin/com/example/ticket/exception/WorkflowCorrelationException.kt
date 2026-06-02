package com.example.ticket.exception

class WorkflowCorrelationException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
