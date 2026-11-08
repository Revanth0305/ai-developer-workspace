package dev.workspace;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.net.URI;
import java.util.Set;

/** The local service has no remote authentication. Refuse foreign browser origins and hosts. */
@Component
public class LocalRequestGuard extends OncePerRequestFilter {
    private static final Set<String> HOSTS = Set.of("localhost", "127.0.0.1", "[::1]", "::1");
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        if (!HOSTS.contains(request.getServerName())) { response.sendError(403); return; }
        String origin = request.getHeader("Origin");
        if (origin != null) {
            try {
                URI uri = URI.create(origin);
                if (!"http".equals(uri.getScheme()) || !HOSTS.contains(uri.getHost()) || (uri.getPort() != 5173 && uri.getPort() != 8080)) {
                    response.sendError(403); return;
                }
            } catch (IllegalArgumentException e) { response.sendError(403); return; }
        }
        String fetchSite = request.getHeader("Sec-Fetch-Site");
        if ("cross-site".equals(fetchSite)) { response.sendError(403); return; }
        chain.doFilter(request, response);
    }
}
