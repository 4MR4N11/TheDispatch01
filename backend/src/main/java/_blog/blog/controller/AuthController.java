package _blog.blog.controller;

import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.beans.factory.annotation.Value;
import _blog.blog.dto.AuthResponse;
import _blog.blog.dto.ErrorResponse;
import _blog.blog.dto.LoginRequest;
import _blog.blog.dto.RegisterRequest;
import _blog.blog.service.JwtService;
import _blog.blog.service.RateLimiterService;
import _blog.blog.service.UserService;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RateLimiterService rateLimiterService;

    @Value("${app.security.cookie.secure}")
    private boolean cookieSecure;

    @Value("${app.security.cookie.same-site}")
    private String cookieSameSite;

    public AuthController(AuthenticationManager authenticationManager, UserService userService, JwtService jwtService, RateLimiterService rateLimiterService) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.rateLimiterService = rateLimiterService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest, HttpServletResponse response) {
        // ✅ SECURITY FIX: Apply rate limiting to prevent abuse
        String ipAddress = httpRequest.getRemoteAddr();
        Bucket bucket = rateLimiterService.resolveBucket(ipAddress);

        if (!bucket.tryConsume(1)) {
            return ResponseEntity.status(429)
                .body(new ErrorResponse("Too many registration attempts. Please try again later."));
        }

        // Save the user
        userService.register(request);

        // Create a LoginRequest from RegisterRequest
        LoginRequest loginRequest = new LoginRequest(
            request.getUsername(),
            request.getPassword()
        );

        // Authenticate
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                loginRequest.getUsername(),
                loginRequest.getPassword()
            )
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        String token = jwtService.generateToken(userService.authenticate(loginRequest));
        String role = authentication.getAuthorities()
        .iterator()
        .next()
        .getAuthority();
        ResponseCookie cookie = ResponseCookie.from("jwt", token)
            .httpOnly(true)
            .secure(cookieSecure) // ✅ FIXED: Now environment-based (true in production)
            .sameSite(cookieSameSite) // ✅ FIXED: Added SameSite protection (Strict/Lax)
            .path("/")
            .maxAge(24 * 60 * 60) // 1 day
            .build();

        response.addHeader("Set-Cookie", cookie.toString());
        return ResponseEntity.ok(new AuthResponse(token, request.getUsername(), role));
    }


    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse response) {
        // ✅ SECURITY FIX: Apply rate limiting to prevent brute force attacks
        String ipAddress = httpRequest.getRemoteAddr();
        Bucket bucket = rateLimiterService.resolveBucket(ipAddress);

        if (!bucket.tryConsume(1)) {
            return ResponseEntity.status(429)
                .body(new ErrorResponse("Too many login attempts. Please try again later."));
        }

        // First authenticate and check if banned
        var user = userService.authenticate(request);

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        String token = jwtService.generateToken(user);
        String role = authentication.getAuthorities()
        .iterator()
        .next()
        .getAuthority();
        ResponseCookie cookie = ResponseCookie.from("jwt", token)
            .httpOnly(true)
            .secure(cookieSecure) // ✅ FIXED: Now environment-based (true in production)
            .sameSite(cookieSameSite) // ✅ FIXED: Added SameSite protection (Strict/Lax)
            .path("/")
            .maxAge(24 * 60 * 60) // 1 day
            .build();

        response.addHeader("Set-Cookie", cookie.toString());
        return ResponseEntity.ok(new AuthResponse(token, request.getUsername(), role));
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(HttpServletResponse response) {
        // Clear the JWT cookie by setting maxAge to 0
        ResponseCookie cookie = ResponseCookie.from("jwt", "")
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite(cookieSameSite)
            .path("/")
            .maxAge(0) // Expire immediately
            .build();

        response.addHeader("Set-Cookie", cookie.toString());
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok("Logged out successfully");
    }
}