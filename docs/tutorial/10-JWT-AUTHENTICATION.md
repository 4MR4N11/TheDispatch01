# JWT Authentication: How Your Login/Logout Works

> **Core Concept**: Understand JWT tokens, how they're generated, validated, and used to secure your API endpoints.

---

## Table of Contents
1. [What is JWT?](#1-what-is-jwt)
2. [JWT Structure](#2-jwt-structure)
3. [Your JWT Service](#3-your-jwt-service)
4. [Token Generation](#4-token-generation)
5. [Token Validation](#5-token-validation)
6. [Complete Login Flow](#6-complete-login-flow)
7. [Complete Logout Flow](#7-complete-logout-flow)
8. [Token Storage (Cookie vs Header)](#8-token-storage-cookie-vs-header)
9. [Security Considerations](#9-security-considerations)
10. [Testing JWT](#10-testing-jwt)

---

## 1. What is JWT?

### 1.1 Definition

**JWT** (JSON Web Token) is a compact, self-contained way to securely transmit information between parties as a JSON object.

**Purpose:**
- **Stateless authentication** - No server-side sessions
- **Self-contained** - Token contains all needed information
- **Signed** - Tamper-proof (server can verify authenticity)

### 1.2 Why JWT Instead of Sessions?

**Traditional Session-Based Auth:**
```
Login → Server creates session → Session stored in memory/database
Each request → Session ID sent → Server looks up session
Logout → Server deletes session
```

**Problems:**
- Requires server-side storage
- Not scalable (need shared session store for multiple servers)
- Stateful (server must remember logged-in users)

**Your JWT-Based Auth:**
```
Login → Server generates JWT → JWT sent to client
Each request → JWT sent → Server validates JWT (no lookup)
Logout → Client deletes JWT
```

**Benefits:**
- **Stateless** - No server-side storage
- **Scalable** - Any server can validate token
- **Cross-domain** - Works across different domains
- **Mobile-friendly** - Easy to use in mobile apps

---

## 2. JWT Structure

### 2.1 Three Parts

JWT consists of three parts separated by dots (`.`):

```
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJqb2huQGV4YW1wbGUuY29tIiwiaWF0IjoxNzMwNzI4MDAwLCJleHAiOjE3MzA3MzE2MDB9.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c
│                      │                                                                                   │
└─ Header             └─ Payload                                                                          └─ Signature
```

### 2.2 Header

**Content (Base64 encoded):**
```json
{
  "alg": "HS256",
  "typ": "JWT"
}
```

**Fields:**
- `alg` - Signing algorithm (HMAC SHA-256)
- `typ` - Token type (JWT)

### 2.3 Payload (Claims)

**Your JWT payload:**
```json
{
  "sub": "john@example.com",      // Subject (username/email)
  "iat": 1730728000,               // Issued At (timestamp)
  "exp": 1730731600                // Expiration (timestamp)
}
```

**Claims:**
- `sub` (subject) - User identifier (email in your case)
- `iat` (issued at) - When token was created
- `exp` (expiration) - When token expires

**Your token expires after 1 hour** (3600000 milliseconds).

### 2.4 Signature

**How signature is created:**
```
HMACSHA256(
  base64UrlEncode(header) + "." + base64UrlEncode(payload),
  secret
)
```

**Purpose:**
- **Verify integrity** - Detect if token was tampered with
- **Verify authenticity** - Only server with secret key can create valid tokens

**Example:**
```
Header:    eyJhbGciOiJIUzI1NiJ9
Payload:   eyJzdWIiOiJqb2huQGV4YW1wbGUuY29tIiwiaWF0IjoxNzMwNzI4MDAwLCJleHAiOjE3MzA3MzE2MDB9
Secret:    your_secret_key_from_env

Signature: HMACSHA256(header + "." + payload, secret)
Result:    SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c
```

---

## 3. Your JWT Service

### 3.1 Configuration

**Your JwtService:**
**`JwtService.java:22-26`**
```java
@Service
public class JwtService {

    @Value("${security.jwt.secret-key}")
    private String secretKey;  // From environment variable

    @Value("${security.jwt.expiration-time}")
    private long jwtExpiration;  // 3600000 ms (1 hour)
```

**From application.properties:**
```properties
security.jwt.secret-key=${JWT_SECRET_KEY}
security.jwt.expiration-time=${JWT_EXPIRATION:3600000}
```

### 3.2 Secret Key Validation

**Your startup validation:**
**`JwtService.java:32-61`**
```java
@PostConstruct
public void validateSecretKey() {
    if (secretKey == null || secretKey.trim().isEmpty()) {
        throw new IllegalStateException(
            "CRITICAL SECURITY ERROR: JWT_SECRET_KEY environment variable must be set!"
        );
    }

    try {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        if (keyBytes.length < 32) {  // 256 bits minimum for HS256
            throw new IllegalStateException(
                "CRITICAL SECURITY ERROR: JWT_SECRET_KEY must be at least 256 bits (32 bytes). " +
                "Current key length: " + keyBytes.length + " bytes."
            );
        }
    } catch (IllegalArgumentException e) {
        throw new IllegalStateException(
            "CRITICAL SECURITY ERROR: JWT_SECRET_KEY must be valid Base64.",
            e
        );
    }

    System.out.println("✅ JWT secret key validated successfully");
}
```

**Why validation?**
- Prevents app from starting without secret key
- Ensures key is strong enough (256 bits minimum)
- Catches configuration errors early

**Generate secure key:**
```bash
openssl rand -base64 32
# Output: 7JnMTZ8F9xK2pL3qR4sT5uV6wX7yZ8aB9cD0eF1gH2i=
```

---

## 4. Token Generation

### 4.1 Generate Token Method

**Your JwtService:**
**`JwtService.java:72-78`**
```java
public String generateToken(UserDetails userDetails) {
    return generateToken(new HashMap<>(), userDetails);
}

public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
    return buildToken(extraClaims, userDetails, jwtExpiration);
}
```

### 4.2 Build Token Method

**`JwtService.java:80-90`**
```java
private String buildToken(Map<String, Object> extraClaims, UserDetails userDetails, long expiration) {
    long currentTimeMillis = System.currentTimeMillis();
    return Jwts
            .builder()
            .claims(extraClaims)                    // Custom claims (empty in your case)
            .subject(userDetails.getUsername())     // Subject: user's email
            .issuedAt(new Date(currentTimeMillis))  // Issued at: now
            .expiration(new Date(currentTimeMillis + expiration))  // Expires: now + 1 hour
            .signWith(getSignInKey())               // Sign with secret key
            .compact();                             // Build final JWT string
}
```

**Step-by-step:**
1. Get current timestamp
2. Create JWT builder
3. Add custom claims (if any)
4. Set subject (user email)
5. Set issued-at time (now)
6. Set expiration time (now + 1 hour)
7. Sign with secret key
8. Compact to string

### 4.3 Signing Key

**`JwtService.java:114-117`**
```java
private Key getSignInKey() {
    byte[] keyBytes = Decoders.BASE64.decode(secretKey);
    return Keys.hmacShaKeyFor(keyBytes);
}
```

**Process:**
1. Decode Base64 secret key to bytes
2. Create HMAC key for signing

### 4.4 Example Token Generation

**Your AuthController login:**
```java
@PostMapping("/login")
public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
    var user = userService.authenticate(request);  // Verify password

    // Generate JWT
    String token = jwtService.generateToken(user);

    // Result:
    // eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJqb2huQGV4YW1wbGUuY29tIiwiaWF0IjoxNzMwNzI4MDAwLCJleHAiOjE3MzA3MzE2MDB9.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c

    return ResponseEntity.ok(new AuthResponse(token, user.getUsername(), role));
}
```

---

## 5. Token Validation

### 5.1 Validate Token Method

**Your JwtService:**
**`JwtService.java:92-95`**
```java
public boolean isTokenValid(String token, UserDetails userDetails) {
    final String username = extractUsername(token);
    return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
}
```

**Validation checks:**
1. Extract username from token
2. Check username matches expected user
3. Check token is not expired

### 5.2 Extract Username

**`JwtService.java:63-65`**
```java
public String extractUsername(String token) {
    return extractClaim(token, Claims::getSubject);
}
```

**Extract any claim:**
**`JwtService.java:67-70`**
```java
public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
    final Claims claims = extractAllClaims(token);
    return claimsResolver.apply(claims);
}
```

### 5.3 Extract All Claims

**`JwtService.java:105-112`**
```java
private Claims extractAllClaims(String token) {
    return Jwts
            .parser()
            .verifyWith((javax.crypto.SecretKey) getSignInKey())  // Verify signature
            .build()
            .parseSignedClaims(token)                             // Parse token
            .getPayload();                                        // Get claims
}
```

**What happens:**
1. Create JWT parser
2. Set verification key (your secret)
3. Parse token (verifies signature automatically)
4. Extract payload (claims)

**If signature is invalid:**
```
io.jsonwebtoken.security.SignatureException: JWT signature does not match
```

**If token is expired:**
```
io.jsonwebtoken.ExpiredJwtException: JWT expired at 2025-11-04T10:30:00Z
```

### 5.4 Check Expiration

**`JwtService.java:97-103`**
```java
private boolean isTokenExpired(String token) {
    return extractExpiration(token).before(new Date());
}

private Date extractExpiration(String token) {
    return extractClaim(token, Claims::getExpiration);
}
```

**Example:**
```java
// Token created: 2025-11-04 10:00:00
// Expiration:     2025-11-04 11:00:00 (1 hour later)
// Current time:   2025-11-04 10:30:00

extractExpiration(token);  // Returns: 2025-11-04 11:00:00
isTokenExpired(token);     // Returns: false (not expired yet)

// Current time:   2025-11-04 11:30:00
isTokenExpired(token);     // Returns: true (expired)
```

---

## 6. Complete Login Flow

### 6.1 End-to-End Flow

```
┌─────────────────────────────────────────────────────────────────────┐
│  1. User enters credentials in frontend                             │
│     username: john@example.com                                      │
│     password: SecurePass123!                                        │
└────────────────────────┬────────────────────────────────────────────┘
                         │
                         ↓
┌─────────────────────────────────────────────────────────────────────┐
│  2. Frontend sends POST request                                     │
│     POST /auth/login                                                │
│     {                                                               │
│       "username": "john@example.com",                               │
│       "password": "SecurePass123!"                                  │
│     }                                                               │
└────────────────────────┬────────────────────────────────────────────┘
                         │
                         ↓
┌─────────────────────────────────────────────────────────────────────┐
│  3. AuthController.login() receives request                         │
└────────────────────────┬────────────────────────────────────────────┘
                         │
                         ↓
┌─────────────────────────────────────────────────────────────────────┐
│  4. UserService.authenticate(request)                               │
│     - Find user by email                                            │
│     - Check if banned                                               │
│     - Verify password with BCrypt                                   │
└────────────────────────┬────────────────────────────────────────────┘
                         │
                         ↓
┌─────────────────────────────────────────────────────────────────────┐
│  5. AuthenticationManager.authenticate()                            │
│     UsernamePasswordAuthenticationToken created                     │
│     DaoAuthenticationProvider verifies password                     │
└────────────────────────┬────────────────────────────────────────────┘
                         │
                         ↓
┌─────────────────────────────────────────────────────────────────────┐
│  6. JwtService.generateToken(user)                                  │
│     Creates JWT with:                                               │
│     - subject: john@example.com                                     │
│     - iat: 1730728000                                               │
│     - exp: 1730731600 (1 hour later)                                │
│     - Signs with secret key                                         │
│                                                                     │
│     Result: eyJhbGciOiJIUzI1NiJ9.eyJzdWI...                         │
└────────────────────────┬────────────────────────────────────────────┘
                         │
                         ↓
┌─────────────────────────────────────────────────────────────────────┐
│  7. Create HTTP-only cookie                                         │
│     ResponseCookie.from("jwt", token)                               │
│       .httpOnly(true)      - JavaScript can't access                │
│       .secure(true)        - Only sent over HTTPS                   │
│       .sameSite("Strict")  - CSRF protection                        │
│       .maxAge(86400)       - 24 hours                               │
└────────────────────────┬────────────────────────────────────────────┘
                         │
                         ↓
┌─────────────────────────────────────────────────────────────────────┐
│  8. Return response                                                 │
│     HTTP/1.1 200 OK                                                 │
│     Set-Cookie: jwt=eyJhbGc...; HttpOnly; Secure; SameSite=Strict   │
│                                                                     │
│     {                                                               │
│       "token": "eyJhbGc...",                                        │
│       "username": "john@example.com",                               │
│       "role": "ROLE_USER"                                           │
│     }                                                               │
└────────────────────────┬────────────────────────────────────────────┘
                         │
                         ↓
┌─────────────────────────────────────────────────────────────────────┐
│  9. Frontend stores token                                           │
│     - Cookie stored by browser automatically                        │
│     - Can also store in localStorage (for Authorization header)     │
└─────────────────────────────────────────────────────────────────────┘
```

### 6.2 Your Login Code

**AuthController:**
**`AuthController.java:80-104`**
```java
@PostMapping("/login")
public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request, HttpServletResponse response) {
    // 1. Authenticate user (verify password)
    var user = userService.authenticate(request);

    // 2. Create authentication token for Spring Security
    Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
    );
    SecurityContextHolder.getContext().setAuthentication(authentication);

    // 3. Generate JWT
    String token = jwtService.generateToken(user);

    // 4. Extract role
    String role = authentication.getAuthorities()
        .iterator()
        .next()
        .getAuthority();

    // 5. Create HTTP-only cookie
    ResponseCookie cookie = ResponseCookie.from("jwt", token)
        .httpOnly(true)
        .secure(cookieSecure)         // true in production
        .sameSite(cookieSameSite)     // "Strict"
        .path("/")
        .maxAge(24 * 60 * 60)         // 24 hours
        .build();

    // 6. Set cookie in response
    response.addHeader("Set-Cookie", cookie.toString());

    // 7. Return response body
    return ResponseEntity.ok(new AuthResponse(token, request.getUsername(), role));
}
```

---

## 7. Complete Logout Flow

### 7.1 End-to-End Flow

```
┌─────────────────────────────────────────────────────────────────────┐
│  1. User clicks logout in frontend                                  │
└────────────────────────┬────────────────────────────────────────────┘
                         │
                         ↓
┌─────────────────────────────────────────────────────────────────────┐
│  2. Frontend sends POST request                                     │
│     POST /auth/logout                                               │
│     Cookie: jwt=eyJhbGc...                                          │
└────────────────────────┬────────────────────────────────────────────┘
                         │
                         ↓
┌─────────────────────────────────────────────────────────────────────┐
│  3. AuthController.logout() receives request                        │
└────────────────────────┬────────────────────────────────────────────┘
                         │
                         ↓
┌─────────────────────────────────────────────────────────────────────┐
│  4. Clear JWT cookie                                                │
│     Create cookie with maxAge=0 (expires immediately)               │
└────────────────────────┬────────────────────────────────────────────┘
                         │
                         ↓
┌─────────────────────────────────────────────────────────────────────┐
│  5. Clear SecurityContext                                           │
│     SecurityContextHolder.clearContext()                            │
│     (Removes authentication for current request)                    │
└────────────────────────┬────────────────────────────────────────────┘
                         │
                         ↓
┌─────────────────────────────────────────────────────────────────────┐
│  6. Return success response                                         │
│     HTTP/1.1 200 OK                                                 │
│     Set-Cookie: jwt=; HttpOnly; Secure; MaxAge=0                    │
│                                                                     │
│     "Logged out successfully"                                       │
└────────────────────────┬────────────────────────────────────────────┘
                         │
                         ↓
┌─────────────────────────────────────────────────────────────────────┐
│  7. Browser deletes cookie                                          │
│     Cookie expired, removed from storage                            │
└─────────────────────────────────────────────────────────────────────┘
```

### 7.2 Your Logout Code

**AuthController:**
**`AuthController.java:106-120`**
```java
@PostMapping("/logout")
public ResponseEntity<String> logout(HttpServletResponse response) {
    // 1. Clear the JWT cookie by setting maxAge to 0
    ResponseCookie cookie = ResponseCookie.from("jwt", "")
        .httpOnly(true)
        .secure(cookieSecure)
        .sameSite(cookieSameSite)
        .path("/")
        .maxAge(0)  // Expire immediately
        .build();

    // 2. Set cookie in response
    response.addHeader("Set-Cookie", cookie.toString());

    // 3. Clear SecurityContext (for current request)
    SecurityContextHolder.clearContext();

    // 4. Return success message
    return ResponseEntity.ok("Logged out successfully");
}
```

**Important Notes:**
- JWT is stateless - server doesn't track logged-in users
- Logout just removes cookie from client
- Token remains valid until expiration (1 hour)
- For immediate invalidation, need token blacklist (not implemented)

---

## 8. Token Storage (Cookie vs Header)

### 8.1 Cookie Storage (Your Primary Method)

**Your approach:**
```java
ResponseCookie cookie = ResponseCookie.from("jwt", token)
    .httpOnly(true)      // JavaScript can't access
    .secure(true)        // Only HTTPS
    .sameSite("Strict")  // CSRF protection
    .build();

response.addHeader("Set-Cookie", cookie.toString());
```

**Client-side (automatic):**
```
Browser stores cookie
Every request includes cookie automatically:
  GET /users
  Cookie: jwt=eyJhbGc...
```

**Advantages:**
- **HttpOnly** - Immune to XSS (JavaScript can't steal token)
- **Automatic** - Browser sends cookie on every request
- **SameSite** - Protection against CSRF

**Disadvantages:**
- Only works for same domain
- Requires CORS configuration

### 8.2 Authorization Header (Fallback)

**Your filter also supports:**
**`JwtAuthenticationFilter.java:63-68`**
```java
if (jwt == null) {
    final String authHeader = request.getHeader("Authorization");
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
        jwt = authHeader.substring(7);
    }
}
```

**Client-side (manual):**
```javascript
// Store token in localStorage
localStorage.setItem('jwt', token);

// Send with every request
fetch('/api/users', {
  headers: {
    'Authorization': `Bearer ${token}`
  }
});
```

**Advantages:**
- Works cross-domain
- Explicit control
- Mobile-friendly

**Disadvantages:**
- Vulnerable to XSS (JavaScript can access localStorage)
- Must manually include in every request

### 8.3 Your Hybrid Approach

**Your filter tries both:**
1. **First**: Check for JWT in cookie (secure)
2. **Fallback**: Check Authorization header (compatibility)

```java
// Try cookie first
if (request.getCookies() != null) {
    for (Cookie cookie : request.getCookies()) {
        if ("jwt".equals(cookie.getName())) {
            jwt = cookie.getValue();
            break;
        }
    }
}

// Fallback to header
if (jwt == null) {
    final String authHeader = request.getHeader("Authorization");
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
        jwt = authHeader.substring(7);
    }
}
```

---

## 9. Security Considerations

### 9.1 Token Expiration

**Your configuration:**
```properties
security.jwt.expiration-time=3600000  # 1 hour
```

**Considerations:**
- **Short expiration** (1 hour) - More secure, but user re-logs often
- **Long expiration** (1 week) - Convenient, but if stolen, valid longer
- **Refresh tokens** (not implemented) - Long-lived tokens to get new access tokens

### 9.2 Secret Key Security

**Your validation:**
```java
if (keyBytes.length < 32) {  // 256 bits minimum
    throw new IllegalStateException("Key too short!");
}
```

**Best practices:**
- **Generate securely**: `openssl rand -base64 32`
- **Store in environment variable**: Never commit to Git
- **Rotate periodically**: Change secret every 6-12 months
- **Different per environment**: Dev, staging, production

### 9.3 HTTPS Required

**Your cookie config:**
```java
.secure(cookieSecure)  // true in production
```

**Why HTTPS?**
- JWT in cookie sent over network
- Without HTTPS, attacker can intercept token
- With HTTPS, encrypted communication

### 9.4 Token Revocation

**Current limitation:**
- Once issued, token valid until expiration
- No way to immediately invalidate token
- User logs out, but token still works if stolen

**Solutions (not implemented):**

**1. Token Blacklist:**
```java
@Service
public class TokenBlacklistService {
    private Set<String> blacklistedTokens = new HashSet<>();

    public void blacklist(String token) {
        blacklistedTokens.add(token);
    }

    public boolean isBlacklisted(String token) {
        return blacklistedTokens.contains(token);
    }
}

// In filter:
if (tokenBlacklistService.isBlacklisted(jwt)) {
    // Reject request
    return;
}
```

**2. Short-Lived Tokens + Refresh Tokens:**
```
Access Token: 15 minutes (short)
Refresh Token: 7 days (long)

Token expires → Use refresh token → Get new access token
Refresh token stolen → Can revoke from database
```

---

## 10. Testing JWT

### 10.1 Decode JWT (jwt.io)

**Visit:** https://jwt.io

**Paste your token:**
```
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJqb2huQGV4YW1wbGUuY29tIiwiaWF0IjoxNzMwNzI4MDAwLCJleHAiOjE3MzA3MzE2MDB9.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c
```

**See decoded:**
```json
// Header
{
  "alg": "HS256",
  "typ": "JWT"
}

// Payload
{
  "sub": "john@example.com",
  "iat": 1730728000,
  "exp": 1730731600
}
```

### 10.2 Test with cURL

**Login:**
```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"john@example.com","password":"SecurePass123!"}' \
  -c cookies.txt

# -c cookies.txt saves cookie to file
```

**Use token:**
```bash
curl http://localhost:8080/users \
  -b cookies.txt

# -b cookies.txt sends cookie from file
```

**Or with Authorization header:**
```bash
TOKEN="eyJhbGc..."

curl http://localhost:8080/users \
  -H "Authorization: Bearer $TOKEN"
```

### 10.3 Test Expiration

**Generate token with short expiration (testing):**
```java
public String generateTestToken(UserDetails userDetails) {
    return buildToken(new HashMap<>(), userDetails, 5000);  // 5 seconds
}
```

**Test:**
```bash
# Get token
TOKEN=$(curl ... | jq -r '.token')

# Use immediately (works)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/users

# Wait 6 seconds
sleep 6

# Use again (fails - expired)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/users
# Error: 401 Unauthorized
```

---

## Key Takeaways

### What You Learned

1. **JWT Structure**
   - Header (algorithm)
   - Payload (claims: sub, iat, exp)
   - Signature (tamper-proof)

2. **Token Generation**
   - JwtService.generateToken()
   - Sets subject (user email), issued-at, expiration
   - Signs with secret key

3. **Token Validation**
   - Extract claims from token
   - Verify signature (prevents tampering)
   - Check expiration
   - Verify username matches

4. **Login Flow**
   - Verify credentials
   - Generate JWT
   - Store in HttpOnly cookie
   - Return token to client

5. **Logout Flow**
   - Clear cookie (maxAge=0)
   - Clear SecurityContext
   - Token remains valid until expiration (limitation)

6. **Token Storage**
   - **Cookie** (preferred) - HttpOnly, Secure, SameSite
   - **Header** (fallback) - Authorization: Bearer {token}
   - Your filter supports both

7. **Security**
   - HTTPS required
   - Strong secret key (256 bits)
   - Short expiration (1 hour)
   - Token revocation needs blacklist or refresh tokens

---

## What's Next?

You now understand JWT authentication completely. Last document:

**→ [11-EXCEPTION-HANDLING.md](./11-EXCEPTION-HANDLING.md)** - Global error handling, custom exceptions

**Key Questions for Next Section:**
- How to handle errors globally?
- What are custom exceptions?
- How to return consistent error responses?
- How to handle validation errors?

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
- ✅ JWT Authentication

**Next**: Exception Handling (Final Document!) 🎯
