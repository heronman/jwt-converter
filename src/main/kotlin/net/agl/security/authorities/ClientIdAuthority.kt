package net.agl.security.authorities

import org.springframework.security.core.GrantedAuthority
import java.util.*

class ClientIdAuthority(val clientId: UUID) : GrantedAuthority {
    override fun getAuthority(): String {
        return "cid:$clientId"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        other as ClientIdAuthority
        return clientId == other.clientId
    }

    override fun hashCode(): Int {
        return clientId.hashCode()
    }
}
