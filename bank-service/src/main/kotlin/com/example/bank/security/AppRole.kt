package com.example.bank.security

enum class AppRole(
    val privileges: Set<AppPrivilege>,
) {
    CLIENT(
        setOf(
            AppPrivilege.ROUTE_VIEW,
            AppPrivilege.ORDER_CREATE,
            AppPrivilege.ORDER_PAY,
            AppPrivilege.TICKET_VIEW,
        )
    ),
    OPERATOR(
        setOf(
            AppPrivilege.ROUTE_VIEW,
            AppPrivilege.ORDER_MANAGE,
            AppPrivilege.TICKET_VIEW,
        )
    ),
    SERVICE_BANK(
        setOf(
            AppPrivilege.ORDER_PAY,
        )
    ),
    ADMIN(AppPrivilege.entries.toSet()),
    ;

    companion object {
        fun fromRaw(rawRole: String): AppRole {
            return entries.firstOrNull { it.name.equals(rawRole.trim(), ignoreCase = true) }
                ?: throw IllegalArgumentException("Unknown role '$rawRole' in XML security users file.")
        }
    }
}
