package net.agl.security.extractors

import net.agl.security.AglAuthProperties
import net.agl.security.GrantedAuthoritiesExtractor
import net.agl.security.Permission.Companion.normalizePath
import net.agl.security.authorities.ClientIdAuthority
import net.agl.security.authorities.PermissionAuthority
import net.agl.security.authorities.UserIdAuthority
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.oauth2.core.ClaimAccessor

class JwtPermissionsExtractor(val properties: AglAuthProperties) : GrantedAuthoritiesExtractor {
    override val order = Int.MIN_VALUE + 1

    override fun extractGrantedAuthorities(
        jwt: ClaimAccessor,
        authorities: Collection<GrantedAuthority>
    ): Collection<GrantedAuthority> {
        if (!properties.useJwtPermissions && authorities.none { it is ClientIdAuthority }) {
            return listOf()
        }
        val uid = authorities.filterIsInstance<UserIdAuthority>().find { true }?.userId?.toString()
        return jwt.getClaimAsStringList("permissions")
            ?.map {
                val (target, action) = it.split(":", limit = 2)
                normalizePath(target)
                    .split("/")
                    .joinToString("/") { if (it == "@self") uid ?: it else it }
                    .let { PermissionAuthority(it, action) }
            }
            .orEmpty()
    }
}