package net.agl.security

import net.agl.security.authorities.*
import net.agl.security.extractors.JwtPermissionsExtractor
import net.agl.security.extractors.JwtRolesExtractor
import net.agl.security.extractors.JwtScopesExtractor
import net.agl.security.extractors.JwtUserIdExtractor
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import org.springframework.security.oauth2.jwt.Jwt
import java.util.*

class JwtGrantedAuthoritiesConverterTest {
    @Test
    fun `User ID along with all necessary authorities is successfully extracted from JWT`() {
        val converter = buildConverter(true, true)

        val (jwt, userId, _, scopes, permissions) = buildJwt(
            UUID.randomUUID(),
            "public-client",
            false,
            listOf("one", "two", "three"),
            listOf("users/all:read", "perm:organizations:*")
        )
        val authorities = converter.convert(jwt)
        assert(
            authorities.filterIsInstance<UserIdAuthority>().any { it.userId == userId },
            { "Expected User ID $userId is absent" })

        authorities.filterIsInstance<ScopeAuthority>()
            .map { it.scope }.forEach { assert(scopes.contains(it), { "Unexpected scope $it found in authorities" }) }
        scopes.forEach { scope ->
            assert(
                authorities.filterIsInstance<ScopeAuthority>().any { s -> s.scope == scope },
                { "Expected scope $scope is absent" })
        }

        authorities.filterIsInstance<PermissionAuthority>()
            .map { it.permission }
            .forEach { assert(permissions.contains(it), { "Unexpected permissions $it found in authorities" }) }
        permissions.forEach { perm ->
            assert(
                authorities.filterIsInstance<PermissionAuthority>().any { a -> a.permission == perm },
                { "Expected permissions authority $perm is absent" }
            )
        }
    }

    @Test
    fun `Permissions aren't extracted from JWT when use-jwt-permissions is turned off`() {
        val converter = buildConverter(true, false)

        val (jwt, _, _, _, _) = buildJwt(
            UUID.randomUUID(),
            "public-client",
            false,
            listOf("one", "two", "three"),
            listOf("users/all:read", "perm:organizations:*")
        )
        val authorities = converter.convert(jwt)
        assert(
            authorities.filterIsInstance<PermissionAuthority>().isEmpty(),
            { "Permissions must not be extracted when use-jwt-permissions is turned off" })
    }

    @Test
    fun `Permissions are extracted from JWT when use-jwt-permissions turned off but the principal is a service-client`() {
        val converter = buildConverter(true, false)

        val (jwt, userId, _, _, permissions) = buildJwt(
            UUID.randomUUID(),
            "service-client",
            true,
            listOf("one", "two", "three"),
            listOf("users/all:read", "perm:organizations:*")
        )
        val authorities = converter.convert(jwt)

        assert(
            authorities.filterIsInstance<ClientIdAuthority>().any { it.clientId == userId },
            { "Expected Client ID $userId is absent" })

        authorities.filterIsInstance<PermissionAuthority>()
            .map { it.permission }
            .forEach { assert(permissions.contains(it), { "Unexpected permission $it found in authorities" }) }
        permissions.forEach { perm ->
            assert(
                authorities.filterIsInstance<PermissionAuthority>().any { p -> p.permission == perm },
                { "Expected permission $perm is absent" })
        }
    }

    @Test
    fun `All roles from the realm_access must be read when the prefix is not set`() {
        val converter = buildConverter(true, false, null)
        val realmRoles = listOf("four", "five", "six")

        val (jwt, _, _, _, _) = buildJwt(
            UUID.randomUUID(),
            "public-client",
            false,
            null, null,
            realmRoles
        )
        val authorities = converter.convert(jwt)

        val parsedRoles = authorities.filterIsInstance<RoleAuthority>().map { it.roleName }
        assert(parsedRoles.containsAll(realmRoles), { "Not all roles parsed" })
        assert(realmRoles.containsAll(parsedRoles), { "Found role authorities that aren't presented in the token" })
    }

    @Test
    fun `Roles from resource_access are not read`() {
        val converter = buildConverter(true, false, null)

        val (jwt, _, _, _, _) = buildJwt(
            UUID.randomUUID(),
            "public-client",
            false,
            null, null,
            null, listOf("one", "two")
        )
        val authorities = converter.convert(jwt)

        assert(authorities.filterIsInstance<RoleAuthority>().isEmpty(), { "resource_access roles must be ignored" })
    }

    @Test
    fun `Roles from the token must be filtered by the prefix`() {
        val converter = buildConverter(true, false, "ROLE_")
        val realmRoles = listOf("ROLE_one", "two", "ROLE_USER", "four", "ROLE__five", "role_six", " ")

        val (jwt, _, _, _, _) = buildJwt(
            UUID.randomUUID(),
            "public-client",
            false,
            null, null,
            realmRoles
        )
        val authorities = converter.convert(jwt)

        val roles = realmRoles.filter { it.startsWith("ROLE_") }
        val parsedRoles = authorities.filterIsInstance<RoleAuthority>().map { it.roleName }
        assert(parsedRoles.containsAll(roles), { "Not all roles parsed" })
        assert(
            roles.containsAll(parsedRoles),
            { "Found role authorities that aren't presented in the token or must be filtered out" })
    }

