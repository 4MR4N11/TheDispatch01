# REST Controllers & HTTP Request Flow

> **Core Concept**: Understand how Spring MVC handles HTTP requests, routes them to controller methods, and returns responses.

---

## Table of Contents
1. [What is REST?](#1-what-is-rest)
2. [@RestController vs @Controller](#2-restcontroller-vs-controller)
3. [Request Mapping](#3-request-mapping)
4. [HTTP Methods](#4-http-methods)
5. [Path Variables](#5-path-variables)
6. [Request Parameters](#6-request-parameters)
7. [Request Body](#7-request-body)
8. [Response Entity](#8-response-entity)
9. [Authentication Parameter](#9-authentication-parameter)
10. [Complete HTTP Request Flow](#10-complete-http-request-flow)

---

## 1. What is REST?

### 1.1 REST Principles

**REST** (Representational State Transfer) is an architectural style for designing APIs.

**Key Principles:**
- **Resource-based**: Everything is a resource (User, Post, Comment)
- **HTTP methods**: Use standard HTTP verbs (GET, POST, PUT, DELETE)
- **Stateless**: Each request contains all needed information
- **JSON**: Standard data format for requests/responses

**Your API Structure:**
```
/users          - User resources
/posts          - Post resources
/comments       - Comment resources
/auth           - Authentication endpoints
/subscriptions  - Subscription resources
```

### 1.2 Resource Naming Conventions

**✅ Good (Your Code):**
```
GET    /users           - Get all users
GET    /users/{id}      - Get specific user
POST   /users           - Create user
PUT    /users/{id}      - Update user
DELETE /users/{id}      - Delete user

GET    /posts           - Get all posts
GET    /posts/{id}      - Get specific post
POST   /posts           - Create post
```

**❌ Bad:**
```
GET    /getAllUsers
GET    /getUserById?id=1
POST   /createUser
POST   /deleteUserById
```

**Why REST is better:**
- Standard HTTP methods convey intent
- URLs represent resources, not actions
- Consistent across all APIs

---

## 2. @RestController vs @Controller

### 2.1 @Controller (Traditional MVC)

**Returns HTML views:**
```java
@Controller
public class WebController {
    @GetMapping("/home")
    public String home(Model model) {
        model.addAttribute("message", "Welcome");
        return "home";  // Returns home.html template
    }
}
```

**Use case:** Server-side rendered web pages

### 2.2 @RestController (REST APIs)

**Your AuthController:**
**`AuthController.java:23-24`**
```java
@RestController
@RequestMapping("/auth")
public class AuthController {
```

**Returns JSON:**
```java
@RestController
public class UserController {
    @GetMapping("/users/{id}")
    public UserResponse getUser(@PathVariable Long id) {
        return userResponse;  // Automatically converted to JSON
    }
}
```

**What @RestController does:**
```java
@RestController =
    @Controller +
    @ResponseBody  // Automatically serialize return values to JSON
```

### 2.3 JSON Serialization

**Your controller returns object:**
```java
@GetMapping("/users/{id}")
public UserResponse getUser(@PathVariable Long id) {
    return new UserResponse(1L, "john", "john@example.com");
}
```

**Spring automatically converts to JSON:**
```json
{
  "id": 1,
  "username": "john",
  "email": "john@example.com"
}
```

**HTTP Response:**
```
HTTP/1.1 200 OK
Content-Type: application/json
Transfer-Encoding: chunked
Date: Mon, 04 Nov 2025 10:30:00 GMT

{
  "id": 1,
  "username": "john",
  "email": "john@example.com"
}
```

---

## 3. Request Mapping

### 3.1 @RequestMapping at Class Level

**Your PostController:**
**`PostController.java:30-32`**
```java
@RestController
@RequestMapping("/posts")
public class PostController {
```

**All methods inherit `/posts` prefix:**
```java
@GetMapping("/my-posts")      // Full URL: /posts/my-posts
@GetMapping("/{username}")    // Full URL: /posts/{username}
@PostMapping                  // Full URL: /posts
```

### 3.2 Multiple Paths

```java
@RequestMapping({"/users", "/api/users"})
public class UserController {
    @GetMapping("/{id}")
    // Matches: /users/1 OR /api/users/1
}
```

### 3.3 Path Patterns

```java
@GetMapping("/users/*")          // Matches: /users/any
@GetMapping("/users/**")         // Matches: /users/any/nested/path
@GetMapping("/users/{id:[0-9]+}") // Matches: /users/123 (digits only)
```

---

## 4. HTTP Methods

### 4.1 @GetMapping - Retrieve Resources

**Your UserController:**
**`UserController.java:37-67`**
```java
@GetMapping
public List<UserResponse> getUsers(Authentication auth) {
    // Return all users
}
```

**Characteristics:**
- **Idempotent**: Multiple calls have same effect
- **Safe**: Doesn't modify data
- **Cacheable**: Browser can cache response

**Your PostController:**
**`PostController.java:45-72`**
```java
@GetMapping("/my-posts")
public ResponseEntity<List<PostResponse>> getMyPosts(Authentication auth) {
    User user = userService.getUserByUsername(auth.getName());
    List<Post> posts = postService.getPostsByIdWithCommentsAndLikes(user.getId());
    // Convert to response DTOs
    return ResponseEntity.ok(respPosts);
}
```

### 4.2 @PostMapping - Create Resources

**Your AuthController:**
**`AuthController.java:43-77`**
```java
@PostMapping("/register")
public ResponseEntity<AuthResponse> register(
    @Valid @RequestBody RegisterRequest request,
    HttpServletResponse response
) {
    userService.register(request);
    // Generate token, set cookie
    return ResponseEntity.ok(new AuthResponse(token, username, role));
}
```

**Characteristics:**
- **Not idempotent**: Multiple calls create multiple resources
- **Not safe**: Modifies data
- **Returns**: 201 Created (or 200 OK)

**Login Endpoint:**
**`AuthController.java:80-104`**
```java
@PostMapping("/login")
public ResponseEntity<AuthResponse> login(
    @RequestBody LoginRequest request,
    HttpServletResponse response
) {
    var user = userService.authenticate(request);
    String token = jwtService.generateToken(user);
    // Set cookie
    return ResponseEntity.ok(new AuthResponse(token, username, role));
}
```

### 4.3 @PutMapping - Update Resources

**Your UserController:**
**`UserController.java:172-183`**
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

**Characteristics:**
- **Idempotent**: Multiple identical calls have same effect
- **Replaces**: Entire resource (or specific fields)
- **Returns**: 200 OK or 204 No Content

**PUT vs PATCH:**
```java
// PUT - Replace entire resource
@PutMapping("/users/{id}")
public User updateUser(@PathVariable Long id, @RequestBody User user) {
    return userService.replaceUser(id, user);  // Replaces ALL fields
}

// PATCH - Partial update
@PatchMapping("/users/{id}")
public User patchUser(@PathVariable Long id, @RequestBody Map<String, Object> updates) {
    return userService.updateFields(id, updates);  // Updates ONLY provided fields
}
```

### 4.4 @DeleteMapping - Delete Resources

**Your UserController:**
**`UserController.java:142-150`**
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

**Note:** Your code uses `@PostMapping` for delete (should be `@DeleteMapping`).

**Proper DELETE:**
```java
@DeleteMapping("/users/{id}")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
    userService.deleteUser(id);
    return ResponseEntity.noContent().build();  // 204 No Content
}
```

**Characteristics:**
- **Idempotent**: Multiple calls have same effect (resource stays deleted)
- **Returns**: 204 No Content or 200 OK with message

---

## 5. Path Variables

### 5.1 @PathVariable Basics

**Your UserController:**
**`UserController.java:96-121`**
```java
@GetMapping("/{id}")
public UserResponse getUser(@PathVariable Long id, Authentication auth) {
    User user = userService.getUser(id);
    // ...
    return userResponse;
}
```

**URL Examples:**
```
GET /users/1    → id = 1
GET /users/42   → id = 42
GET /users/999  → id = 999
```

### 5.2 Multiple Path Variables

```java
@GetMapping("/users/{userId}/posts/{postId}")
public PostResponse getUserPost(
    @PathVariable Long userId,
    @PathVariable Long postId
) {
    return postService.getPostByUserAndId(userId, postId);
}
```

**URL:**
```
GET /users/5/posts/123
    userId = 5
    postId = 123
```

### 5.3 Path Variable Names

**Same name as parameter:**
```java
@GetMapping("/{id}")
public User getUser(@PathVariable Long id) {
    // Parameter name matches path variable name
}
```

**Different name:**
```java
@GetMapping("/{id}")
public User getUser(@PathVariable("id") Long userId) {
    // Path variable "id" mapped to parameter "userId"
}
```

### 5.4 Optional Path Variables

```java
@GetMapping({"/users", "/users/{id}"})
public ResponseEntity<?> getUsers(@PathVariable(required = false) Long id) {
    if (id == null) {
        return ResponseEntity.ok(userService.getAllUsers());
    } else {
        return ResponseEntity.ok(userService.getUser(id));
    }
}
```

**Matches:**
```
GET /users     → id = null (return all users)
GET /users/5   → id = 5 (return specific user)
```

---

## 6. Request Parameters

### 6.1 @RequestParam Basics

**Query parameters in URL:**
```
GET /posts?page=0&size=10&sort=createdAt
```

**Controller:**
```java
@GetMapping("/posts")
public Page<PostResponse> getPosts(
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "10") int size,
    @RequestParam(defaultValue = "createdAt") String sort
) {
    Pageable pageable = PageRequest.of(page, size, Sort.by(sort).descending());
    return postService.getPosts(pageable);
}
```

**URL Examples:**
```
GET /posts                               → page=0, size=10, sort=createdAt
GET /posts?page=2                        → page=2, size=10, sort=createdAt
GET /posts?page=1&size=20&sort=title     → page=1, size=20, sort=title
```

### 6.2 Required vs Optional Parameters

**Required (default):**
```java
@GetMapping("/search")
public List<Post> search(@RequestParam String query) {
    // query is required - 400 Bad Request if missing
}
```

**Optional:**
```java
@GetMapping("/search")
public List<Post> search(@RequestParam(required = false) String query) {
    if (query == null) {
        return postService.getAll();
    }
    return postService.search(query);
}
```

**With Default Value:**
```java
@GetMapping("/search")
public List<Post> search(@RequestParam(defaultValue = "") String query) {
    // query = "" if not provided
}
```

### 6.3 Multiple Values

```java
@GetMapping("/posts")
public List<Post> getPostsByIds(@RequestParam List<Long> ids) {
    return postService.findAllById(ids);
}
```

**URL:**
```
GET /posts?ids=1&ids=5&ids=7
    ids = [1, 5, 7]

GET /posts?ids=1,5,7
    ids = [1, 5, 7]  (comma-separated)
```

### 6.4 Path Variable vs Request Parameter

**Path Variable - Identifies Resource:**
```java
GET /users/5         @PathVariable Long id
GET /posts/123       @PathVariable Long postId
```

**Request Parameter - Filters/Options:**
```java
GET /posts?page=0&size=10           Pagination
GET /users?role=ADMIN&banned=false  Filtering
GET /search?q=spring                Search query
```

---

## 7. Request Body

### 7.1 @RequestBody Annotation

**Your AuthController:**
**`AuthController.java:43-44`**
```java
@PostMapping("/register")
public ResponseEntity<AuthResponse> register(
    @Valid @RequestBody RegisterRequest request,
    HttpServletResponse response
) {
```

**What @RequestBody does:**
- Reads HTTP request body
- Deserializes JSON to Java object
- Validates object (with `@Valid`)

**HTTP Request:**
```
POST /auth/register
Content-Type: application/json

{
  "username": "john",
  "email": "john@example.com",
  "password": "securePassword123",
  "firstName": "John",
  "lastName": "Smith"
}
```

**Spring converts to:**
```java
RegisterRequest request = new RegisterRequest(
    "john",
    "john@example.com",
    "securePassword123",
    "John",
    "Smith"
);
```

### 7.2 Validation with @Valid

**Your code uses `@Valid`:**
```java
@PostMapping("/register")
public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
```

**RegisterRequest DTO (with validation):**
```java
public class RegisterRequest {
    @NotBlank(message = "Username is required")
    @Size(min = 4, max = 30)
    private String username;

    @Email(message = "Invalid email format")
    @NotBlank(message = "Email is required")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    // Getters, setters, constructors
}
```

**Invalid Request:**
```json
{
  "username": "ab",
  "email": "invalid-email",
  "password": "short"
}
```

**Response (400 Bad Request):**
```json
{
  "username": "size must be between 4 and 30",
  "email": "Invalid email format",
  "password": "Password must be at least 8 characters"
}
```

### 7.3 Request Body vs Request Parameters

**@RequestBody (JSON in body):**
```java
@PostMapping("/users")
public User createUser(@RequestBody User user) {
    return userService.save(user);
}
```

**Request:**
```
POST /users
Content-Type: application/json

{
  "username": "john",
  "email": "john@example.com"
}
```

**@RequestParam (Query parameters):**
```java
@PostMapping("/users")
public User createUser(
    @RequestParam String username,
    @RequestParam String email
) {
    return userService.save(new User(username, email));
}
```

**Request:**
```
POST /users?username=john&email=john@example.com
```

**When to use:**
- `@RequestBody` - Complex objects, nested data, multiple fields
- `@RequestParam` - Simple values, form submissions, filters

---

## 8. Response Entity

### 8.1 ResponseEntity Basics

**Your AuthController:**
**`AuthController.java:43-77`**
```java
@PostMapping("/register")
public ResponseEntity<AuthResponse> register(...) {
    // ...
    return ResponseEntity.ok(new AuthResponse(token, username, role));
}
```

**What is ResponseEntity?**
- Wrapper for HTTP response
- Controls status code, headers, body

### 8.2 Status Codes

**Common status codes:**
```java
// 200 OK
return ResponseEntity.ok(data);

// 201 Created
return ResponseEntity.status(HttpStatus.CREATED).body(user);
return ResponseEntity.created(location).body(user);

// 204 No Content
return ResponseEntity.noContent().build();

// 400 Bad Request
return ResponseEntity.badRequest().body(error);

// 404 Not Found
return ResponseEntity.notFound().build();
return ResponseEntity.status(404).body(error);

// 500 Internal Server Error
return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
```

**Your UserController:**
**`UserController.java:172-183`**
```java
@PutMapping("/update-profile")
public ResponseEntity<?> updateProfile(...) {
    try {
        UserResponse updatedUser = userService.updateProfile(user.getId(), request);
        return ResponseEntity.ok(updatedUser);  // 200 OK
    } catch (RuntimeException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));  // 400
    }
}
```

### 8.3 Custom Headers

**Your AuthController (Setting Cookie):**
**`AuthController.java:67-76`**
```java
ResponseCookie cookie = ResponseCookie.from("jwt", token)
    .httpOnly(true)
    .secure(cookieSecure)
    .sameSite(cookieSameSite)
    .path("/")
    .maxAge(24 * 60 * 60)
    .build();

response.addHeader("Set-Cookie", cookie.toString());
return ResponseEntity.ok(new AuthResponse(token, username, role));
```

**Custom Headers Example:**
```java
HttpHeaders headers = new HttpHeaders();
headers.add("X-Custom-Header", "value");
headers.add("Cache-Control", "no-cache");

return ResponseEntity
    .ok()
    .headers(headers)
    .body(data);
```

### 8.4 Return Type: ResponseEntity vs Direct Object

**Direct Object (simpler):**
```java
@GetMapping("/{id}")
public UserResponse getUser(@PathVariable Long id) {
    return userService.getUser(id);  // Always 200 OK
}
```

**ResponseEntity (more control):**
```java
@GetMapping("/{id}")
public ResponseEntity<UserResponse> getUser(@PathVariable Long id) {
    try {
        UserResponse user = userService.getUser(id);
        return ResponseEntity.ok(user);  // 200 OK
    } catch (UserNotFoundException e) {
        return ResponseEntity.notFound().build();  // 404
    }
}
```

---

## 9. Authentication Parameter

### 9.1 Authentication from Security Context

**Your UserController:**
**`UserController.java:38-67`**
```java
@GetMapping
public List<UserResponse> getUsers(Authentication auth) {
    User currentUser = auth != null ? userService.getUserByUsername(auth.getName()) : null;
    boolean isAdmin = currentUser != null && currentUser.getRole() == Role.ADMIN;
    // Use currentUser for authorization logic
}
```

**What is Authentication?**
- Provided by Spring Security
- Contains authenticated user information
- Null if not authenticated

**Your PostController:**
**`PostController.java:45-72`**
```java
@GetMapping("/my-posts")
public ResponseEntity<List<PostResponse>> getMyPosts(Authentication auth) {
    User user = userService.getUserByUsername(auth.getName());
    //                                          ↑ Get username from token
    List<Post> posts = postService.getPostsByIdWithCommentsAndLikes(user.getId());
    return ResponseEntity.ok(respPosts);
}
```

### 9.2 Getting User Information

```java
@GetMapping("/me")
public UserResponse getCurrentUser(Authentication auth) {
    String username = auth.getName();  // Username from JWT
    Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();  // Roles
    Object principal = auth.getPrincipal();  // User object

    return userService.getUserByUsername(username);
}
```

### 9.3 Optional Authentication

**Allow both authenticated and anonymous access:**
```java
@GetMapping("/posts")
public List<PostResponse> getPosts(Authentication auth) {
    if (auth != null) {
        // Authenticated user - show personalized content
        User user = userService.getUserByUsername(auth.getName());
        return postService.getPersonalizedPosts(user);
    } else {
        // Anonymous user - show public content
        return postService.getPublicPosts();
    }
}
```

### 9.4 @AuthenticationPrincipal (Alternative)

```java
@GetMapping("/me")
public UserResponse getCurrentUser(@AuthenticationPrincipal User user) {
    //                                                       ↑ User object directly
    return UserResponse.fromEntity(user);
}
```

**Requires custom UserDetailsService returning User:**
```java
@Service
public class CustomUserDetailsService implements UserDetailsService {
    @Override
    public UserDetails loadUserByUsername(String username) {
        User user = userRepository.findByUsername(username).orElseThrow();
        return user;  // User implements UserDetails
    }
}
```

---

## 10. Complete HTTP Request Flow

### 10.1 Request Flow Diagram

```
┌──────────────────────────────────────────────────────────────┐
│  1. Client (Browser/Mobile App)                              │
│     HTTP Request: POST /auth/login                           │
│     Body: {"username": "john", "password": "pass123"}        │
└─────────────────────┬────────────────────────────────────────┘
                      │
                      ↓
┌──────────────────────────────────────────────────────────────┐
│  2. Tomcat (Embedded Web Server)                             │
│     - Receives HTTP request on port 8080                     │
│     - Creates HttpServletRequest object                      │
└─────────────────────┬────────────────────────────────────────┘
                      │
                      ↓
┌──────────────────────────────────────────────────────────────┐
│  3. Spring Security Filter Chain                             │
│     - JwtAuthenticationFilter                                │
│     - Extracts JWT from cookie                               │
│     - Validates token                                        │
│     - Sets Authentication in SecurityContext                 │
└─────────────────────┬────────────────────────────────────────┘
                      │
                      ↓
┌──────────────────────────────────────────────────────────────┐
│  4. DispatcherServlet (Front Controller)                     │
│     - Looks up handler for "/auth/login"                     │
│     - Finds: AuthController.login()                          │
└─────────────────────┬────────────────────────────────────────┘
                      │
                      ↓
┌──────────────────────────────────────────────────────────────┐
│  5. HandlerAdapter                                           │
│     - Deserializes JSON body to LoginRequest object          │
│     - Validates with @Valid                                  │
│     - Injects Authentication parameter                       │
└─────────────────────┬────────────────────────────────────────┘
                      │
                      ↓
┌──────────────────────────────────────────────────────────────┐
│  6. AuthController.login()                                   │
│     - Calls userService.authenticate(request)                │
│     - Generates JWT token                                    │
│     - Creates ResponseCookie                                 │
│     - Returns ResponseEntity<AuthResponse>                   │
└─────────────────────┬────────────────────────────────────────┘
                      │
                      ↓
┌──────────────────────────────────────────────────────────────┐
│  7. UserService.authenticate()                               │
│     - Calls userRepository.findByUsername()                  │
│     - Spring Data JPA generates SQL                          │
│     - Executes query via EntityManager                       │
└─────────────────────┬────────────────────────────────────────┘
                      │
                      ↓
┌──────────────────────────────────────────────────────────────┐
│  8. Database (PostgreSQL)                                    │
│     - Executes: SELECT * FROM users WHERE username = 'john'  │
│     - Returns result set                                     │
└─────────────────────┬────────────────────────────────────────┘
                      │
                      ↓
┌──────────────────────────────────────────────────────────────┐
│  9. Hibernate (ORM)                                          │
│     - Maps ResultSet to User entity                          │
│     - Returns User object to repository                      │
└─────────────────────┬────────────────────────────────────────┘
                      │
                      ↓
┌──────────────────────────────────────────────────────────────┐
│  10. Service Layer Returns to Controller                     │
│      - User authenticated successfully                       │
│      - JWT token generated                                   │
└─────────────────────┬────────────────────────────────────────┘
                      │
                      ↓
┌──────────────────────────────────────────────────────────────┐
│  11. Message Converter                                       │
│      - Serializes AuthResponse to JSON using Jackson         │
│      - Sets Content-Type: application/json                   │
└─────────────────────┬────────────────────────────────────────┘
                      │
                      ↓
┌──────────────────────────────────────────────────────────────┐
│  12. HTTP Response                                           │
│      HTTP/1.1 200 OK                                         │
│      Set-Cookie: jwt=eyJhbGc...; HttpOnly; Secure            │
│      Content-Type: application/json                          │
│                                                              │
│      {                                                       │
│        "token": "eyJhbGc...",                                │
│        "username": "john",                                   │
│        "role": "ROLE_USER"                                   │
│      }                                                       │
└──────────────────────────────────────────────────────────────┘
```

### 10.2 Example: Login Request

**1. Client sends request:**
```
POST /auth/login HTTP/1.1
Host: localhost:8080
Content-Type: application/json
Content-Length: 54

{
  "username": "john",
  "password": "securePassword123"
}
```

**2. Spring Security processes request:**
- No JWT cookie → user not authenticated (OK for login endpoint)

**3. DispatcherServlet routes to AuthController.login():**
```java
@PostMapping("/login")
public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
    // request.username = "john"
    // request.password = "securePassword123"
}
```

**4. Service layer authenticates:**
```java
var user = userService.authenticate(request);
// Checks password hash matches
```

**5. Repository queries database:**
```sql
SELECT * FROM users WHERE username = 'john';
```

**6. Password verified, JWT generated:**
```java
String token = jwtService.generateToken(user);
// token = "eyJhbGciOiJIUzI1NiJ9..."
```

**7. Cookie created:**
```java
ResponseCookie cookie = ResponseCookie.from("jwt", token)
    .httpOnly(true)
    .secure(true)
    .maxAge(24 * 60 * 60)
    .build();
```

**8. Response sent:**
```
HTTP/1.1 200 OK
Set-Cookie: jwt=eyJhbGciOiJIUzI1NiJ9...; HttpOnly; Secure; Max-Age=86400; Path=/
Content-Type: application/json
Transfer-Encoding: chunked

{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "username": "john",
  "role": "ROLE_USER"
}
```

---

## Key Takeaways

### What You Learned

1. **@RestController**
   - Returns JSON automatically
   - Combination of @Controller + @ResponseBody
   - Used for REST APIs

2. **Request Mapping**
   - `@RequestMapping` at class level (base path)
   - `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`
   - HTTP methods convey intent

3. **Parameters**
   - `@PathVariable` - Part of URL path (`/users/{id}`)
   - `@RequestParam` - Query parameters (`?page=0&size=10`)
   - `@RequestBody` - JSON in request body
   - `Authentication` - Current authenticated user

4. **ResponseEntity**
   - Control HTTP status code
   - Add custom headers
   - Return different types based on conditions

5. **Validation**
   - `@Valid` triggers validation
   - Validation annotations in DTOs
   - Automatic 400 Bad Request on validation failure

6. **HTTP Request Flow**
   - Client → Tomcat → Security Filters → DispatcherServlet → Controller → Service → Repository → Database
   - Response travels back: Database → Repository → Service → Controller → JSON Serialization → Client

---

## What's Next?

You now understand how REST controllers handle HTTP requests. Next:

**→ [08-DTOS-AND-MAPPERS.md](./08-DTOS-AND-MAPPERS.md)** - Data Transfer Objects and entity-DTO mapping

**Key Questions for Next Section:**
- Why use DTOs instead of returning entities directly?
- How to convert entities to DTOs?
- What is the DTO pattern and why is it important?
- How to handle nested relationships in DTOs?

**Completed**:
- ✅ Java Essentials
- ✅ Spring Core (IoC, DI)
- ✅ Spring Boot Essentials
- ✅ JPA & Hibernate Basics
- ✅ JPA Relationships
- ✅ Spring Data JPA Repositories
- ✅ REST Controllers & HTTP Flow

**Next**: DTOs and Mappers 🎯
