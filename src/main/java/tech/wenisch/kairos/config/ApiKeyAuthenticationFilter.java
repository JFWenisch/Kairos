package tech.wenisch.kairos.config;

import tech.wenisch.kairos.entity.ApiKey;
import tech.wenisch.kairos.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final ApiKeyService apiKeyService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authorization == null || authorization.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = extractToken(authorization);
        if (token == null || token.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<ApiKey> apiKey = apiKeyService.validateToken(token);
        if (apiKey.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"Invalid API key token\"}");
            return;
        }

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "api-key:" + apiKey.get().getName(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_API_KEY"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
        filterChain.doFilter(request, response);
    }

    private String extractToken(String authorization) {
        if (authorization.startsWith("Bearer ")) {
            return authorization.substring(7).trim();
        }
        if (authorization.startsWith("ApiKey ")) {
            return authorization.substring(7).trim();
        }
        return null;
    }
}
