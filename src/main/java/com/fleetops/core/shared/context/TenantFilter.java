package com.fleetops.core.shared.context;

import com.fleetops.core.module.auth.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class TenantFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")
                    && SecurityContextHolder.getContext().getAuthentication() != null) {
                String token = authHeader.substring(7);
                Claims claims = jwtUtil.extractAllClaims(token);

                Long userId = claims.get("userId", Long.class);
                Long companyId = claims.get("companyId", Long.class);
                String role = claims.get("role", String.class);
                String userType = claims.get("userType", String.class);

                TenantContext.set(companyId, userId, role, userType);
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
