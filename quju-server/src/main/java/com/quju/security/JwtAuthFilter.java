package com.quju.security;

import com.quju.user.entity.User;
import com.quju.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtUtil jwtUtil, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        // 公开接口跳过认证
        if (path.startsWith("/api/auth/register") ||
            path.startsWith("/api/auth/login") ||
            path.startsWith("/api/auth/logout") ||
            path.startsWith("/api/auth/activate") ||
            path.startsWith("/api/auth/resend-activation") ||
            path.startsWith("/api/auth/refresh") ||
            path.startsWith("/api/users/check-nickname") ||
            (path.startsWith("/api/users/") && path.split("/").length == 4 && !path.contains("/me")) ||  // GET /api/users/{id}
            path.startsWith("/api/discover") ||
            (path.startsWith("/api/activities") && request.getMethod().equals("GET")) ||
            path.startsWith("/h2-console")) {
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtUtil.validateAccessToken(token)) {
                String userId = jwtUtil.parseAccessToken(token).getSubject();
                Optional<User> userOpt = userRepository.findById(userId);
                if (userOpt.isPresent()) {
                    request.setAttribute("currentUser", userOpt.get());
                    chain.doFilter(request, response);
                    return;
                }
            }
        }

        // 未通过认证，返回 401
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().write("{\"code\":40101,\"message\":\"请先登录\",\"data\":null}");
    }
}
