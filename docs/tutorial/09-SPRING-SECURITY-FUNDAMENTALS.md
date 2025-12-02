# Spring Security Fundamentals: Authentication & Authorization

> **Core Concept**: Understand how Spring Security protects your application, how the filter chain works, and how authentication is stored and validated.

---

## Table of Contents
1. [What is Spring Security?](#1-what-is-spring-security)
2. [Security Filter Chain](#2-security-filter-chain)
3. [Authentication vs Authorization](#3-authentication-vs-authorization)
4. [Your Security Configuration](#4-your-security-configuration)
5. [Password Encoding](#5-password-encoding)
6. [UserDetailsService](#6-userdetailsservice)
7. [SecurityContext](#7-securitycontext)
8. [Method-Level Security](#8-method-level-security)
9. [CORS Configuration](#9-cors-configuration)
10. [Security Headers](#10-security-headers)

---

## 1. What is Spring Security?

### 1.1 Purpose

**Spring Security** is a framework that provides:
- **Authentication** - Who are you?
- **Authorization** - What can you do?
- **Protection** - Against common attacks (CSRF, XSS, etc.)

### 1.2 Without Spring Security

**Unprotected Controller:**
```java
@RestController
public class UserController {
    @GetMapping("/users")
    public List<User> getUsers() {
        return userRepository.findAll();  // ⚠️ Anyone can access!
    }

    @DeleteMapping("/users/{id}")
    public void deleteUser(@PathVariable Long id) {
        userRepository.deleteById(id);  // ⚠️ Anyone can delete!
    }
}
```

**Problems:**
- No login required
- Anonymous users can access everything
- No protection against attacks
- Cannot identify who made the request

### 1.3 With Spring Security

**Your Security Configuration:**
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        return http
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/auth/**").permitAll()        // Public
                .anyRequest().authenticated()                    // Protected
            )
            .build();
    }
}
```

**Now:**
- Login required for protected endpoints
- Anonymous users can only access `/auth/**`
- JWT validates identity on every request
- Can identify user: `Authentication auth` parameter

---

## 2. Security Filter Chain

### 2.1 What is a Filter Chain?

**Filter Chain** is a series of filters that process HTTP requests before they reach your controller.

**Request Flow:**
```
HTTP Request
    ↓
┌───────────────────────────────────────┐
│  1. Tomcat Receives Request           │
└───────────────┬───────────────────────┘
                ↓
┌───────────────────────────────────────┐
│  2. Spring Security Filter Chain      │
├───────────────────────────────────────┤
│  Filter 1: CORS Filter                │ - Allow cross-origin requests
│  Filter 2: CSRF Filter (disabled)     │ - CSRF protection
│  Filter 3: JwtAuthenticationFilter    │ - Extract & validate JWT
│  Filter 4: UsernamePasswordAuth...    │ - Standard auth
│  Filter 5: Authorization Filter       │ - Check permissions
└───────────────┬───────────────────────┘
                ↓
┌───────────────────────────────────────┐
│  3. DispatcherServlet (if allowed)    │
│     → Controller → Service → ...      │
└───────────────────────────────────────┘
```

### 2.2 Your Custom JWT Filter

**Your JwtAuthenticationFilter:**
**`JwtAuthenticationFilter.java:21-29`**
```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }
```

**Why `OncePerRequestFilter`?**
- Guarantees filter executes **exactly once** per request
- Even if request is forwarded internally

### 2.3 Filter Execution Logic

**Your filter's `doFilterInternal` method:**
**`JwtAuthenticationFilter.java:32-94`**
```java
@Override
protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
) throws ServletException, IOException {

    // 1. Skip authentication for public endpoints
    String path = request.getServletPath();
    String method = request.getMethod();

    if (path.contains("/auth") ||
        path.contains("/actuator/health") ||
        (path.startsWith("/uploads") && ("GET".equals(method) || "HEAD".equals(method)))) {
        filterChain.doFilter(request, response);  // Skip to next filter
        return;
    }

    // 2. Extract JWT from cookie (or Authorization header as fallback)
    String jwt = null;

    if (request.getCookies() != null) {
        for (Cookie cookie : request.getCookies()) {
            if ("jwt".equals(cookie.getName())) {
                jwt = cookie.getValue();
                break;
            }
        }
    }

    // Fallback: Authorization header
    if (jwt == null) {
        final String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            jwt = authHeader.substring(7);
        }
    }

    // 3. If no JWT, continue without authentication
    if (jwt == null) {
        filterChain.doFilter(request, response);
        return;
    }

    // 4. Extract username from JWT
    final String userEmail = jwtService.extractUsername(jwt);

    // 5. If user not already authenticated, validate token
    if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
        UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);

        // 6. Validate token and check if user is enabled
        if (jwtService.isTokenValid(jwt, userDetails) && userDetails.isEnabled()) {
            // 7. Create authentication token
            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.getAuthorities()
            );
            authToken.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request)
            );

            // 8. Set authentication in SecurityContext
            SecurityContextHolder.getContext().setAuthentication(authToken);
        }
    }

    // 9. Continue to next filter
    filterChain.doFilter(request, response);
}
```

**Step-by-Step:**
1. **Skip public endpoints** - `/auth/**`, `/actuator/health`, etc.
2. **Extract JWT** - From cookie (preferred) or Authorization header
3. **Early exit** - If no JWT, continue without authentication
4. **Extract username** - From JWT claims
5. **Load user** - Via UserDetailsService
6. **Validate** - Check token signature and expiration
7. **Create auth token** - With user details and authorities
8. **Store in SecurityContext** - Makes user available to controllers
9. **Continue** - Pass request to next filter

### 2.4 Registering Custom Filter

**Your SecurityConfig:**
**`SecurityConfig.java:78`**
```java
.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
```

**Filter Order:**
```
Request
  ↓
CorsFilter
  ↓
JwtAuthenticationFilter  ← YOUR CUSTOM FILTER (runs before)
  ↓
UsernamePasswordAuthenticationFilter
  ↓
AuthorizationFilter
  ↓
Controller
```

---

## 3. Authentication vs Authorization

### 3.1 Definitions

**Authentication** - Verifying identity
- "Who are you?"
- Login with username/password
- Validate JWT token
- Result: `Authentication` object in `SecurityContext`

**Authorization** - Checking permissions
- "What can you do?"
- Role-based access (ADMIN, USER)
- Method-level security (`@PreAuthorize`)
- URL-based security (`.requestMatchers()`)

### 3.2 Authentication Flow

**Your Login Process:**
```
1. User sends credentials
   POST /auth/login
   {"username": "john", "password": "pass123"}

2. AuthController receives request
   ↓
3. UserService.authenticate()
   - Find user by username
   - Verify password (BCrypt)

4. AuthenticationManager authenticates
   UsernamePasswordAuthenticationToken created

5. JWT generated
   JwtService.generateToken(user)

6. JWT returned to client
   Set in cookie (HttpOnly, Secure)

7. Client stores JWT
   Sent with every subsequent request

8. JwtAuthenticationFilter validates
   On every request, JWT extracted and validated

9. Authentication stored in SecurityContext
   Available to all controllers
```

### 3.3 Authorization Examples

**URL-Based Authorization (Your Config):**
**`SecurityConfig.java:49-59`**
```java
.authorizeHttpRequests(authz -> authz
    .requestMatchers("/auth/**").permitAll()           // Anyone
    .requestMatchers("/actuator/health").permitAll()   // Anyone
    .requestMatchers(HttpMethod.POST, "/uploads/upload").authenticated()  // Logged in users
    .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()           // Anyone
    .anyRequest().authenticated()                      // Logged in users
)
```

**Method-Based Authorization:**
**`UserController.java:142-143`**
```java
@PostMapping("/delete/{id}")
@PreAuthorize("hasRole('ADMIN')")  // Only ADMIN role
public ResponseEntity<Map<String, String>> deleteUser(@PathVariable Long id) {
```

---

## 4. Your Security Configuration

### 4.1 Complete SecurityConfig

**Your SecurityConfig:**
**`SecurityConfig.java:27-40`**
```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity  // Enables @PreAuthorize, @Secured, etc.
public class SecurityConfig {

    private final UserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(UserDetailsService userDetailsService,
                         JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.userDetailsService = userDetailsService;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }
```

**Annotations:**
- `@Configuration` - This is a configuration class
- `@EnableWebSecurity` - Enable Spring Security
- `@EnableMethodSecurity` - Enable method-level security annotations

### 4.2 SecurityFilterChain Bean

**`SecurityConfig.java:42-80`**
```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
        .csrf(csrf -> csrf.disable())  // CSRF disabled (using JWT)
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .authorizeHttpRequests(authz -> authz
            .requestMatchers("/auth/**").permitAll()
            .anyRequest().authenticated()
        )
        .sessionManagement(session -> session
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS)  // No sessions
        )
        .headers(headers -> headers
            .frameOptions(frame -> frame.deny())       // Prevent clickjacking
            .httpStrictTransportSecurity(hsts -> hsts
                .includeSubDomains(true)
                .maxAgeInSeconds(31536000)             // 1 year
            )
            .contentSecurityPolicy(csp -> csp
                .policyDirectives("default-src 'self'; ...")
            )
        )
        .authenticationProvider(authenticationProvider())
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
}
```

### 4.3 CSRF Disabled Explanation

**`SecurityConfig.java:47`**
```java
.csrf(csrf -> csrf.disable())  // ✅ Safe because using JWT
```

**Why disabled?**
- **CSRF** (Cross-Site Request Forgery) protects against attacks where a malicious site tricks your browser into making requests
- **Not needed with JWT** in Authorization header (attacker can't access it)
- **If using cookies for auth** (like your JWT cookie), consider enabling CSRF

**Your JWT is in cookie, but:**
- Cookie is HttpOnly (JavaScript can't access it)
- Using SameSite=Strict (cookie not sent cross-site)
- So CSRF risk is mitigated

### 4.4 Stateless Sessions

**`SecurityConfig.java:61-63`**
```java
.sessionManagement(session -> session
    .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
)
```

**What is STATELESS?**
- **No server-side sessions** (no HttpSession)
- **Each request is independent** (JWT contains all info)
- **Scalable** (no session store needed)

**Traditional (STATEFUL):**
```
User logs in → Server creates session → Session stored in memory
Each request → Session ID in cookie → Server looks up session
```

**Your JWT (STATELESS):**
```
User logs in → Server creates JWT → JWT sent to client
Each request → JWT in cookie → Server validates JWT (no lookup)
```

---

## 5. Password Encoding

### 5.1 Why Hash Passwords?

**❌ Never store passwords in plaintext:**
```sql
-- BAD!
INSERT INTO users (username, password) VALUES ('john', 'password123');
```

**✅ Always hash passwords:**
```sql
-- GOOD!
INSERT INTO users (username, password)
VALUES ('john', '$2a$10$slYQmyNdGzTn7ZYnPcwNbuH...');
```

**Why?**
- If database is compromised, attacker can't read passwords
- Even administrators can't see passwords
- Each user's hash is unique (salt)

### 5.2 BCrypt Password Encoder

**Your SecurityConfig:**
**`SecurityConfig.java:104-107`**
```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

**How BCrypt works:**
```java
String plainPassword = "password123";
String hashed = passwordEncoder.encode(plainPassword);
// Result: $2a$10$slYQmyNdGzTn7ZYnPcwNbuH7YnTZ7DlDcVMNLJ8h8vJnW9FqK6Szy
//         │  │  │                                              │
//         │  │  └─ Salt (random)                               └─ Hash
//         │  └─ Cost factor (10 = 2^10 iterations)
//         └─ Algorithm version (2a)
```

**Properties:**
- **One-way** - Cannot reverse hash to get password
- **Salted** - Each password gets unique salt (prevents rainbow tables)
- **Adaptive** - Cost factor can increase over time (as computers get faster)
- **Slow** - Intentionally slow to prevent brute-force attacks

### 5.3 Hashing on Registration

**Your UserMapper:**
**`UserMapper.java:17`**
```java
.password(passwordEncoder.encode(request.getPassword()))
```

**Flow:**
```
User registers with "password123"
  ↓
UserMapper.toEntity()
  ↓
passwordEncoder.encode("password123")
  ↓
Returns: "$2a$10$slYQmy..."
  ↓
Saved to database (hashed)
```

### 5.4 Verifying Passwords

**During login:**
```java
// User enters: "password123"
String enteredPassword = request.getPassword();

// Database has: "$2a$10$slYQmy..."
String storedHash = user.getPassword();

// Verify:
boolean matches = passwordEncoder.matches(enteredPassword, storedHash);
// Returns: true (passwords match)
```

**BCrypt's `matches()` method:**
1. Extracts salt from stored hash
2. Hashes entered password with same salt
3. Compares hashes
4. Returns true if identical

---

## 6. UserDetailsService

### 6.1 What is UserDetailsService?

**Interface for loading user data:**
```java
public interface UserDetailsService {
    UserDetails loadUserByUsername(String username) throws UsernameNotFoundException;
}
```

**Purpose:**
- Spring Security calls this to load user from database
- Used during authentication
- Used by JWT filter to load user details

### 6.2 Your User Entity Implements UserDetails

**Your User entity:**
**`User.java:43`**
```java
public class User implements UserDetails {
```

**Required methods:**
```java
@Override
public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    // Returns: ["ROLE_USER"] or ["ROLE_ADMIN"]
}

@Override
public String getUsername() {
    return email;  // Use email as username for Spring Security
}

@Override
public String getPassword() {
    return password;  // BCrypt hash
}

@Override
public boolean isEnabled() {
    return !banned;  // Disabled if banned
}

@Override
public boolean isAccountNonExpired() {
    return true;
}

@Override
public boolean isAccountNonLocked() {
    return true;
}

@Override
public boolean isCredentialsNonExpired() {
    return true;
}
```

### 6.3 Custom UserDetailsService Implementation

**Your implementation (implicit via repository):**
```java
@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByEmail(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }
}
```

**Used by:**
1. **AuthenticationManager** - During login
2. **JwtAuthenticationFilter** - On every request
3. **Spring Security** - For authorization checks

---

## 7. SecurityContext

### 7.1 What is SecurityContext?

**SecurityContext** stores authentication information for the current request.

**Thread-local storage:**
```java
SecurityContextHolder.getContext().getAuthentication()
```

**Lifecycle:**
```
Request Start
  ↓
SecurityContext created (empty)
  ↓
JwtAuthenticationFilter runs
  ↓
Authentication set in SecurityContext
  ↓
Controller accesses Authentication
  ↓
Request End
  ↓
SecurityContext cleared
```

### 7.2 Setting Authentication

**Your JwtAuthenticationFilter:**
**`JwtAuthenticationFilter.java:82-90`**
```java
UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
        userDetails,           // Principal (user)
        null,                  // Credentials (not needed after authentication)
        userDetails.getAuthorities()  // Roles/permissions
);
authToken.setDetails(
        new WebAuthenticationDetailsSource().buildDetails(request)
);
SecurityContextHolder.getContext().setAuthentication(authToken);
```

### 7.3 Accessing Authentication

**In your controllers:**
```java
@GetMapping("/users")
public List<UserResponse> getUsers(Authentication auth) {
    //                                ↑ Spring injects this

    // Get username:
    String username = auth.getName();  // Email in your case

    // Get user object:
    User user = (User) auth.getPrincipal();

    // Get roles:
    Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();

    // Check if authenticated:
    boolean isAuthenticated = auth.isAuthenticated();
}
```

**Alternative - Programmatic Access:**
```java
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
if (auth != null && auth.isAuthenticated()) {
    String username = auth.getName();
}
```

---

## 8. Method-Level Security

### 8.1 @PreAuthorize

**Your UserController:**
**`UserController.java:142-143`**
```java
@PostMapping("/delete/{id}")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<Map<String, String>> deleteUser(@PathVariable Long id) {
```

**What happens:**
1. Request reaches controller
2. Spring Security checks `@PreAuthorize` expression
3. If expression evaluates to `false` → `403 Forbidden`
4. If expression evaluates to `true` → method executes

**Common Expressions:**
```java
// Role-based
@PreAuthorize("hasRole('ADMIN')")
@PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")

// Authority-based
@PreAuthorize("hasAuthority('DELETE_USER')")
@PreAuthorize("hasAnyAuthority('DELETE_USER', 'MANAGE_USERS')")

// Authentication-based
@PreAuthorize("isAuthenticated()")
@PreAuthorize("isAnonymous()")

// Custom expressions
@PreAuthorize("#username == authentication.name")  // Own profile only
@PreAuthorize("@userService.isOwner(#id, authentication.name)")  // Custom logic
```

### 8.2 Enabling Method Security

**Your SecurityConfig:**
**`SecurityConfig.java:29`**
```java
@EnableMethodSecurity
```

**This enables:**
- `@PreAuthorize` - Check before method execution
- `@PostAuthorize` - Check after method execution
- `@Secured` - Simpler role checking
- `@RolesAllowed` - JSR-250 annotation

### 8.3 Example Usage in Your App

**Ban user (ADMIN only):**
**`UserController.java:152-153`**
```java
@PostMapping("/ban/{id}")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<Map<String, String>> banUser(@PathVariable Long id) {
```

**Update profile (own profile only):**
```java
@PutMapping("/update-profile")
@PreAuthorize("isAuthenticated()")  // Any logged-in user
public ResponseEntity<?> updateProfile(@RequestBody UpdateProfileRequest request, Authentication auth) {
    // Inside method, check it's their own profile
    User user = userService.getUserByUsername(auth.getName());
    // ...
}
```

---

## 9. CORS Configuration

### 9.1 What is CORS?

**CORS** (Cross-Origin Resource Sharing) allows your API to accept requests from different domains.

**Scenario:**
```
Frontend:  http://localhost:4200  (Angular)
Backend:   http://localhost:8080  (Spring Boot)
           ↑ Different port = Different origin
```

**Without CORS:**
```
Frontend makes request → Browser blocks it
Error: "Access to XMLHttpRequest has been blocked by CORS policy"
```

**With CORS:**
```
Frontend makes request → Browser sends preflight → Server allows → Request succeeds
```

### 9.2 Your CORS Configuration

**`SecurityConfig.java:83-102`**
```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();

    configuration.setAllowedOrigins(Arrays.asList("http://localhost:4200"));
    //                               ↑ Your Angular app

    configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    //                                             ↑ HTTP methods allowed

    configuration.setAllowedHeaders(Arrays.asList(
        "Content-Type",
        "Authorization",
        "X-Requested-With",
        "Accept",
        "Origin"
    ));

    configuration.setAllowCredentials(true);  // Allow cookies
    configuration.setMaxAge(3600L);           // Cache preflight for 1 hour

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);  // Apply to all endpoints
    return source;
}
```

### 9.3 Preflight Requests

**For certain requests (PUT, DELETE, custom headers), browser sends preflight:**
```
Browser:
OPTIONS /users/1
Origin: http://localhost:4200
Access-Control-Request-Method: DELETE
Access-Control-Request-Headers: Authorization

Server:
HTTP/1.1 200 OK
Access-Control-Allow-Origin: http://localhost:4200
Access-Control-Allow-Methods: GET, POST, PUT, DELETE
Access-Control-Allow-Headers: Authorization, Content-Type
Access-Control-Max-Age: 3600

Browser: Preflight passed, send actual request
DELETE /users/1
```

---

## 10. Security Headers

### 10.1 Your Security Headers Configuration

**`SecurityConfig.java:65-76`**
```java
.headers(headers -> headers
    .frameOptions(frame -> frame.deny())       // Prevent clickjacking
    .xssProtection(xss -> xss.disable())       // Modern browsers use CSP
    .contentTypeOptions(contentType -> contentType.disable())
    .httpStrictTransportSecurity(hsts -> hsts
        .includeSubDomains(true)
        .maxAgeInSeconds(31536000)             // 1 year
    )
    .contentSecurityPolicy(csp -> csp
        .policyDirectives("default-src 'self'; script-src 'self'; ...")
    )
)
```

### 10.2 Security Headers Explained

#### X-Frame-Options: DENY
```
X-Frame-Options: DENY
```
**Prevents clickjacking** - Your site cannot be embedded in `<iframe>`

**Attack scenario (without header):**
```html
<!-- Malicious site -->
<iframe src="https://yoursite.com/delete-account" style="opacity:0">
  <!-- User thinks they're clicking something else, actually clicks delete -->
</iframe>
```

#### Strict-Transport-Security (HSTS)
```
Strict-Transport-Security: max-age=31536000; includeSubDomains
```
**Forces HTTPS** - Browser will only connect via HTTPS for 1 year

#### Content-Security-Policy (CSP)
```
Content-Security-Policy: default-src 'self'; script-src 'self'
```
**Prevents XSS attacks** - Only allows scripts from your domain

---

## Key Takeaways

### What You Learned

1. **Security Filter Chain**
   - Series of filters processing requests
   - JwtAuthenticationFilter validates tokens
   - Authentication stored in SecurityContext

2. **Authentication vs Authorization**
   - **Authentication** - Who are you? (login, JWT validation)
   - **Authorization** - What can you do? (roles, permissions)

3. **Password Security**
   - BCrypt hashing (one-way, salted, adaptive)
   - Never store plaintext passwords
   - Verify with `passwordEncoder.matches()`

4. **SecurityContext**
   - Thread-local storage for authentication
   - Set by filters, accessed by controllers
   - Cleared after request

5. **Method Security**
   - `@PreAuthorize` for method-level protection
   - `hasRole('ADMIN')` checks roles
   - Enabled with `@EnableMethodSecurity`

6. **CORS**
   - Allows cross-origin requests
   - Configure allowed origins, methods, headers
   - Preflight requests for complex requests

7. **Security Headers**
   - X-Frame-Options prevents clickjacking
   - HSTS forces HTTPS
   - CSP prevents XSS

---

## What's Next?

You now understand Spring Security fundamentals. Next:

**→ [10-JWT-AUTHENTICATION.md](./10-JWT-AUTHENTICATION.md)** - JWT tokens, how your login/logout works

**Key Questions for Next Section:**
- What is a JWT token?
- How are tokens generated and validated?
- How does your login flow work end-to-end?
- What's in the token payload?

**Completed**:
- ✅ Java Essentials
- ✅ Spring Core (IoC, DI)
- ✅ Spring Boot Essentials
- ✅ JPA & Hibernate Basics
- ✅ JPA Relationships
- ✅ Spring Data JPA Repositories
- ✅ REST Controllers & HTTP Flow
- ✅ DTOs and Mappers
- ✅ Spring Security Fundamentals

**Next**: JWT Authentication 🎯
