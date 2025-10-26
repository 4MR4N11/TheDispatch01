# The Dispatch - Complete Learning Guide
## Spring Boot & Angular Tutorial for Beginners

---

## Table of Contents

### Part 1: Spring Boot Fundamentals
1. [Project Overview](#project-overview)
2. [Spring Boot Basics](#spring-boot-basics)
3. [Understanding Annotations](#understanding-annotations)
4. [JPA Relationships](#jpa-relationships)
5. [Dependency Injection](#dependency-injection)
6. [Spring MVC & REST APIs](#spring-mvc--rest-apis)
7. [Service Layer](#service-layer)
8. [Repository Layer](#repository-layer)
9. [Spring Security & JWT](#spring-security--jwt)

### Part 2: Angular Fundamentals
10. [Angular Basics](#angular-basics)
11. [TypeScript Fundamentals](#typescript-fundamentals)
12. [Components](#components)
13. [Signals - Reactive State](#signals---reactive-state)
14. [Services in Angular](#services-in-angular)
15. [Observables & RxJS](#observables--rxjs)
16. [Template Syntax](#template-syntax)
17. [HTTP Interceptors](#http-interceptors)
18. [Routing & Guards](#routing--guards)

### Part 3: Full Stack Integration
19. [Complete Flow Example: Like a Post](#complete-flow-example-like-a-post)
20. [Key Concepts Recap](#key-concepts-recap)

---

# PART 1: SPRING BOOT FUNDAMENTALS

## Project Overview

**The Dispatch** is a full-stack blog platform with:
- **Backend**: Spring Boot 3.5.6 (Java 17)
- **Frontend**: Angular 20.3 (TypeScript 5.9.2)
- **Database**: PostgreSQL 18+
- **Authentication**: JWT (JSON Web Tokens)
- **Features**: Posts, Comments, Likes, Subscriptions, Notifications, Reporting System

---

## Spring Boot Basics

### What is Spring Boot?

**Spring** is a Java framework for building applications. **Spring Boot** is Spring made easier - it auto-configures everything so you can start coding immediately.

**Analogy**: Think of Spring as a toolbox with hundreds of tools. Spring Boot is like having an assistant who hands you exactly the tools you need, already configured.

### The Main Application Class

**File**: `backend/src/main/java/_blog/blog/BlogApplication.java`

```java
package _blog.blog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BlogApplication {
    public static void main(String[] args) {
        SpringApplication.run(BlogApplication.class, args);
    }
}
```

#### Understanding Every Line:

1. **`package _blog.blog;`**
   - A **package** is like a folder for organizing your code
   - `_blog.blog` is the base package name

2. **Imports**
   ```java
   import org.springframework.boot.SpringApplication;
   import org.springframework.boot.autoconfigure.SpringBootApplication;
   ```
   - Brings in code from other libraries
   - Like saying "I need to use these tools from Spring Boot"

3. **`@SpringBootApplication`**
   - This is an **annotation** - metadata that tells Spring "this is special"
   - Does 3 things automatically:
     - `@Configuration`: Says this class can define beans (objects Spring manages)
     - `@EnableAutoConfiguration`: Tells Spring Boot to guess what you need
     - `@ComponentScan`: Tells Spring to look for classes in this package

4. **`main` method**
   ```java
   public static void main(String[] args) {
       SpringApplication.run(BlogApplication.class, args);
   }
   ```
   - Every Java application needs a `main` method - the entry point
   - `SpringApplication.run()` starts the entire Spring Boot application

#### What Happens When You Run This?

1. Spring Boot starts
2. Scans all files in `_blog.blog` package
3. Finds classes with annotations like `@RestController`, `@Service`, `@Repository`
4. Creates instances of those classes
5. Connects them together (Dependency Injection)
6. Starts web server on port 8080
7. Your API is ready!

---

## Understanding Annotations

Annotations are like **labels** that give instructions to Spring.

### JPA Entity Annotations

**File**: `backend/src/main/java/_blog/blog/entity/User.java`

```java
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable=false, length=30)
    private String firstName;

    // ... more fields
}
```

#### Annotation Breakdown:

**`@Entity`**
- **Meaning**: "This class represents a database table"
- **What Spring Does**: Creates a table in PostgreSQL when the app starts

**`@Table(name = "users")`**
- **Meaning**: "The table should be called 'users'"
- **Without This**: Table would be named "User" (class name)

**`@Getter` & `@Setter`** (Lombok)
- **Meaning**: Lombok generates getter/setter methods for all fields
- **What It Generates**:
  ```java
  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public String getFirstName() { return firstName; }
  public void setFirstName(String firstName) { this.firstName = firstName; }
  // ... for every field
  ```
- **Why It's Awesome**: Saves you from writing 200+ lines of repetitive code!

**`@NoArgsConstructor`**
- Generates: `User() {}`

**`@AllArgsConstructor`**
- Generates: `User(Long id, String firstName, String lastName, ...)`

**`@Builder`**
- Generates builder pattern:
  ```java
  User user = User.builder()
      .id(1L)
      .firstName("John")
      .lastName("Doe")
      .build();
  ```

**`@Id` & `@GeneratedValue`**
```java
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;
```
- `@Id`: This is the primary key
- `@GeneratedValue`: Database auto-generates the ID
- `IDENTITY`: Use database's auto-increment feature

**`@Column`**
```java
@Column(name = "first_name", nullable=false, length=30)
private String firstName;
```
- Column in database is called `first_name`
- Cannot be NULL (required field)
- Maximum 30 characters

**SQL Equivalent**:
```sql
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    first_name VARCHAR(30) NOT NULL,
    ...
);
```

---

## JPA Relationships

### The Most Important Concept in Spring Data JPA

JPA (Java Persistence API) handles relationships between database tables automatically.

### Relationship Types

#### 1. @ManyToOne - Many Posts belong to One User

**File**: `backend/src/main/java/_blog/blog/entity/Post.java`

```java
@ManyToOne
@JoinColumn(name="author_id", nullable=false)
private User author;
```

**Real-world analogy**: Many articles written by one author

**What happens in database**:
```sql
CREATE TABLE posts (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200),
    author_id BIGINT NOT NULL,  -- Stores the User's ID
    FOREIGN KEY (author_id) REFERENCES users(id)
);
```

**In Java**:
```java
// Creating a post
User john = userRepository.findById(1L).get();
Post post = new Post();
post.setAuthor(john);  // Links post to user
postRepository.save(post);
```

**Key Points**:
- `@JoinColumn(name="author_id")` creates a column that stores the user's ID
- The **"Many" side** has the foreign key column
- Posts table has `author_id`, not Users table

---

#### 2. @OneToMany - One User has Many Posts

**File**: `backend/src/main/java/_blog/blog/entity/User.java`

```java
@OneToMany(mappedBy="author", cascade=CascadeType.ALL, orphanRemoval=true)
private List<Post> Posts;
```

**What this means**:
- One User can have multiple Posts
- `mappedBy="author"` says "the Post class has a field called 'author' that owns this relationship"
- `cascade=CascadeType.ALL` means operations cascade (if you delete a user, delete their posts too)
- `orphanRemoval=true` means if you remove a post from the list, delete it from database

**Real example**:
```java
User john = userRepository.findById(1L).get();
List<Post> johnsPosts = john.getPosts(); // Gets all posts by John

// Delete all John's posts
john.getPosts().clear();
userRepository.save(john); // Posts are deleted due to orphanRemoval
```

**Important**: `mappedBy` means "I don't own this relationship, the other side does"

---

#### 3. @ManyToMany - Users can like Many Posts, Posts can be liked by Many Users

**In Post.java (OWNING SIDE)**:
```java
@ManyToMany
@JoinTable(
    name = "post_likes",
    joinColumns = @JoinColumn(name = "post_id"),
    inverseJoinColumns = @JoinColumn(name = "user_id")
)
private Set<User> likedBy = new HashSet<>();
```

**In User.java (INVERSE SIDE)**:
```java
@ManyToMany(mappedBy = "likedBy")
private Set<Post> likedPosts = new HashSet<>();
```

**What happens in database**:
```sql
-- Creates a JOIN TABLE
CREATE TABLE post_likes (
    post_id BIGINT REFERENCES posts(id),
    user_id BIGINT REFERENCES users(id),
    PRIMARY KEY (post_id, user_id)
);
```

**Visual representation**:
```
Users:        Posts:
┌────┐        ┌────┐
│ 1  │───┐    │ 10 │
└────┘   │    └────┘
         │      ▲
┌────┐   └──────┘
│ 2  │──────┘
└────┘

post_likes table:
┌─────────┬─────────┐
│ post_id │ user_id │
├─────────┼─────────┤
│   10    │    1    │  -- User 1 likes Post 10
│   10    │    2    │  -- User 2 likes Post 10
└─────────┴─────────┘
```

**Real example**:
```java
// User likes a post
User user = userRepository.findById(1L).get();
Post post = postRepository.findById(10L).get();

post.getLikedBy().add(user);  // Add user to post's likedBy set
postRepository.save(post);     // Save creates row in post_likes table

// Check if user liked post
boolean liked = post.getLikedBy().contains(user);
```

---

### Cascade Types Explained

```java
@OneToMany(mappedBy="author", cascade=CascadeType.ALL, orphanRemoval=true)
```

**Cascade types** control what happens to related entities:

| Cascade Type | What It Does |
|--------------|--------------|
| `ALL` | All operations (persist, remove, refresh, merge, detach) cascade |
| `PERSIST` | When you save parent, save children too |
| `REMOVE` | When you delete parent, delete children too |
| `MERGE` | When you update parent, update children too |
| `REFRESH` | When you reload parent from DB, reload children |
| `DETACH` | When you detach parent from session, detach children |

**Example**:
```java
User user = new User();
user.setUsername("john");

Post post1 = new Post();
post1.setTitle("First Post");
post1.setAuthor(user);

Post post2 = new Post();
post2.setTitle("Second Post");
post2.setAuthor(user);

user.setPosts(Arrays.asList(post1, post2));

userRepository.save(user);
// Because of cascade=ALL, this saves:
// 1. The user
// 2. Both posts automatically!
```

**Without cascade**, you'd have to do:
```java
userRepository.save(user);
postRepository.save(post1);
postRepository.save(post2);
```

---

## Dependency Injection

### Spring's Superpower

**Problem**: Objects need other objects. How do they get them?

**Bad way** (without Spring):
```java
public class PostController {
    private PostService postService = new PostService();  // ❌ Tight coupling
    private UserService userService = new UserService();  // ❌ Hard to test
}
```

**Spring way** (Dependency Injection):

**File**: `backend/src/main/java/_blog/blog/controller/PostController.java`

```java
@RestController
@RequestMapping("/posts")
public class PostController {
    private final UserService userService;
    private final PostService postService;
    private final CommentService commentService;

    // Constructor Injection (best practice)
    public PostController(UserService userService,
                         PostService postService,
                         CommentService commentService) {
        this.userService = userService;
        this.postService = postService;
        this.commentService = commentService;
    }
}
```

### What Spring Does Automatically:

1. **At startup**, Spring scans for classes with `@Service`, `@RestController`, `@Repository`
2. **Creates one instance** of each (called a "bean" or "singleton")
3. **Sees the constructor** needs UserService, PostService, CommentService
4. **Injects them** automatically - passes the instances to the constructor

### Why This Is Amazing:

- ✅ You never write `new UserService()` - Spring manages it
- ✅ Easy to test - can inject mock services
- ✅ Loose coupling - controller doesn't know how services are created
- ✅ Single instance reused - memory efficient

### Spring's Object Creation Flow:

```
1. Creates UserRepository
2. Creates PostRepository
3. Creates UserService (needs UserRepository) ← injects #1
4. Creates PostService (needs PostRepository) ← injects #2
5. Creates PostController (needs UserService, PostService) ← injects #3, #4
```

### Three Types of Injection:

```java
// 1. Constructor Injection (BEST - recommended)
public PostController(UserService userService) {
    this.userService = userService;
}

// 2. Field Injection (NOT recommended - hard to test)
@Autowired
private UserService userService;

// 3. Setter Injection (rare)
@Autowired
public void setUserService(UserService userService) {
    this.userService = userService;
}
```

---

## Spring MVC & REST APIs

### How REST APIs Work

**File**: `backend/src/main/java/_blog/blog/controller/PostController.java`

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

### Breaking Down the REST Endpoint:

#### 1. `@PostMapping("/create")`
- Maps HTTP POST requests to `/posts/create`
- Remember: `@RequestMapping("/posts")` on class level
- **Full URL**: `POST http://localhost:8080/posts/create`
- **Other options**: `@GetMapping`, `@PutMapping`, `@DeleteMapping`, `@PatchMapping`

#### 2. `@RequestBody PostRequest request`
- Takes JSON from request body and converts it to Java object
- **Example incoming JSON**:
  ```json
  {
    "title": "My First Post",
    "content": "{\"blocks\":[...]}",
    "media_url": "http://example.com/image.jpg"
  }
  ```
- **Spring automatically converts this to**:
  ```java
  PostRequest request = new PostRequest();
  request.setTitle("My First Post");
  request.setContent("{\"blocks\":[...]}");
  request.setMediaUrl("http://example.com/image.jpg");
  ```

#### 3. `@Valid` - Validation

**File**: `backend/src/main/java/_blog/blog/dto/PostRequest.java`

```java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PostRequest {
    @Size(max = 200, message = "Title must not exceed 200 characters")
    String title;

    @NotBlank(message = "Content is required")
    @Size(min = 1, max = 100000, message = "Content must be between 1 and 100000 characters")
    String content;

    @Pattern(regexp = "^(image|video|audio)?$", message = "Media type must be 'image', 'video', 'audio', or empty")
    String media_type;

    @Size(max = 2048, message = "Media URL must not exceed 2048 characters")
    @Pattern(regexp = "^(https?://.*|/uploads/.*)?$", message = "Media URL must be a valid HTTP/HTTPS URL or relative upload path")
    String media_url;
}
```

**Validation Annotations**:
- `@NotBlank`: Ensures string is not null, not empty, not just whitespace
- `@Size`: Checks length
- `@Pattern`: Validates against regex
- If validation fails, Spring automatically returns `400 Bad Request`

**Example validation failure response**:
```json
{
  "timestamp": "2025-10-23T20:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Content is required"
}
```

#### 4. `Authentication auth`
- Spring Security automatically injects the current logged-in user
- `auth.getName()` returns the username from the JWT token
- This is why you can do: `User user = userService.getUserByUsername(auth.getName())`

#### 5. `@PathVariable` (in another method)

```java
@PutMapping("/{id}")
public ResponseEntity<String> updatePost(
    @PathVariable Long id,
    @Valid @RequestBody PostRequest request,
    Authentication auth
) {
    // id is extracted from URL
}
```
- Extracts `id` from URL path
- Example: `PUT /posts/42` → `id = 42L`

#### 6. `ResponseEntity<String>`
- Wrapper that lets you control HTTP response
- Examples:
  ```java
  ResponseEntity.ok("message")              // HTTP 200 with body
  ResponseEntity.status(403).body("error")  // HTTP 403 with body
  ResponseEntity.noContent().build()        // HTTP 204 (no body)
  ```

---

## Service Layer

### Business Logic

**File**: `backend/src/main/java/_blog/blog/service/PostServiceImpl.java`

```java
@Service
public class PostServiceImpl implements PostService {

    private final PostRepository postRepository;
    private final SubscriptionService subscriptionService;
    private final CommentService commentService;
    private final LikeService likeService;
    private final NotificationRepository notificationRepository;

    public PostServiceImpl(LikeService likeService,
                          CommentService commentService,
                          PostRepository postRepository,
                          SubscriptionService subscriptionService,
                          NotificationRepository notificationRepository) {
        this.postRepository = postRepository;
        this.subscriptionService = subscriptionService;
        this.commentService = commentService;
        this.likeService = likeService;
        this.notificationRepository = notificationRepository;
    }

    @Override
    public Post createPost(PostRequest request, User author) {
        Post post = PostMapper.toEntity(request, author);
        return postRepository.save(post);
    }

    @Override
    public Post updatePost(Long postId, PostRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        post.setTitle(request.getTitle());
        post.setContent(request.getContent());
        post.setMediaType(request.getMedia_type());
        post.setMediaUrl(request.getMedia_url());

        return postRepository.save(post);
    }

    @Override
    @Transactional
    public boolean deletePost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        // Delete all notifications related to this post first
        notificationRepository.deleteByPost(post);

        // Then delete the post (comments and likes handled by cascade)
        postRepository.delete(post);
        return true;
    }
}
```

### Service Layer Explained:

#### 1. `@Service` annotation
```java
@Service
public class PostServiceImpl implements PostService {
```
- Marks this as a Spring-managed service
- Spring creates one instance (singleton)
- Available for dependency injection

#### 2. Interface Implementation Pattern
```java
public class PostServiceImpl implements PostService {
```
**Why use interface + implementation?**
- ✅ Easy to switch implementations (testing, different databases)
- ✅ Follows Dependency Inversion Principle (SOLID)
- ✅ Can mock interfaces in tests

#### 3. Repository Usage
```java
@Override
public Post createPost(PostRequest request, User author) {
    Post post = PostMapper.toEntity(request, author);
    return postRepository.save(post);
}
```

**Flow**:
1. **DTO → Entity mapping**: Convert PostRequest (API layer) to Post (database layer)
2. **Repository.save()**: Calls JPA to INSERT into database
3. **Return entity**: With generated ID

#### 4. `@Transactional` Annotation
```java
@Override
@Transactional
public boolean deletePost(Long postId) {
    Post post = postRepository.findById(postId)
            .orElseThrow(() -> new RuntimeException("Post not found"));

    notificationRepository.deleteByPost(post);  // Operation 1
    postRepository.delete(post);                // Operation 2
    return true;
}
```

**What @Transactional does**:
- Wraps method in a database transaction
- **All-or-nothing**: Both operations succeed or both rollback
- Example: If deleting notifications succeeds but deleting post fails, notifications are rolled back

**Transaction example**:
```java
@Transactional
public void transferMoney(Account from, Account to, int amount) {
    from.balance -= amount;  // Deduct money
    to.balance += amount;    // Add money
    accountRepo.save(from);
    accountRepo.save(to);
    // If anything fails, BOTH are rolled back - no money lost!
}
```

#### 5. Optional and orElseThrow Pattern
```java
Post post = postRepository.findById(postId)
    .orElseThrow(() -> new RuntimeException("Post not found"));
```

**What's happening**:
- `findById` returns `Optional<Post>` (might be present, might be empty)
- `.orElseThrow()` says "if empty, throw exception"
- **Alternative methods**:
  - `.orElse(defaultValue)` - return default if empty
  - `.orElseGet(() -> createDefault())` - create default if empty
  - `.isPresent()` - check if value exists

#### 6. Feed Algorithm
```java
public List<Post> getFeedPosts(Long userId) {
    List<User> subscriptions = subscriptionService.getSubscriptions(userId);

    List<Long> authorIds = new ArrayList<>();
    authorIds.add(userId); // User's own posts

    authorIds.addAll(subscriptions.stream()
            .map(User::getId)
            .toList());

    return postRepository.findPostsByAuthorIdsWithCommentsAndLikes(authorIds);
}
```

**What this does**:
1. Get list of users this person follows
2. Create list of IDs: [userID, followedUser1, followedUser2, ...]
3. Query database for posts by all those authors
4. Returns personalized feed (like Twitter/X timeline)

**Streams explained** (Java 8+ feature):
```java
subscriptions.stream()      // Convert list to stream
    .map(User::getId)        // Transform each User to their ID
    .toList();               // Collect back to list
```

Equivalent to:
```java
List<Long> ids = new ArrayList<>();
for (User user : subscriptions) {
    ids.add(user.getId());
}
```

---

## Repository Layer

### Database Queries with Spring Data JPA

**File**: `backend/src/main/java/_blog/blog/repository/PostRepository.java`

```java
public interface PostRepository extends JpaRepository<Post, Long> {

    List<Post> findAllByAuthorId(Long authorId);

    @Query("SELECT p FROM Post p LEFT JOIN FETCH p.likedBy WHERE p.id = :postId AND p.hidden = false")
    Optional<Post> findByIdWithLikes(@Param("postId") Long postId);

    @Query("SELECT p.id FROM Post p JOIN p.likedBy u WHERE u.id = :userId AND p.hidden = false")
    List<Long> findPostsLikedByUser(@Param("userId") Long userId);

    @Query("SELECT p FROM Post p LEFT JOIN FETCH p.comments LEFT JOIN FETCH p.likedBy WHERE p.id = :postId AND p.hidden = false")
    Optional<Post> findByIdWithCommentsAndLikes(@Param("postId") Long postId);

    @Query("SELECT p FROM Post p LEFT JOIN FETCH p.comments LEFT JOIN FETCH p.likedBy WHERE p.author.id IN :authorIds AND p.hidden = false ORDER BY p.createdAt DESC")
    List<Post> findPostsByAuthorIdsWithCommentsAndLikes(@Param("authorIds") List<Long> authorIds);

    // Admin queries - include hidden posts
    @Query("SELECT p FROM Post p LEFT JOIN FETCH p.comments LEFT JOIN FETCH p.likedBy ORDER BY p.createdAt DESC")
    List<Post> findAllWithCommentsAndLikesIncludingHidden();
}
```

### Repository Magic - Spring Data JPA

```java
public interface PostRepository extends JpaRepository<Post, Long> {
```

**What is JpaRepository?**
- An interface from Spring Data JPA
- `<Post, Long>` means: "Manage Post entities with Long IDs"
- **You write ZERO implementation code** - Spring generates it!

**What JpaRepository gives you for FREE**:
```java
postRepository.save(post);           // INSERT or UPDATE
postRepository.findById(1L);         // SELECT by ID
postRepository.findAll();            // SELECT all
postRepository.delete(post);         // DELETE
postRepository.count();              // COUNT(*)
postRepository.existsById(1L);       // Check if exists
// ... and many more!
```

### Method Name Query Generation

```java
List<Post> findAllByAuthorId(Long authorId);
```

**Spring automatically generates SQL from method name!**

**Naming pattern**:
```
find + All + By + AuthorId
 ↓     ↓     ↓      ↓
SELECT * FROM posts WHERE author_id = ?
```

**More examples**:
```java
// Spring generates SQL automatically:
findByUsername(String username)          → WHERE username = ?
findByEmailAndPassword(String email, String pwd) → WHERE email = ? AND password = ?
findByCreatedAtAfter(Date date)          → WHERE created_at > ?
findByTitleContaining(String keyword)    → WHERE title LIKE %keyword%
findByAgeLessThan(int age)               → WHERE age < ?
findTop10ByOrderByCreatedAtDesc()        → ORDER BY created_at DESC LIMIT 10
countByStatus(String status)             → SELECT COUNT(*) WHERE status = ?
```

**Keywords you can use**:
- `And`, `Or`
- `LessThan`, `GreaterThan`, `Between`
- `Like`, `Containing`, `StartingWith`, `EndingWith`
- `OrderBy`, `Top`, `First`
- `In`, `NotIn`

### Custom JPQL Queries

```java
@Query("SELECT p FROM Post p LEFT JOIN FETCH p.comments LEFT JOIN FETCH p.likedBy WHERE p.id = :postId AND p.hidden = false")
Optional<Post> findByIdWithCommentsAndLikes(@Param("postId") Long postId);
```

**JPQL vs SQL**:
- **JPQL** (Java Persistence Query Language): Works with Java objects
- **SQL**: Works with database tables

**JPQL example**:
```java
@Query("SELECT p FROM Post p WHERE p.author.username = :username")
```

**Equivalent SQL**:
```sql
SELECT * FROM posts p
INNER JOIN users u ON p.author_id = u.id
WHERE u.username = :username
```

**Key difference**: JPQL uses **object relationships**, SQL uses **foreign keys**.

### LEFT JOIN FETCH - Solving the N+1 Problem

**The N+1 Problem** (performance killer):

**Bad code** (without JOIN FETCH):
```java
@Query("SELECT p FROM Post p WHERE p.hidden = false")
List<Post> getAllPosts();

// In your controller:
List<Post> posts = postRepository.getAllPosts();  // 1 query
for (Post post : posts) {
    post.getComments().size();  // N queries (one per post!)
}
// Total: 1 + N queries (if 100 posts = 101 queries!)
```

**Good code** (with JOIN FETCH):
```java
@Query("SELECT p FROM Post p LEFT JOIN FETCH p.comments WHERE p.hidden = false")
List<Post> getAllPostsWithComments();

List<Post> posts = postRepository.getAllPostsWithComments();  // 1 query!
for (Post post : posts) {
    post.getComments().size();  // No query - already loaded!
}
// Total: 1 query only!
```

**Breakdown of the query**:
```java
@Query("SELECT p FROM Post p LEFT JOIN FETCH p.comments LEFT JOIN FETCH p.likedBy WHERE p.id = :postId AND p.hidden = false")
```

- `SELECT p FROM Post p` - Get posts
- `LEFT JOIN FETCH p.comments` - Also fetch comments in same query
- `LEFT JOIN FETCH p.likedBy` - Also fetch users who liked it
- `WHERE p.id = :postId` - Filter by ID
- `AND p.hidden = false` - Only visible posts
- `@Param("postId")` - Bind method parameter to query

**SQL generated** (approximately):
```sql
SELECT
    p.*,
    c.*,  -- comments
    u.*   -- users who liked
FROM posts p
LEFT JOIN comments c ON c.post_id = p.id
LEFT JOIN post_likes pl ON pl.post_id = p.id
LEFT JOIN users u ON u.id = pl.user_id
WHERE p.id = ? AND p.hidden = false
```

**Result**: One trip to database instead of 3+ trips!

---

## Spring Security & JWT

### JWT Authentication Flow

#### Understanding JWT (JSON Web Tokens)

**What is JWT?**
A JWT is a secure way to send user information. It's like a **digital passport**.

**JWT Structure**:
```
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJqb2huIn0.abc123signature
    ↓                    ↓                  ↓
  HEADER              PAYLOAD          SIGNATURE
```

**Decoded JWT**:
```json
// HEADER
{
  "alg": "HS256",  // Algorithm used
  "typ": "JWT"     // Token type
}

// PAYLOAD (claims)
{
  "sub": "john",              // Subject (username)
  "iat": 1729710000,          // Issued at timestamp
  "exp": 1729713600           // Expiration timestamp
}

// SIGNATURE
HMACSHA256(
  base64(header) + "." + base64(payload),
  secretKey
)
```

#### JWT Service

**File**: `backend/src/main/java/_blog/blog/service/JwtService.java`

```java
@Service
public class JwtService {

    @Value("${security.jwt.secret-key}")
    private String secretKey;

    @Value("${security.jwt.expiration-time}")
    private long jwtExpiration;

    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }

    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return buildToken(extraClaims, userDetails, jwtExpiration);
    }

    private String buildToken(Map<String, Object> extraClaims, UserDetails userDetails, long expiration) {
        return Jwts.builder()
            .setClaims(extraClaims)                                           // Custom data
            .setSubject(userDetails.getUsername())                           // Username
            .setIssuedAt(new Date(System.currentTimeMillis()))              // Now
            .setExpiration(new Date(System.currentTimeMillis() + expiration)) // Now + 1 hour
            .signWith(getSignInKey(), SignatureAlgorithm.HS256)             // Sign it
            .compact();                                                      // Convert to string
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }
}
```

**Token Generation Steps**:
1. **Create claims**: What information to include
2. **Set subject**: Username (who this token is for)
3. **Set timestamps**: When created and when expires
4. **Sign**: Use secret key to create signature (prevents tampering)
5. **Compact**: Convert to string format

**Token Validation Checks**:
1. Username in token matches current user
2. Token hasn't expired
3. Signature is valid (checked automatically by JWT library)

#### JWT Authentication Filter

**File**: `backend/src/main/java/_blog/blog/filter/JwtAuthenticationFilter.java`

```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // Skip auth endpoints
        if (request.getServletPath().contains("/auth")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Extract token from header
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userEmail;

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        jwt = authHeader.substring(7);  // Remove "Bearer " prefix
        userEmail = jwtService.extractUsername(jwt);

        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);

            if (jwtService.isTokenValid(jwt, userDetails) && userDetails.isEnabled()) {
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()  // Roles: ROLE_USER, ROLE_ADMIN
                );
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }
        filterChain.doFilter(request, response);
    }
}
```

**What is a Filter?**
- Runs **before** every HTTP request reaches your controller
- Like a security checkpoint at an airport
- Can modify request/response or block access

**Filter Flow**:
1. Skip `/auth` endpoints (can't check JWT when logging in to GET a JWT!)
2. Extract token from `Authorization: Bearer eyJhbGci...` header
3. Validate token
4. Load user from database
5. Check if user is enabled (not banned)
6. Set SecurityContext (user is now authenticated for this request)
7. Continue to controller

---

# PART 2: ANGULAR FUNDAMENTALS

## Angular Basics

### What is Angular?

**Angular** is a TypeScript framework for building Single Page Applications (SPAs).

**Key concepts**:
- **Components**: Building blocks of UI (like LEGO pieces)
- **Services**: Shared logic (like Spring Services)
- **Dependency Injection**: Same concept as Spring!
- **RxJS**: Reactive programming with Observables
- **Signals**: New reactive primitive (simpler than RxJS)

---

## TypeScript Fundamentals

Angular uses **TypeScript** (JavaScript with types):

```typescript
// JavaScript
let name = "John";
name = 123;  // OK in JS, causes bugs

// TypeScript
let name: string = "John";
name = 123;  // ❌ ERROR: Type 'number' is not assignable to type 'string'
```

### Interfaces

**File**: `frontend/src/app/shared/models/models.ts`

```typescript
export interface UserResponse {
  id: number;
  firstname: string;
  lastname: string;
  username: string;
  email: string;
  avatar: string;
  role: string;
  banned: boolean;
  subscriptions: string[];
  posts: PostResponse[];
}

export interface PostResponse {
  id: number;
  author: string;
  authorAvatar: string;
  title: string;
  content: string;
  media_type: string;
  media_url: string;
  hidden: boolean;
  comments: CommentResponse[];
  created_at: string | Date;
  updated_at: string | Date;
  likeCount: number;
  likedByUsernames: string[];
}
```

**These interfaces match your backend DTOs!**

**Union types** (TypeScript feature):
```typescript
created_at: string | Date;  // Can be EITHER string OR Date
```

**Array types**:
```typescript
comments: CommentResponse[];  // Array of CommentResponse objects
subscriptions: string[];       // Array of strings
```

---

## Components

### Building UI Blocks

**File**: `frontend/src/app/features/home/home.ts`

```typescript
@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, MatIconModule],
  templateUrl: './home.html',
  styleUrl: './home.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomeComponent {
  private readonly apiService = inject(ApiService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly posts = signal<PostResponse[]>([]);
  protected readonly currentUser = this.authService.currentUser;
  protected readonly loading = signal(true);

  constructor() {
    this.loadFeed();
  }

  toggleLike(postId: number, event: Event) {
    event.stopPropagation();
    // ... implementation
  }
}
```

### Component Anatomy:

**@Component decorator** (like Spring's @RestController):
- Tells Angular "this is a component"
- Provides metadata

**selector**: `'app-home'`
- How to use in HTML: `<app-home></app-home>`
- Like a custom HTML tag

**standalone**: `true`
- **New in Angular 16+**: Component doesn't need NgModule
- Component declares its own dependencies

**imports**: `[CommonModule, ...]`
- What this component needs

**templateUrl**: `'./home.html'`
- The HTML template (the view)

**styleUrl**: `'./home.css'`
- CSS styles for this component only
- Styles are **scoped** - won't affect other components!

**changeDetection**: `ChangeDetectionStrategy.OnPush`
- Performance optimization
- Only check for changes when inputs change or events fire

---

## Signals - Reactive State

### Angular's New Reactivity

**What are signals?** Think of them as "smart variables" that notify Angular when they change.

```typescript
protected readonly posts = signal<PostResponse[]>([]);
protected readonly loading = signal(true);
```

**How to use signals**:

```typescript
// 1. Create signal
const count = signal(0);

// 2. Read value (in HTML or TypeScript)
console.log(count());  // Note the () - signals are functions!

// 3. Update value
count.set(5);           // Set to 5
count.update(n => n + 1);  // Increment

// 4. In template (HTML)
{{ count() }}  // Displays: 5
```

**Real example**:
```typescript
togglePostMenu(postId: number, event: Event) {
  if (this.openMenuPostId() === postId) {
    this.openMenuPostId.set(null);   // Close menu
  } else {
    this.openMenuPostId.set(postId); // Open menu for this post
  }
}
```

**In template**:
```html
@if (openMenuPostId() === post.id) {
  <div class="menu">...</div>
}
```

**Why signals are amazing**:
- ✅ Fine-grained reactivity (only update what changed)
- ✅ Simpler than RxJS for local state
- ✅ Better performance
- ✅ More readable (no subscriptions to manage)

---

## Services in Angular

### Shared Logic

**File**: `frontend/src/app/core/auth/auth.service.ts`

```typescript
@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/auth`;

  readonly isLoggedIn = signal(false);
  readonly currentUser = signal<UserResponse | null>(null);

  private readonly TOKEN_KEY = 'authToken';

  // Retrieve token from local storage
  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  // Store token in local storage
  private saveToken(token: string): void {
    localStorage.setItem(this.TOKEN_KEY, token);
  }

  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.baseUrl}/login`, request)
      .pipe(
        tap(response => {
          if (response.token) {
            this.saveToken(response.token);
            this.isLoggedIn.set(true);
          }
        })
      );
  }
}
```

### Service Explained:

**@Injectable({ providedIn: 'root' })**:
- Makes this a singleton service (one instance for entire app)
- `'root'` means available everywhere
- Same as Spring's `@Service`!

**State management with signals**:
```typescript
readonly isLoggedIn = signal(false);
readonly currentUser = signal<UserResponse | null>(null);
```

**These are application-wide state!**
- Any component can read: `authService.isLoggedIn()`
- When changed, all components using it update automatically

**localStorage** (browser's database):
```typescript
private saveToken(token: string): void {
  localStorage.setItem(this.TOKEN_KEY, token);
}

getToken(): string | null {
  return localStorage.getItem(this.TOKEN_KEY);
}
```

**What this does**:
- Saves JWT token in browser
- Persists even if user refreshes page
- Retrieved automatically on app start

### Dependency Injection in Angular

```typescript
private readonly apiService = inject(ApiService);
private readonly authService = inject(AuthService);
private readonly router = inject(Router);
```

**This is Angular's DI!** (Same concept as Spring)

**Old way** (before inject function):
```typescript
constructor(
  private apiService: ApiService,
  private authService: AuthService,
  private router: Router
) {}
```

**New way** (Angular 14+):
```typescript
private readonly apiService = inject(ApiService);
```

**Benefits**:
- Cleaner code
- Can inject in methods, not just constructor
- Easier to test

---

## Observables & RxJS

### Handling Async Operations

**What is an Observable?**
Think of it as a **stream of data over time**.

**Analogy**:
- **Promise**: Like ordering pizza - you get ONE pizza when it's ready
- **Observable**: Like Netflix - you get MULTIPLE episodes over time

```typescript
login(request: LoginRequest): Observable<AuthResponse> {
  return this.http.post<AuthResponse>(`${this.baseUrl}/login`, request)
    .pipe(
      tap(response => {
        if (response.token) {
          this.saveToken(response.token);
          this.isLoggedIn.set(true);
        }
      })
    );
}
```

### Breaking it down:

**1. HttpClient.post returns Observable**:
```typescript
this.http.post<AuthResponse>(`${this.baseUrl}/login`, request)
```
- Makes HTTP POST to backend
- `<AuthResponse>` = expected response type
- Returns Observable (not the data yet!)

**2. .pipe() - Chain operations**:
```typescript
.pipe(
  tap(response => { ... })
)
```
- Like Java streams: `list.stream().map().filter()`
- Operators transform the data

**3. tap() - Side effects**:
```typescript
tap(response => {
  this.saveToken(response.token);
  this.isLoggedIn.set(true);
})
```
- **tap**: Do something without changing data
- Like peeking into the stream

**Using observables**:
```typescript
// In component:
this.authService.login(request).subscribe({
  next: (response) => {
    console.log('Login successful!', response);
    this.router.navigate(['/home']);
  },
  error: (error) => {
    console.error('Login failed:', error);
  }
});
```

**subscribe()** is like pressing "play" on the stream:
- `next`: Called when data arrives
- `error`: Called if something goes wrong
- `complete`: Called when stream finishes

### Common RxJS Operators:

```typescript
// map - Transform data
this.http.get<User[]>('/users').pipe(
  map(users => users.filter(u => u.active))
)

// filter - Filter data
this.clicks$.pipe(
  filter(click => click.button === 'left')
)

// catchError - Handle errors
this.http.get('/posts').pipe(
  catchError(error => {
    console.error(error);
    return of([]); // Return empty array on error
  })
)

// switchMap - Switch to new observable
this.searchBox$.pipe(
  debounceTime(300),        // Wait 300ms after typing stops
  switchMap(term =>
    this.http.get(`/search?q=${term}`)
  )
)

// tap - Side effects (logging, updating state)
.pipe(
  tap(data => console.log('Data:', data))
)
```

---

## Template Syntax

### New Angular Template Syntax (v17+)

**File**: `frontend/src/app/features/home/home.html`

#### 1. @if - Conditional rendering

```html
@if (loading()) {
  <div>Loading...</div>
} @else if (posts().length === 0) {
  <div>No posts</div>
} @else {
  <div>Show posts</div>
}
```

**Old syntax**:
```html
<div *ngIf="loading()">Loading...</div>
<div *ngIf="!loading() && posts().length === 0">No posts</div>
```

**Much cleaner now!**

#### 2. @for - Looping

```html
@for (post of posts(); track post.id) {
  <article>{{ post.title }}</article>
}
```

**Key part**: `track post.id`
- Tells Angular how to identify each item
- Performance optimization
- Angular only re-renders items that changed

#### 3. Interpolation - Display data

```html
{{ post.author }}
{{ getTimeAgo(post.created_at) }}
```

**Double curly braces** = evaluate TypeScript expression

#### 4. Property Binding - Set HTML properties

```html
<img [src]="getFullAvatarUrl(post.authorAvatar)">
```

**Square brackets** = bind TypeScript expression to property

#### 5. Event Binding - Handle user events

```html
<button (click)="togglePostMenu(post.id, $event)">
```

**Parentheses** = listen for events
- `(click)` = onClick
- `(input)` = onInput
- `(submit)` = onSubmit
- `$event` = event object

#### 6. Two-way Binding

```html
<input [(ngModel)]="username">
```

**Banana in a box** syntax: `[( )]`
- Combines property binding and event binding
- Changes in input update `username`
- Changes to `username` update input

#### 7. Attribute Binding

```html
<button [attr.aria-expanded]="openMenuPostId() === post.id">
```

**Why `attr.`?**
- Some attributes don't have DOM properties
- `aria-*`, `data-*` need `attr.` prefix

#### 8. Class & Style Binding

```html
<div [class.active]="isActive">
<div [style.color]="'red'">
```

---

## HTTP Interceptors

### Auto JWT Injection

**File**: `frontend/src/app/core/auth/auth.interceptor.ts`

```typescript
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const token = authService.getToken();

  // Skip adding token for auth endpoints
  if (req.url.includes('/auth/')) {
    return next(req);
  }

  // Add token to all other requests
  if (token) {
    const authReq = req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    });
    return next(authReq);
  }

  return next(req);
};
```

**What it does**: Intercepts EVERY HTTP request automatically

**Same concept as Spring's JwtAuthenticationFilter!**

**Flow**:
1. Component makes request: `http.get('/posts/all')`
2. **Interceptor catches it** (before sending)
3. Adds Authorization header
4. Sends modified request

**Before/After**:

**Without interceptor** (manual):
```typescript
this.http.get('/posts/all', {
  headers: { Authorization: `Bearer ${token}` }
})
```

**With interceptor** (automatic):
```typescript
this.http.get('/posts/all')  // Token added automatically!
```

---

## Routing & Guards

### Protecting Routes

**File**: `frontend/src/app/core/guard/auth-guard.ts`

```typescript
export const AuthGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const token = authService.getToken();

  if (!token) {
    router.navigate(['/login'], { queryParams: { returnUrl: state.url } });
    return false;
  }

  return authService.waitForAuthInitialization().pipe(
    map(() => {
      if (authService.isLoggedIn()) {
        return true;
      } else {
        router.navigate(['/login'], { queryParams: { returnUrl: state.url } });
        return false;
      }
    })
  );
};
```

**What is a guard?**
- Checks if user can access a route
- Like a bouncer at a club entrance

**How it works**:
1. Check if token exists
2. If no: Redirect to login with return URL
3. Return `false` = block access

**returnUrl parameter**:
```typescript
{ queryParams: { returnUrl: state.url } }
```

**URL becomes**: `/login?returnUrl=/profile`

**After login**, redirect back:
```typescript
const returnUrl = route.snapshot.queryParams['returnUrl'] || '/home';
router.navigate([returnUrl]);
```

**Usage in routes**:

**File**: `frontend/src/app/app.routes.ts`

```typescript
export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  {
    path: 'home',
    component: HomeComponent,
    canActivate: [AuthGuard]  // Protected!
  },
  {
    path: 'admin',
    component: AdminComponent,
    canActivate: [AuthGuard, AdminGuard]  // Multiple guards!
  }
];
```

---

# PART 3: FULL STACK INTEGRATION

## Complete Flow Example: Like a Post

Let me show you how everything connects - from button click to database update:

### Frontend (Angular):

**1. User clicks heart icon** (home.html):
```html
<button (click)="toggleLike(post.id, $event)">
  <svg>...</svg>
  <span>{{ post.likeCount }}</span>
</button>
```

**2. Event handler runs** (home.ts):
```typescript
toggleLike(postId: number, event: Event) {
  event.stopPropagation();

  const post = this.posts().find(p => p.id === postId);
  const isLiked = post.likedByUsernames?.includes(currentUsername);

  if (isLiked) {
    this.apiService.unlikePost(postId).subscribe({
      next: () => this.loadFeed(),
      error: (error) => this.notificationService.error('Failed')
    });
  } else {
    this.apiService.likePost(postId).subscribe({
      next: () => this.loadFeed(),
      error: (error) => this.notificationService.error('Failed')
    });
  }
}
```

**3. ApiService makes HTTP call** (api.service.ts):
```typescript
likePost(postId: number): Observable<string> {
  return this.http.post(`${this.baseUrl}/likes/post/${postId}`, {},
    { responseType: 'text' });
}
```

**4. Interceptor adds JWT**:
```typescript
// authInterceptor automatically adds:
// Authorization: Bearer eyJhbGci...
```

**5. HTTP request sent**:
```
POST http://localhost:8080/likes/post/42
Headers:
  Authorization: Bearer eyJhbGci...
  Content-Type: application/json
Body: {}
```

---

### Backend (Spring Boot):

**6. JwtAuthenticationFilter intercepts**:
```java
// Extract token
String jwt = authHeader.substring(7);
// Validate token
// Load user from database
// Set SecurityContext
```

**7. Request reaches LikeController**:
```java
@PostMapping("/post/{postId}")
public ResponseEntity<String> likePost(
    @PathVariable Long postId,
    Authentication auth
) {
    User user = userService.getUserByUsername(auth.getName());
    likeService.likePost(postId, user.getId());
    return ResponseEntity.ok("Post liked successfully");
}
```

**8. LikeService handles business logic**:
```java
@Transactional
public void likePost(Long postId, Long userId) {
    Post post = postRepository.findByIdWithLikes(postId)
        .orElseThrow(() -> new RuntimeException("Post not found"));
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new RuntimeException("User not found"));

    post.getLikedBy().add(user);
    postRepository.save(post);

    notificationService.createNotification(
        post.getAuthor(),
        user,
        NotificationType.POST_LIKE,
        post
    );
}
```

**9. Database operations**:
```sql
-- Insert like
INSERT INTO post_likes (post_id, user_id) VALUES (42, 5);

-- Insert notification
INSERT INTO notifications (user_id, actor_id, type, message, post_id, created_at)
VALUES (1, 5, 'POST_LIKE', 'John liked your post', 42, NOW());
```

**10. Response sent back**:
```
HTTP/1.1 200 OK
Content-Type: text/plain

Post liked successfully
```

---

### Back to Frontend:

**11. Observable emits response**:
```typescript
.subscribe({
  next: () => this.loadFeed(),
  error: (error) => this.notificationService.error('Failed')
});
```

**12. Feed reloads**:
```typescript
loadFeed() {
  this.loading.set(true);
  this.apiService.getFeed().subscribe({
    next: (posts) => {
      this.posts.set(posts);
      this.loading.set(false);
    }
  });
}
```

**13. Angular detects signal change**:
```typescript
this.posts.set(posts);  // This triggers re-render!
```

**14. Template updates**:
```html
<span>{{ post.likeCount }}</span>
<!-- Was: 5, Now: 6 -->
```

---

### Visual Flow Diagram:

```
User Click
    ↓
Component Method
    ↓
API Service (HTTP)
    ↓
Interceptor (Add JWT)
    ↓
╔═══════════════════════════╗
║      NETWORK (HTTP)       ║
╚═══════════════════════════╝
    ↓
Backend Filter (Validate JWT)
    ↓
Controller (Auth check)
    ↓
Service (Business Logic)
    ↓
Repository (JPA)
    ↓
Database (PostgreSQL)
    ↓
Response
    ↓
╔═══════════════════════════╗
║      NETWORK (HTTP)       ║
╚═══════════════════════════╝
    ↓
Observable emits
    ↓
Reload feed
    ↓
Signal updates
    ↓
UI re-renders
    ↓
User sees updated count!
```

---

## Key Concepts Recap

### Spring Boot:
1. **Annotations**: Metadata that tells Spring what to do
2. **Dependency Injection**: Spring creates and connects objects
3. **JPA Relationships**: @OneToMany, @ManyToOne, @ManyToMany
4. **Repositories**: Auto-generated database queries
5. **Services**: Business logic layer
6. **Controllers**: HTTP endpoint handlers
7. **Security Filter**: Validates JWT on every request
8. **Transactions**: All-or-nothing database operations

### Angular:
1. **Components**: UI building blocks with template + logic
2. **Services**: Shared logic (like Spring services)
3. **Dependency Injection**: inject() function
4. **Signals**: Reactive state (new!)
5. **Observables**: Async data streams
6. **RxJS Operators**: Transform observable data
7. **HTTP Interceptor**: Modify requests automatically
8. **Guards**: Protect routes from unauthorized access
9. **Template Syntax**: @if, @for, {{ }}, [], ()

---

## How to Run the Project

### Backend:
```bash
cd backend

# Set environment variables
export JWT_SECRET_KEY=$(openssl rand -hex 32)
export DB_URL=jdbc:postgresql://localhost:5432/blog
export DB_USERNAME=blog
export DB_PASSWORD=blog

# Run
./mvnw spring-boot:run
```
Backend runs on **http://localhost:8080**

### Frontend:
```bash
cd frontend
npm install
npm start
```
Frontend runs on **http://localhost:4200**

### Database:
```bash
createdb blog
createuser blog -P  # password: blog
```

---

## Next Steps for Learning

1. **Error Handling**: How to handle exceptions properly
2. **Testing**: Unit tests, integration tests
3. **Performance**: Caching, lazy loading, database indexing
4. **Deployment**: How to deploy to production
5. **Adding New Features**: Step-by-step guide to extend the application

---

**Happy Learning!** 🚀
