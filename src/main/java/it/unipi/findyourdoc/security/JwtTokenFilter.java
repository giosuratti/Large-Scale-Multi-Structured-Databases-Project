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
 * Custom filter that executes once per request to validate JWT tokens.
 *
 * <p>This filter intercepts incoming HTTP requests, extracts the JWT token from the header,
 * validates it using {@link JwtTokenProvider}, and sets the authentication in the {@link
 * SecurityContextHolder} if the token is valid.
 */
@Component
@RequiredArgsConstructor // Uses Lombok for clean constructor injection
public class JwtTokenFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Core filter logic to resolve and validate the token.
     *
     * @param request The incoming HTTP request.
     * @param response The outgoing HTTP response.
     * @param filterChain The chain of filters to proceed with.
     * @throws ServletException If a servlet-specific error occurs.
     * @throws IOException If an I/O error occurs.
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        // Use the injected instance 'jwtTokenProvider' to extract the token
        String token = jwtTokenProvider.resolveToken(request);

        // 1. Check if the token exists and is valid
        if (token != null && jwtTokenProvider.validateToken(token)) {

            // 2. Extract user identity and permissions (Authentication object)
            UsernamePasswordAuthenticationToken auth = jwtTokenProvider.getAuthentication(token);

            // 3. Set the authentication in the Spring Security Context
            // This allows Spring Security to know who the current user is for this request.
            if (auth != null) {
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        // Continue the filter chain (pass the request to the next filter or controller)
        filterChain.doFilter(request, response);
    }
}