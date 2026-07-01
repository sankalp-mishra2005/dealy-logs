package com.bsp.delay.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable) // Disable CSRF for simplified demo REST APIs
            .authorizeHttpRequests(authorize -> authorize
                // Permit all requests for now to remove login system
                .anyRequest().permitAll()
                /*
                // Public files (CSS, JS, index.html, and OpenAPI specs)
                .requestMatchers("/", "/index.html", "/script.js", "/style.css", "/favicon.ico").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                
                // REST API Access Control
                // Reading data: GET endpoints are allowed for ADMIN, ENGINEER, and VIEWER roles
                .requestMatchers(HttpMethod.GET, "/api/**").hasAnyRole("ADMIN", "ENGINEER", "VIEWER")
                // Writing/Deleting data: POST/DELETE endpoints are restricted to ADMIN and ENGINEER roles
                .requestMatchers(HttpMethod.POST, "/api/**").hasAnyRole("ADMIN", "ENGINEER")
                .requestMatchers(HttpMethod.DELETE, "/api/**").hasAnyRole("ADMIN", "ENGINEER")
                
                .anyRequest().authenticated()
                */
            );
            // .httpBasic(Customizer.withDefaults()); // Enable HTTP Basic Auth

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        UserDetails admin = User.builder()
                .username("admin")
                .password(passwordEncoder.encode("admin123"))
                .roles("ADMIN")
                .build();

        UserDetails engineer = User.builder()
                .username("engineer")
                .password(passwordEncoder.encode("engineer123"))
                .roles("ENGINEER")
                .build();

        UserDetails viewer = User.builder()
                .username("viewer")
                .password(passwordEncoder.encode("viewer123"))
                .roles("VIEWER")
                .build();

        return new InMemoryUserDetailsManager(admin, engineer, viewer);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
