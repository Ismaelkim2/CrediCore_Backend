
package com.example.Credit.management.SecConfig;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
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

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtFilter
    ) throws Exception {

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())

                .authorizeHttpRequests(auth -> auth

                        // PUBLIC ENDPOINTS
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/register",
                                "/api/auth/send-otp",
                                "/api/auth/verify-otp",
                                "/api/auth/forgot-password",
                                "/api/auth/verify-account",
                                "/ws/**",
                                "/uploads/**",
                                "/api/accounts/image/**"
                        ).permitAll()

                        // ADMIN ONLY
                        .requestMatchers("/api/auth/users/**")
                        .hasRole("ADMIN")

                        // TENANT MANAGEMENT
                        .requestMatchers("/api/tenant/**")
                        .hasAnyRole("TENANT_AGENT", "ADMIN")

                        // ALL OTHER ENDPOINTS
                        .anyRequest().authenticated()
                )

                .addFilterBefore(
                        jwtFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(List.of(
                "http://localhost:4200",
                "https://credit-management1.netlify.app",
                "https://creditbank-frontend.onrender.com"
        ));

        config.setAllowedMethods(List.of(
                "GET",
                "POST",
                "PUT",
                "PATCH",
                "DELETE",
                "OPTIONS"
        ));

        config.setAllowedHeaders(List.of("*"));

        config.setExposedHeaders(List.of(
                "Authorization"
        ));

        // Required for credentialed cross-origin requests.
        config.setAllowCredentials(true);

        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", config);

        return source;
    }

    @Bean
    public ApplicationRunner runner(Environment env) {

        return args -> System.out.println(
                ">>> SERVER PORT = " +
                        env.getProperty("server.port")
        );
    }

    @Configuration
    public static class WebConfig implements WebMvcConfigurer {

        @Value("${app.upload.property-images-dir:uploads/properties}")
        private String propertyImagesDirectory;

        @Override
        public void addResourceHandlers(
                ResourceHandlerRegistry registry
        ) {

            Path propertyImagesPath = Paths
                    .get(propertyImagesDirectory)
                    .toAbsolutePath()
                    .normalize();

            String resourceLocation =
                    propertyImagesPath.toUri().toString();

            registry
                    .addResourceHandler("/uploads/properties/**")
                    .addResourceLocations(resourceLocation);
        }
    }
}