    @Test
    fun `Only realm_access entries in the permission format become permissions`() {
        val converter = buildConverter(false, true, null)

        val (jwt, _, _, _, _) = buildJwt(
            UUID.randomUUID(),
            "public-client",
            false,
            null, null,
            listOf("ROLE_USER", "plain", "users/**:read", "orders:read", "perm:items/all:write", "files/a/b:*")
        )
        val authorities = converter.convert(jwt)

        val parsed = authorities.filterIsInstance<PermissionAuthority>().map { it.permission }.toSet()
        assert(
            parsed == setOf("orders:read", "items/all:write", "files/a/b:*"),
            { "Unexpected permissions: $parsed" })
    }

    @Test
    fun `A warning is logged only for permission-like entries that fail validation`() {
        val converter = buildConverter(false, true, null)
        val logger = LoggerFactory.getLogger(JwtPermissionsExtractor::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().also { it.start() }
        logger.addAppender(appender)
        try {
            val (jwt, _, _, _, _) = buildJwt(
                UUID.randomUUID(),
                "public-client",
                false,
                null, null,
                listOf(
                    "ROLE_USER", "offline_access",              // plain roles: silent
                    "orders:read", "perm:items/all:write",      // valid: silent
                    "users/**:read", "orders:", ":read", "a:b:c" // invalid: warn
                )
            )
            converter.convert(jwt)
        } finally {
            logger.detachAppender(appender)
        }

        val warnings = appender.list.filter { it.level == Level.WARN }.map { it.formattedMessage }
        assert(warnings.size == 4, { "Expected 4 warnings, got ${warnings.size}: $warnings" })
        listOf("users/**:read", "orders:", ":read", "a:b:c").forEach { bad ->
            assert(warnings.any { it.contains("[$bad]") }, { "No warning for [$bad]: $warnings" })
        }
    }

    @Test
    fun `The @self path element is replaced with the User ID`() {
        val converter = buildConverter(true, true)
        val userId = UUID.randomUUID()

        val (jwt, _, _, _, _) = buildJwt(userId, "public-client", false, null, listOf("users/@self:read"))
        val authorities = converter.convert(jwt)

        assert(
            authorities.filterIsInstance<PermissionAuthority>().any { it.permission == "users/$userId:read" },
            { "@self wasn't replaced with the User ID" })
    }

    //

    fun buildJwt(
        userId: UUID?,
        clientId: String?,
        isServiceAccount: Boolean,
        scopes: Collection<String>?,
        permissions: Collection<String>?,
        realmRoles: Collection<String>? = null,
        accountRoles: Collection<String>? = null
    ): JwtWithOptions {
        val builder = Jwt.withTokenValue("test-token")
            .header("alg", "RS256")

        if (userId != null) {
            builder.claim("sub", userId.toString())
        }
        if (!clientId.isNullOrEmpty()) {
            builder.claim("client_id", clientId)
        }
        if (!clientId.isNullOrEmpty() && isServiceAccount) {
            builder.claim("preferred_username", "service-account-${clientId}")
        }
        if (!scopes.isNullOrEmpty()) {
            builder.claim("scope", scopes.joinToString(separator = " "))
        }
        val realmAccessRoles = permissions.orEmpty() + realmRoles.orEmpty()
        if (realmAccessRoles.isNotEmpty()) {
            builder.claim("realm_access", mapOf(Pair("roles", realmAccessRoles)))
        }
        if (!accountRoles.isNullOrEmpty()) {
            builder.claim("resource_access", mapOf(Pair("account", mapOf(Pair("roles", accountRoles)))))
        }

        return JwtWithOptions(
            spy(builder.build()),
            userId,
            clientId,
            scopes ?: listOf(),
            permissions?.map { it.removePrefix("perm:") } ?: listOf()
        )
    }

    fun buildConverter(
        useJwtRoles: Boolean,
        useJwtPermissions: Boolean,
        rolePrefix: String? = "ROLE_"
    ): JwtGrantedAuthoritiesConverter {
        val properties = AglAuthProperties(useJwtRoles, useJwtPermissions, rolePrefix)
        val ctx = mock<ApplicationContext>()
        `when`(ctx.getBeansOfType(GrantedAuthoritiesExtractor::class.java))
            .thenReturn(
                mapOf(
                    "jwtRolesExtractor" to JwtRolesExtractor(properties),
                    "jwtPermissionsExtractor" to JwtPermissionsExtractor(properties),
                    "jwtScopesExtractor" to JwtScopesExtractor(),
                    "jwtUserIdExtractor" to JwtUserIdExtractor(),
                )
            )
        return JwtGrantedAuthoritiesConverter(ctx)
    }

    data class JwtWithOptions(
        val jwt: Jwt,
        val userId: UUID?,
        val clientId: String?,
        val scopes: Collection<String>,
        val permissions: Collection<String>
    )

}