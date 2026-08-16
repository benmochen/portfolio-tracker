package com.benmochen.portfolio.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    /**
     * BCrypt with a work factor of 12.
     *
     * The work factor is how many rounds of hashing each password costs.
     * Higher is slower for everyone, including an attacker with the stolen
     * table, which is the point. 12 takes roughly a quarter of a second on
     * current hardware: unnoticeable on login, expensive across millions of
     * guesses. The default of 10 is defensible; 12 is the current common
     * recommendation.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // HTTP Basic over HTTPS, with no session. Chosen over a cookie
                // session deliberately: a cookie is sent automatically by the
                // browser on any request, including one triggered by another
                // site, which is what CSRF exploits. Basic credentials are
                // sent only when the client chooses to, so there is no ambient
                // authority to forge, and disabling CSRF below is therefore
                // safe rather than a shortcut.
                //
                // The tradeoff is real: Basic sends credentials on every
                // request, so this is only acceptable over HTTPS. A browser
                // frontend would be better served by a token or session with
                // CSRF protection enabled.
                .httpBasic(basic -> {
                })
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // Health check stays open so a deployment platform can
                        // tell whether the app is alive without credentials.
                        .requestMatchers("/actuator/health").permitAll()
                        // Everything else, including every read, requires a
                        // login. Default-deny: a new endpoint added later is
                        // protected without anyone remembering to protect it.
                        .anyRequest().authenticated())
                .build();
    }
}
