package com.shopsphere.order_service.config;

import com.shopsphere.order_service.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/error",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/actuator/health/**",
                                "/actuator/prometheus"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/orders/my"
                        ).hasAnyRole("USER", "ADMIN")
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/orders/user/**"
                        ).hasAnyRole("USER", "ADMIN")
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/orders/**"
                        ).hasAnyRole("USER", "ADMIN")
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/orders/**"
                        ).hasAnyRole("USER", "ADMIN")
                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/orders/**"
                        ).hasAnyRole("USER", "ADMIN")
                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/orders/**"
                        ).hasAnyRole("USER", "ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}
