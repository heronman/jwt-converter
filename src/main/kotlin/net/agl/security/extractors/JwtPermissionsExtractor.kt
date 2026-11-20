package net.agl.security.extractors

import net.agl.security.AglAuthProperties
import net.agl.security.GrantedAuthoritiesExtractor
import net.agl.security.Permission.Companion.normalizePath
import net.agl.security.authorities.ClientIdAuthority
import net.agl.security.authorities.PermissionAuthority
import net.agl.security.authorities.UserIdAuthority
import org.slf4j.LoggerFactory
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

        return jwt.getClaimAsMap("realm_access")
            ?.getOrDefault("roles", null)
            ?.takeIf { it is Collection<*> }
            ?.let { HashSet(it as Collection<*>) }
            ?.mapNotNull {
                if (it !is String || !it.contains(":")) return@mapNotNull null
                val match = regex.find(it) ?: run {
                    log.warn("A user has invalid permission record at realm_access.roles: [${it.replace("\n", "\\n")}]")
                    return@mapNotNull null
                }
                // additional check for groups 2 and 3 is not needed as the pattern already requires them
                val target = match.groupValues[2]
                val action = match.groupValues[3]
                normalizePath(target)
                    .split("/")
                    .joinToString("/") { p -> if (p == "@self") uid ?: p else p }
                    .let { path -> PermissionAuthority(path, action) }
            }.orEmpty()
    }

    companion object {
        private val regex = Regex($$"^(perm:)?([\\w@/.]+|\\*{1,2}):([\\w@.]+|\\*{1,2})$")
        private val log = LoggerFactory.getLogger(JwtPermissionsExtractor::class.java)
    }
}