package net.agl.security.authorities

import org.springframework.security.core.GrantedAuthority

class RoleAuthority(val roleName: String) : GrantedAuthority {
    override fun getAuthority(): String {
        return "role:$roleName"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        other as RoleAuthority
        return roleName == other.roleName
    }

    override fun hashCode(): Int {
        return roleName.hashCode()
    }
}
