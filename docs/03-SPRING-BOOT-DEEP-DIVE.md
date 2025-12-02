# Spring Boot - Deep Dive: How It Actually Works

## What Is Spring Boot Really?

Spring Boot is a **framework** that does 3 main things:
1. **Manages your objects** (Dependency Injection)
2. **Configures your application automatically** (Auto-configuration)
3. **Provides ready-to-use components** (Starters)

---

## Part 1: The Spring Container (ApplicationContext)

### What Is It?

The Spring Container is like a **factory that creates and manages all your objects**.

```java
// Without Spring (manual creation):
UserRepository userRepository = new UserRepository();
UserService userService = new UserService(userRepository);
UserController userController = new UserController(userService);
// You create everything manually! Messy!

// With Spring (automatic):
@Autowired
private UserService userService;
// Spring creates everything for you!
```

### How Does It Work?

When your application starts:

```
1. JVM starts
   ↓
2. Spring Boot main() method runs
   @SpringBootApplication
   public static void main(String[] args) {
       SpringApplication.run(BlogApplication.class, args);
   }
   ↓
3. Spring scans your packages for @Component, @Service, @Repository, @Controller
   ↓
4. Spring creates the ApplicationContext (the container)
   ↓
5. Spring creates all beans (objects) and stores them in the container
   ↓
6. Spring injects dependencies (connects objects together)
   ↓
7. Your application is ready!
```

### What Is a Bean?

A **bean** is just an object managed by Spring.

```java
// This is a bean:
@Service
public class UserService {
    // Spring creates this object
    // Spring manages its lifecycle
    // Spring injects its dependencies
}

// This is NOT a bean:
public class MyHelper {
    // You create this with: new MyHelper()
    // Spring doesn't know about it
    // You manage it manually
}
```

### How Spring Creates Beans

```java
// Step 1: Spring scans and finds this class
@Service  // <-- This annotation tells Spring: "I'm a bean!"
public class UserService {

    private final UserRepository userRepository;

    // Step 2: Spring sees constructor
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
}

// What Spring does internally (simplified):
class SpringContainer {
    Map<String, Object> beans = new HashMap<>();

    void createBeans() {
        // 1. Create UserRepository first (dependency)
        UserRepository repo = new UserRepository();
        beans.put("userRepository", repo);

        // 2. Create UserService with dependency
        UserService service = new UserService(repo);
        beans.put("userService", service);

        // 3. Now anyone can ask for UserService
    }

    Object getBean(String name) {
        return beans.get(name);
    }
}
```

### Bean Scopes

```java
@Service  // Default: Singleton
public class UserService {
    // Spring creates ONLY ONE instance
    // Everyone gets the same object
}

@Service
@Scope("prototype")
public class TempService {
    // Spring creates NEW instance every time
    // Each request gets different object
}
```

**Singleton vs Prototype**:
```java
// Singleton (default):
UserService service1 = context.getBean(UserService.class);
UserService service2 = context.getBean(UserService.class);
// service1 == service2 (same object)

// Prototype:
@Scope("prototype")
TempService temp1 = context.getBean(TempService.class);
TempService temp2 = context.getBean(TempService.class);
// temp1 != temp2 (different objects)
```

**Why singleton by default?**
- Performance: Create once, reuse forever
- Stateless services work fine as singletons
- Thread-safe if no mutable state

⚠️ **Warning**: Never store request-specific data in singleton beans!
```java
@Service  // Singleton!
public class UserService {
    private User currentUser;  // ❌ BAD! Shared across all requests!

    public void doSomething() {
        // All users would see same currentUser!
    }
}
```

---

## Part 2: Dependency Injection (DI)

### What Problem Does DI Solve?

**Without DI** (manual wiring):
```java
public class UserController {
    private UserService userService;

    public UserController() {
        UserRepository userRepository = new UserRepository();
        this.userService = new UserService(userRepository);
        // Problem 1: Tight coupling
        // Problem 2: Hard to test (can't mock dependencies)
        // Problem 3: Have to create everything manually
    }
}
```

**With DI** (Spring wiring):
```java
@RestController
public class UserController {
    private final UserService userService;

    // Spring automatically provides UserService
    public UserController(UserService userService) {
        this.userService = userService;
    }
}
```

### Three Ways to Inject Dependencies

#### 1. Constructor Injection (RECOMMENDED)
```java
@Service
public class UserService {
    private final UserRepository userRepository;
    private final EmailService emailService;

    // Spring calls this constructor and provides dependencies
    public UserService(UserRepository userRepository,
                      EmailService emailService) {
        this.userRepository = userRepository;
        this.emailService = emailService;
    }
}
```

**Why constructor injection is best:**
- ✅ Dependencies are final (immutable)
- ✅ Class can't be created without dependencies
- ✅ Easy to test (just call constructor)
- ✅ Makes dependencies explicit

