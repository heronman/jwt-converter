package net.agl.security.authorities

import org.springframework.security.core.GrantedAuthority
import java.util.*

class UserIdAuthority(val userId: UUID) : GrantedAuthority {
    override fun getAuthority(): String {
        return "uid:$userId"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        other as UserIdAuthority
        return userId == other.userId
    }

    override fun hashCode(): Int {
        return userId.hashCode()
    }
}
