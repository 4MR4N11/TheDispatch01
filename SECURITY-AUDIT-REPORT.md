# Security Audit Report - The Dispatch

**Generated:** 2025-11-30
**Application:** The Dispatch (Blog Platform)
**Stack:** Spring Boot 3.5.6 + Angular 20.3.7 + PostgreSQL
**Audit Scope:** Full Application Security Review

---

## Executive Summary

This comprehensive security audit identified **12 security issues** across the application, ranging from **CRITICAL to LOW** severity. The application has already implemented several strong security measures, but critical vulnerabilities remain that require immediate attention.

### Severity Breakdown
- **CRITICAL**: 2 findings
- **HIGH**: 3 findings
- **MEDIUM**: 4 findings
- **LOW**: 3 findings

### Overall Security Posture: **MODERATE**

The application demonstrates good security practices in authentication (JWT with HttpOnly cookies, BCrypt password hashing) and input validation (Bean Validation, custom validators). However, critical dependency vulnerabilities and configuration issues pose significant risks.

---

## Critical Findings (Immediate Action Required)

### 1. Angular XSRF Token Leakage Vulnerability (CRITICAL)
**CVE:** GHSA-58c5-g7wp-6w37
**Affected Components:** @angular/common, @angular/forms, @angular/platform-browser, @angular/router
**Current Version:** 20.3.7
**Fixed Version:** 20.3.14+

**Risk:**
- XSRF tokens can be leaked via protocol-relative URLs
- Allows attackers to bypass CSRF protection
- Could lead to unauthorized actions on behalf of authenticated users

**Remediation:**
```bash
cd frontend
npm update @angular/common @angular/forms @angular/platform-browser @angular/router
# Or upgrade to Angular 20.3.14+
```

**Priority:** IMMEDIATE (Fix within 24 hours)

---

### 2. Body-Parser Denial of Service Vulnerability (CRITICAL)
**CVE:** GHSA-wqch-xfxh-vrr4
**Affected Component:** body-parser (transitive dependency)
**CVSS Score:** 5.3 (Medium, but critical for availability)

**Risk:**
- DoS attack possible when URL encoding is used
- Could render application unavailable
- Affects indirect dependency through Express/middleware

**Remediation:**
```bash
cd frontend
npm audit fix
# May require updating parent packages
```

**Priority:** IMMEDIATE (Fix within 48 hours)

---

## High Severity Findings

### 3. Weak Default JWT Secret in Docker Configuration (HIGH)
**Location:** `docker-compose.yml:37`

**Issue:**
```yaml
JWT_SECRET_KEY: ${JWT_SECRET_KEY:-dGhpc2lzYXNlY3VyZXNlY3JldGtleWZvcmp3dHRva2VuZ2VuZXJhdGlvbg==}
```

The default JWT secret is a Base64-encoded string "thisisasecuresecretkeyforjwttokengeneration" which is:
- Publicly visible in repository
- Not cryptographically random
- Could be used to forge JWTs if production uses default

**Impact:**
- Complete authentication bypass if default is used in production
- Attackers can create admin tokens
- Full account takeover possible

**Remediation:**
1. Remove default value from docker-compose.yml:
```yaml
JWT_SECRET_KEY: ${JWT_SECRET_KEY}  # No default!
```

2. Generate strong secret:
```bash
openssl rand -base64 64 > jwt_secret.txt
```

3. Add to production environment variables (never commit)

4. Update .env.example to clearly warn:
```
# CRITICAL: Generate with: openssl rand -base64 64
# NEVER use default values in production!
JWT_SECRET_KEY=your_jwt_secret_key_here
```

**Priority:** HIGH (Fix before production deployment)

---

### 4. Database Credentials Hardcoded in Docker Compose (HIGH)
**Location:** `docker-compose.yml:9-11, 32-34`

**Issue:**
```yaml
environment:
  POSTGRES_DB: blog
  POSTGRES_USER: blog
  POSTGRES_PASSWORD: blog  # ❌ Weak password hardcoded
```

**Impact:**
- Default credentials are trivial to guess
- Anyone with network access can access database
- Data breach, data manipulation, or deletion possible

**Remediation:**
1. Use environment variables for all credentials:
```yaml
environment:
  POSTGRES_DB: ${POSTGRES_DB}
  POSTGRES_USER: ${POSTGRES_USER}
  POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
```

2. Generate strong passwords:
```bash
openssl rand -base64 32
```

3. Store in .env (gitignored):
```
POSTGRES_DB=blog
POSTGRES_USER=blog_user
POSTGRES_PASSWORD=<generated_strong_password>
```

**Priority:** HIGH (Fix before production deployment)

---

