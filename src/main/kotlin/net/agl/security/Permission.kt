package net.agl.security

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import net.agl.security.PermissionChecker.matchCurrentUserPermissions

fun normalizePath(path: String?): String {
    return path?.trim('/', ' ')
        ?.replace(Regex("[/]{2,}"), "/")
        .let { if (it.isNullOrBlank()) "*" else it }
}

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonSerialize(using = PermissionSerializer::class)
@JsonDeserialize(using = PermissionDeserializer::class)
open class Permission(target: String?, action: String?) {
    val target: String = normalizePath(target)
    val action: String = if (action.isNullOrBlank()) "*" else action
    val permission: String = "${this.target}:$action"

    constructor(permission: String) : this(permission.split(':', limit = 2))

    private constructor(permission: List<String>) : this(permission[0], permission[1]) {
        if (permission.size != 2) {
            throw IllegalArgumentException("The argument must be an array of two strings: target and action")
        }
    }

    //

    fun spawn(fragment: String): Permission {
        return Permission("${target}/${fragment}", action)
    }

    val match: Boolean get() = matchCurrentUserPermissions(target, action)

    fun matchFor(vararg args: Any): Boolean = matchCurrentUserPermissions(target, action, *args)

    operator fun invoke(vararg args: Any): Boolean {
        return matchFor(*args)
    }

    //

    override fun toString(): String {
        return permission
    }

    override fun equals(other: Any?): Boolean {
        return this === other || other != null && other is Permission && permission == other.permission
    }

    override fun hashCode(): Int {
        return permission.hashCode()
    }

}

class PermissionSerializer : JsonSerializer<Permission>() {
    override fun serialize(value: Permission?, gen: JsonGenerator?, serializers: SerializerProvider?) {
        gen?.writeString(value?.permission)
    }
}

class PermissionDeserializer : JsonDeserializer<Permission>() {
    override fun deserialize(p: JsonParser?, ctxt: DeserializationContext?): Permission {
        return Permission(p?.text!!)
    }
}
