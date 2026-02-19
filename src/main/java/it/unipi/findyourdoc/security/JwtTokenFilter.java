package it.unipi.findyourdoc.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Custom filter executed once per request to validate JWT tokens.
 * Intercepts incoming HTTP requests, extracts the token, validates it via {@link JwtTokenProvider},
 * and populates the {@link SecurityContextHolder} for the current security context.
 */
@Component
@RequiredArgsConstructor
public class JwtTokenFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Core filter logic to resolve, validate, and process the JWT token.
     *
     * @param request     The incoming HTTP request.
     * @param response    The outgoing HTTP response.
     * @param filterChain The chain of filters to proceed with.
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        // Extracts the JWT token from the HTTP request header
        String token = jwtTokenProvider.resolveToken(request);

        // Checks if the token is present and cryptographically valid
        if (token != null && jwtTokenProvider.validateToken(token)) {

            // Retrieves user identity and roles to build the Authentication object
            UsernamePasswordAuthenticationToken auth = jwtTokenProvider.getAuthentication(token);

            // Populates the Spring Security context with the authenticated user for the current request
            if (auth != null) {
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        // Proceeds with the next filter in the security chain
        filterChain.doFilter(request, response);
    }
}