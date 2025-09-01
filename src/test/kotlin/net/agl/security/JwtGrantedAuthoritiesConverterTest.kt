package net.agl.security

import net.agl.security.authorities.*
import net.agl.security.extractors.JwtPermissionsExtractor
import net.agl.security.extractors.JwtRolesExtractor
import net.agl.security.extractors.JwtScopesExtractor
import net.agl.security.extractors.JwtUserIdExtractor
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.context.ApplicationContext
import org.springframework.security.oauth2.jwt.Jwt
import java.util.*
import java.util.regex.Pattern

class JwtGrantedAuthoritiesConverterTest {
    @Test
    fun `User ID along with all necessary authorities is successfully extracted from JWT`() {
        val converter = buildConverter(true, true)

        val (jwt, userId, _, scopes, permissions) = buildJwt(
            UUID.randomUUID(),
            "public-client",
            false,
            listOf("one", "two", "three"),
            listOf("users/**:read", "organizations:*")
        )
        val authorities = converter.convert(jwt)
        assert(authorities.filterIsInstance<UserIdAuthority>().any { it.userId == userId },
            { "Expected User ID $userId is absent" })

        authorities.filterIsInstance<ScopeAuthority>()
            .map { it.scope }.forEach { assert(scopes.contains(it), { "Unexpected scope $it found in authorities" }) }
        scopes.forEach { scope ->
            assert(authorities.filterIsInstance<ScopeAuthority>().any { s -> s.scope == scope },
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
            listOf("users/**:read", "organizations:*")
        )
        converter.convert(jwt)
        verify(jwt, never()).getClaimAsStringList("permissions")
    }

    @Test
    fun `Permissions are extracted from JWT when use-jwt-permissions turned off but the principal is a service-client`() {
        val converter = buildConverter(true, false)

        val (jwt, userId, _, _, permissions) = buildJwt(
            UUID.randomUUID(),
            "service-client",
            true,
            listOf("one", "two", "three"),
            listOf("users/**:read", "organizations:*")
        )
        val authorities = converter.convert(jwt)
        verify(jwt, times(1)).getClaimAsStringList("permissions")

        assert(authorities.filterIsInstance<ClientIdAuthority>().any { it.clientId == userId },
            { "Expected Client ID $userId is absent" })

        authorities.filterIsInstance<PermissionAuthority>()
            .map { it.permission }
            .forEach { assert(permissions.contains(it), { "Unexpected permission $it found in authorities" }) }
        permissions.forEach { perm ->
            assert(authorities.filterIsInstance<PermissionAuthority>().any { p -> p.permission == perm },
                { "Expected permission $perm is absent" })
        }
    }

    @Test
    fun `All roles from the realm and account scopes must be read`() {
        val converter = buildConverter(true, false)
        val accountRoles = listOf("one", "two", "three")
        val realmRoles = listOf("four", "five", "six")

        val (jwt, userId, _, scopes, permissions) = buildJwt(
            UUID.randomUUID(),
            "public-client",
            false,
            null, null,
            realmRoles, accountRoles
        )
        val authorities = converter.convert(jwt)

        val roles = accountRoles + realmRoles
        val parsedRoles = authorities.filterIsInstance<RoleAuthority>().map { it.roleName }
        assert(parsedRoles.containsAll(roles), { "Not all roles parsed" })
        assert(roles.containsAll(parsedRoles), { "Found role authorities that aren't presented in the token" })
    }

    @Test
    fun `Roles from the token must be filtered by the regular expression if it is present`() {
        val converter = buildConverter(true, false, Pattern.compile("^ROLE_.*"))
        val accountRoles = listOf("ROLE_one", "two", "ROLE_USER")
        val realmRoles = listOf("four", "ROLE__five", "role_six")

        val (jwt, userId, _, scopes, permissions) = buildJwt(
            UUID.randomUUID(),
            "public-client",
            false,
            null, null,
            realmRoles, accountRoles
        )
        val authorities = converter.convert(jwt)

        val roles = (accountRoles + realmRoles).filter { it.startsWith("ROLE_") }
        val parsedRoles = authorities.filterIsInstance<RoleAuthority>().map { it.roleName }
        assert(parsedRoles.containsAll(roles), { "Not all roles parsed" })
        assert(roles.containsAll(parsedRoles), { "Found role authorities that aren't presented in the token or must be filtered out" })
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
        if (!permissions.isNullOrEmpty()) {
            builder.claim("permissions", permissions)
        }
        if (!realmRoles.isNullOrEmpty()) {
            builder.claim("realm_access", mapOf(Pair("roles", realmRoles)))
        }
        if (!accountRoles.isNullOrEmpty()) {
            builder.claim("resource_access", mapOf(Pair("account", mapOf(Pair("roles", accountRoles)))))
        }

        return JwtWithOptions(
            spy(builder.build()),
            userId,
            clientId,
            scopes ?: listOf(),
            permissions ?: listOf()
        )
    }

    fun buildConverter(
        useJwtRoles: Boolean,
        useJwtPermissions: Boolean,
        rolePattern: Pattern? = null
    ): JwtGrantedAuthoritiesConverter {
        val properties = AglAuthProperties(useJwtRoles, useJwtPermissions, rolePattern)
        val ctx = mock<ApplicationContext>()
        `when`(ctx.getBeansOfType(GrantedAuthoritiesExtractor::class.java))
            .thenReturn(
                mapOf(
                    Pair("jwtRolesExtractor", JwtRolesExtractor(properties)),
                    Pair("jwtPermissionsExtractor", JwtPermissionsExtractor(properties)),
                    Pair("jwtScopesExtractor", JwtScopesExtractor()),
                    Pair("jwtUserIdExtractor", JwtUserIdExtractor()),
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