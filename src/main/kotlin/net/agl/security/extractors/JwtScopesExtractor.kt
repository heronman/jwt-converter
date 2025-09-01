package net.agl.security.extractors

import net.agl.security.GrantedAuthoritiesExtractor
import net.agl.security.authorities.ScopeAuthority
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.oauth2.core.ClaimAccessor

class JwtScopesExtractor : GrantedAuthoritiesExtractor {
    override val order: Int = Int.MIN_VALUE + 1

    override fun extractGrantedAuthorities(
        jwt: ClaimAccessor,
        authorities: Collection<GrantedAuthority>
    ): Collection<GrantedAuthority> {
        return jwt.getClaimAsString("scope")
            ?.split(' ')
            ?.map { ScopeAuthority(it) }
            .orEmpty()
    }
}
