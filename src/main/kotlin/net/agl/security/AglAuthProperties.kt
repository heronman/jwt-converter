package net.agl.security

import org.springframework.boot.context.properties.ConfigurationProperties
import java.util.regex.Pattern

@ConfigurationProperties(prefix = "agl.auth")
class AglAuthProperties(
    val useJwtRoles: Boolean = true,
    val useJwtPermissions: Boolean = true,
    val jwtRolePattern: Pattern? = null
)
