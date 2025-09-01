package net.agl.security.authorities

import org.springframework.security.core.GrantedAuthority
import java.util.*

class SessionIdAuthority(val sessionId: UUID) : GrantedAuthority {
    override fun getAuthority(): String {
        return "sid:$sessionId"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        other as SessionIdAuthority
        return sessionId == other.sessionId
    }

    override fun hashCode(): Int {
        return sessionId.hashCode()
    }
}
