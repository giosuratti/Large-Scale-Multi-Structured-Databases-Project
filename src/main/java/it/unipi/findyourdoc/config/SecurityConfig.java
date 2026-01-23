package it.unipi.findyourdoc.config;

import it.unipi.findyourdoc.security.JwtTokenFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Main configuration class for Spring Security.
 *
 * <p>This class configures the application to use stateless authentication via JWT, disables
 * standard web protections that are not needed for REST APIs (like CSRF), and defines the global
 * access rules for HTTP endpoints.
 *
 * <p>It leverages {@link EnableMethodSecurity} to allow fine-grained access control directly on
 * service or controller methods using annotations like {@code @PreAuthorize}.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // Essential for @PreAuthorize to work
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenFilter jwtTokenFilter;

    /**
     * Configures the Security Filter Chain.
     *
     * <p>This method defines the security policy for HTTP requests:
     *
     * <ul>
     *   <li>Disables CSRF, Form Login, and HTTP Basic authentication (API-centric approach).
     *   <li>Sets the session management policy to {@link SessionCreationPolicy#STATELESS} since JWTs
     *       are used.
     *   <li>Whitelists public endpoints such as authentication routes and Swagger UI documentation.
     *   <li>Configures a "permit all" default strategy, relying on method-level security for
     *       protection.
     *   <li>Registers the custom {@link JwtTokenFilter} before the standard Spring Security
     *       authentication filter.
     * </ul>
     *
     * @param http The {@link HttpSecurity} object to configure.
     * @return The built {@link SecurityFilterChain}.
     * @throws Exception If an error occurs during configuration.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth ->
                                auth
                                        // 1. Allow Auth endpoints (Login, Register)
                                        .requestMatchers("/api/auth/**")
                                        .permitAll()

                                        // 2. Allow Swagger UI and API Docs
                                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                                        .permitAll()

                                        // 3. ALLOW EVERYTHING ELSE BY DEFAULT
                                        // The strategy here is to open the API by default and restrict specific
                                        // methods using @PreAuthorize in the controllers/services.
                                        .anyRequest()
                                        .permitAll())
                .addFilterBefore(jwtTokenFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Provides the password encoder bean.
     *
     * <p>Uses {@link BCryptPasswordEncoder}, which is a strong hashing function specifically designed
     * for password storage.
     *
     * @return A new instance of {@link BCryptPasswordEncoder}.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}