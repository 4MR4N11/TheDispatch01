# Java Essentials for Spring Boot - The Dispatch Codebase

> **Audience**: You already know Java. This document shows HOW Java features are used in The Dispatch blog application, preparing you for Spring Boot concepts.

---

## Table of Contents
1. [Collections in Action](#1-collections-in-action)
2. [Streams and Lambda Expressions](#2-streams-and-lambda-expressions)
3. [Optional - Avoiding Null](#3-optional---avoiding-null)
4. [Generics in Repositories](#4-generics-in-repositories)
5. [Annotations and Reflection](#5-annotations-and-reflection)
6. [Quick Reference](#6-quick-reference)

---

## 1. Collections in Action

### 1.1 List - One-to-Many Relationships

**In Your Code**: `backend/src/main/java/com/blog/entity/User.java`

```java
@Entity
public class User {
    @OneToMany(mappedBy = "author", cascade = CascadeType.ALL)
    private List<Post> posts = new ArrayList<>();
    //      ↑ Why List? Order matters, duplicates possible, indexed access

    @OneToMany(mappedBy = "user")
    private List<Comment> comments = new ArrayList<>();

    @OneToMany(mappedBy = "user")
    private List<Notification> notifications = new ArrayList<>();
}
```

**WHY List?**
- Posts have chronological order (newest to oldest)
- User can theoretically comment multiple times (List allows duplicates)
- Hibernate fetches collections as `PersistentBag` (backed by List)
- Indexed access: `user.getPosts().get(0)` gets latest post

**Alternative - Set**:
```java
@ManyToMany
private Set<User> followers = new HashSet<>();
//      ↑ Set prevents duplicate followers (User can't follow twice)
```

### 1.2 Set - Preventing Duplicates

**In Your Code**: `backend/src/main/java/com/blog/entity/Subscription.java`

```java
@Entity
public class User {
    @ManyToMany
    @JoinTable(name = "subscriptions",
        joinColumns = @JoinColumn(name = "follower_id"),
        inverseJoinColumns = @JoinColumn(name = "following_id"))
    private Set<User> following = new HashSet<>();
    //      ↑ Set ensures you can't follow the same user twice
}
```

**WHY Set?**
- Mathematical set: no duplicates allowed
- Order doesn't matter (following list can be sorted later)
- `contains()` is O(1) with HashSet: `user.getFollowing().contains(otherUser)`
- Prevents database constraint violations

---

## 2. Streams and Lambda Expressions

### 2.1 Filtering and Mapping

**Example from User Service** (typical usage):

```java
@Service
public class UserServiceImpl {
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
            //  ↑ Convert List<User> to Stream<User>

            .filter(user -> user.getRole() != Role.BANNED)
            //      ↑ Lambda: user -> boolean (keep if true)

            .map(user -> new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail()
            ))
            //  ↑ Lambda: User -> UserResponse (transform)

            .collect(Collectors.toList());
            //       ↑ Convert Stream back to List
    }
}
```

**Breaking Down Lambda**:
```java
// Traditional way (verbose):
.filter(new Predicate<User>() {
    @Override
    public boolean test(User user) {
        return user.getRole() != Role.BANNED;
    }
})

// Lambda way (concise):
.filter(user -> user.getRole() != Role.BANNED)
//      ↑      ↑
//   parameter  expression
```

### 2.2 Method References

**In Your DTOs**:
```java
public class PostResponse {
    private List<String> tags;

    public static PostResponse fromEntity(Post post) {
        response.setTags(
            post.getTags().stream()
                .map(Tag::getName)  // Method reference
                //    ↑ Equivalent to: tag -> tag.getName()
                .collect(Collectors.toList())
        );
    }
}
```

**Method Reference Types**:
```java
// 1. Instance method
.map(Tag::getName)              // tag -> tag.getName()

// 2. Static method
.map(UserResponse::fromEntity)  // user -> UserResponse.fromEntity(user)

// 3. Constructor
.map(ArrayList::new)            // () -> new ArrayList()
```

### 2.3 Sorting Streams

**Sorting Posts by Date**:
```java
@Service
public class PostServiceImpl {
    public List<PostResponse> getRecentPosts() {
        return postRepository.findAll().stream()
            .sorted(Comparator.comparing(Post::getCreatedAt).reversed())
            //      ↑ Extract createdAt, compare, reverse for newest first
            .limit(10)  // Take only 10
            .map(PostResponse::fromEntity)
            .collect(Collectors.toList());
    }
}
```

**Comparator Chains**:
```java
// Sort by likes (descending), then by date (descending)
.sorted(
    Comparator.comparing(Post::getLikesCount).reversed()
        .thenComparing(Post::getCreatedAt).reversed()
)
```

### 2.4 Grouping and Collecting

**Grouping Posts by User**:
```java
Map<User, List<Post>> postsByUser = posts.stream()
    .collect(Collectors.groupingBy(Post::getAuthor));
    //       ↑ Group posts by their author

// Result: {user1 -> [post1, post2], user2 -> [post3], ...}
```

**Counting**:
```java
long activeUsers = users.stream()
    .filter(user -> user.getLastLogin().isAfter(oneMonthAgo))
    .count();
```

---

## 3. Optional - Avoiding Null

### 3.1 Repository Methods Return Optional

**In Your Repositories**:
```java
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    //  ↑ Might not find a user - returns Optional instead of null

    Optional<User> findByEmail(String email);
}
```

**WHY Optional?**
- **Explicit contract**: "This might not exist"
- **Forces handling**: Can't forget to check for null
- **Functional style**: Chain operations safely

### 3.2 Handling Optional - The Right Way

**In Your Service**:
```java
@Service
public class UserServiceImpl {
    public UserResponse getUserByUsername(String username) {
        return userRepository.findByUsername(username)
            //  ↑ Returns Optional<User>

            .map(UserResponse::fromEntity)
            //  ↑ If present: convert to UserResponse
            //    If empty: stay empty

            .orElseThrow(() -> new UserNotFoundException(username));
            //           ↑ If empty: throw exception
            //             If present: return the UserResponse
    }
}
```

**Optional Methods**:
```java
Optional<User> userOpt = userRepository.findByUsername("john");

// 1. orElse - provide default value
User user = userOpt.orElse(new User("guest"));

// 2. orElseGet - provide default via lambda (lazy)
User user = userOpt.orElseGet(() -> createGuestUser());

// 3. orElseThrow - throw exception if empty
User user = userOpt.orElseThrow(() -> new UserNotFoundException());

// 4. ifPresent - execute if exists
userOpt.ifPresent(user -> sendWelcomeEmail(user));

// 5. isPresent - check existence (avoid this pattern)
if (userOpt.isPresent()) {  // ❌ NOT RECOMMENDED
    User user = userOpt.get();
}
```

### 3.3 Chaining Optional Operations

**Complex Example**:
```java
public PostResponse getLatestPostByUsername(String username) {
    return userRepository.findByUsername(username)
        //  ↑ Optional<User>

        .flatMap(user ->
            user.getPosts().stream()
                .max(Comparator.comparing(Post::getCreatedAt))
        )
        //  ↑ Optional<Post> (flatMap flattens Optional<Optional<Post>>)

        .map(PostResponse::fromEntity)
        //  ↑ Optional<PostResponse>

        .orElseThrow(() -> new PostNotFoundException());
}
```

**flatMap vs map**:
- `map`: Transform value inside Optional (returns `Optional<Optional<T>>` if transformation returns Optional)
- `flatMap`: Transform and flatten (returns `Optional<T>`)

---

## 4. Generics in Repositories

### 4.1 Understanding JpaRepository<T, ID>

**Your Repository**:
```java
public interface UserRepository extends JpaRepository<User, Long> {
    //                                                    ↑     ↑
    //                                            Entity Type  ID Type
}
```

**What Does This Mean?**
```java
public interface JpaRepository<T, ID> {
    //                         ↑  ↑
    //                     T = User
    //                         ID = Long

    // Spring generates implementations:
    T save(T entity);              // User save(User entity)
    Optional<T> findById(ID id);   // Optional<User> findById(Long id)
    List<T> findAll();             // List<User> findAll()
    void deleteById(ID id);        // void deleteById(Long id)
}
```

**WHY Generics?**
- **Type safety**: Can't accidentally pass `Post` to `UserRepository.save()`
- **Reusability**: Same `JpaRepository` interface for all entities
- **Compile-time checks**: Errors caught before runtime

### 4.2 Generic Methods in Service Layer

**Generic Response Builder**:
```java
public class ResponseBuilder<T> {
    //                        ↑ Generic type parameter

    private T data;
    private String message;

    public static <T> ResponseBuilder<T> success(T data) {
        //        ↑ Generic method (independent of class generic)
        ResponseBuilder<T> builder = new ResponseBuilder<>();
        builder.data = data;
        return builder;
    }
}

// Usage:
ResponseBuilder<UserResponse> response = ResponseBuilder.success(userResponse);
ResponseBuilder<List<PostResponse>> response = ResponseBuilder.success(posts);
```

---

## 5. Annotations and Reflection

### 5.1 How Spring Uses Annotations

**Your Entity**:
```java
@Entity  // ← Annotation
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;
}
```

**Behind the Scenes** (Spring does this):
```java
// 1. Spring scans classpath for @Entity classes (at startup)
for (Class<?> clazz : allClasses) {
    if (clazz.isAnnotationPresent(Entity.class)) {
        // Found User class!
        Entity entityAnnotation = clazz.getAnnotation(Entity.class);

        // 2. Read @Table annotation
        Table tableAnnotation = clazz.getAnnotation(Table.class);
        String tableName = tableAnnotation.name(); // "users"

        // 3. Create database table
        createTable(tableName);

        // 4. Register entity with Hibernate
        registerEntity(clazz);
    }
}
```

### 5.2 Creating Custom Annotations

**Example - Rate Limiting Annotation** (how you could extend your app):
```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RateLimit {
    int requests() default 100;
    int perMinutes() default 1;
}

// Usage in controller:
@PostMapping("/posts")
@RateLimit(requests = 10, perMinutes = 1)
public ResponseEntity<PostResponse> createPost(@RequestBody PostRequest request) {
    // Spring AOP can intercept this and check rate limits
}
```

### 5.3 Reflection in Action

**How @Autowired Works** (simplified):
```java
// Your controller:
@RestController
public class UserController {
    @Autowired
    private UserService userService;
}

// Spring does (at startup):
Class<?> controllerClass = UserController.class;

for (Field field : controllerClass.getDeclaredFields()) {
    if (field.isAnnotationPresent(Autowired.class)) {
        // Found: private UserService userService

        Class<?> fieldType = field.getType(); // UserService.class

        Object bean = applicationContext.getBean(fieldType);
        // ↑ Get UserServiceImpl instance from Spring container

        field.setAccessible(true);
        field.set(controllerInstance, bean);
        // ↑ Inject: userService = userServiceImpl
    }
}
```

**Key Reflection APIs**:
- `Class<?> clazz = User.class` - Get class metadata
- `clazz.getDeclaredFields()` - Get all fields (including private)
- `field.get(object)` - Read field value
- `field.set(object, value)` - Write field value
- `clazz.isAnnotationPresent(Entity.class)` - Check annotation
- `method.invoke(object, args)` - Call method dynamically

---

## 6. Quick Reference

### 6.1 Collection Choice Matrix

| Collection | Use When | Example in Your Code |
|------------|----------|----------------------|
| `List<T>` | Order matters, duplicates allowed | `List<Post> posts` (chronological) |
| `Set<T>` | No duplicates, order doesn't matter | `Set<User> followers` |
| `Map<K,V>` | Key-value pairs, fast lookup | `Map<String, User> userCache` |
| `Queue<T>` | FIFO processing | `Queue<Notification> pending` |

### 6.2 Stream Operations

```java
// Intermediate (return Stream - can chain):
.filter(predicate)          // Keep elements matching condition
.map(function)              // Transform elements
.flatMap(function)          // Transform and flatten
.distinct()                 // Remove duplicates
.sorted()                   // Sort elements
.limit(n)                   // Take first n elements
.skip(n)                    // Skip first n elements

// Terminal (return result - end chain):
.collect(Collectors.toList())  // To List
.collect(Collectors.toSet())   // To Set
.collect(Collectors.groupingBy(fn))  // To Map (grouped)
.forEach(consumer)          // Execute for each
.count()                    // Count elements
.findFirst()                // Get first (returns Optional)
.anyMatch(predicate)        // Check if any match
.allMatch(predicate)        // Check if all match
```

### 6.3 Optional Patterns

```java
// ✅ GOOD:
return userRepository.findByUsername(username)
    .map(UserResponse::fromEntity)
    .orElseThrow(() -> new UserNotFoundException());

// ❌ BAD (defeats purpose of Optional):
Optional<User> userOpt = userRepository.findByUsername(username);
if (userOpt.isPresent()) {
    return userOpt.get();
} else {
    throw new UserNotFoundException();
}

// ✅ GOOD (chaining):
userRepository.findByUsername(username)
    .ifPresent(user -> sendWelcomeEmail(user));

// ❌ BAD (unnecessary Optional creation):
return Optional.of(user);  // Don't wrap non-nullable values
```

### 6.4 Lambda Syntax Quick Guide

```java
// No parameters:
() -> System.out.println("Hello")
() -> { return "Hello"; }

// One parameter (parentheses optional):
user -> user.getUsername()
(user) -> user.getUsername()
user -> { return user.getUsername(); }

// Multiple parameters:
(a, b) -> a + b
(user, post) -> user.getPosts().contains(post)

// Type inference vs explicit:
user -> user.getId()                    // Inferred
(User user) -> user.getId()             // Explicit

// Method reference:
User::getUsername                       // Instead of: user -> user.getUsername()
UserResponse::fromEntity               // Instead of: user -> UserResponse.fromEntity(user)
```

---

## What's Next?

You now understand the Java foundations used in The Dispatch codebase. Next, you'll learn:

**→ [02-SPRING-CORE-IOC-DI.md](./02-SPRING-CORE-IOC-DI.md)** - How Spring manages your objects with IoC and Dependency Injection

**Key Questions for Next Section:**
- Why don't we write `new UserService()` in controllers?
- How does `@Autowired` magically inject dependencies?
- What happens when `SpringApplication.run()` executes?
- What is the ApplicationContext and why does it matter?

**Completed**: Java Essentials ✅
**Next**: Spring Core Concepts 🎯
