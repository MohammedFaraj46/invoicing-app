package io.github.mohammedfaraj46.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads the login cookie, verifies the token and puts the user in the
 * SecurityContext so protected endpoints see an authenticated request.
 *
 * <p>Anything wrong with the cookie simply leaves the request anonymous; the
 * authorization rules then decide whether that is a 401. The filter itself never
 * rejects, so public endpoints keep working with a stale cookie present.
 */
public class JwtCookieAuthenticationFilter extends OncePerRequestFilter {

    private final JwtCookieFactory cookieFactory;
    private final JwtService jwtService;
    private final AppUserDetailsService userDetailsService;

    public JwtCookieAuthenticationFilter(JwtCookieFactory cookieFactory,
                                         JwtService jwtService,
                                         AppUserDetailsService userDetailsService) {
        this.cookieFactory = cookieFactory;
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(request);
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request) {
        Optional<Long> userId = cookieFactory.readToken(request).flatMap(jwtService::extractUserId);
        if (userId.isEmpty()) {
            return;
        }
        try {
            AppUserPrincipal principal = userDetailsService.loadById(userId.get());
            UsernamePasswordAuthenticationToken authentication =
                    UsernamePasswordAuthenticationToken.authenticated(
                            principal, null, principal.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (UsernameNotFoundException exception) {
            // valid token for an account that no longer exists: stay anonymous
            SecurityContextHolder.clearContext();
        }
    }
}
