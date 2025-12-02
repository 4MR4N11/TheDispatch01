# Controller Layer Deep Dive

This document explains HOW Spring MVC controllers work, WHAT happens during HTTP request processing, and WHY we structure controllers this way. We'll cover the complete flow from HTTP request to JSON response.

---

## Table of Contents

1. [What Are Controllers?](#what-are-controllers)
2. [Spring MVC Request Flow](#spring-mvc-request-flow)
3. [Controller Annotations Explained](#controller-annotations-explained)
4. [HTTP Methods and Mapping](#http-methods-and-mapping)
5. [Request Parameters Deep Dive](#request-parameters-deep-dive)
6. [ResponseEntity and HTTP Status](#responseentity-and-http-status)
7. [Authentication Integration](#authentication-integration)
8. [Authorization with @PreAuthorize](#authorization-with-preauthorize)
9. [Line-by-Line: AuthController](#line-by-line-authcontroller)
10. [Line-by-Line: UserController](#line-by-line-usercontroller)
11. [Line-by-Line: PostController](#line-by-line-postcontroller)
12. [Line-by-Line: CommentController](#line-by-line-commentcontroller)
13. [DTO Transformation Pattern](#dto-transformation-pattern)
14. [Error Handling](#error-handling)

---

## What Are Controllers?

Controllers are the **entry point** for HTTP requests in your application. They sit at the boundary between the outside world (web browsers, mobile apps, API clients) and your business logic.

### The Three-Layer Architecture

```
┌─────────────────┐
│   Controller    │ ← Handles HTTP requests/responses
├─────────────────┤
│    Service      │ ← Business logic and transactions
├─────────────────┤
│   Repository    │ ← Database access
└─────────────────┘
```

**Controller responsibilities:**
1. **Receive** HTTP requests
2. **Extract** data from request (path, body, parameters)
3. **Validate** input (with `@Valid`)
4. **Call** service layer to do business logic
5. **Transform** entities to DTOs (never expose entities directly!)
6. **Build** HTTP response with appropriate status code
7. **Return** JSON response to client

**Controllers should NOT:**
- Contain business logic (that's service layer)
- Access database directly (that's repository layer)
- Manage transactions (that's service layer with `@Transactional`)

---

## Spring MVC Request Flow

Let's trace EXACTLY what happens when a browser sends `GET /posts/all`:

### Step-by-Step Flow

```
1. Browser sends HTTP request
   ↓
2. Request arrives at Tomcat (embedded web server)
   ↓
3. Tomcat hands request to Spring's DispatcherServlet
   ↓
4. DispatcherServlet asks HandlerMapping: "Which controller handles this URL?"
   ↓
5. HandlerMapping finds: PostController.getAllPosts()
   ↓
6. DispatcherServlet checks if security filters allow this request
   ↓
7. JWT filter extracts token from cookie
   ↓
8. If valid, creates Authentication object and puts in SecurityContext
   ↓
9. DispatcherServlet calls the controller method
   ↓
10. Controller calls service layer
   ↓
11. Service calls repository
   ↓
12. Repository queries database
   ↓
13. Results bubble back up: Repository → Service → Controller
   ↓
14. Controller transforms entities to DTOs
   ↓
15. Controller returns ResponseEntity<List<PostResponse>>
   ↓
16. Jackson (JSON library) converts DTOs to JSON
   ↓
17. DispatcherServlet sends HTTP response with JSON body
   ↓
18. Browser receives JSON and renders it
```

### What Is DispatcherServlet?

**DispatcherServlet** is Spring's "front controller" - a single servlet that receives ALL HTTP requests and routes them to the correct controller method.

Think of it like a receptionist at a company:
- Receives all visitors (HTTP requests)
- Checks the directory (HandlerMapping) to find who should handle them
- Escorts them to the right department (Controller)
- Collects the response and sends it back

### What Is HandlerMapping?

**HandlerMapping** is a registry that Spring builds at startup by scanning all `@RestController` and `@RequestMapping` annotations.

It creates a map like:
```
GET    /posts/all         → PostController.getAllPosts()
POST   /posts/create      → PostController.createPost()
GET    /users/{id}        → UserController.getUser()
DELETE /comments/{id}     → CommentController.deleteComment()
```

When a request comes in, DispatcherServlet looks up the URL+method in this map.

---

## Controller Annotations Explained

### @RestController

```java
@RestController
@RequestMapping("/posts")
public class PostController {
    // ...
}
```

**What is @RestController?**

It's a **combination** of two annotations:
1. `@Controller` - Tells Spring this is a controller bean
2. `@ResponseBody` - Tells Spring to convert return values to JSON automatically

**How it works:**
- At startup, Spring's component scanner finds this class
- Spring creates a singleton bean of PostController
- Spring registers all methods with HandlerMapping
- For every method return value, Spring uses Jackson to convert objects → JSON

**Without @RestController, you'd need:**
```java
@Controller
public class PostController {

    @GetMapping("/all")
    @ResponseBody  // ← You'd need this on EVERY method
    public List<PostResponse> getAllPosts() {
        // ...
    }
}
```

### @RequestMapping

```java
@RequestMapping("/posts")
```

**What it does:**
- Sets a **base URL** for all methods in this controller
- Acts as a prefix for all method-level mappings

**Example:**
```java
@RestController
@RequestMapping("/posts")
public class PostController {

    @GetMapping("/all")  // Full URL: /posts/all
    public List<PostResponse> getAllPosts() { }

    @PostMapping("/create")  // Full URL: /posts/create
    public String createPost() { }
}
```

---

## HTTP Methods and Mapping

### The HTTP Method Annotations

Spring provides specific annotations for each HTTP method:

```java
@GetMapping     // HTTP GET    - Retrieve data (read-only)
@PostMapping    // HTTP POST   - Create new resource
@PutMapping     // HTTP PUT    - Update existing resource
@DeleteMapping  // HTTP DELETE - Remove resource
@PatchMapping   // HTTP PATCH  - Partial update
```

### Why Different HTTP Methods?

**REST principles** say URLs should represent resources, and HTTP methods represent actions:

```
GET    /posts/123    - "Get me post 123"
PUT    /posts/123    - "Update post 123"
DELETE /posts/123    - "Delete post 123"
POST   /posts        - "Create a new post"
```

### How Spring Matches Requests

When a request comes in, Spring matches on **BOTH** URL and HTTP method:

```
Incoming: GET /posts/all
          ↓
Spring looks for: @GetMapping + URL pattern "/posts/all"
          ↓
Finds: PostController.getAllPosts()
```

If you send `POST /posts/all` but only `@GetMapping` exists, you get **405 Method Not Allowed**.

---

## Request Parameters Deep Dive

Controllers extract data from HTTP requests in three main ways:

### 1. @PathVariable - Data from URL Path

```java
@GetMapping("/users/{id}")
public UserResponse getUser(@PathVariable Long id) {
    // If request is GET /users/42
    // id = 42
}
```

**What happens:**
1. Spring sees `{id}` in the URL pattern
2. When request comes in, Spring extracts the value from that position
3. Spring converts the String "42" to Long 42
4. Spring injects it as the method parameter

**Multiple path variables:**
```java
@GetMapping("/users/{userId}/posts/{postId}")
public Post getUserPost(
    @PathVariable Long userId,
    @PathVariable Long postId
) {
    // GET /users/10/posts/50
    // userId = 10, postId = 50
}
```

### 2. @RequestBody - Data from HTTP Body (JSON)

```java
@PostMapping("/posts/create")
public String createPost(@RequestBody PostRequest request) {
    // HTTP POST /posts/create
    // Body: {"title": "Hello", "content": "World"}
    // request.getTitle() = "Hello"
    // request.getContent() = "World"
}
```

**What happens (JSON Deserialization):**

1. Client sends JSON in HTTP body:
   ```json
   {
     "title": "My Post",
     "content": "This is content"
   }
   ```

2. Spring's **Jackson** library reads the JSON

3. Jackson creates a new `PostRequest` object

4. Jackson uses **reflection** to call setters:
   ```java
   PostRequest request = new PostRequest();
   request.setTitle("My Post");
   request.setContent("This is content");
   ```

5. Spring passes the object to your method

### 3. @RequestParam - Data from Query String

```java
@GetMapping("/posts/search")
public List<Post> search(
    @RequestParam String keyword,
    @RequestParam(defaultValue = "10") int limit
) {
    // GET /posts/search?keyword=java&limit=20
    // keyword = "java"
    // limit = 20
}
```

---

## @Valid - Input Validation

### How @Valid Works

```java
@PostMapping("/create")
public String createPost(@Valid @RequestBody PostRequest request) {
    // ...
}
```

**Step-by-step:**

1. Jackson deserializes JSON → PostRequest object

2. Spring sees `@Valid` annotation

3. Spring's **Validator** checks all constraint annotations on PostRequest:
   ```java
   public class PostRequest {
       @NotBlank(message = "Content is required")
       @Size(min = 1, max = 100000)
       String content;
   }
   ```

4. If validation fails, Spring throws `MethodArgumentNotValidException`

5. Spring automatically returns **400 Bad Request** with error details

6. If validation passes, method executes normally

**Without @Valid:**
- Spring skips validation
- Invalid data reaches your service layer
- You'd need manual validation everywhere

---

## ResponseEntity and HTTP Status

### What Is ResponseEntity?

`ResponseEntity<T>` is a wrapper that lets you control:
- **Response body** (the data)
- **HTTP status code** (200, 404, 500, etc.)
- **HTTP headers** (Content-Type, Cache-Control, etc.)

### Basic Usage

```java
@GetMapping("/{id}")
public ResponseEntity<UserResponse> getUser(@PathVariable Long id) {
    UserResponse user = userService.getUser(id);
    return ResponseEntity.ok(user);
    // Returns: 200 OK with user data in JSON
}
```

### HTTP Status Codes

Common status codes and when to use them:

```java
// ✅ Success responses (2xx)
ResponseEntity.ok(data)                    // 200 OK - Success
ResponseEntity.status(201).body(data)      // 201 Created - Resource created
ResponseEntity.noContent().build()         // 204 No Content - Success but no data

// ❌ Client errors (4xx)
ResponseEntity.badRequest().body(error)    // 400 Bad Request - Invalid input
ResponseEntity.status(401).build()         // 401 Unauthorized - Not authenticated
ResponseEntity.status(403).body(msg)       // 403 Forbidden - Not authorized
ResponseEntity.status(404).body(msg)       // 404 Not Found - Resource doesn't exist

// 💥 Server errors (5xx)
ResponseEntity.status(500).body(error)     // 500 Internal Server Error
```

### Example: Different Status Codes

```java
@DeleteMapping("/{id}")
public ResponseEntity<Map<String, String>> deletePost(
    @PathVariable Long id,
    Authentication auth
) {
    User user = userService.getUserByUsername(auth.getName());
    Post post = postService.getPostById(id);

    // Check authorization
    if (!post.getAuthor().getId().equals(user.getId())) {
        return ResponseEntity
            .status(403)  // 403 Forbidden
            .body(Map.of("message", "Not authorized"));
    }

    // Try to delete
    if (postService.deletePost(id)) {
        return ResponseEntity
            .ok()  // 200 OK
            .body(Map.of("message", "Deleted successfully"));
    } else {
        return ResponseEntity
            .status(404)  // 404 Not Found
            .body(Map.of("message", "Post not found"));
    }
}
```

---

## Authentication Integration

### How Authentication Object Works

```java
@GetMapping("/me")
public UserResponse getMe(Authentication auth) {
    String username = auth.getName();  // Get logged-in username
    User user = userService.getUserByUsername(username);
    return new UserResponse(/* ... */);
}
```

**What is Authentication?**

`Authentication` is a Spring Security interface representing the currently logged-in user.

**How does it get populated?**

The complete flow:

1. **Client sends request with JWT cookie:**
   ```
   GET /users/me
   Cookie: jwt=eyJhbGciOiJIUzI1NiJ9...
   ```

2. **JwtAuthenticationFilter intercepts request** (runs BEFORE controller)

3. **Filter extracts JWT from cookie:**
   ```java
   String token = extractTokenFromCookie(request);
   ```

4. **Filter validates JWT:**
   ```java
   if (jwtService.isTokenValid(token)) {
       String username = jwtService.extractUsername(token);
       // ...
   }
   ```

5. **Filter loads user details:**
   ```java
   UserDetails userDetails = userDetailsService.loadUserByUsername(username);
   ```

6. **Filter creates Authentication object:**
   ```java
   Authentication auth = new UsernamePasswordAuthenticationToken(
       userDetails,
       null,
       userDetails.getAuthorities()
   );
   ```

7. **Filter stores in SecurityContext:**
   ```java
   SecurityContextHolder.getContext().setAuthentication(auth);
   ```

8. **Spring MVC automatically injects Authentication into controller:**
   ```java
   public UserResponse getMe(Authentication auth) {
       // auth is automatically available!
   }
   ```

### What's Inside Authentication?

```java
Authentication auth = /* injected by Spring */

auth.getName()          // Username of logged-in user
auth.getAuthorities()   // List of roles/permissions (e.g., "ROLE_USER", "ROLE_ADMIN")
auth.getPrincipal()     // UserDetails object
auth.isAuthenticated()  // true/false
```

### Optional Authentication

Some endpoints allow **both** authenticated and unauthenticated access:

```java
@GetMapping
public List<UserResponse> getUsers(Authentication auth) {
    User currentUser = auth != null
        ? userService.getUserByUsername(auth.getName())
        : null;

    boolean isAdmin = currentUser != null
        && currentUser.getRole() == Role.ADMIN;

    // Adjust response based on who's asking
    for (User u : users) {
        String email = (isAdmin || isCurrentUser)
            ? u.getEmail()   // Show email to admin or self
            : null;          // Hide email from others
    }
}
```

**How this works:**
- If request has valid JWT, `auth` is populated
- If no JWT or invalid JWT, `auth` is null
- We check `auth != null` to see if user is logged in

---

## Authorization with @PreAuthorize

### What Is @PreAuthorize?

`@PreAuthorize` is an annotation that checks permissions **BEFORE** executing the method.

```java
@PostMapping("/delete/{id}")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<?> deleteUser(@PathVariable Long id) {
    // Only ADMIN users can reach here
    userService.deleteUser(id);
    return ResponseEntity.ok(Map.of("message", "Deleted"));
}
```

### How @PreAuthorize Works

**Step-by-step flow:**

1. Request comes in: `POST /users/delete/5`

2. DispatcherServlet finds the controller method

3. **BEFORE calling the method**, Spring Security's **AOP proxy** intercepts

4. Proxy evaluates the SpEL expression: `"hasRole('ADMIN')"`

5. Proxy checks current user's authorities:
   ```java
   Authentication auth = SecurityContextHolder.getContext().getAuthentication();
   Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
   // authorities = ["ROLE_ADMIN"]
   ```

6. If user has `ROLE_ADMIN`, proceed to method

7. If user doesn't have `ROLE_ADMIN`, throw `AccessDeniedException`

8. Spring automatically returns **403 Forbidden**

### SpEL Expressions in @PreAuthorize

You can use Spring Expression Language (SpEL) for complex checks:

```java
// Check single role
@PreAuthorize("hasRole('ADMIN')")

// Check multiple roles (OR)
@PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")

// Check authentication
@PreAuthorize("isAuthenticated()")

// Complex expression
@PreAuthorize("hasRole('ADMIN') or #username == authentication.name")
public void updateUser(@PathVariable String username) {
    // Allow admins OR the user themselves
}
```

### @PreAuthorize vs Manual Checks

**Without @PreAuthorize (manual):**
```java
@PostMapping("/ban/{id}")
public ResponseEntity<?> banUser(@PathVariable Long id, Authentication auth) {
    User currentUser = userService.getUserByUsername(auth.getName());

    if (currentUser.getRole() != Role.ADMIN) {
        return ResponseEntity.status(403).body("Not authorized");
    }

    userService.banUser(id);
    return ResponseEntity.ok("Banned");
}
```

**With @PreAuthorize (declarative):**
```java
@PostMapping("/ban/{id}")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<?> banUser(@PathVariable Long id) {
    // Security check happens automatically
    userService.banUser(id);
    return ResponseEntity.ok("Banned");
}
```

**Benefits:**
- Less boilerplate code
- Centralized security logic
- Can't forget to check permissions
- Easier to audit security rules

---

## Line-by-Line: AuthController

**File:** `backend/src/main/java/_blog/blog/controller/AuthController.java`

### Lines 1-12: Imports

```java
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
```

**What's imported:**

- `ResponseCookie` - Builder for HTTP cookies (secure, httpOnly, sameSite)
- `ResponseEntity` - Wrapper for HTTP response with status codes
- `AuthenticationManager` - Spring Security component that validates credentials
- `UsernamePasswordAuthenticationToken` - Authentication object with username/password
- `SecurityContextHolder` - Thread-local storage for current user's authentication
- Web annotations - `@RestController`, `@RequestMapping`, `@PostMapping`, `@RequestBody`

### Lines 23-25: Class Declaration

```java
@RestController
@RequestMapping("/auth")
public class AuthController {
```

**@RestController:**
- Tells Spring this is a REST API controller
- Automatically converts return values to JSON
- Creates singleton bean at startup

**@RequestMapping("/auth"):**
- Base URL for all endpoints: `/auth/...`
- Organize related endpoints together

### Lines 27-40: Dependency Injection

```java
private final UserService userService;
private final JwtService jwtService;
private final AuthenticationManager authenticationManager;

@Value("${app.security.cookie.secure}")
private boolean cookieSecure;

@Value("${app.security.cookie.same-site}")
private String cookieSameSite;

public AuthController(AuthenticationManager authenticationManager,
                     UserService userService,
                     JwtService jwtService) {
    this.userService = userService;
    this.jwtService = jwtService;
    this.authenticationManager = authenticationManager;
}
```

**private final fields:**
- `final` ensures they're set once in constructor (immutability)
- Spring injects these dependencies

**@Value annotations:**
- Inject configuration properties from `application.properties`
- `${app.security.cookie.secure}` reads property value
- Allows environment-specific config (dev vs production)

**Constructor injection:**
- Spring calls this constructor at startup
- Spring automatically passes bean instances
- **Why constructor injection?** Ensures required dependencies are always present

### Lines 43-77: Register Endpoint

```java
@PostMapping("/register")
public ResponseEntity<AuthResponse> register(
    @Valid @RequestBody RegisterRequest request,
    HttpServletResponse response
) {
```

**@PostMapping("/register"):**
- Maps to `POST /auth/register`
- HTTP POST because we're creating a new user

**@Valid @RequestBody RegisterRequest request:**
- `@RequestBody` - Jackson deserializes JSON body → RegisterRequest object
- `@Valid` - Spring validates constraints on RegisterRequest fields
- If validation fails → automatic 400 Bad Request response

**HttpServletResponse response:**
- Spring automatically injects this
- Gives us access to raw HTTP response to set cookies

#### Register Logic (Lines 45-46)

```java
// Save the user
userService.register(request);
```

**What happens:**
1. Service validates username/email uniqueness
2. Service hashes password with BCrypt
3. Service saves user to database
4. Returns User entity

#### Auto-Login After Registration (Lines 48-62)

```java
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
```

**Why auto-login?**
- Better UX - user doesn't need to login after registering
- We need to generate JWT token anyway

**authenticationManager.authenticate():**
- **What it does:** Validates credentials against database
- **How:**
  1. Calls `UserDetailsService.loadUserByUsername(username)`
  2. Gets hashed password from database
  3. Compares with provided password using BCrypt
  4. If match → creates Authentication object
  5. If no match → throws `BadCredentialsException`

**UsernamePasswordAuthenticationToken:**
- This is the **input** to authentication
- Contains raw username and password
- After successful auth, it's replaced with a fully populated Authentication object

#### Set Authentication in Context (Line 61)

```java
SecurityContextHolder.getContext().setAuthentication(authentication);
```

**What is SecurityContextHolder?**
- **Thread-local storage** for current user's authentication
- Each HTTP request has its own thread, so each has its own SecurityContext

**Why set it?**
- Makes user "logged in" for the duration of this request
- Allows security checks later in the request

#### Generate JWT Token (Lines 62-66)

```java
String token = jwtService.generateToken(userService.authenticate(loginRequest));
String role = authentication.getAuthorities()
    .iterator()
    .next()
    .getAuthority();
```

**jwtService.generateToken():**
- Creates JWT with username, expiration, signature
- JWT structure: `header.payload.signature`
- Signed with secret key so it can't be tampered with

**Extract role:**
- `authentication.getAuthorities()` returns collection of roles
- In our app, users have one role: "ROLE_USER" or "ROLE_ADMIN"
- `.iterator().next()` gets the first (and only) role
- `.getAuthority()` returns the string name

#### Create Secure Cookie (Lines 67-73)

```java
ResponseCookie cookie = ResponseCookie.from("jwt", token)
    .httpOnly(true)
    .secure(cookieSecure)
    .sameSite(cookieSameSite)
    .path("/")
    .maxAge(24 * 60 * 60)
    .build();
```

**ResponseCookie.from("jwt", token):**
- Creates cookie named "jwt" with JWT as value

**Cookie security attributes:**

1. **httpOnly(true)** - JavaScript cannot read this cookie
   - **Why?** Prevents XSS attacks from stealing JWT
   - Cookie is only sent in HTTP headers, not accessible via `document.cookie`

2. **secure(cookieSecure)** - Cookie only sent over HTTPS
   - **Why?** Prevents man-in-the-middle attacks
   - In dev: false (http://localhost)
   - In production: true (https://yourdomain.com)

3. **sameSite(cookieSameSite)** - Cookie only sent to same-site requests
   - **Why?** Prevents CSRF attacks
   - Values: "Strict" or "Lax"
   - "Strict" = never sent on cross-site requests
   - "Lax" = sent on top-level navigation (clicking link)

4. **path("/")** - Cookie sent for all paths
   - Alternative: `path("/api")` would only send for /api/* requests

5. **maxAge(24 * 60 * 60)** - Cookie expires in 1 day (86400 seconds)
   - After 24 hours, browser deletes cookie
   - User needs to login again

#### Send Response (Lines 75-76)

```java
response.addHeader("Set-Cookie", cookie.toString());
return ResponseEntity.ok(new AuthResponse(token, request.getUsername(), role));
```

**response.addHeader("Set-Cookie", ...):**
- Adds cookie to HTTP response headers
- Browser receives this and stores the cookie
- Browser automatically sends this cookie on future requests

**ResponseEntity.ok(...):**
- HTTP status 200 OK
- Body contains JSON: `{"token": "...", "username": "...", "role": "..."}`
- **Why send token in BOTH cookie and body?**
  - Cookie: For automatic authentication on subsequent requests
  - Body: For frontend to store username/role for UI decisions

### Lines 80-104: Login Endpoint

```java
@PostMapping("/login")
public ResponseEntity<AuthResponse> login(
    @RequestBody LoginRequest request,
    HttpServletResponse response
) {
    // First authenticate and check if banned
    var user = userService.authenticate(request);

    Authentication authentication = authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(
            request.getUsername(),
            request.getPassword()
        )
    );

    // ... (same cookie logic as register)

    return ResponseEntity.ok(new AuthResponse(token, request.getUsername(), role));
}
```

**Difference from register:**
- No `@Valid` on LoginRequest (login has simpler validation)
- `userService.authenticate()` checks if user is banned
- No user creation - just credential verification

**userService.authenticate():**
```java
public User authenticate(LoginRequest request) {
    User user = findByEmailOrUsername(request.getUsernameOrEmail());

    if (user.isBanned()) {
        throw new RuntimeException("Account banned");
    }

    return user;
}
```

### Lines 106-120: Logout Endpoint

```java
@PostMapping("/logout")
public ResponseEntity<String> logout(HttpServletResponse response) {
    ResponseCookie cookie = ResponseCookie.from("jwt", "")
        .httpOnly(true)
        .secure(cookieSecure)
        .sameSite(cookieSameSite)
        .path("/")
        .maxAge(0)  // ← Expire immediately
        .build();

    response.addHeader("Set-Cookie", cookie.toString());
    SecurityContextHolder.clearContext();
    return ResponseEntity.ok("Logged out successfully");
}
```

**How logout works:**

1. **Create cookie with maxAge(0):**
   - Tells browser to delete the cookie immediately
   - Empty value ("") ensures no token remains

2. **SecurityContextHolder.clearContext():**
   - Clears authentication from current thread
   - User is no longer authenticated for this request

**Why JWT logout is tricky:**
- JWT tokens are **stateless** - server doesn't track them
- Once issued, a JWT is valid until it expires
- We delete the cookie, but if someone copied the token, it still works
- **Solution:** Keep tokens short-lived (24 hours) or implement token blacklist

---

## Line-by-Line: UserController

**File:** `backend/src/main/java/_blog/blog/controller/UserController.java`

### Lines 26-35: Class Setup

```java
@RestController
@RequestMapping("/users")
public class UserController {
    private final UserService userService;
    private final PostService postService;

    public UserController(UserService userService, PostService postService) {
        this.userService = userService;
        this.postService = postService;
    }
}
```

**Why inject PostService?**
- UserController needs to include user's posts in response
- Service coordination - controller orchestrates multiple services
- Follows single responsibility (PostService owns post logic)

### Lines 37-67: Get All Users

```java
@GetMapping
public List<UserResponse> getUsers(Authentication auth) {
    User currentUser = auth != null
        ? userService.getUserByUsername(auth.getName())
        : null;

    boolean isAdmin = currentUser != null
        && currentUser.getRole() == Role.ADMIN;
```

**Authentication auth:**
- **Optional** parameter - can be null
- If request has valid JWT, auth is populated
- If no JWT, auth is null

**Privacy logic:**
```java
List<User> users = userService.getUsers();
List<UserResponse> usersResp = new ArrayList<>();

for (User u : users) {
    // Only expose email to the user themselves or admins
    String email = (isAdmin || (currentUser != null && currentUser.getId().equals(u.getId())))
        ? u.getEmail()
        : null;

    usersResp.add(new UserResponse(
        u.getId(),
        u.getFirstName(),
        u.getLastName(),
        u.getUsername(),
        email,  // ← Conditionally included
        u.getAvatar(),
        u.getRole().toString(),
        u.isBanned(),
        u.getSubscriptions().stream()
            .map(sub -> sub.getSubscribedTo().getUsername())
            .toList(),
        postService.getPostsRespByUserId(u.getId())
    ));
}

return usersResp;
```

**Privacy model:**
- **Public data:** username, avatar, first/last name (shown to everyone)
- **Private data:** email (only shown to owner or admin)

**Why not just return User entities?**
- Entities contain sensitive data (password hash, internal IDs)
- Entities cause lazy-loading issues (might trigger N+1 queries)
- DTOs give precise control over what's exposed

**Stream transformation:**
```java
u.getSubscriptions().stream()
    .map(sub -> sub.getSubscribedTo().getUsername())
    .toList()
```
- `getSubscriptions()` returns List<Subscription>
- Each Subscription has `subscribedTo` field (the User being followed)
- We extract just the username for the response
- Result: ["alice", "bob", "charlie"]

### Lines 69-94: Get User by Username

```java
@GetMapping("/username/{username}")
public UserResponse getUserByUsername(
    @PathVariable String username,
    Authentication auth
) {
    User user = userService.getUserByUsername(username);
    User currentUser = auth != null
        ? userService.getUserByUsername(auth.getName())
        : null;

    boolean isAdmin = currentUser != null
        && currentUser.getRole() == Role.ADMIN;

    String email = (isAdmin || (currentUser != null && currentUser.getUsername().equals(username)))
        ? user.getEmail()
        : null;

    // ... build UserResponse ...
}
```

**@PathVariable String username:**
- Extracts username from URL
- `GET /users/username/johndoe` → username = "johndoe"

**Same privacy logic as getUsers():**
- Show email only to owner or admin
- Other users see null for email field

### Lines 96-121: Get User by ID

```java
@GetMapping("/{id}")
public UserResponse getUser(
    @PathVariable Long id,
    Authentication auth
) {
    // Same privacy logic...
    String email = (isAdmin || (currentUser != null && currentUser.getId().equals(id)))
        ? user.getEmail()
        : null;
}
```

**Why two endpoints for same data?**
- `/users/{id}` - Lookup by database ID
- `/users/username/{username}` - Lookup by username

**Use cases:**
- ID: When you already have the ID (from another API call)
- Username: User-friendly URLs, public profiles

### Lines 123-140: Get Current User (/me)

```java
@GetMapping("/me")
public UserResponse getMe(Authentication auth) {
    User user = userService.getUserByUsername(auth.getName());

    UserResponse userResponse = new UserResponse(
        user.getId(),
        user.getFirstName(),
        user.getLastName(),
        user.getUsername(),
        user.getEmail(),  // ← Always included (it's their own data)
        user.getAvatar(),
        user.getRole().toString(),
        user.isBanned(),
        u.getSubscriptions().stream()
            .map(sub -> sub.getSubscribedTo().getUsername())
            .toList(),
        postService.getPostsRespByUserId(user.getId())
    );

    return userResponse;
}
```

**Why a dedicated /me endpoint?**
- Common pattern in REST APIs
- Frontend doesn't need to know username to get current user
- Cleaner than `/users/{id}` where you need to know your own ID

**No privacy checks:**
- This endpoint requires authentication (otherwise auth.getName() throws NPE)
- User is always getting their own data
- Email is always included

### Lines 142-150: Delete User (Admin Only)

```java
@PostMapping("/delete/{id}")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<Map<String, String>> deleteUser(@PathVariable Long id) {
    if (userService.deleteUser(id)) {
        return ResponseEntity.ok(Map.of("message", "User deleted successfully!"));
    } else {
        return ResponseEntity.status(404).body(Map.of("message", "User not found!"));
    }
}
```

**@PreAuthorize("hasRole('ADMIN')"):**
- **Security check happens BEFORE method executes**
- If current user doesn't have ROLE_ADMIN, Spring throws AccessDeniedException
- Client receives 403 Forbidden

**Why POST instead of DELETE?**
- Debatable - DELETE would be more RESTful
- Some proxies/firewalls block DELETE
- POST is universally supported

**Map.of("message", "..."):**
- Creates immutable map for JSON response
- JSON output: `{"message": "User deleted successfully!"}`

### Lines 152-170: Ban/Unban User (Admin Only)

```java
@PostMapping("/ban/{id}")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<Map<String, String>> banUser(@PathVariable Long id) {
    if (userService.banUser(id)) {
        return ResponseEntity.ok(Map.of("message", "User banned successfully!"));
    } else {
        return ResponseEntity.status(404).body(Map.of("message", "User not found!"));
    }
}
```

**Soft delete pattern:**
- Instead of deleting user, set `banned = true`
- Banned users can't login (checked in `UserService.authenticate()`)
- Preserves data for audit/investigation
- Can be reversed with `/unban`

### Lines 172-183: Update Profile

```java
@PutMapping("/update-profile")
public ResponseEntity<?> updateProfile(
    @Valid @RequestBody UpdateProfileRequest request,
    Authentication auth
) {
    try {
        User user = userService.getUserByUsername(auth.getName());
        UserResponse updatedUser = userService.updateProfile(user.getId(), request);
        return ResponseEntity.ok(updatedUser);
    } catch (RuntimeException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
```

**@PutMapping:**
- HTTP PUT for updates
- Replaces existing resource

**@Valid @RequestBody UpdateProfileRequest:**
- Validates password strength, email format, etc.
- Constraints defined on UpdateProfileRequest class

**ResponseEntity<?>:**
- `?` means "any type"
- Returns UserResponse on success, Map<String, String> on error

**Exception handling:**
- Service throws RuntimeException for validation errors
- Controller catches and returns 400 Bad Request
- **Better approach:** Use `@ControllerAdvice` for global exception handling

**Security:**
- User can only update their own profile
- Auth username determines which user to update
- No way to update someone else's profile (no user ID in request)

---

## Line-by-Line: PostController

**File:** `backend/src/main/java/_blog/blog/controller/PostController.java`

### Lines 30-43: Class Setup

```java
@RestController
@RequestMapping("/posts")
public class PostController {
    private final UserService userService;
    private final PostService postService;
    private final CommentService commentService;

    public PostController(UserService userService, PostService postService, CommentService commentService) {
        this.userService = userService;
        this.postService = postService;
        this.commentService = commentService;
    }
}
```

**Three service dependencies:**
- `UserService` - Get current user from authentication
- `PostService` - Post CRUD operations
- `CommentService` - Get comments for posts

**Why CommentService in PostController?**
- PostResponse includes embedded comments
- Controller orchestrates: get posts + get comments for each post

### Lines 45-72: Get My Posts

```java
@GetMapping("/my-posts")
public ResponseEntity<List<PostResponse>> getMyPosts(Authentication auth) {
    User user = userService.getUserByUsername(auth.getName());
    List<Post> posts = postService.getPostsByIdWithCommentsAndLikes(user.getId());
    List<PostResponse> respPosts = new ArrayList<>();

    for (Post p : posts) {
        List<CommentResponse> comments = commentService.getCommentsRespByPost(p.getId());
        respPosts.add(new PostResponse(
            p.getId(),
            p.getAuthor().getUsername(),
            p.getAuthor().getAvatar(),
            p.getTitle(),
            p.getContent(),
            p.getMediaType(),
            p.getMediaUrl(),
            p.isHidden(),
            comments,
            p.getCreatedAt(),
            p.getUpdatedAt(),
            p.getLikedBy().size(),
            p.getLikedBy().stream()
                .map(User::getUsername)
                .toList()
        ));
    }

    return ResponseEntity.ok(respPosts);
}
```

**Authentication required:**
- Method parameter is NOT nullable
- If no JWT, Spring throws exception → 401 Unauthorized

**getPostsByIdWithCommentsAndLikes:**
- Uses JOIN FETCH to load posts + comments + likes in one query
- Avoids N+1 problem

**Manual DTO transformation:**
```java
for (Post p : posts) {
    List<CommentResponse> comments = commentService.getCommentsRespByPost(p.getId());
    respPosts.add(new PostResponse(...));
}
```

**Why manual loop instead of mapper?**
- Need to call commentService for each post
- Need to transform nested collections (likedBy → usernames)
- More control over exactly what's included

**Includes hidden posts:**
- User can see their own hidden posts
- Other endpoints filter hidden posts for public view

### Lines 74-102: Get Posts by Username (Public View)

```java
@GetMapping("/{username}")
public ResponseEntity<List<PostResponse>> getPostsByUsername(@PathVariable String username) {
    User user = userService.getUserByUsername(username);
    // Use getVisiblePostsByIdWithCommentsAndLikes to filter out hidden posts
    List<Post> posts = postService.getVisiblePostsByIdWithCommentsAndLikes(user.getId());
    // ... same DTO transformation ...
}
```

**Key difference from /my-posts:**
- Uses `getVisiblePostsByIdWithCommentsAndLikes()` instead of `getPostsByIdWithCommentsAndLikes()`
- **Hides** posts where `hidden = true`
- Public-facing endpoint - anyone can view

**No authentication required:**
- Method doesn't have `Authentication` parameter
- Public endpoint - view anyone's visible posts

### Lines 104-127: Get Single Post by ID

```java
@GetMapping("/post/{postId}")
public ResponseEntity<PostResponse> getPostById(@PathVariable Long postId) {
    Post post = postService.getPostByIdWithCommentsAndLikes(postId);
    List<CommentResponse> comments = commentService.getCommentsRespByPost(post.getId());

    PostResponse response = new PostResponse(
        post.getId(),
        post.getAuthor().getUsername(),
        post.getAuthor().getAvatar(),
        post.getTitle(),
        post.getContent(),
        post.getMediaType(),
        post.getMediaUrl(),
        post.isHidden(),
        comments,
        post.getCreatedAt(),
        post.getUpdatedAt(),
        post.getLikedBy().size(),
        post.getLikedBy().stream()
            .map(User::getUsername)
            .toList()
    );

    return ResponseEntity.ok(response);
}
```

**Single post retrieval:**
- Used for post detail page
- Includes all comments and likes
- One database query with JOIN FETCH

### Lines 129-134: Create Post

```java
@PostMapping("/create")
public ResponseEntity<String> createPost(
    @Valid @RequestBody PostRequest request,
    Authentication auth
) {
    User user = userService.getUserByUsername(auth.getName());
    postService.createPost(request, user);
    return ResponseEntity.ok("Post has been created.");
}
```

**@Valid @RequestBody PostRequest:**
- Validates constraints on PostRequest:
  ```java
  @Size(max = 200, message = "Title must not exceed 200 characters")
  String title;

  @NotBlank(message = "Content is required")
  @Size(min = 1, max = 100000)
  String content;
  ```
- If validation fails, Spring returns 400 Bad Request automatically

**Authentication required:**
- Only logged-in users can create posts
- Auth provides the author

**Service call:**
```java
postService.createPost(request, user);
```
- Service layer converts DTO → Entity
- Service saves to database
- Service handles transaction

### Lines 136-148: Update Post

```java
@PutMapping("/{id}")
public ResponseEntity<String> updatePost(
    @PathVariable Long id,
    @Valid @RequestBody PostRequest request,
    Authentication auth
) {
    User user = userService.getUserByUsername(auth.getName());
    Post post = postService.getPostById(id);

    // Check if the user is the author of the post
    if (!post.getAuthor().getId().equals(user.getId())) {
        return ResponseEntity.status(403).body("You are not authorized to edit this post");
    }

    postService.updatePost(id, request);
    return ResponseEntity.ok("Post has been updated.");
}
```

**Authorization check:**
```java
if (!post.getAuthor().getId().equals(user.getId())) {
    return ResponseEntity.status(403).body("Not authorized");
}
```

**Why manual check instead of @PreAuthorize?**
- Authorization depends on **data** (post author), not just **role**
- @PreAuthorize is for role-based checks
- This is **ownership-based** authorization

**Flow:**
1. Get current user from JWT
2. Load post from database
3. Compare post.author.id with current user.id
4. If match → allow update
5. If no match → 403 Forbidden

### Lines 150-176: Get All Posts (Public)

```java
@GetMapping("/all")
public ResponseEntity<List<PostResponse>> getAllPosts() {
    List<Post> posts = postService.getAllPostsWithCommentsAndLikes();
    // ... transform to DTOs ...
    return ResponseEntity.ok(respPosts);
}
```

**Public endpoint:**
- No authentication required
- Shows all visible posts (hidden=false)
- Used for "Explore" or "All Posts" page

### Lines 178-205: Get All Posts Including Hidden (Admin)

```java
@GetMapping("/admin/all")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<List<PostResponse>> getAllPostsForAdmin() {
    List<Post> posts = postService.getAllPostsIncludingHidden();
    // ... transform to DTOs ...
    return ResponseEntity.ok(respPosts);
}
```

**@PreAuthorize("hasRole('ADMIN')"):**
- Only admins can access
- Non-admins get 403 Forbidden

**Includes hidden posts:**
- Admins see everything for moderation
- Uses `getAllPostsIncludingHidden()` query

### Lines 207-234: Get Feed (Personalized)

```java
@GetMapping("/feed")
public ResponseEntity<List<PostResponse>> getFeed(Authentication auth) {
    User user = userService.getUserByUsername(auth.getName());
    List<Post> posts = postService.getFeedPosts(user.getId());
    // ... transform to DTOs ...
    return ResponseEntity.ok(respPosts);
}
```

**Personalized feed:**
- Shows posts from users you follow
- Plus your own posts
- Sorted by creation date (newest first)

**Service coordination:**
```java
// In PostService.getFeedPosts():
List<User> subscriptions = subscriptionService.getSubscriptions(userId);
List<Long> authorIds = new ArrayList<>();
authorIds.add(userId);
authorIds.addAll(subscriptions.stream().map(User::getId).toList());
return postRepository.findPostsByAuthorIdsWithCommentsAndLikes(authorIds);
```

### Lines 236-251: Delete Post

```java
@DeleteMapping("/{id}")
public ResponseEntity<Map<String, String>> deletePost(
    @PathVariable Long id,
    Authentication auth
) {
    User user = userService.getUserByUsername(auth.getName());
    Post post = postService.getPostById(id);

    // Check if the user is the author or an admin
    if (!post.getAuthor().getId().equals(user.getId()) && user.getRole() != Role.ADMIN) {
        return ResponseEntity.status(403).body(Map.of("message", "Not authorized"));
    }

    if (postService.deletePost(id)) {
        return ResponseEntity.ok(Map.of("message", "Post deleted successfully!"));
    } else {
        return ResponseEntity.status(404).body(Map.of("message", "Post not found!"));
    }
}
```

**Dual authorization:**
```java
if (!post.getAuthor().getId().equals(user.getId()) && user.getRole() != Role.ADMIN)
```

**Allows:**
- Post author can delete their own posts
- Admins can delete any post

**HTTP DELETE method:**
- RESTful approach for deletion
- No request body needed

### Lines 253-268: Hide Post

```java
@PutMapping("/hide/{id}")
public ResponseEntity<Map<String, String>> hidePost(
    @PathVariable Long id,
    Authentication auth
) {
    User user = userService.getUserByUsername(auth.getName());
    Post post = postService.getPostById(id);

    if (!post.getAuthor().getId().equals(user.getId()) && user.getRole() != Role.ADMIN) {
        return ResponseEntity.status(403).body(Map.of("message", "Not authorized"));
    }

    if (postService.hidePost(id)) {
        return ResponseEntity.ok(Map.of("message", "Post hidden successfully!"));
    } else {
        return ResponseEntity.status(404).body(Map.of("message", "Post not found!"));
    }
}
```

**Soft delete pattern:**
- Doesn't delete from database
- Sets `hidden = true`
- Hidden posts don't show in public queries

**Why hide instead of delete?**
- Preserve data for potential recovery
- Faster than deletion (no cascade deletes)
- Can be unhidden later

**HTTP PUT method:**
- Updating the post's hidden status
- Idempotent - calling multiple times has same effect

---

## Line-by-Line: CommentController

**File:** `backend/src/main/java/_blog/blog/controller/CommentController.java`

### Lines 28-40: Class Setup

```java
@RestController
@RequestMapping("/comments")
public class CommentController {

    private final CommentService commentService;
    private final UserService userService;
    private final PostService postService;

    public CommentController(CommentService commentService, UserService userService, PostService postService) {
        this.commentService = commentService;
        this.userService = userService;
        this.postService = postService;
    }
}
```

**Three services:**
- `CommentService` - Comment operations
- `UserService` - Get current user
- `PostService` - Verify post exists when creating comment

### Lines 42-60: Create Comment

```java
@PostMapping("/create/{postId}")
public ResponseEntity<String> createComment(
    @PathVariable Long postId,
    @Valid @RequestBody CommentRequest request,
    Authentication auth
) {
    User user = userService.getUserByUsername(auth.getName());
    Post post = postService.getPostById(postId);

    Comment comment = Comment.builder()
        .content(request.getContent())
        .author(user)
        .post(post)
        .build();

    commentService.saveComment(comment);
    return ResponseEntity.ok("Comment created successfully");
}
```

**URL pattern:**
- `POST /comments/create/{postId}`
- postId in URL, comment content in body
- Example: `POST /comments/create/42` with body `{"content": "Great post!"}`

**@Valid @RequestBody CommentRequest:**
- Validates comment content:
  ```java
  @NotBlank(message = "Comment content cannot be empty")
  @Size(min = 1, max = 5000, message = "Comment must be between 1 and 5000 characters")
  private String content;
  ```

**Builder pattern:**
```java
Comment comment = Comment.builder()
    .content(request.getContent())
    .author(user)
    .post(post)
    .build();
```

**How Lombok's @Builder works:**
1. Lombok generates a static inner class `CommentBuilder`
2. Each field gets a setter method that returns `this`
3. `.build()` calls the all-args constructor
4. Result: fluent, readable object creation

**Equivalent without builder:**
```java
Comment comment = new Comment();
comment.setContent(request.getContent());
comment.setAuthor(user);
comment.setPost(post);
```

### Lines 62-78: Get Comments by Post

```java
@GetMapping("/post/{postId}")
public List<CommentResponse> getCommentsByPost(@PathVariable Long postId) {
    List<Comment> comments = commentService.getCommentsByPostId(postId);
    List<CommentResponse> commentResponses = new ArrayList<>();

    for (Comment comment : comments) {
        commentResponses.add(new CommentResponse(
            comment.getId(),
            comment.getAuthor().getUsername(),
            comment.getAuthor().getAvatar(),
            comment.getContent(),
            comment.getCreatedAt()
        ));
    }

    return commentResponses;
}
```

**No authentication required:**
- Public endpoint - anyone can view comments
- Comments are already filtered in PostController when getting posts

**Manual DTO transformation:**
- Extracts author username and avatar
- Hides author's email and other sensitive data

### Lines 80-96: Get All Comments

```java
@GetMapping
public ResponseEntity<List<CommentResponse>> getAllComments() {
    List<Comment> comments = commentService.fetchComments();
    // ... transform to DTOs ...
    return ResponseEntity.ok(commentResponses);
}
```

**Global comment list:**
- Used for admin moderation
- Shows all comments across all posts

### Lines 98-122: Update Comment

```java
@PutMapping("/{commentId}")
public ResponseEntity<String> updateComment(
    @PathVariable Long commentId,
    @RequestBody CommentRequest request,
    Authentication auth
) {
    User user = userService.getUserByUsername(auth.getName());
    Comment existingComment = commentService.getCommentById(commentId);

    // Check if the user is the author of the comment
    if (!existingComment.getAuthor().getId().equals(user.getId())) {
        return ResponseEntity.status(403).body("You can only edit your own comments");
    }

    Comment updatedComment = Comment.builder()
        .id(commentId)
        .content(request.getContent())
        .author(existingComment.getAuthor())
        .post(existingComment.getPost())
        .createdAt(existingComment.getCreatedAt())
        .build();

    commentService.updateComment(updatedComment, commentId);
    return ResponseEntity.ok("Comment updated successfully");
}
```

**Ownership check:**
```java
if (!existingComment.getAuthor().getId().equals(user.getId())) {
    return ResponseEntity.status(403).body("You can only edit your own comments");
}
```

**Why rebuild comment with builder?**
- Immutable pattern - create new object instead of modifying
- Ensures all fields are set correctly
- Preserves createdAt timestamp

**Alternative approach (mutable):**
```java
existingComment.setContent(request.getContent());
commentService.updateComment(existingComment, commentId);
```

### Lines 124-139: Delete Comment

```java
@DeleteMapping("/{commentId}")
public ResponseEntity<String> deleteComment(
    @PathVariable Long commentId,
    Authentication auth
) {
    User user = userService.getUserByUsername(auth.getName());
    Comment comment = commentService.getCommentById(commentId);

    // Check if the user is the author or admin
    if (!comment.getAuthor().getId().equals(user.getId()) &&
        !user.getRole().name().equals("ADMIN")) {
        return ResponseEntity.status(403).body("You can only delete your own comments");
    }

    commentService.deleteComment(commentId);
    return ResponseEntity.ok("Comment deleted successfully");
}
```

**Dual authorization:**
- Comment author can delete their comment
- Admins can delete any comment (moderation)

**Role check:**
```java
!user.getRole().name().equals("ADMIN")
```

**Alternative (better):**
```java
user.getRole() != Role.ADMIN
```
Using enum directly is cleaner than string comparison.

---

## DTO Transformation Pattern

### Why Transform Entities to DTOs?

**Problem with exposing entities directly:**
```java
// ❌ BAD: Don't do this
@GetMapping("/{id}")
public User getUser(@PathVariable Long id) {
    return userRepository.findById(id).orElseThrow();
}
```

**Issues:**
1. **Security:** Exposes password hash, internal IDs
2. **Lazy loading:** Might trigger N+1 queries during JSON serialization
3. **Circular references:** User → Posts → Comments → Author (User) → infinite loop
4. **API coupling:** Can't change entity without breaking API

**Solution: Use DTOs**
```java
// ✅ GOOD: Return DTOs
@GetMapping("/{id}")
public UserResponse getUser(@PathVariable Long id) {
    User user = userRepository.findById(id).orElseThrow();
    return new UserResponse(
        user.getId(),
        user.getUsername(),
        user.getEmail()
        // Only include what clients need
    );
}
```

### Common DTO Patterns

#### Request DTOs (Input)

```java
public class PostRequest {
    @NotBlank(message = "Title required")
    String title;

    @Size(min = 1, max = 100000)
    String content;
}
```

**Purpose:**
- Validate incoming data
- Separate API contract from domain model
- Can change entity without breaking API

#### Response DTOs (Output)

```java
public class PostResponse {
    private Long id;
    private String author;          // ← Username, not full User object
    private String content;
    private List<CommentResponse> comments;  // ← Nested DTO
    private List<String> likedByUsernames;   // ← Just usernames, not full users
}
```

**Purpose:**
- Control exactly what's exposed
- Flatten nested structures
- Avoid circular references

### Transformation Strategies

#### Manual Transformation (Current Approach)

```java
for (Post p : posts) {
    respPosts.add(new PostResponse(
        p.getId(),
        p.getAuthor().getUsername(),
        p.getTitle(),
        // ... 10 more fields
    ));
}
```

**Pros:**
- Simple, explicit
- Full control over transformation logic

**Cons:**
- Repetitive code
- Error-prone when adding fields

#### MapStruct (Alternative)

```java
@Mapper
public interface PostMapper {
    @Mapping(source = "author.username", target = "author")
    PostResponse toResponse(Post post);
}
```

**Pros:**
- Less boilerplate
- Compile-time code generation (fast)

**Cons:**
- Learning curve
- Complex mappings can be tricky

---

## Error Handling

### Current Approach

**Controller-level try-catch:**
```java
@PutMapping("/update-profile")
public ResponseEntity<?> updateProfile(@Valid @RequestBody UpdateProfileRequest request, Authentication auth) {
    try {
        UserResponse updatedUser = userService.updateProfile(user.getId(), request);
        return ResponseEntity.ok(updatedUser);
    } catch (RuntimeException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
```

**Issues:**
- Repetitive - every controller needs try-catch
- Inconsistent error format across controllers
- Mixes error handling with business logic

### Better Approach: @ControllerAdvice

**Global exception handler:**
```java
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException e) {
        return ResponseEntity
            .badRequest()
            .body(new ErrorResponse(e.getMessage(), 400));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors()
            .stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining(", "));
        return ResponseEntity
            .badRequest()
            .body(new ErrorResponse(message, 400));
    }
}
```

**Benefits:**
- Centralized error handling
- Consistent error format
- Controllers focus on happy path

---

## Complete Request/Response Flow

Let's trace a complete request: `POST /posts/create`

### 1. Client Sends Request

```http
POST /posts/create HTTP/1.1
Host: localhost:8080
Content-Type: application/json
Cookie: jwt=eyJhbGciOiJIUzI1NiJ9...

{
  "title": "My Post",
  "content": "This is my post content"
}
```

### 2. Tomcat Receives Request

- Embedded Tomcat server receives HTTP request
- Creates HttpServletRequest and HttpServletResponse objects
- Passes to DispatcherServlet

### 3. Security Filter Chain

**JwtAuthenticationFilter:**
```java
String token = extractTokenFromCookie(request);
if (jwtService.isTokenValid(token)) {
    String username = jwtService.extractUsername(token);
    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
    Authentication auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    SecurityContextHolder.getContext().setAuthentication(auth);
}
```

### 4. DispatcherServlet Routes Request

- Looks up HandlerMapping: `POST /posts/create` → `PostController.createPost()`
- Checks @PreAuthorize (none on this endpoint, so passes)

### 5. Jackson Deserializes JSON

```java
// Jackson does this automatically:
PostRequest request = new PostRequest();
request.setTitle("My Post");
request.setContent("This is my post content");
```

### 6. Spring Validates with @Valid

```java
// Spring's validator checks:
- Is title <= 200 characters? ✅
- Is content not blank? ✅
- Is content <= 100000 characters? ✅
// All pass, proceed to controller
```

### 7. Controller Method Executes

```java
@PostMapping("/create")
public ResponseEntity<String> createPost(@Valid @RequestBody PostRequest request, Authentication auth) {
    User user = userService.getUserByUsername(auth.getName());  // Gets "johndoe"
    postService.createPost(request, user);
    return ResponseEntity.ok("Post has been created.");
}
```

### 8. Service Layer Executes

```java
public Post createPost(PostRequest request, User author) {
    Post post = PostMapper.toEntity(request, author);
    return postRepository.save(post);  // Triggers INSERT query
}
```

### 9. Repository Saves to Database

```sql
INSERT INTO posts (title, content, author_id, created_at, updated_at)
VALUES ('My Post', 'This is my post content', 42, NOW(), NOW());
```

### 10. Response Bubbles Up

- Repository → Service → Controller
- Controller returns `ResponseEntity.ok("Post has been created.")`

### 11. Jackson Serializes Response

```java
// Jackson converts String to JSON:
"Post has been created."
```

### 12. DispatcherServlet Sends Response

```http
HTTP/1.1 200 OK
Content-Type: application/json

"Post has been created."
```

### 13. Browser Receives Response

- Frontend JavaScript receives response
- Updates UI to show new post

---

## Key Takeaways

### Controllers Are Thin

Controllers should be **thin wrappers** around service layer:
- Extract data from HTTP request
- Call service layer
- Transform result to DTO
- Build HTTP response

### Spring MVC Magic

Spring does a LOT automatically:
- Routes requests to correct controller method
- Deserializes JSON to Java objects
- Validates input with @Valid
- Injects Authentication from JWT
- Serializes return values to JSON
- Handles exceptions → HTTP status codes

### Three-Layer Separation

```
Controller  ← HTTP, validation, DTOs
Service     ← Business logic, transactions
Repository  ← Database queries
```

Each layer has a clear responsibility.

### Security Integration

Security happens in filters **before** controllers:
- JWT extraction
- Token validation
- User loading
- Authentication object creation

Controllers receive populated `Authentication` object.

### DTO Transformation

**Never expose entities directly:**
- Security risk (password leaks)
- Lazy loading issues (N+1 queries)
- Circular references (infinite loops)
- Tight coupling (can't change entity without breaking API)

**Always use DTOs:**
- Request DTOs for input validation
- Response DTOs for controlled output

---

**Next**: Security Configuration Deep Dive - How JWT authentication works, filter chain, SecurityConfig explained! Continue?
