package io.github.mohammedfaraj46.backend.config;

import io.github.mohammedfaraj46.backend.security.AppUserDetailsService;
import io.github.mohammedfaraj46.backend.security.CsrfCookieFilter;
import io.github.mohammedfaraj46.backend.security.JwtCookieAuthenticationFilter;
import io.github.mohammedfaraj46.backend.security.JwtCookieFactory;
import io.github.mohammedfaraj46.backend.security.JwtService;
import io.github.mohammedfaraj46.backend.security.ProblemDetailAccessDeniedHandler;
import io.github.mohammedfaraj46.backend.security.ProblemDetailAuthenticationEntryPoint;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Stateless security: the session lives entirely in a signed JWT carried by an
 * httpOnly cookie, which {@link JwtCookieAuthenticationFilter} turns back into
 * an authenticated request.
 *
 * <p>Because the browser attaches that cookie automatically, every
 * state-changing request also needs a CSRF token that an attacker's site cannot
 * read. The token is handed out in a readable XSRF-TOKEN cookie and must come
 * back in the X-XSRF-TOKEN header — something only same-origin JavaScript can do.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtCookieFactory cookieFactory,
                                                   JwtService jwtService,
                                                   AppUserDetailsService userDetailsService)
            throws Exception {

        // built here rather than exposed as a @Component: Boot registers stray
        // Filter beans in the servlet chain as well, which would run it twice
        JwtCookieAuthenticationFilter jwtCookieAuthenticationFilter =
                new JwtCookieAuthenticationFilter(cookieFactory, jwtService, userDetailsService);

        http
                .csrf(csrf -> csrf
                        // readable by the SPA, which echoes it back in a header
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        // plain token rather than the BREACH-encoded default, so the
                        // header value matches the cookie the SPA just read
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        // a visitor with no token yet must still be able to get one:
                        // these two are the entry points into the application
                        .ignoringRequestMatchers("/api/auth/register", "/api/auth/login")
                )
                // no server-side session; the cookie is the whole story
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // registering, logging in and fetching a CSRF token happen
                        // without a session; logout is public but does need a token
                        .requestMatchers("/api/auth/register", "/api/auth/login",
                                "/api/auth/logout", "/api/auth/csrf")
                        .permitAll()
                        // so a container error dispatch does not come back as a 401
                        .requestMatchers("/error").permitAll()
                        // the container health check runs before anything can log
                        // in; it answers UP or DOWN and nothing else
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(handling -> handling
                        // without this, an unauthenticated call returns 403 instead of 401;
                        // problem+json so a 401 reads like every other failure
                        .authenticationEntryPoint(new ProblemDetailAuthenticationEntryPoint())
                        .accessDeniedHandler(new ProblemDetailAccessDeniedHandler())
                )
                // before the CSRF check on purpose: reading the login cookie changes
                // nothing, and it means a rejected CSRF token on a signed-in request
                // comes back as 403 (authenticated but not allowed) instead of 401
                .addFilterBefore(jwtCookieAuthenticationFilter, CsrfFilter.class)
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class);

        return http.build();
    }
}
