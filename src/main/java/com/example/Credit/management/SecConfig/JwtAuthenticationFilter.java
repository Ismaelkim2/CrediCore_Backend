package com.example.Credit.management.SecConfig;

import com.example.Credit.management.Model.User;
import com.example.Credit.management.Repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        logger.debug("🔐 Checking JWT for request: {}", request.getRequestURI());

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String jwt = authHeader.substring(7);

            if (jwtUtil.validateToken(jwt)) {
                String email = jwtUtil.getEmailFromToken(jwt);
                logger.debug("✅ JWT validated successfully for email: {}", email);

                User user = userRepository.findByEmail(email).orElse(null);
                if (user != null) {
                    String roleName = user.getRole().name();
                    SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + roleName);

                    logger.info("👤 Authenticated User: {} with Role: {}", email, authority.getAuthority());

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(email, null, List.of(authority));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    logger.warn("⚠️ User not found for email: {}", email);
                }
            } else {
                logger.warn("🚫 Invalid JWT token for request: {}", request.getRequestURI());
            }
        } else {
            logger.debug("ℹ️ No JWT token found in header for: {}", request.getRequestURI());
        }

        filterChain.doFilter(request, response);
    }
}
