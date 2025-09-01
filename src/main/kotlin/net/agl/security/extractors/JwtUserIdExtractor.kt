package net.agl.security.extractors

import net.agl.security.GrantedAuthoritiesExtractor
import net.agl.security.authorities.ClientIdAuthority
import net.agl.security.authorities.UserIdAuthority
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.oauth2.core.ClaimAccessor
import java.util.*

private val log: Logger = LoggerFactory.getLogger(JwtUserIdExtractor::class.java)

class JwtUserIdExtractor : GrantedAuthoritiesExtractor {
    override val order: Int = Int.MIN_VALUE

    override fun extractGrantedAuthorities(
        jwt: ClaimAccessor,
        authorities: Collection<GrantedAuthority>
    ): Collection<GrantedAuthority> {
        val clientId = jwt.getClaimAsString("client_id") ?: jwt.getClaimAsString("clientId")
        val userName = jwt.getClaimAsString("preferred_username")

        return jwt.getClaimAsString("sub")
            ?.let {
                if (it.isNotBlank()) {
                    try {
                        UUID.fromString(it)
                    } catch (e: Exception) {
                        log.error("Failed to parse 'sub' claim from token", e)
                        null
                    }
                } else null
            }
            ?.let {
                if (userName == "service-account-$clientId") {
                    MDC.put("clientId", it.toString())
                    return listOf(ClientIdAuthority(it))
                } else {
                    MDC.put("userId", it.toString())
                    return listOf(UserIdAuthority(it))
                }
            } ?: listOf()
    }
}