# Spring Core: IoC and Dependency Injection

> **Start Here**: This document explains the CORE principle that makes Spring Boot work - how it manages objects for you. Everything in Spring Boot builds on this foundation.

---

## Table of Contents
1. [The Problem: Manual Object Creation](#1-the-problem-manual-object-creation)
2. [The Solution: Inversion of Control (IoC)](#2-the-solution-inversion-of-control-ioc)
3. [How Dependency Injection Works](#3-how-dependency-injection-works)
4. [Understanding Spring Annotations](#4-understanding-spring-annotations)
5. [The ApplicationContext - Spring's Container](#5-the-applicationcontext---springs-container)
6. [Bean Lifecycle](#6-bean-lifecycle)
7. [Real Examples from Your Code](#7-real-examples-from-your-code)
8. [Common Patterns and Best Practices](#8-common-patterns-and-best-practices)

---

## 1. The Problem: Manual Object Creation

### 1.1 Without Spring - The Hard Way

Imagine writing your blog application WITHOUT Spring Boot:

```java
public class BlogApplication {
    public static void main(String[] args) {
        // 1. Create database connection
        DataSource dataSource = new PostgreSQLDataSource();
        dataSource.setUrl("jdbc:postgresql://localhost:5432/blog");

        // 2. Create EntityManager for database access
        EntityManager entityManager = createEntityManager(dataSource);

        // 3. Create repositories
        UserRepository userRepository = new UserRepositoryImpl(entityManager);
        PostRepository postRepository = new PostRepositoryImpl(entityManager);
        CommentRepository commentRepository = new CommentRepositoryImpl(entityManager);

        // 4. Create password encoder
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

        // 5. Create services
        PostService postService = new PostServiceImpl(postRepository, commentRepository);
        UserService userService = new UserServiceImpl(
            userRepository,
            passwordEncoder,
            postService,  // UserService needs PostService!
            // ... 5 more dependencies
        );

        // 6. Create controllers
        UserController userController = new UserController(userService, postService);
        PostController postController = new PostController(postService, userService);

        // 7. Start web server
        TomcatServer server = new TomcatServer();
        server.addController(userController);
        server.addController(postController);
        server.start(8080);
    }
}
```

**Problems:**
1. **Order matters**: Must create `PostService` before `UserService` (dependency graph)
2. **Circular dependencies**: What if `UserService` needs `PostService` AND `PostService` needs `UserService`?
3. **Configuration hell**: Dozens of objects to create manually
4. **Testing nightmare**: Can't easily replace real `UserRepository` with mock
5. **Tight coupling**: `UserController` knows HOW to create `UserService`

### 1.2 Your Actual Code - The Spring Way

**`BlogApplication.java:6-11`**
```java
@SpringBootApplication
public class BlogApplication {
    public static void main(String[] args) {
        SpringApplication.run(BlogApplication.class, args);
        // ↑ That's it. Spring does EVERYTHING.
    }
}
```

**WHY IS THIS MAGIC?**

That single line does:
1. Scans your entire codebase for `@Component`, `@Service`, `@Repository`, `@Controller`
2. Creates ALL objects (beans) automatically
3. Figures out dependencies and injects them
4. Starts the web server
5. Configures database connections
6. Sets up security

**This is Inversion of Control** - YOU don't create objects, SPRING does.

---

## 2. The Solution: Inversion of Control (IoC)

### 2.1 What is IoC?

**Definition**: Inversion of Control means THE FRAMEWORK controls object creation, not your code.

**Traditional Way** (You control):
```java
public class UserController {
    private UserService userService = new UserServiceImpl();
    //                                 ↑ YOU create it
}
```

**IoC Way** (Spring controls):
```java
@RestController
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
        //               ↑ SPRING creates it and gives it to you
    }
}
```

### 2.2 WHY IoC Exists

**Problem**: Your `UserController` needs a `UserService`. Your `UserService` needs:
- `UserRepository`
- `PasswordEncoder`
- `AuthenticationManager`
- `PostService`
- `ReportRepository`
- `NotificationRepository`
- `CommentRepository`
- `SubscriptionRepository`
- `PostRepository`

That's **9 dependencies** just for `UserService`! And each of those has their own dependencies.

**Without IoC**: You create all 9 objects manually, in the right order.
**With IoC**: Spring creates everything automatically.

### 2.3 The ApplicationContext - Spring's Container

When you run `SpringApplication.run()`, Spring creates an **ApplicationContext**:

```
┌─────────────────────────────────────────────────────────┐
│          ApplicationContext (Spring Container)          │
├─────────────────────────────────────────────────────────┤
│  Bean: userRepository (UserRepository)                  │
│  Bean: postRepository (PostRepository)                  │
│  Bean: passwordEncoder (PasswordEncoder)                │
│  Bean: postService (PostServiceImpl)                    │
│  Bean: userService (UserServiceImpl)                    │
│  Bean: userController (UserController)                  │
│  Bean: postController (PostController)                  │
│  Bean: ... (100+ beans for your application)            │
└─────────────────────────────────────────────────────────┘
```

**Bean** = An object managed by Spring.

---

## 3. How Dependency Injection Works

### 3.1 Your UserController - Real Example

**`UserController.java:26-35`**
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

**What Happens Behind the Scenes:**

```java
// Spring does this at startup (simplified):

// 1. Scan for @RestController
Class<?> controllerClass = UserController.class;

// 2. Find constructor
Constructor<?> constructor = controllerClass.getConstructors()[0];
// Found: UserController(UserService, PostService)

// 3. Get parameter types
Class<?>[] paramTypes = constructor.getParameterTypes();
// [UserService.class, PostService.class]

// 4. Get beans from ApplicationContext
Object userServiceBean = applicationContext.getBean(UserService.class);
// ↑ Returns the UserServiceImpl instance

Object postServiceBean = applicationContext.getBean(PostService.class);
// ↑ Returns the PostServiceImpl instance

// 5. Create controller instance
Object controller = constructor.newInstance(userServiceBean, postServiceBean);
// ↑ UserController is now created with its dependencies!

// 6. Register controller
applicationContext.registerBean("userController", controller);
```

**Key Point**: Spring looks at your constructor, sees what you need (`UserService` and `PostService`), finds those beans in the ApplicationContext, and injects them.

### 3.2 Types of Dependency Injection

#### Constructor Injection (Recommended - Your Code Uses This)

**`UserController.java:32-35`**
```java
public UserController(UserService userService, PostService postService) {
    this.userService = userService;
    this.postService = postService;
}
```

**Advantages:**
- Dependencies are **immutable** (`final` keyword)
- Easier to test (just call constructor with mocks)
- Required dependencies are explicit
- No need for `@Autowired` annotation (Spring 4.3+)

#### Field Injection (NOT Recommended)

```java
@RestController
public class UserController {
    @Autowired  // ❌ Avoid this
    private UserService userService;
}
```

**Why avoid?**
- Can't make field `final` (mutable state)
- Harder to test (need reflection to inject mocks)
- Hides dependencies (unclear what's required)
- Can cause circular dependency issues

#### Setter Injection (Rarely Used)

```java
@RestController
public class UserController {
    private UserService userService;

    @Autowired
    public void setUserService(UserService userService) {
        this.userService = userService;
    }
}
```

**When to use?**
- Optional dependencies
- Circular dependencies (last resort)

### 3.3 Your UserServiceImpl - Complex Dependencies

**`UserServiceImpl.java:39-59`**
```java
public UserServiceImpl(
    UserRepository userRepository,
    AuthenticationManager authenticationManager,
    PasswordEncoder passwordEncoder,
    PostService postService,
    ReportRepository reportRepository,
    NotificationRepository notificationRepository,
    CommentRepository commentRepository,
    SubscriptionRepository subscriptionRepository,
    PostRepository postRepository
) {
    this.authenticationManager = authenticationManager;
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.postService = postService;
    this.reportRepository = reportRepository;
    this.notificationRepository = notificationRepository;
    this.commentRepository = commentRepository;
    this.subscriptionRepository = subscriptionRepository;
    this.postRepository = postRepository;
}
```

**Spring's Injection Process:**

```
1. Create UserRepository (depends on EntityManager)
   └─> Create EntityManager (depends on DataSource)
       └─> Create DataSource (from application.properties)

2. Create PasswordEncoder (no dependencies)

3. Create AuthenticationManager (depends on UserDetailsService)
   └─> Create UserDetailsService

4. Create all other repositories...

5. Create PostService (depends on PostRepository, CommentRepository)

6. Finally, create UserServiceImpl with all 9 dependencies

7. Create UserController (depends on UserService, PostService)
```

**Key Point**: Spring figures out the dependency graph automatically and creates objects in the correct order.

---

## 4. Understanding Spring Annotations

### 4.1 Stereotype Annotations

These annotations tell Spring: "This is a bean - manage it for me."

#### @Component - Generic Bean

```java
@Component
public class ImageProcessor {
    // Spring creates and manages this bean
}
```

#### @Service - Business Logic Layer

**`UserServiceImpl.java:26`**
```java
@Service
public class UserServiceImpl implements UserService {
    // Marks this as a service layer bean
}
```

**WHY @Service?**
- Semantic meaning: "This contains business logic"
- Spring can apply transaction management
- Clear separation of concerns
- Same as `@Component` technically, but conveys intent

#### @Repository - Data Access Layer

**`UserRepository.java:12`**
```java
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    // Marks this as a data access bean
}
```

**WHY @Repository?**
- Enables Spring Data JPA auto-implementation
- Translates database exceptions to Spring's DataAccessException
- Clear indication this talks to the database

#### @RestController - Web Layer

**`UserController.java:26`**
```java
@RestController
@RequestMapping("/users")
public class UserController {
    // Marks this as a REST API controller
}
```

**WHY @RestController?**
- Combination of `@Controller` + `@ResponseBody`
- All methods return JSON by default
- Spring registers HTTP endpoints

### 4.2 The Bean Creation Hierarchy

```
@Component (Generic)
    │
    ├─> @Service (Business Logic)
    ├─> @Repository (Data Access)
    └─> @Controller (Web Layer)
         └─> @RestController (REST APIs)
```

All are `@Component` under the hood, but with semantic meaning.

### 4.3 @SpringBootApplication - The Magic Annotation

**`BlogApplication.java:6`**
```java
@SpringBootApplication
public class BlogApplication {
```

This ONE annotation is actually THREE annotations:

```java
@SpringBootApplication =
    @Configuration +        // "This class configures beans"
    @EnableAutoConfiguration +  // "Auto-configure based on classpath"
    @ComponentScan          // "Scan for @Component, @Service, etc."
```

**@ComponentScan** scans your package (`_blog.blog`) and all sub-packages:
```
_blog.blog/
  ├─ BlogApplication.java (@SpringBootApplication) ← STARTS HERE
  ├─ controller/
  │   └─ UserController.java (@RestController) ✓ Found
  ├─ service/
  │   └─ UserServiceImpl.java (@Service) ✓ Found
  └─ repository/
      └─ UserRepository.java (@Repository) ✓ Found
```

---

## 5. The ApplicationContext - Spring's Container

### 5.1 What is ApplicationContext?

The **ApplicationContext** is Spring's container that:
1. Holds all beans (objects)
2. Manages bean lifecycle (creation, initialization, destruction)
3. Resolves dependencies
4. Provides beans when requested

### 5.2 How to Access ApplicationContext

**Directly (rarely needed):**
```java
@RestController
public class DebugController {
    private final ApplicationContext context;

    public DebugController(ApplicationContext context) {
        this.context = context;
    }

    @GetMapping("/debug/beans")
    public List<String> getAllBeans() {
        return Arrays.asList(context.getBeanDefinitionNames());
        // Returns: ["userController", "userService", "postService", ...]
    }
}
```

**Typical usage (you don't access it directly):**
```java
@RestController
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        // Spring uses ApplicationContext internally to inject userService
        this.userService = userService;
    }
}
```

### 5.3 When ApplicationContext is Created

```java
public static void main(String[] args) {
    SpringApplication.run(BlogApplication.class, args);
    // ↑ This creates the ApplicationContext
}
```

**Startup Sequence:**

```
1. SpringApplication.run() called

2. Load Configuration
   - Read application.properties
   - Database URL, port, etc.

3. Create ApplicationContext

4. Component Scanning
   - Scan _blog.blog package
   - Find all @Component, @Service, @Repository, @Controller

5. Bean Registration
   - Register bean definitions
   - Calculate dependency graph

6. Bean Creation (in order)
   - DataSource
   - EntityManager
   - Repositories
   - Services
   - Controllers

7. Dependency Injection
   - Inject dependencies into each bean

8. Post-Processing
   - @PostConstruct methods
   - AOP proxies
   - Transaction management

9. Start Web Server (Tomcat)
   - Listen on port 8080

10. Application Ready!
```

### 5.4 Bean Scopes

By default, beans are **singletons** (one instance per ApplicationContext).

**Your UserService:**
```java
@Service
public class UserServiceImpl implements UserService {
    // Spring creates ONE instance of UserServiceImpl
    // All controllers share the SAME instance
}
```

**Demonstration:**
```java
@RestController
public class UserController {
    public UserController(UserService userService) {
        System.out.println(userService); // UserServiceImpl@1a2b3c
    }
}

@RestController
public class PostController {
    public PostController(UserService userService) {
        System.out.println(userService); // UserServiceImpl@1a2b3c (SAME)
    }
}
```

**Other Scopes (rarely used):**
```java
@Service
@Scope("prototype")  // New instance every time
public class UserService { }

@Service
@Scope("request")  // New instance per HTTP request
public class RequestScopedService { }
```

---

## 6. Bean Lifecycle

### 6.1 Bean Creation Lifecycle

```
1. Constructor Called
   ↓
2. Dependencies Injected (via constructor/setter)
   ↓
3. @PostConstruct Method Called (initialization)
   ↓
4. Bean Ready (in use)
   ↓
5. @PreDestroy Method Called (cleanup)
   ↓
6. Bean Destroyed
```

### 6.2 Using @PostConstruct and @PreDestroy

**Example - Cache Initialization:**
```java
@Service
public class UserService {
    private final UserRepository userRepository;
    private Map<String, User> userCache;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
        System.out.println("1. Constructor called");
    }

    @PostConstruct
    public void init() {
        System.out.println("2. @PostConstruct - loading cache");
        userCache = new HashMap<>();
        // Preload frequently accessed users
        userRepository.findAll().forEach(user ->
            userCache.put(user.getUsername(), user)
        );
    }

    @PreDestroy
    public void cleanup() {
        System.out.println("3. @PreDestroy - clearing cache");
        userCache.clear();
    }

    public User getUserByUsername(String username) {
        System.out.println("4. Bean in use - serving requests");
        return userCache.get(username);
    }
}
```

**Output:**
```
1. Constructor called
2. @PostConstruct - loading cache
4. Bean in use - serving requests
4. Bean in use - serving requests
...
3. @PreDestroy - clearing cache (when application shuts down)
```

### 6.3 When to Use Lifecycle Hooks

**@PostConstruct - Use for:**
- Initializing caches
- Connecting to external services
- Loading configuration
- Validating dependencies

**@PreDestroy - Use for:**
- Closing database connections (usually not needed, Spring handles this)
- Flushing caches to disk
- Releasing resources (file handles, threads)

---

## 7. Real Examples from Your Code

### 7.1 UserRepository - Spring Data Magic

**`UserRepository.java:12-23`**
```java
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);

    @Query("""
        SELECT u FROM User u
        LEFT JOIN FETCH u.subscriptions s
        LEFT JOIN FETCH u.Posts p
        WHERE u.username = :username
    """)
    Optional<User> findByUsernameWithSubscriptionsAndPosts(@Param("username") String username);
}
```

**Question**: Where's the implementation?

**Answer**: Spring Data JPA generates it automatically!

**Behind the Scenes:**
```java
// Spring creates this at runtime (simplified):
public class UserRepositoryImpl implements UserRepository {
    private EntityManager em;

    public Optional<User> findByEmail(String email) {
        TypedQuery<User> query = em.createQuery(
            "SELECT u FROM User u WHERE u.email = :email",
            User.class
        );
        query.setParameter("email", email);
        return query.getResultList().stream().findFirst();
    }

    public Optional<User> findByUsername(String username) {
        // Similar generated code...
    }

    // Spring sees your @Query annotation and uses it directly
    public Optional<User> findByUsernameWithSubscriptionsAndPosts(String username) {
        return em.createQuery(yourCustomQuery, User.class)
            .setParameter("username", username)
            .getResultList().stream().findFirst();
    }
}
```

**Key Point**: Spring generates the implementation bean at runtime. You just define the interface.

### 7.2 UserServiceImpl - Business Logic Bean

**`UserServiceImpl.java:26-59`**
```java
@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    // ... 6 more dependencies

    public UserServiceImpl(
        UserRepository userRepository,
        AuthenticationManager authenticationManager,
        PasswordEncoder passwordEncoder,
        PostService postService,
        ReportRepository reportRepository,
        NotificationRepository notificationRepository,
        CommentRepository commentRepository,
        SubscriptionRepository subscriptionRepository,
        PostRepository postRepository
    ) {
        // Spring injects all 9 dependencies
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.postService = postService;
        this.reportRepository = reportRepository;
        this.notificationRepository = notificationRepository;
        this.commentRepository = commentRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.postRepository = postRepository;
    }
}
```

**Dependency Graph:**
```
UserServiceImpl
  ├─> UserRepository (interface)
  │     └─> UserRepositoryImpl (generated by Spring Data JPA)
  │           └─> EntityManager
  │                 └─> DataSource
  ├─> PasswordEncoder
  │     └─> BCryptPasswordEncoder (configured in SecurityConfig)
  ├─> AuthenticationManager
  │     └─> ... (Spring Security provides this)
  ├─> PostService
  │     └─> PostServiceImpl
  │           ├─> PostRepository
  │           └─> CommentRepository
  └─> 5 more repositories...
```

**Key Point**: You don't create any of these - Spring does. You just declare dependencies in constructor.

### 7.3 UserController - Web Layer Bean

**`UserController.java:26-35`**
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

    @GetMapping
    public List<UserResponse> getUsers(Authentication auth) {
        // Spring resolves UserService interface to UserServiceImpl bean
        List<User> users = userService.getUsers();
        // ...
    }
}
```

**What Spring Does:**
1. **Bean Creation**: Creates `UserController` bean with injected dependencies
2. **Endpoint Registration**: Maps `GET /users` to `getUsers()` method
3. **Request Handling**:
   ```java
   // Incoming request: GET /users

   // Spring does:
   UserController controller = applicationContext.getBean(UserController.class);
   Authentication auth = securityContext.getAuthentication();
   List<UserResponse> response = controller.getUsers(auth);

   // Spring converts to JSON and returns
   ```

---

## 8. Common Patterns and Best Practices

### 8.1 Interface vs Implementation

**Your Code Pattern (Recommended):**
```java
// Interface
public interface UserService {
    User register(RegisterRequest request);
    User authenticate(LoginRequest request);
    // ...
}

// Implementation
@Service
public class UserServiceImpl implements UserService {
    // Implementation details
}

// Controller
@RestController
public class UserController {
    private final UserService userService;
    //            ↑ Depend on interface, not implementation

    public UserController(UserService userService) {
        // Spring injects UserServiceImpl automatically
        this.userService = userService;
    }
}
```

**WHY Interface?**
- **Decoupling**: Controller doesn't know about `UserServiceImpl`
- **Testing**: Easy to create mock implementations
- **Swappable**: Can change implementation without touching controller

**Example - Testing:**
```java
public class UserControllerTest {
    @Test
    void testGetUser() {
        // Create mock implementation
        UserService mockService = Mockito.mock(UserService.class);
        when(mockService.getUser(1L)).thenReturn(new User("test"));

        // Pass mock to controller
        UserController controller = new UserController(mockService, mockPostService);

        // Test controller logic
        UserResponse response = controller.getUser(1L, null);
        assertEquals("test", response.getUsername());
    }
}
```

### 8.2 @Autowired is Optional (Constructor Injection)

**Old Way (Spring 3.x):**
```java
@RestController
public class UserController {
    private final UserService userService;

    @Autowired  // Required in old Spring versions
    public UserController(UserService userService) {
        this.userService = userService;
    }
}
```

**Modern Way (Spring 4.3+) - Your Code:**
```java
@RestController
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        // @Autowired is implicit for single constructor
        this.userService = userService;
    }
}
```

**When @Autowired is Required:**
- Field injection (not recommended)
- Multiple constructors
- Setter injection

### 8.3 Constructor vs Field Injection

**Your Code - Constructor Injection (Recommended):**
```java
@Service
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }
}
```

**Benefits:**
- `final` keyword → immutable, thread-safe
- Clear dependencies at construction time
- Easy to test (just call constructor)
- Compile-time safety

**Alternative - Field Injection (Avoid):**
```java
@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserRepository userRepository;  // Can't be final

    @Autowired
    private PasswordEncoder passwordEncoder;
}
```

**Problems:**
- No `final` → mutable state
- Hidden dependencies (unclear what's required)
- Harder to test (need reflection)
- Can't detect circular dependencies at compile time

### 8.4 Handling Circular Dependencies

**Problem:**
```java
@Service
public class UserService {
    private final PostService postService;

    public UserService(PostService postService) {
        this.postService = postService;
    }
}

@Service
public class PostService {
    private final UserService userService;

    public PostService(UserService userService) {
        this.userService = userService;
    }
}
```

**Error:**
```
The dependencies of some of the beans in the application context form a cycle:
   userService -> postService -> userService
```

**Solution 1: Refactor (Best)**
Extract shared logic to a new service:
```java
@Service
public class UserService {
    private final CommonService commonService;
}

@Service
public class PostService {
    private final CommonService commonService;
}

@Service
public class CommonService {
    // Shared logic
}
```

**Solution 2: Setter Injection (Last Resort)**
```java
@Service
public class UserService {
    private PostService postService;

    @Autowired
    public void setPostService(PostService postService) {
        this.postService = postService;
    }
}
```

**Solution 3: @Lazy (Not Recommended)**
```java
@Service
public class UserService {
    private final PostService postService;

    public UserService(@Lazy PostService postService) {
        this.postService = postService;
    }
}
```

### 8.5 Conditional Bean Creation

**Example - Different Password Encoders for Dev vs Prod:**
```java
@Configuration
public class SecurityConfig {

    @Bean
    @Profile("dev")
    public PasswordEncoder devPasswordEncoder() {
        return NoOpPasswordEncoder.getInstance(); // No encryption in dev
    }

    @Bean
    @Profile("prod")
    public PasswordEncoder prodPasswordEncoder() {
        return new BCryptPasswordEncoder(12); // Strong encryption in prod
    }
}
```

**Usage in application.properties:**
```properties
# Dev environment
spring.profiles.active=dev

# Production environment
spring.profiles.active=prod
```

---

## Key Takeaways

### What You Learned

1. **Inversion of Control (IoC)**
   - Spring creates and manages objects (beans) for you
   - You declare dependencies, Spring resolves them
   - ApplicationContext is the container holding all beans

2. **Dependency Injection (DI)**
   - Constructor injection (recommended - your code uses this)
   - Spring injects dependencies automatically
   - Use interfaces for flexibility

3. **Annotations**
   - `@Component` - generic bean
   - `@Service` - business logic
   - `@Repository` - data access
   - `@RestController` - REST API endpoints
   - `@SpringBootApplication` - enables everything

4. **Bean Lifecycle**
   - Constructor → Dependencies Injected → @PostConstruct → Ready → @PreDestroy
   - Beans are singletons by default (one instance per application)

5. **Best Practices**
   - Use constructor injection (your code does this ✓)
   - Depend on interfaces, not implementations
   - Use `final` for dependencies
   - Avoid field injection with `@Autowired`

### The Big Picture

```
┌──────────────────────────────────────────────────────────────┐
│                    YOUR CODE                                 │
│                                                              │
│  @SpringBootApplication                                      │
│  public class BlogApplication {                              │
│      public static void main(String[] args) {                │
│          SpringApplication.run(BlogApplication.class, args); │
│          ↓                                                   │
└──────────┼───────────────────────────────────────────────────┘
           │
           ↓
┌──────────────────────────────────────────────────────────────┐
│              SPRING BOOT DOES THIS                           │
├──────────────────────────────────────────────────────────────┤
│  1. Create ApplicationContext                                │
│  2. Scan for @Component, @Service, @Repository, @Controller  │
│  3. Create Beans:                                            │
│     - UserRepository                                         │
│     - PostRepository                                         │
│     - PasswordEncoder                                        │
│     - PostServiceImpl                                        │
│     - UserServiceImpl (inject 9 dependencies)                │
│     - UserController (inject UserService, PostService)       │
│  4. Start web server on port 8080                            │
│  5. Application ready!                                       │
└──────────────────────────────────────────────────────────────┘
```

---

## What's Next?

You now understand HOW Spring creates and manages objects. Next:

**→ [03-SPRING-BOOT-ESSENTIALS.md](./03-SPRING-BOOT-ESSENTIALS.md)** - Spring Boot Auto-Configuration, Properties, Profiles

**→ [04-JPA-HIBERNATE-BASICS.md](./04-JPA-HIBERNATE-BASICS.md)** - How your entities become database tables

**Key Questions for Next Sections:**
- How does Spring Boot auto-configure the database?
- What is `application.properties` and how does it work?
- How do `@Entity` classes become database tables?
- What is an EntityManager and why does it matter?

**Completed**:
- ✅ Java Essentials
- ✅ Spring Core (IoC, DI)

**Next**: Spring Boot Essentials 🎯