### 5. Missing Rate Limiting on Authentication Endpoints (HIGH)
**Locations:**
- `backend/src/main/java/_blog/blog/controller/AuthController.java:80` (login)
- `backend/src/main/java/_blog/blog/controller/AuthController.java:43` (register)

**Issue:**
No rate limiting on login/register endpoints allows:
- Brute force attacks on user accounts
- Credential stuffing attacks
- Account enumeration
- Resource exhaustion (DoS)

**Impact:**
- Account takeover through password guessing
- Service degradation from excessive requests

**Remediation:**

Add Spring Security rate limiting with Bucket4j:

1. Add dependency to `pom.xml`:
```xml
<dependency>
    <groupId>com.github.vladimir-bukhtoyarov</groupId>
    <artifactId>bucket4j-core</artifactId>
    <version>8.7.0</version>
</dependency>
```

2. Create rate limiter filter:
```java
@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private final Map<String, Bucket> cache = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                   HttpServletResponse response,
                                   FilterChain filterChain) throws ServletException, IOException {
        String path = request.getServletPath();

        if (path.matches("/auth/(login|register)")) {
            String key = getClientIP(request);
            Bucket bucket = resolveBucket(key);

            if (bucket.tryConsume(1)) {
                filterChain.doFilter(request, response);
            } else {
                response.setStatus(429);
                response.getWriter().write("Too many requests");
            }
        } else {
            filterChain.doFilter(request, response);
        }
    }

    private Bucket resolveBucket(String key) {
        return cache.computeIfAbsent(key, k -> {
            // 5 requests per minute
            Bandwidth limit = Bandwidth.simple(5, Duration.ofMinutes(1));
            return Bucket.builder().addLimit(limit).build();
        });
    }
}
```

**Priority:** HIGH (Implement within 1 week)

---

## Medium Severity Findings

### 6. Insufficient Password Complexity Enforcement (MEDIUM)
**Location:** `backend/src/main/java/_blog/blog/validation/StrongPasswordValidator.java:24-25`

**Issue:**
Current password pattern requires:
- 8+ characters
- 1 uppercase, 1 lowercase, 1 digit, 1 special char
- Blocks only 7 common passwords

However:
- No maximum length limit (could allow DoS via bcrypt)
- Limited common password list
- Special characters limited to `@$!%*?&`

**Recommendations:**

1. Add maximum length (72 chars for BCrypt):
```java
@Size(min = 8, max = 72, message = "Password must be 8-72 characters")
@StrongPassword
private String password;
```

2. Expand common passwords list or use a library like `passay`:
```xml
<dependency>
    <groupId>org.passay</groupId>
    <artifactId>passay</artifactId>
    <version>1.6.4</version>
</dependency>
```

3. Consider adding password strength meter on frontend

**Priority:** MEDIUM (Enhance within 2 weeks)

---

### 7. No Protection Against Account Enumeration (MEDIUM)
**Location:** `backend/src/main/java/_blog/blog/controller/AuthController.java`

**Issue:**
Login errors may reveal whether username exists:
- Different error messages for "user not found" vs "wrong password"
- Timing attacks possible (database lookup vs password check)

**Impact:**
- Attackers can enumerate valid usernames
- Facilitates targeted attacks

**Remediation:**

1. Use constant-time responses:
```java
@PostMapping("/login")
public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request, HttpServletResponse response) {
    try {
        var user = userService.authenticate(request);
        // ... success logic
    } catch (Exception e) {
        // Generic error message
        return ResponseEntity.status(401)
            .body(new AuthResponse("Invalid credentials"));
    }
}
```

2. Add artificial delay (constant time):
```java
try {
    Thread.sleep(100 + new Random().nextInt(100)); // 100-200ms
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
}
```

**Priority:** MEDIUM (Fix within 2 weeks)

---

### 8. Missing Security Headers in Production (MEDIUM)
**Location:** `backend/src/main/java/_blog/blog/config/SecurityConfig.java:62-73`

**Issue:**
While some security headers are configured, the following are missing:
- `Referrer-Policy`
- `Permissions-Policy`
- `X-Content-Type-Options` (currently disabled)

**Current CSP is also restrictive and may block legitimate content:**
```java
.policyDirectives("default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self' data:;")
```

**Recommendations:**

1. Enable X-Content-Type-Options:
```java
.contentTypeOptions(contentType -> contentType.disable())  // ❌ Should enable
```

Change to:
```java
.contentTypeOptions(Customizer.withDefaults())  // ✅ Enables MIME sniffing protection
```

