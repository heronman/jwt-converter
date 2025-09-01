package net.agl.security.extractors

import net.agl.security.AglAuthProperties
import net.agl.security.GrantedAuthoritiesExtractor
import net.agl.security.authorities.ClientIdAuthority
import net.agl.security.authorities.RoleAuthority
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.oauth2.core.ClaimAccessor

class JwtRolesExtractor(private val properties: AglAuthProperties) : GrantedAuthoritiesExtractor {
    override val order = Int.MIN_VALUE + 1

    override fun extractGrantedAuthorities(
        jwt: ClaimAccessor,
        authorities: Collection<GrantedAuthority>
    ): Collection<GrantedAuthority> {
        if (!properties.useJwtRoles && authorities.none { it is ClientIdAuthority }) {
            return listOf()
        }

        return ((jwt.getClaimAsMap("realm_access")
            ?.getOrDefault("roles", null)
            ?.takeIf { it is Collection<*> && it.all { e -> e is String } }
            ?.let { HashSet(it as Collection<String>) }
            ?: setOf()) +
                (jwt.getClaimAsMap("resource_access")
                    ?.getOrDefault("account", null)
                    ?.takeIf { it is Map<*, *> }
                    ?.let { it as Map<String, Any> }
                    ?.getOrDefault("roles", emptyList<String>())
                    ?.takeIf { it is Collection<*> && it.all { e -> e is String } }
                    ?.let { HashSet(it as Collection<String>) }
                    ?: setOf()))
            .filter { properties.jwtRolePattern?.let { p -> p.matcher(it) }?.matches() ?: true }
            .map { RoleAuthority(it) }
    }
}
