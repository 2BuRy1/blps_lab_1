package com.example.bank

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class BankServiceApplication

fun main(args: Array<String>) {
    runApplication<BankServiceApplication>(*args)
}
