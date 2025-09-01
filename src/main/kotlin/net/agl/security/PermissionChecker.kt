package net.agl.security

import net.agl.security.authorities.UserIdAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.util.*

object PermissionChecker {
    private val indexPattern = Regex("^\\?(\\d+)$")

    fun matchPermissions(
        required: Permission,
        granted: Permission,
        self: String = UUID.randomUUID().toString(),
        vararg args: Any
    ): Boolean {
        return matchPermissions(required.target, required.action, listOf(granted), self, *args)
    }

    fun matchPermissions(
        required: Permission,
        granted: List<Permission>,
        self: String = UUID.randomUUID().toString(),
        vararg args: Any
    ): Boolean {
        return matchPermissions(required.target, required.action, granted, self, *args)
    }

    fun matchPermissions(
        target: String,
        action: String,
        grantedPermissions: List<Permission>,
        self: String,
        vararg args: Any
    ): Boolean {
        var argIndex = 0

        val nextArg = {
            if (argIndex > args.lastIndex)
                throw IllegalArgumentException(
                    "Target path \"$target\" has more placeholders" +
                            " than arguments given (${args.size})"
                )
            args[argIndex++].toString()
        }

        val preparedTarget = target
            .split("/")
            .joinToString("/") {
                when (it) {
                    "@self" -> self
                    "?" -> nextArg()
                    else -> indexPattern.find(it)
                        ?.let { r -> r.groupValues[1].toInt() }
                        ?.let { r -> argByIndex(r, args, target) }
                        ?: it
                }
            }

        return match(preparedTarget, action, grantedPermissions)
    }

    fun matchCurrentUserPermissions(target: String, action: String, vararg args: Any): Boolean {
        // no authentication -> fail
        val authorities = SecurityContextHolder.getContext()?.authentication?.authorities
            ?: return false

        // no User ID -> fail
        val userId = authorities
            .filterIsInstance<UserIdAuthority>()
            .firstOrNull()
            ?.userId
            ?: return false

        val userPermissions = authorities.filterIsInstance<Permission>()

        return matchPermissions(target, action, userPermissions, userId.toString(), *args)
    }

    private fun matchPaths(required: String, permitted: String): Boolean {
        val requiredPath = required.split("/")
        val permittedPath = permitted.split("/")

        for (i in 0..<requiredPath.size.coerceAtMost(permittedPath.size)) {
            // ** in the end of the permitted path means any rest of the path.
            // Example: a/b/** - any object in a/b and all its successors
            if (permittedPath[i] == "**" && i == permittedPath.lastIndex) {
                return true
            }
            // * in any position mean any path element
            // Example: a/*/b - object 'b' in any subfolder of the folder 'a'
            // In the required path, works as "some", in the permitted path, works as "all"
            if (permittedPath[i] != "*" && requiredPath[i] != "*" && requiredPath[i] != permittedPath[i]) {
                return false
            }
        }

        return requiredPath.size == permittedPath.size
    }

    private fun match(target: String, action: String, granted: Collection<Permission>): Boolean {
        return granted.any {
            matchPaths(target, it.target)
                    && (it.action == action || it.action == "*" || action == "*")
        }
    }

    private fun argByIndex(index: Int, args: Array<out Any>, path: String): String {
        if (index > args.size)
            throw IllegalArgumentException(
                "Target path \"$path\" contains an indexed placeholder" +
                        " \"?$index\" that exceeds the arguments size of ${args.size}"
            )
        if (index < 1)
            throw IllegalArgumentException(
                "Target path \"$path\" contains an indexed placeholder \"?$index\" " +
                        "while indexes must start from 1."
            )
        return args[index - 1].toString()
    }

}
