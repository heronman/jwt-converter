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

        return jwt.getClaimAsMap("realm_access")
            ?.getOrDefault("roles", null)
            ?.takeIf { it is Collection<*> }
            ?.let { HashSet(it as Collection<*>) }
            ?.mapNotNull {
                when {
                    it !is String -> null
                    it.isBlank() -> null
                    properties.jwtRolePrefix.isNullOrBlank() -> it
                    it.startsWith(properties.jwtRolePrefix) -> it
                    else -> null
                }
            }
            .orEmpty()
            .map(::RoleAuthority)
    }
}