2. Add missing headers:
```java
.headers(headers -> headers
    .frameOptions(frame -> frame.deny())
    .contentTypeOptions(Customizer.withDefaults())  // Fix
    .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
    .permissionsPolicy(permissions -> permissions.policy(
        "geolocation=(), microphone=(), camera=()"
    ))
    .httpStrictTransportSecurity(hsts -> hsts
        .includeSubDomains(true)
        .maxAgeInSeconds(31536000)
    )
    .contentSecurityPolicy(csp -> csp
        .policyDirectives("default-src 'self'; " +
            "script-src 'self' 'unsafe-inline'; " +  // May need adjustment for Angular
            "style-src 'self' 'unsafe-inline'; " +
            "img-src 'self' data: http://localhost:8080; " +
            "font-src 'self' data:; " +
            "connect-src 'self'; " +
            "frame-ancestors 'none';")
    )
)
```

**Priority:** MEDIUM (Implement before production)

---

### 9. Potential Path Traversal in File Upload (MEDIUM)
**Location:** `backend/src/main/java/_blog/blog/controller/UploadController.java:98`

**Issue:**
While `FileValidator.sanitizeFilename()` removes path separators, there's no explicit check preventing path traversal in the upload path itself.

**Current code:**
```java
Path filePath = uploadDir.resolve(filename).normalize();
```

**Vulnerability:**
If `uploadDir` or `filename` manipulation bypasses sanitization, files could be written outside intended directory.

**Remediation:**

Add explicit path traversal check:
```java
Path uploadDir = Paths.get(uploadPath).toAbsolutePath().normalize();
Files.createDirectories(uploadDir);

String ext = FileValidator.getExtension(file.getOriginalFilename());
String filename = UUID.randomUUID() + "." + ext;  // Already using UUID - good
Path filePath = uploadDir.resolve(filename).normalize();

// ✅ Add explicit check
if (!filePath.startsWith(uploadDir)) {
    return ResponseEntity.badRequest()
        .body(Map.of("success", 0, "error", "Invalid file path"));
}
```

**Priority:** MEDIUM (Fix within 2 weeks)

---

## Low Severity Findings

### 10. Verbose Error Messages in Development (LOW)
**Location:** Various controller exception handlers

**Issue:**
Some error responses may expose internal details:
```java
"Failed to upload file: " + e.getMessage()  // May expose paths/internals
```

**Recommendation:**
- Use generic error messages in production
- Log detailed errors server-side only
- Configure logging levels via environment

**Priority:** LOW (Address during code review)

---

### 11. Missing Request Size Limits on Specific Endpoints (LOW)
**Location:** `application.properties:25-27, 35-36`

**Issue:**
Global limits are set, but individual endpoints (comments, posts) don't enforce stricter limits.

**Current:**
```properties
server.tomcat.max-http-post-size=10MB
spring.servlet.multipart.max-file-size=10MB
```

**Recommendation:**
Add per-endpoint validation:
```java
@PostMapping("/create")
public ResponseEntity<String> createPost(
    @Valid @RequestBody PostRequest request,
    Authentication auth) {

    if (request.getContent().length() > 50000) {
        return ResponseEntity.badRequest()
            .body("Content too large");
    }
    // ...
}
```

**Priority:** LOW (Optional enhancement)

---

### 12. Information Disclosure via Console Logging (LOW)
**Location:** `backend/src/main/java/_blog/blog/service/JwtService.java:59`

**Issue:**
```java
System.out.println("✅ JWT secret key validated successfully (length: " +
    Decoders.BASE64.decode(secretKey).length + " bytes)");
```

While not exposing the secret itself, this confirms JWT configuration details.

**Recommendation:**
Replace with proper logging:
```java
log.info("JWT secret key validated (length: {} bytes)", keyLength);
```

**Priority:** LOW (Code quality improvement)

---

## Positive Security Findings (Strengths)

### ✅ Strong Authentication Implementation
1. **JWT with HttpOnly Cookies** (AuthService.ts:21-24)
   - Tokens stored in HttpOnly, Secure, SameSite cookies
   - Prevents XSS token theft
   - Proper cookie-based authentication

2. **BCrypt Password Hashing** (SecurityConfig.java:102-104)
   - Industry-standard password hashing
   - Automatic salt generation
   - Computationally expensive (mitigates brute force)

3. **Strong Password Validation** (StrongPasswordValidator.java)
   - Minimum 8 characters
   - Complexity requirements enforced
   - Common passwords blocked

### ✅ Proper Input Validation
1. **Bean Validation** throughout DTOs
   - `@NotBlank`, `@Email`, `@Size` constraints
   - Custom validators for passwords
   - Pattern validation for media URLs

2. **File Upload Validation** (FileValidator.java)
   - Magic byte verification for images
   - File size limits enforced
   - Extension whitelisting
   - Filename sanitization

