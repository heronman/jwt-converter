package net.agl.security.authorities

import net.agl.security.Permission
import org.springframework.security.core.GrantedAuthority

open class PermissionAuthority: Permission, GrantedAuthority {

    constructor(target: String?, action: String?) : super(target, action)
    constructor(combinedPermission: String) : super(combinedPermission)

    override fun getAuthority(): String {
        return "permit:$permission"
    }

}
