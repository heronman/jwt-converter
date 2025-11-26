package net.agl.security

import net.agl.security.extractors.JwtPermissionsExtractor
import net.agl.security.extractors.JwtRolesExtractor
import net.agl.security.extractors.JwtScopesExtractor
import net.agl.security.extractors.JwtUserIdExtractor
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import java.util.regex.Pattern

@Configuration
@EnableConfigurationProperties(AglAuthProperties::class)
class OAuth2JwtConfig {
    @Bean
    fun jwtPermissionsExtractor(properties: AglAuthProperties): GrantedAuthoritiesExtractor = JwtPermissionsExtractor(properties)

    @Bean
    fun jwtRolesExtractor(properties: AglAuthProperties): GrantedAuthoritiesExtractor = JwtRolesExtractor(properties)

    @Bean
    fun jwtScopesExtractor(): GrantedAuthoritiesExtractor = JwtScopesExtractor()

    @Bean
    fun jwtUserIdExtractor(): GrantedAuthoritiesExtractor = JwtUserIdExtractor()

    @Bean
    fun jwtAuthenticationConverter(ctx: ApplicationContext): Converter<Jwt, AbstractAuthenticationToken> =
        object : JwtAuthenticationConverter() {init {
            setJwtGrantedAuthoritiesConverter(JwtGrantedAuthoritiesConverter(ctx))
        } }

    @Bean
    @ConfigurationPropertiesBinding
    fun patternConverter(): Converter<String, Pattern> = Converter<String, Pattern> { Pattern.compile(it) }
}
