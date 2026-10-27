package edu.sjsu.scheduler.config;

import edu.sjsu.scheduler.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // student-only pages
                .requestMatchers("/slots/*/book", "/appointments", "/appointments/**").hasRole("CUSTOMER")
                // advisor-only pages
                .requestMatchers("/provider/**").hasRole("PROVIDER")
                // public pages
                .requestMatchers("/", "/slots", "/login", "/error").permitAll()
                // Milestone 3: health + metrics, and the mock notification service API
                .requestMatchers("/health", "/actuator/health", "/actuator/metrics", "/actuator/metrics/**").permitAll()
                .requestMatchers("/mock-notify/**").permitAll()
                .anyRequest().authenticated())
            // The mock notification service is a machine-to-machine JSON API (no browser form),
            // so it doesn't use CSRF tokens.
            .csrf(csrf -> csrf.ignoringRequestMatchers("/mock-notify/**"))
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/dashboard", true)
                .permitAll())
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout")
                .permitAll());
        return http.build();
    }

    /** Loads users from our users table with hand-written SQL (no JPA). */
    @Bean
    public UserDetailsService userDetailsService(UserRepository userRepository) {
        return username -> userRepository.findByUsername(username)
                .map(u -> User.withUsername(u.username())
                        .password(u.passwordHash())
                        .roles(u.role())          // CUSTOMER -> ROLE_CUSTOMER, PROVIDER -> ROLE_PROVIDER
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