#### 2. Field Injection (NOT RECOMMENDED)
```java
@Service
public class UserService {
    @Autowired  // Spring injects via reflection
    private UserRepository userRepository;

    // Problems:
    // ❌ Can't make field final
    // ❌ Harder to test
    // ❌ Hidden dependencies
    // ❌ Can create object without dependencies
}
```

#### 3. Setter Injection (RARELY USED)
```java
@Service
public class UserService {
    private UserRepository userRepository;

    @Autowired
    public void setUserRepository(UserRepository repo) {
        this.userRepository = repo;
    }

    // Used for optional dependencies
}
```

### How Spring Resolves Dependencies

```java
// Your code:
@RestController
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }
}

// What Spring does:
1. Sees UserController needs UserService
2. Looks in container for bean of type UserService
3. Finds: userService bean
4. Sees UserService needs UserRepository
5. Looks in container for bean of type UserRepository
6. Finds: userRepository bean
7. Creates UserService with UserRepository
8. Creates UserController with UserService
9. Done!
```

**Dependency Graph**:
```
UserController
    ↓ needs
UserService
    ↓ needs
UserRepository
    ↓ needs
EntityManager (provided by Spring Data JPA)
    ↓ needs
DataSource (provided by Spring Boot)
```

Spring resolves this from bottom-up automatically!

### Circular Dependencies

```java
@Service
public class ServiceA {
    private final ServiceB serviceB;

    public ServiceA(ServiceB serviceB) {
        this.serviceB = serviceB;
    }
}

@Service
public class ServiceB {
    private final ServiceA serviceA;

    public ServiceB(ServiceA serviceA) {
        this.serviceA = serviceA;
    }
}

// Spring can't resolve this!
// ServiceA needs ServiceB
// ServiceB needs ServiceA
// Which to create first? 🤯
// ERROR: The dependencies of some beans in the application context form a cycle
```

**Solution**: Don't create circular dependencies! Redesign your classes.

---

## Part 3: Annotations - How They Work

### What Is an Annotation?

An annotation is **metadata** - information ABOUT your code.

```java
@Service  // This is metadata saying "I'm a service"
public class UserService { }
```

### Who Processes Annotations?

Different processors handle different annotations:

```java
@Entity         // Processed by: Hibernate
@Table(name)    // Processed by: Hibernate
@Id             // Processed by: Hibernate

@Service        // Processed by: Spring Container
@Autowired      // Processed by: Spring Container
@Transactional  // Processed by: Spring Transaction Manager

@GetMapping     // Processed by: Spring MVC
@RequestBody    // Processed by: Spring MVC
```

### When Are Annotations Processed?

**Compile time vs Runtime:**

```java
// Compile time (Lombok):
@Data  // Lombok generates getters/setters BEFORE compilation
public class User { }
// After Lombok: class has getters/setters in .class file

// Runtime (Spring):
@Service  // Spring reads this WHEN application starts
public class UserService { }
// Spring creates bean at startup
```

### How Spring Processes Annotations

```java
// 1. Component Scanning
@SpringBootApplication  // This triggers component scanning
public class BlogApplication {
    public static void main(String[] args) {
        SpringApplication.run(BlogApplication.class, args);
    }
}

// 2. Spring scans base package and sub-packages
scan(_blog.blog.*)

// 3. Spring finds classes with component annotations:
@Component   // Generic component
@Service     // Service layer component
@Repository  // Data layer component
@Controller  // Web layer component
@RestController  // REST API component

// 4. Spring creates beans for each

// 5. Spring looks for @Autowired and injects dependencies
```

### Stereotype Annotations

All these are the same to Spring, just different names for readability:

```java
@Component   // Generic
@Service     // Business logic
@Repository  // Database access
@Controller  // Web MVC
@RestController  // REST API

// They all do the same thing:
// Tell Spring: "Create a bean of this class"
```

**Why different names?**
- **Documentation**: Code is self-documenting
- **Future features**: Spring might add special behavior later
- **Exception translation**: `@Repository` adds automatic exception translation

### Meta-Annotations

Annotations can be built from other annotations:

```java
// @RestController is actually:
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Controller        // <-- Built on top of @Controller
@ResponseBody      // <-- Adds @ResponseBody to all methods
public @interface RestController {
}

// So this:
@RestController
public class UserController { }

// Is the same as:
@Controller
@ResponseBody
public class UserController { }
```

---

## Part 4: Spring Boot Auto-Configuration

### What Is Auto-Configuration?

Spring Boot **automatically configures** your application based on:
1. Dependencies in classpath
2. Properties in application.properties
3. Existing beans

```java
// You add this dependency:
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

// Spring Boot automatically configures:
- EntityManagerFactory
- TransactionManager
- DataSource
- JPA repositories
// You don't have to configure anything!
```

### How It Works

```java
@SpringBootApplication
// This is actually 3 annotations:
@Configuration       // Marks as configuration class
@EnableAutoConfiguration  // <-- This enables auto-configuration
@ComponentScan       // Enables component scanning

public class BlogApplication {
    public static void main(String[] args) {
        SpringApplication.run(BlogApplication.class, args);
    }
}
```

