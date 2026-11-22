
package com.example.Credit.management.SecConfig;

import com.example.Credit.management.Model.AccountStatus;
import com.example.Credit.management.Model.User;
import com.example.Credit.management.Repository.UserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String requestPath = request.getServletPath();

        // PUBLIC ENDPOINTS
        // These must work without an existing login session.
        if (
                requestPath.startsWith("/api/auth/login") ||
                        requestPath.startsWith("/api/auth/register") ||
                        requestPath.startsWith("/api/auth/send-otp") ||
                        requestPath.startsWith("/api/auth/verify-otp") ||
                        requestPath.startsWith("/api/auth/invitations/validate") ||
                        requestPath.startsWith("/api/auth/invitations/complete") ||
                        requestPath.startsWith("/api/auth/forgot-password") ||
                        requestPath.startsWith("/api/auth/verify-account") ||
                        requestPath.startsWith("/uploads/") ||
                        requestPath.startsWith("/api/accounts/image/")
        ) {
            filterChain.doFilter(request, response);
            return;
        }

        SecurityContextHolder.clearContext();

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {

            String jwt = authHeader.substring(7);

            try {

                if (jwtUtil.validateToken(jwt)) {

                    String email = jwtUtil.getEmailFromToken(jwt);

                    User user = userRepository
                            .findByEmail(email)
                            .orElse(null);

                    /*
                     * Authentication requirements:
                     *
                     * 1. User must exist.
                     * 2. User must have an assigned role.
                     * 3. User must be approved.
                     * 4. User must have completed registration.
                     *
                     * Explicit FALSE blocks incomplete staff.
                     * NULL remains temporarily compatible with
                     * legacy accounts until database backfill.
                     */

                    if (
                            user != null &&
                                    user.getRole() != null &&
                                    user.getStatus() == AccountStatus.APPROVED &&
                                    !Boolean.FALSE.equals(
                                            user.getRegistrationCompleted()
                                    )
                    ) {

                        String roleName = user.getRole().name();

                        SimpleGrantedAuthority authority =
                                new SimpleGrantedAuthority(
                                        "ROLE_" + roleName
                                );

                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        email,
                                        null,
                                        List.of(authority)
                                );

                        SecurityContextHolder
                                .getContext()
                                .setAuthentication(authentication);
                    }
                }

            } catch (Exception ex) {

                SecurityContextHolder.clearContext();

                // Never log JWT contents or credentials.
            }
        }

        filterChain.doFilter(request, response);
    }
}
