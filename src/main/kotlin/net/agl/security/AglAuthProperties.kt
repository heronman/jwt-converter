package net.agl.security

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "agl.auth")
class AglAuthProperties(
    val useJwtRoles: Boolean = true,
    val useJwtPermissions: Boolean = true,
    val jwtRolePrefix: String? = "ROLE_",
)
