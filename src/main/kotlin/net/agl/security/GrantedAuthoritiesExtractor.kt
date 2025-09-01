package net.agl.security

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.oauth2.core.ClaimAccessor

interface GrantedAuthoritiesExtractor {
    val order: Int
        get() = 0

    fun extractGrantedAuthorities(
        jwt: ClaimAccessor,
        authorities: Collection<GrantedAuthority>
    ): Collection<GrantedAuthority>
}
