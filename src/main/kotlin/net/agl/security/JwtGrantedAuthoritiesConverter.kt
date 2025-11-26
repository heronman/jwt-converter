package net.agl.security

import org.springframework.context.ApplicationContext
import org.springframework.core.convert.converter.Converter
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority
import org.springframework.security.oauth2.jwt.Jwt
import kotlin.reflect.cast


class JwtGrantedAuthoritiesConverter(private val ctx: ApplicationContext) :
    Converter<Jwt, Collection<GrantedAuthority>>, GrantedAuthoritiesMapper {
    override fun convert(claims: Jwt): Collection<GrantedAuthority> {
        return ctx.getBeansOfType(GrantedAuthoritiesExtractor::class.java).values
            .sortedBy { it.order }
            .fold(setOf(), { acc, extractor ->
                acc + extractor.extractGrantedAuthorities(claims, acc)
            })
    }

    override fun mapAuthorities(authorities: Collection<GrantedAuthority>): Collection<GrantedAuthority> {
        return authorities
            .filterIsInstance<OidcUserAuthority>()
            .map { it.userInfo }
            .map { Jwt::class.cast(it) }
            .flatMap { convert(it) }
    }
}
