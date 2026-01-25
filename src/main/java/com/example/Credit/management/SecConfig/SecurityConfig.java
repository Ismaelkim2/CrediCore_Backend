package com.example.Credit.management.SecConfig;

import jakarta.servlet.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Configuration
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // Allow all OPTIONS requests
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Public endpoints: login, register, OTP, static uploads
                        .requestMatchers(
                                "/uploads/**",
                                "/api/accounts/image/**",
                                "/api/auth/login",
                                "/api/auth/register",
                                "/api/auth/send-otp",
                                "/api/auth/verify-otp",
                                "/api/mpesa/**",
                                "/api/loan-decision/**",
                                "/api/statements/**",
                                "/api/accounts/deposit",
                                "/api/products/**",
                                "/api/loans/**",
                                "/api/repayments/**"
                        ).permitAll()

                        .requestMatchers("/api/send-reminder/**").authenticated()

                        // Admin-only routes
                        .requestMatchers(
                                "/api/auth/users/pending",
                                "/api/reports",
                                "/api/settings/**",
                                "/api/auth/delete/**",
                                "/api/auth/approve/**"
                        ).hasRole("ADMIN")

                        // Any other /api/auth routes require authentication
                        .requestMatchers("/api/auth/**").authenticated()
                        .requestMatchers("/api/auth/me").authenticated()
                        .requestMatchers("/api/auth/user/by-national-id/**", "/api/accounts/by-id/**").authenticated()

                        // All other requests require authentication
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    // CORS configuration
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
                "http://localhost:4200",
                "http://127.0.0.1:4200",
                "https://*.ngrok-free.app",
                "https://credit-management.fly.dev"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    // Static resources
    @Configuration
    public static class WebConfig implements WebMvcConfigurer {
        @Override
        public void addResourceHandlers(ResourceHandlerRegistry registry) {
            Path uploadDir = Paths.get("uploads/loan_docs");
            String uploadPath = uploadDir.toFile().getAbsolutePath();

            registry.addResourceHandler("/uploads/loan_docs/**")
                    .addResourceLocations("file:" + uploadPath + "/");
        }
    }
}
