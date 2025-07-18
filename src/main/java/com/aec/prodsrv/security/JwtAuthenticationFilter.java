package com.aec.prodsrv.security;

import com.aec.prodsrv.security.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetails; // Correct import
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
// No longer need java.util.HashMap or java.util.Map if we modify WebAuthenticationDetails directly

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
        throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7); // The raw JWT token string
            if (jwtUtils.validateToken(token)) {
                String username = jwtUtils.getUsernameFromToken(token);
                var userDetails = userDetailsService.loadUserByUsername(username);

                var auth = new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.getAuthorities()
                );

                // --- CORRECTED KEY CHANGE HERE: Extend WebAuthenticationDetails ---
                WebAuthenticationDetails webDetails = new WebAuthenticationDetailsSource().buildDetails(request);

                // Create a custom WebAuthenticationDetails that includes the JWT token
                // This is the cleanest way to extend the details object.
                CustomWebAuthenticationDetails customDetails = new CustomWebAuthenticationDetails(webDetails.getRemoteAddress(), webDetails.getSessionId(), token);

                auth.setDetails(customDetails); // Set our custom details object

                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        chain.doFilter(request, response);
    }

    // --- NEW INNER CLASS: CustomWebAuthenticationDetails ---
    // We need a custom class to extend WebAuthenticationDetails and hold our JWT token.
    public static class CustomWebAuthenticationDetails extends WebAuthenticationDetails {
        private final String jwtToken;

        public CustomWebAuthenticationDetails(String remoteAddress, String sessionId, String jwtToken) {
            super(remoteAddress, sessionId);
            this.jwtToken = jwtToken;
        }

        public String getJwtToken() {
            return jwtToken;
        }

        // It's good practice to override equals and hashCode if you add fields
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            if (!super.equals(obj)) return false;
            CustomWebAuthenticationDetails that = (CustomWebAuthenticationDetails) obj;
            return jwtToken != null ? jwtToken.equals(that.jwtToken) : that.jwtToken == null;
        }

        @Override
        public int hashCode() {
            int result = super.hashCode();
            result = 31 * result + (jwtToken != null ? jwtToken.hashCode() : 0);
            return result;
        }
    }
}