### ✅ SQL Injection Protection
1. **JPA/JPQL Parameterized Queries** (all Repository classes)
   - All database queries use `@Query` with `@Param`
   - No string concatenation in queries
   - Proper entity relationships

### ✅ Authorization Controls
1. **Method-level Security** (PostController.java:179)
   - `@PreAuthorize` annotations
   - Role-based access control (RBAC)
   - Ownership verification before actions

### ✅ Updated Dependencies
1. **JWT Library Updated** (pom.xml:54-71)
   - JJWT 0.12.5 (latest secure version)
   - Recent Spring Boot 3.5.6
   - Security fixes applied

---

## Compliance & Best Practices Assessment

### OWASP Top 10 (2021) Coverage

| Risk | Status | Notes |
|------|--------|-------|
| A01:2021 - Broken Access Control | ✅ GOOD | Role-based auth, ownership checks |
| A02:2021 - Cryptographic Failures | ⚠️ PARTIAL | Good JWT/BCrypt, but weak defaults |
| A03:2021 - Injection | ✅ GOOD | Parameterized queries, input validation |
| A04:2021 - Insecure Design | ✅ GOOD | Secure architecture, defense in depth |
| A05:2021 - Security Misconfiguration | ⚠️ NEEDS WORK | Weak defaults, missing headers |
| A06:2021 - Vulnerable Components | ❌ CRITICAL | Angular XSRF vuln, body-parser DoS |
| A07:2021 - Identification Failures | ⚠️ PARTIAL | Good auth, but missing rate limiting |
| A08:2021 - Software/Data Integrity | ✅ GOOD | Proper validation, no dynamic code |
| A09:2021 - Logging Failures | ⚠️ PARTIAL | SLF4J used, but some console logging |
| A10:2021 - Server-Side Request Forgery | ✅ N/A | No SSRF vectors identified |

---

## Remediation Roadmap

### Phase 1: Critical (0-48 hours)
1. ✅ Update Angular to 20.3.14+ (XSRF fix)
2. ✅ Fix body-parser vulnerability via npm audit fix
3. ✅ Remove default JWT secret from docker-compose.yml
4. ✅ Implement strong password for database in production

### Phase 2: High Priority (1-2 weeks)
5. ✅ Implement rate limiting on auth endpoints
6. ✅ Fix account enumeration vulnerabilities
7. ✅ Complete security headers configuration

### Phase 3: Medium Priority (2-4 weeks)
8. ✅ Enhanced password validation with Passay
9. ✅ Path traversal protection in file uploads
10. ✅ Review and sanitize error messages

### Phase 4: Low Priority (Ongoing)
11. ✅ Code quality improvements (logging)
12. ✅ Per-endpoint request size validation
13. ✅ Security monitoring and alerting

---

## Security Testing Recommendations

### 1. Automated Testing
- Set up OWASP ZAP or Burp Suite automated scans
- Integrate Snyk or Dependabot for dependency monitoring
- Add SAST tools (SonarQube, Checkmarx)

### 2. Manual Testing
- Penetration testing before production launch
- Security code reviews for all auth/upload code
- Test rate limiting effectiveness

### 3. Monitoring
- Set up security event logging
- Monitor failed login attempts
- Track file upload patterns
- Alert on suspicious activity

---

## Production Deployment Checklist

Before deploying to production, ensure:

- [ ] All CRITICAL and HIGH severity issues resolved
- [ ] Strong, unique secrets generated for JWT and database
- [ ] All secrets stored in secure vault (not .env files)
- [ ] HTTPS enabled with valid certificate
- [ ] `COOKIE_SECURE=true` configured
- [ ] `COOKIE_SAME_SITE=Strict` (if no cross-origin needs)
- [ ] Security headers enabled and tested
- [ ] Rate limiting implemented and tested
- [ ] Database credentials rotated from defaults
- [ ] Logging configured for security events
- [ ] Backup and disaster recovery tested
- [ ] Security monitoring in place

---

## Conclusion

The Dispatch demonstrates **strong foundational security practices** with proper authentication, input validation, and SQL injection prevention. However, **critical dependency vulnerabilities and configuration weaknesses** pose immediate risks that must be addressed before production deployment.

### Key Recommendations:
1. **Immediately** update Angular dependencies (XSRF vulnerability)
2. **Before production**: Remove all default credentials and secrets
3. **High priority**: Implement rate limiting on authentication
4. **Ongoing**: Maintain dependency updates and security monitoring

With the remediation of critical and high-severity findings, this application can achieve a **STRONG** security posture suitable for production deployment.

---

**Report Generated:** 2025-11-30
**Auditor:** Claude Code Security Audit
**Next Review:** Recommended within 90 days or after major changes
