package com.example.ticket.bpm

import com.example.ticket.security.AppRole
import com.example.ticket.security.XmlUser
import com.example.ticket.security.XmlUserStore
import org.camunda.bpm.engine.AuthorizationService
import org.camunda.bpm.engine.IdentityService
import org.camunda.bpm.engine.authorization.Authorization
import org.camunda.bpm.engine.authorization.Permissions
import org.camunda.bpm.engine.authorization.Resources
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component

@Component
class CamundaIdentitySyncService(
    private val identityService: IdentityService,
    private val authorizationService: AuthorizationService,
    private val xmlUserStore: XmlUserStore,
) : CommandLineRunner {

    override fun run(vararg args: String?) {
        syncAll()
    }

    fun syncAll() {
        AppRole.entries.forEach { ensureGroup(it) }
        xmlUserStore.findAll().forEach { syncUser(it) }
        resetGroupAuthorizations()
        log.info("Camunda identity synchronization completed")
    }

    fun syncUser(user: XmlUser) {
        syncUser(user, camundaPassword = user.password.trim().takeIf { it.startsWith("{noop}") }?.let(::stripDelegatingPrefix))
    }

    fun syncUser(user: XmlUser, camundaPassword: String?) {
        val username = user.username.trim()
        if (username.isBlank()) return
        if (!username.matches(CAMUNDA_ID_REGEX)) {
            log.warn("Skipping Camunda user synchronization for unsupported user id '{}'", username)
            return
        }

        val camundaUser = identityService.createUserQuery().userId(username).singleResult()
            ?: identityService.newUser(username).also {
                it.firstName = username
                identityService.saveUser(it)
            }

        camundaUser.firstName = username
        camundaPassword?.let { camundaUser.password = it }
        identityService.saveUser(camundaUser)

        user.roles.split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { AppRole.fromRaw(it) }
            .forEach { role ->
                ensureGroup(role)
                val groupId = role.camundaGroupId()
                if (identityService.createGroupQuery().groupMember(username).groupId(groupId).count() == 0L) {
                    identityService.createMembership(username, groupId)
                }
            }
    }

    private fun ensureGroup(role: AppRole) {
        val groupId = role.camundaGroupId()
        if (identityService.createGroupQuery().groupId(groupId).singleResult() != null) {
            return
        }
        val group = identityService.newGroup(groupId)
        group.name = role.name
        group.type = "WORKFLOW"
        identityService.saveGroup(group)
    }

    private fun resetGroupAuthorizations() {
        AppRole.entries.forEach { role ->
            authorizationService.createAuthorizationQuery()
                .groupIdIn(role.camundaGroupId())
                .list()
                .forEach { authorizationService.deleteAuthorization(it.id) }
        }

        grantClient()
        grantOperator()
        grantAdmin()
    }

    private fun grantClient() {
        grantApplication(AppRole.CLIENT, "tasklist")
        grantProcessStart(AppRole.CLIENT, CamundaOrderProcess.PROCESS_CLIENT_KEY)
        grantTaskWork(AppRole.CLIENT)
        grantProcessInstanceRead(AppRole.CLIENT)
        grantFilterRead(AppRole.CLIENT)
    }

    private fun grantOperator() {
        grantApplication(AppRole.OPERATOR, "tasklist")
        grantProcessStart(AppRole.OPERATOR, CamundaOrderProcess.PROCESS_SERVICE_OPERATIONS_KEY)
        grantTaskWork(AppRole.OPERATOR)
        grantProcessInstanceRead(AppRole.OPERATOR)
        grantFilterRead(AppRole.OPERATOR)
    }

    private fun grantAdmin() {
        Resources.entries.forEach { resource ->
            createGrant(AppRole.ADMIN, resource.resourceType(), "*", Permissions.ALL)
        }
    }

    private fun grantApplication(role: AppRole, application: String) {
        createGrant(role, Resources.APPLICATION.resourceType(), application, Permissions.ACCESS)
    }

    private fun grantProcessStart(role: AppRole, processDefinitionKey: String) {
        createGrant(
            role,
            Resources.PROCESS_DEFINITION.resourceType(),
            processDefinitionKey,
            Permissions.READ,
            Permissions.READ_INSTANCE,
            Permissions.CREATE_INSTANCE,
        )
    }

    private fun grantTaskWork(role: AppRole) {
        createGrant(role, Resources.TASK.resourceType(), "*", Permissions.READ, Permissions.UPDATE, Permissions.TASK_WORK)
    }

    private fun grantProcessInstanceRead(role: AppRole) {
        createGrant(role, Resources.PROCESS_INSTANCE.resourceType(), "*", Permissions.CREATE, Permissions.READ, Permissions.UPDATE)
    }

    private fun grantFilterRead(role: AppRole) {
        createGrant(role, Resources.FILTER.resourceType(), "*", Permissions.READ)
    }

    private fun createGrant(role: AppRole, resourceType: Int, resourceId: String, vararg permissions: Permissions) {
        val authorization = authorizationService.createNewAuthorization(Authorization.AUTH_TYPE_GRANT)
        authorization.groupId = role.camundaGroupId()
        authorization.resourceType = resourceType
        authorization.resourceId = resourceId
        permissions.forEach { authorization.addPermission(it) }
        authorizationService.saveAuthorization(authorization)
    }

    private fun stripDelegatingPrefix(password: String): String {
        if (!password.startsWith("{")) return password
        val end = password.indexOf('}')
        if (end <= 0) return password
        return password.substring(end + 1)
    }

    companion object {
        private val log = LoggerFactory.getLogger(CamundaIdentitySyncService::class.java)
        private val CAMUNDA_ID_REGEX = Regex("^[A-Za-z0-9]+$")
    }
}

private fun AppRole.camundaGroupId(): String = name.lowercase().filter { it.isLetterOrDigit() }