### Auto-Configuration Classes

Spring Boot has ~200 auto-configuration classes like:

```java
@Configuration
@ConditionalOnClass(EntityManager.class)  // Only if JPA is present
@EnableConfigurationProperties(JpaProperties.class)
public class JpaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean  // Only create if you haven't created one
    public EntityManagerFactory entityManagerFactory() {
        // Configure EntityManagerFactory
        return emf;
    }

    @Bean
    @ConditionalOnMissingBean
    public TransactionManager transactionManager() {
        // Configure transaction manager
        return tm;
    }
}
```

### Conditional Annotations

```java
@ConditionalOnClass(DataSource.class)
// Only configure if DataSource class is in classpath

@ConditionalOnMissingBean(DataSource.class)
// Only configure if no DataSource bean exists

@ConditionalOnProperty(name = "spring.jpa.show-sql", havingValue = "true")
// Only configure if property is set to true

@ConditionalOnWebApplication
// Only configure if this is a web application
```

### Override Auto-Configuration

```java
// Spring Boot auto-configures DataSource

// But you can override:
@Configuration
public class MyConfig {

    @Bean  // Your bean takes precedence
    public DataSource dataSource() {
        // Your custom configuration
        return new HikariDataSource(...);
    }
}
```

---

## Part 5: Spring Boot Starters

### What Are Starters?

Starters are **curated dependency bundles**.

```xml
<!-- Without starter (manual): -->
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-web</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-webmvc</artifactId>
</dependency>
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
<dependency>
    <groupId>org.apache.tomcat.embed</groupId>
    <artifactId>tomcat-embed-core</artifactId>
</dependency>
<!-- ...20 more dependencies -->

<!-- With starter (automatic): -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<!-- Includes everything above automatically! -->
```

### Common Starters in Our Project

```xml
spring-boot-starter-web
  ├── Spring MVC
  ├── Tomcat
  ├── Jackson (JSON)
  └── Validation

spring-boot-starter-data-jpa
  ├── Hibernate
  ├── Spring Data JPA
  ├── JDBC
  └── Transaction management

spring-boot-starter-security
  ├── Spring Security Core
  ├── Spring Security Config
  ├── Spring Security Web
  └── BCrypt password encoder
```

---

## Part 6: How Our Application Starts

### Startup Sequence

```java
// 1. JVM starts
public static void main(String[] args) {
    // 2. Spring Boot starts
    SpringApplication.run(BlogApplication.class, args);
}

// 3. Spring Boot does (internally):
public static ConfigurableApplicationContext run(Class<?> primarySource, String... args) {
    // a. Create ApplicationContext
    context = new AnnotationConfigApplicationContext();

    // b. Register @Configuration classes
    context.register(BlogApplication.class);

    // c. Scan for components
    context.scan("_blog.blog");  // Scans _blog.blog package

    // d. Process auto-configurations
    processAutoConfigurations();

    // e. Create beans
    createAllBeans();

    // f. Inject dependencies
    injectDependencies();

    // g. Call @PostConstruct methods
    callPostConstructMethods();

    // h. Start Tomcat server
    tomcat.start();

    // i. Application is ready!
    return context;
}
```

### Bean Creation Order

```java
// Spring creates beans in dependency order:

1. EntityManager (no dependencies)
   ↓
2. UserRepository (needs EntityManager)
   ↓
3. UserService (needs UserRepository)
   ↓
4. UserController (needs UserService)
   ↓
5. DispatcherServlet (needs UserController)
   ↓
6. Tomcat (needs DispatcherServlet)
```

### What Happens on First Request

```
User: GET /posts/all
  ↓
1. Tomcat receives request
  ↓
2. DispatcherServlet (Spring MVC entry point)
  ↓
3. HandlerMapping finds controller method
   Found: PostController.getAllPosts()
  ↓
4. Call method on PostController bean
  ↓
5. PostController calls PostService
  ↓
6. PostService calls PostRepository
  ↓
7. Repository queries database via Hibernate
  ↓
8. Results flow back up
  ↓
9. Jackson converts objects to JSON
  ↓
10. Response sent to user
```

---

## Summary: Key Spring Boot Concepts

| Concept | What It Is | Why It Matters |
|---------|------------|----------------|
| **ApplicationContext** | Container that holds beans | Creates and manages all objects |
| **Bean** | Object managed by Spring | You don't create it, Spring does |
| **DI** | Spring provides dependencies | Loose coupling, easy testing |
| **Component Scanning** | Spring finds @Component classes | Automatic bean discovery |
| **Auto-Configuration** | Spring configures based on classpath | Zero configuration needed |
| **Starters** | Dependency bundles | Include everything you need |

---

**Next**: I'll explain **Hibernate/JPA internals** with the same depth, then apply all this to our actual code line-by-line.

Continue?
