package net.agl.security.authorities

import org.springframework.security.core.GrantedAuthority

class ScopeAuthority(val scope: String) : GrantedAuthority {
    override fun getAuthority(): String {
        return "scope:$scope"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        other as ScopeAuthority
        return scope == other.scope
    }

    override fun hashCode(): Int {
        return scope.hashCode()
    }
}
