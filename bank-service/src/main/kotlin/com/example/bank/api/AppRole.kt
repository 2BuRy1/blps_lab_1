package com.example.bank.api

enum class AppRole(
    val privileges: Set<AppPrivilege>,
) {
    SERVICE_BANK(
        setOf(
            AppPrivilege.BANK_PAY,
        )
    ),
    ;

    companion object {
        fun fromRaw(rawRole: String): AppRole {
            return entries.firstOrNull { it.name.equals(rawRole.trim(), ignoreCase = true) }
                ?: throw IllegalArgumentException("Unknown role '$rawRole' in XML security users file.")
        }
    }
}
