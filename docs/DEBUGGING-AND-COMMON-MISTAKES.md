# Debugging Guide & Common Mistakes

A practical guide to troubleshooting issues in "The Dispatch" project, with common mistakes beginners make and how to fix them.

---

## Table of Contents

1. [Backend Debugging](#backend-debugging)
2. [Frontend Debugging](#frontend-debugging)
3. [Common Spring Boot Mistakes](#common-spring-boot-mistakes)
4. [Common Angular Mistakes](#common-angular-mistakes)
5. [Database Issues](#database-issues)
6. [Authentication Problems](#authentication-problems)
7. [CORS Errors](#cors-errors)
8. [Performance Issues](#performance-issues)
9. [Debugging Tools](#debugging-tools)

---

## Backend Debugging

### Enable SQL Logging

**application.properties**:
```properties
# See all SQL queries
spring.jpa.show-sql=true

# Format SQL for readability
spring.jpa.properties.hibernate.format_sql=true

# Show parameter values
logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE
```

**Example Output**:
```sql
Hibernate:
    select
        user0_.id as id1_5_,
        user0_.username as username2_5_,
        user0_.email as email3_5_
    from
        users user0_
    where
        user0_.username=?
binding parameter [1] as [VARCHAR] - [johndoe]
```

### Enable Spring Logging

**application.properties**:
```properties
# See all Spring Web logs
logging.level.org.springframework.web=DEBUG

# See Spring Security decisions
logging.level.org.springframework.security=DEBUG

# See your application logs
logging.level._blog.blog=DEBUG
```

### Using Lombok @Slf4j

**Add to any class**:
```java
@Service
@Slf4j  // <-- Generates a 'log' field
public class PostServiceImpl implements PostService {

    public Post getPost(Long id) {
        log.info("Getting post with id: {}", id);  // Info level
        log.debug("Full post details: {}", post);   // Debug level
        log.error("Failed to get post", exception); // Error level
        log.warn("Post might be deleted");          // Warning level

        return post;
    }
}
```

**Log Levels** (from least to most severe):
- `TRACE`: Very detailed information
- `DEBUG`: Detailed information for debugging
- `INFO`: General informational messages
- `WARN`: Warning messages (might be a problem)
- `ERROR`: Error messages (definitely a problem)

### Debugging REST Endpoints

**Print request details**:
```java
@PostMapping("/posts/create")
public ResponseEntity<String> createPost(
        @RequestBody PostRequest request,
        Authentication auth) {

    log.info("Received request: {}", request);
    log.info("Authenticated user: {}", auth.getName());

    try {
        postService.createPost(request, auth);
        log.info("Post created successfully");
        return ResponseEntity.ok("Success");
    } catch (Exception e) {
        log.error("Failed to create post", e);
        return ResponseEntity.internalServerError().body("Failed");
    }
}
```

### Using IntelliJ Debugger

1. **Set Breakpoint**: Click left of line number (red dot appears)
2. **Debug Mode**: Right-click Spring Boot main class → "Debug 'BlogApplication'"
3. **Step Through**:
   - `F8`: Step over (run current line)
   - `F7`: Step into (enter method)
   - `Shift+F8`: Step out (exit method)
4. **Evaluate Expression**: Select code → Right-click → "Evaluate Expression"

**Common Breakpoint Locations**:
- Controller methods (entry point)
- Service methods (business logic)
- Before/after database operations

---

## Frontend Debugging

### Browser DevTools

**Open DevTools**:
- Chrome/Edge: `F12` or `Ctrl+Shift+I`
- Firefox: `F12` or `Ctrl+Shift+I`
- Safari: `Cmd+Option+I`

### Console Logging

**In TypeScript**:
```typescript
export class HomeComponent {
  loadPosts() {
    console.log('Loading posts...');

    this.apiService.getPosts().subscribe({
      next: (posts) => {
        console.log('Received posts:', posts);
        console.log('Post count:', posts.length);
        this.posts.set(posts);
      },
      error: (err) => {
        console.error('Failed to load posts:', err);
        console.error('Error status:', err.status);
        console.error('Error message:', err.message);
      }
    });
  }
}
```

**Better Logging**:
```typescript
// Group related logs
console.group('Loading Posts');
console.log('User:', this.authService.currentUser());
console.log('Page:', this.currentPage);
console.log('Size:', this.pageSize);
console.groupEnd();

// Log tables
console.table(this.posts());

// Log with colors
console.log('%cSuccess!', 'color: green; font-size: 20px');
console.log('%cError!', 'color: red; font-weight: bold');
```

### Network Tab

**Inspect HTTP Requests**:
1. Open DevTools → Network tab
2. Reload page or trigger action
3. Click on request to see:
   - **Headers**: Request/response headers, status code
   - **Preview**: Formatted response
   - **Response**: Raw response
   - **Timing**: How long each phase took

**Common Issues**:
- **404 Not Found**: Wrong URL or endpoint doesn't exist
- **401 Unauthorized**: Missing or invalid JWT token
- **403 Forbidden**: Valid token but no permission
- **500 Internal Server Error**: Backend crashed (check backend logs)

### Angular Debugging

**Debug Observable Issues**:
```typescript
this.apiService.getPosts().pipe(
  tap(posts => console.log('Raw posts:', posts)),
  map(posts => posts.filter(p => !p.deleted)),
  tap(filtered => console.log('Filtered posts:', filtered))
).subscribe({
  next: (posts) => console.log('Final posts:', posts),
  error: (err) => console.error('Error:', err),
  complete: () => console.log('Observable completed')
});
```

**Debug Signals**:
```typescript
const posts = signal<Post[]>([]);

// Log when signal changes
effect(() => {
  console.log('Posts changed:', posts());
});

// Log computed signals
const postCount = computed(() => {
  const count = posts().length;
  console.log('Post count computed:', count);
  return count;
});
```

---

## Common Spring Boot Mistakes

### 1. Forgetting @Transactional

**Wrong**:
```java
public void deletePost(Long id) {
    Post post = postRepository.findById(id).orElseThrow();

    // Delete associated comments
    commentRepository.deleteByPost(post);

    // Delete post
    postRepository.delete(post);
    // If deleteByPost succeeds but delete fails, comments are still deleted!
}
```

**Correct**:
```java
@Transactional  // <-- All-or-nothing
public void deletePost(Long id) {
    Post post = postRepository.findById(id).orElseThrow();
    commentRepository.deleteByPost(post);
    postRepository.delete(post);
    // If anything fails, everything rolls back
}
```

### 2. N+1 Query Problem

**Wrong**:
```java
List<Post> posts = postRepository.findAll();

for (Post post : posts) {
    post.getAuthor().getUsername();  // Triggers 1 query per post!
}
// Total: 1 + N queries (N = number of posts)
```

**Correct**:
```java
@Query("SELECT p FROM Post p LEFT JOIN FETCH p.author")
List<Post> findAllWithAuthor();

// Only 1 query for everything!
```

### 3. Not Using DTOs

**Wrong** (exposing entities directly):
```java
@GetMapping("/{id}")
public ResponseEntity<User> getUser(@PathVariable Long id) {
    User user = userService.getUser(id);
    return ResponseEntity.ok(user);  // Exposes password, email, etc!
}
```

**Correct** (use DTOs):
```java
@GetMapping("/{id}")
public ResponseEntity<UserResponse> getUser(@PathVariable Long id) {
    User user = userService.getUser(id);
    UserResponse dto = userMapper.toDto(user);  // Only exposed fields
    return ResponseEntity.ok(dto);
}
```

### 4. Comparing Objects with ==

**Wrong**:
```java
if (user.getId() == post.getAuthor().getId()) {  // Wrong!
    // Compares object references, not values
}
```

**Correct**:
```java
if (user.getId().equals(post.getAuthor().getId())) {  // Correct!
    // Compares actual values
}
```

### 5. Not Handling Optional

**Wrong**:
```java
User user = userRepository.findById(id).get();  // Throws exception if not found!
```

**Correct**:
```java
// Option 1: orElseThrow with custom message
User user = userRepository.findById(id)
    .orElseThrow(() -> new ResourceNotFoundException("User not found"));

// Option 2: orElse with default
User user = userRepository.findById(id)
    .orElse(null);

// Option 3: ifPresent
userRepository.findById(id).ifPresent(user -> {
    // Do something with user
});
```

### 6. Forgetting @RequestBody or @PathVariable

**Wrong**:
```java
@PostMapping("/create")
public ResponseEntity<String> create(PostRequest request) {  // request is null!
    // ...
}
```

**Correct**:
```java
@PostMapping("/create")
public ResponseEntity<String> create(@RequestBody PostRequest request) {
    // request is populated from JSON body
}

@GetMapping("/{id}")
public ResponseEntity<Post> getPost(Long id) {  // id is null!
    // ...
}
```

**Correct**:
```java
@GetMapping("/{id}")
public ResponseEntity<Post> getPost(@PathVariable Long id) {
    // id is extracted from URL
}
```

### 7. Circular Dependencies

**Wrong**:
```java
@Service
public class UserService {
    private final PostService postService;  // UserService → PostService
}

@Service
public class PostService {
    private final UserService userService;  // PostService → UserService
}
// Circular dependency! Spring can't instantiate either.
```

**Solution 1**: Extract common logic to a third service
```java
@Service
public class NotificationService {
    // Both UserService and PostService can inject this
}
```

**Solution 2**: Use `@Lazy`
```java
@Service
public class UserService {
    private final PostService postService;

    public UserService(@Lazy PostService postService) {
        this.postService = postService;
    }
}
```

---

## Common Angular Mistakes

### 1. Not Subscribing to Observables

**Wrong**:
```typescript
loadPosts() {
  this.apiService.getPosts();  // Does nothing! No subscription
}
```

**Correct**:
```typescript
loadPosts() {
  this.apiService.getPosts().subscribe({  // Subscribe to execute
    next: (posts) => this.posts.set(posts),
    error: (err) => console.error(err)
  });
}
```

### 2. Memory Leaks (Not Unsubscribing)

**Wrong**:
```typescript
export class MyComponent {
  ngOnInit() {
    this.apiService.getPosts().subscribe(posts => {
      this.posts.set(posts);
    });
    // Subscription never cleaned up!
  }
}
```

**Correct Option 1**: Store and unsubscribe
```typescript
export class MyComponent implements OnDestroy {
  private subscription = new Subscription();

  ngOnInit() {
    this.subscription.add(
      this.apiService.getPosts().subscribe(posts => {
        this.posts.set(posts);
      })
    );
  }

  ngOnDestroy() {
    this.subscription.unsubscribe();
  }
}
```

**Correct Option 2**: Use async pipe (auto-unsubscribes)
```typescript
export class MyComponent {
  posts$ = this.apiService.getPosts();
}
```
```html
@for (post of posts$ | async; track post.id) {
  <div>{{ post.title }}</div>
}
```

**Correct Option 3**: Use `takeUntilDestroyed()` (Angular 16+)
```typescript
export class MyComponent {
  private destroyRef = inject(DestroyRef);

  ngOnInit() {
    this.apiService.getPosts()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(posts => this.posts.set(posts));
  }
}
```

### 3. Mutating Signals Directly

**Wrong**:
```typescript
posts = signal<Post[]>([]);

addPost(newPost: Post) {
  this.posts().push(newPost);  // Mutates array, signal doesn't detect change!
}
```

**Correct**:
```typescript
posts = signal<Post[]>([]);

addPost(newPost: Post) {
  this.posts.update(current => [...current, newPost]);  // Creates new array
}
```

### 4. Forgetting track in @for

**Wrong** (causes re-rendering of all items):
```html
@for (post of posts(); track post) {
  <div>{{ post.title }}</div>
}
```

**Correct**:
```html
@for (post of posts(); track post.id) {
  <div>{{ post.title }}</div>
}
```

### 5. Not Handling Errors

**Wrong**:
```typescript
this.apiService.deletePost(id).subscribe(() => {
  this.notificationService.success('Deleted!');
});
// If it fails, user sees nothing!
```

**Correct**:
```typescript
this.apiService.deletePost(id).subscribe({
  next: () => {
    this.notificationService.success('Deleted!');
    this.loadPosts(); // Refresh
  },
  error: (err) => {
    this.notificationService.error('Failed to delete');
    console.error(err);
  }
});
```

### 6. Using Wrong Binding Syntax

**Wrong**:
```html
<button (click)="likePost">Like</button>  <!-- Calls immediately on render! -->
<input value="{{title()}}">  <!-- One-way binding only -->
```

**Correct**:
```html
<button (click)="likePost()">Like</button>  <!-- Calls on click -->
<input [value]="title()">  <!-- Property binding -->
<input [(ngModel)]="title">  <!-- Two-way binding -->
```

---

## Database Issues

### 1. Connection Refused

**Error**:
```
Connection to localhost:5432 refused
```

**Solution**:
```bash
# Check if PostgreSQL is running
sudo service postgresql status

# Start if stopped
sudo service postgresql start

# Check if port 5432 is in use
sudo lsof -i :5432
```

### 2. Authentication Failed

**Error**:
```
password authentication failed for user "blog"
```

**Solution**:
```bash
# Reset user password
sudo -u postgres psql

ALTER USER blog WITH PASSWORD 'blog';
\q
```

**Check application.properties**:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/blog
spring.datasource.username=blog
spring.datasource.password=blog
```

### 3. Database Does Not Exist

**Error**:
```
database "blog" does not exist
```

**Solution**:
```bash
# Create database
sudo -u postgres psql
CREATE DATABASE blog;
CREATE USER blog WITH PASSWORD 'blog';
GRANT ALL PRIVILEGES ON DATABASE blog TO blog;
\q
```

### 4. Schema Update Issues

**Error**: Table or column doesn't exist after adding entity

**Solution**:
```properties
# application.properties
spring.jpa.hibernate.ddl-auto=update  # Make sure this is set
```

**If still not working**:
```bash
# Drop and recreate (WARNING: Deletes all data!)
sudo -u postgres psql
DROP DATABASE blog;
CREATE DATABASE blog;
GRANT ALL PRIVILEGES ON DATABASE blog TO blog;
\q

# Restart backend (will recreate tables)
```

---

## Authentication Problems

### 1. JWT Token Not Sent

**Symptom**: Always getting 401 Unauthorized

**Check**:
1. **Is token stored?**
```typescript
console.log('Token:', localStorage.getItem('jwt_token'));
```

2. **Is interceptor registered?**
```typescript
// app.config.ts
export const appConfig: ApplicationConfig = {
  providers: [
    provideHttpClient(
      withInterceptors([authInterceptor])  // Make sure this is here!
    )
  ]
};
```

3. **Is token being sent?**
- Open DevTools → Network tab
- Click on request
- Check Headers → Request Headers
- Should see: `Authorization: Bearer eyJ...`

### 2. Token Expired

**Symptom**: Was working, now 401 Unauthorized

**Solution**: Login again to get new token
```typescript
isTokenExpired(token: string): boolean {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    const expiresAt = payload.exp * 1000;
    console.log('Token expires at:', new Date(expiresAt));
    console.log('Now:', new Date());
    return expiresAt < Date.now();
  } catch {
    return true;
  }
}
```

### 3. CORS Preflight Failure

**Error in console**:
```
Access to XMLHttpRequest has been blocked by CORS policy:
Response to preflight request doesn't pass access control check
```

**Solution**: Check SecurityConfig.java:
```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();

    // Make sure frontend URL is allowed
    configuration.setAllowedOrigins(Arrays.asList(
        "http://localhost:4200"  // Your Angular dev server
    ));

    configuration.setAllowedMethods(Arrays.asList(
        "GET", "POST", "PUT", "DELETE", "OPTIONS"  // Include OPTIONS!
    ));

    configuration.setAllowedHeaders(Arrays.asList("*"));
    configuration.setAllowCredentials(true);
    configuration.setMaxAge(3600L);  // Cache preflight for 1 hour

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
}
```

---

## CORS Errors

### Understanding CORS

**What is CORS?**
- Cross-Origin Resource Sharing
- Browser security feature
- Prevents malicious sites from accessing your API

**When does CORS apply?**
- Only in browsers (not Postman, curl)
- When frontend and backend are on different origins

**Origins**:
- `http://localhost:4200` (Angular)
- `http://localhost:8080` (Spring Boot)
- These are **different origins** → CORS applies!

### Common CORS Errors

**Error 1**: "No 'Access-Control-Allow-Origin' header"
```
Access to XMLHttpRequest at 'http://localhost:8080/posts/all'
from origin 'http://localhost:4200' has been blocked by CORS policy:
No 'Access-Control-Allow-Origin' header is present
```

**Solution**: Add CORS configuration in Spring Boot (see above)

**Error 2**: "The value of 'Access-Control-Allow-Credentials' is '' which must be 'true'"
```properties
configuration.setAllowCredentials(true);  // Add this!
```

**Error 3**: "Method DELETE is not allowed"
```java
configuration.setAllowedMethods(Arrays.asList(
    "GET", "POST", "PUT", "DELETE"  // Make sure all methods are listed
));
```

### Testing CORS

**Test with curl**:
```bash
curl -H "Origin: http://localhost:4200" \
     -H "Access-Control-Request-Method: POST" \
     -H "Access-Control-Request-Headers: Authorization" \
     -X OPTIONS \
     http://localhost:8080/posts/all \
     -v
```

**Should see**:
```
Access-Control-Allow-Origin: http://localhost:4200
Access-Control-Allow-Methods: GET, POST, PUT, DELETE
Access-Control-Allow-Credentials: true
```

---

## Performance Issues

### 1. Slow Page Load

**Symptom**: Page takes 5+ seconds to load

**Possible Causes**:

**N+1 Queries** (most common):
```java
// Enable SQL logging to see
spring.jpa.show-sql=true
```

**If you see many similar queries**:
```sql
SELECT * FROM posts WHERE id = 1
SELECT * FROM users WHERE id = 5
SELECT * FROM posts WHERE id = 2
SELECT * FROM users WHERE id = 3
...
```

**Solution**: Use JOIN FETCH (see ADVANCED-CONCEPTS.md)

### 2. Memory Issues (Backend)

**Symptom**: Backend crashes, OutOfMemoryError

**Possible Causes**:
1. Loading too much data at once
2. Not using pagination
3. Memory leak (holding references)

**Solution**:
```java
// Don't do this
List<Post> posts = postRepository.findAll();  // Loads 10,000 posts!

// Do this
Page<Post> posts = postRepository.findAll(PageRequest.of(0, 20));
```

### 3. Memory Issues (Frontend)

**Symptom**: Browser tab becomes slow/unresponsive

**Possible Causes**:
1. Memory leaks (not unsubscribing)
2. Too many DOM elements
3. Heavy operations in rendering

**Debug**:
1. Chrome DevTools → Memory tab
2. Take heap snapshot
3. Look for "Detached" elements

**Solution**: Unsubscribe from observables (see above)

---

## Debugging Tools

### 1. Postman

**Test API without frontend**:

**Login**:
```
POST http://localhost:8080/auth/login
Content-Type: application/json

{
  "usernameOrEmail": "john",
  "password": "password123"
}
```

**Copy token from response**, then:

**Get Posts**:
```
GET http://localhost:8080/posts/all
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

### 2. Database Client

**DBeaver** (free, cross-platform):
1. Download from https://dbeaver.io/
2. Connect to PostgreSQL
3. Browse tables, run queries

**Useful Queries**:
```sql
-- See all users
SELECT * FROM users;

-- See all posts with author
SELECT p.id, p.title, u.username as author
FROM posts p
JOIN users u ON p.author_id = u.id;

-- Count notifications per user
SELECT u.username, COUNT(n.id) as notification_count
FROM users u
LEFT JOIN notifications n ON n.user_id = u.id
GROUP BY u.username;

-- See unread notifications
SELECT u.username, n.message, n.created_at
FROM notifications n
JOIN users u ON n.user_id = u.id
WHERE n.read = false
ORDER BY n.created_at DESC;
```

### 3. Browser Extensions

**React/Angular DevTools**:
- Chrome: Search "Angular DevTools" in Chrome Web Store
- Firefox: Search "Angular DevTools" in Firefox Add-ons

**Features**:
- Inspect component tree
- See component properties
- View signal values
- Profile performance

### 4. Spring Boot Actuator

**Add to pom.xml**:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

**application.properties**:
```properties
management.endpoints.web.exposure.include=health,info,metrics
```

**Access**:
```
http://localhost:8080/actuator/health
http://localhost:8080/actuator/metrics
```

---

## Troubleshooting Checklist

### Backend Not Starting

- [ ] Is PostgreSQL running?
- [ ] Is database created?
- [ ] Are environment variables set (JWT_SECRET_KEY)?
- [ ] Is port 8080 free? (Check with `lsof -i :8080`)
- [ ] Maven dependencies downloaded? (Run `./mvnw clean install`)

### Frontend Not Starting

- [ ] Node modules installed? (`npm install`)
- [ ] Is port 4200 free?
- [ ] Angular CLI installed? (`npm install -g @angular/cli`)
- [ ] Correct Node version? (18+)

### API Calls Failing

- [ ] Is backend running?
- [ ] Correct API URL in environment.ts?
- [ ] JWT token present? (Check localStorage)
- [ ] CORS configured?
- [ ] Check Network tab for actual error
- [ ] Check backend logs

### Authentication Not Working

- [ ] User exists in database?
- [ ] Password correct?
- [ ] JWT_SECRET_KEY set in backend?
- [ ] Token stored in localStorage?
- [ ] Interceptor registered?
- [ ] Token not expired?

---

## Common Error Messages Decoded

### Backend Errors

**"Could not resolve placeholder 'JWT_SECRET_KEY'"**
```bash
export JWT_SECRET_KEY=$(openssl rand -hex 32)
./mvnw spring-boot:run
```

**"Table 'blog.posts' doesn't exist"**
```properties
# application.properties
spring.jpa.hibernate.ddl-auto=update  # Should be 'update' or 'create'
```

**"Cannot construct instance of User"**
- Add `@NoArgsConstructor` to entity
- Or add default constructor manually

**"StackOverflowError" when returning entity**
- Circular reference in JSON serialization
- Use DTOs instead of entities in controllers

### Frontend Errors

**"Cannot find module '@angular/core'"**
```bash
npm install
```

**"Property 'x' does not exist on type"**
- Check your model interfaces
- TypeScript is case-sensitive

**"Cannot read properties of undefined"**
- Check null safety with optional chaining: `user?.email`
- Or use `@if` in template: `@if (user) { {{ user.email }} }`

**"NG0100: ExpressionChangedAfterItHasBeenCheckedError"**
- You're changing data during change detection
- Wrap in `setTimeout()` or use `ChangeDetectorRef`

---

## Pro Tips

### 1. Always Check Logs First

**Backend**: Look at console where Spring Boot is running
**Frontend**: Open browser DevTools → Console tab

### 2. Use Breakpoints, Not Endless console.log

**Backend**: IntelliJ debugger
**Frontend**: DevTools → Sources → Set breakpoint

### 3. Simplify to Isolate

**If feature broken**:
1. Test endpoint with Postman (is backend working?)
2. Check Network tab (is request reaching backend?)
3. Test with hardcoded data (is frontend logic working?)

### 4. Read Error Messages Carefully

**Don't just see "error" and panic**:
- Read the full message
- Note the line number
- Google the specific error message

### 5. Git Commit Often

```bash
# Before making risky changes
git add .
git commit -m "Working state before experiment"

# If you break something
git reset --hard HEAD  # Go back to last commit
```

---

## Getting Help

### Stack Overflow Search Template

```
[spring-boot] <your specific error message>
[angular] <your specific error message>
```

### Good Question Format

1. **What are you trying to do?**
2. **What did you expect to happen?**
3. **What actually happened?** (error message, unexpected behavior)
4. **What have you tried?**
5. **Relevant code** (minimal reproducible example)

### Documentation Links

**Spring Boot**:
- https://spring.io/guides
- https://www.baeldung.com/

**Angular**:
- https://angular.dev/
- https://rxjs.dev/

**PostgreSQL**:
- https://www.postgresql.org/docs/

---

## Summary

**Most Common Issues**:
1. Forgetting `@Transactional`
2. Not subscribing to Observables
3. CORS misconfiguration
4. N+1 query problem
5. Not handling errors

**Best Debugging Practices**:
1. Enable logging
2. Use debugger, not console.log
3. Read error messages fully
4. Test in isolation (Postman)
5. Check both frontend and backend

**Remember**: Every expert was stuck once too. Debugging is a skill that improves with practice!
