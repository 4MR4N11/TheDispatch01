# Spring Boot Internals: How It Actually Works at Runtime

## What This Document Covers

This is a **deep technical dive** into Spring Boot's internal mechanics. You'll learn:
- What happens when you run `SpringApplication.run()`
- How component scanning actually works (bytecode analysis with ASM)
- Bean instantiation and dependency resolution algorithms
- Proxy creation mechanics (CGLIB vs JDK)
- Auto-configuration loading process
- Runtime behavior and lifecycle hooks

**Prerequisites**: Understanding of Java reflection, classloaders, and bytecode basics.

---

## 1. The Starting Point: @SpringBootApplication

Let's start with your `BlogApplication.java`:

```java
@SpringBootApplication
public class BlogApplication {
    public static void main(String[] args) {
        SpringApplication.run(BlogApplication.class, args);
    }
}
```

### What @SpringBootApplication Actually Is

`@SpringBootApplication` is a **composite annotation** (meta-annotation). Let's see what it expands to:

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@SpringBootConfiguration      // ← Marks this as configuration class
@EnableAutoConfiguration      // ← Triggers auto-configuration
@ComponentScan(               // ← Scans for @Component, @Service, etc.
    excludeFilters = {
        @Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
        @Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class)
    }
)
public @interface SpringBootApplication {
    // ...
}
```

**Breaking it down**:

1. **@SpringBootConfiguration**: Just a specialized `@Configuration`. Tells Spring this class can define beans.
2. **@EnableAutoConfiguration**: The "magic" - automatically configures beans based on classpath and properties.
3. **@ComponentScan**: Scans the package `_blog.blog` and all sub-packages for Spring components.

**Why _blog.blog package?**
By default, `@ComponentScan` starts from the package containing `BlogApplication.class`. Since your main class is in `_blog.blog`, it scans:
- `_blog.blog.controller.*`
- `_blog.blog.service.*`
- `_blog.blog.repository.*`
- All other sub-packages

**Important**: If you put `BlogApplication` in a different package (e.g., `com.example`), it would **NOT** find your components in `_blog.blog` unless you explicitly specify `@ComponentScan(basePackages = "_blog.blog")`.

---

## 2. SpringApplication.run() - The Complete Startup Sequence

When you call `SpringApplication.run(BlogApplication.class, args)`, here's what happens step-by-step:

### Phase 1: Pre-Initialization (Milliseconds 0-50)

```java
public static ConfigurableApplicationContext run(Class<?> primarySource, String... args) {
    return new SpringApplication(primarySource).run(args);
}
```

#### Step 1.1: SpringApplication Constructor

```java
public SpringApplication(Class<?>... primarySources) {
    this.primarySources = new LinkedHashSet<>(Arrays.asList(primarySources));

    // Detect web application type (SERVLET, REACTIVE, or NONE)
    this.webApplicationType = WebApplicationType.deduceFromClasspath();

    // Load ApplicationContextInitializers from spring.factories
    setInitializers(getSpringFactoriesInstances(ApplicationContextInitializer.class));

    // Load ApplicationListeners from spring.factories
    setListeners(getSpringFactoriesInstances(ApplicationListener.class));

    // Find the main class (BlogApplication) by analyzing stack trace
    this.mainApplicationClass = deduceMainApplicationClass();
}
```

**What's happening**:
- **Web type detection**: Checks if `org.springframework.web.servlet.DispatcherServlet` is on classpath → **SERVLET**
- **spring.factories loading**: Reads `META-INF/spring.factories` from all JARs (more on this later)
- **Main class detection**: Walks stack trace to find the class with `main()` method

#### Step 1.2: Load spring.factories Files

Spring Boot scans **ALL** JARs in your classpath for `META-INF/spring.factories`:

```properties
# From spring-boot-autoconfigure.jar
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,\
org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,\
org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,\
org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration,\
# ... 150+ more auto-configuration classes
```

**Your application loads**:
- `JpaRepositoriesAutoConfiguration` (because you have spring-boot-starter-data-jpa)
- `HibernateJpaAutoConfiguration` (JPA implementation)
- `SecurityAutoConfiguration` (because you have spring-boot-starter-security)
- `WebMvcAutoConfiguration` (because you have spring-boot-starter-web)

### Phase 2: ApplicationContext Creation (Milliseconds 50-100)

```java
public ConfigurableApplicationContext run(String... args) {
    StopWatch stopWatch = new StopWatch();
    stopWatch.start();

    ConfigurableApplicationContext context = null;

    // 1. Prepare environment (loads application.properties)
    ConfigurableEnvironment environment = prepareEnvironment(listeners, applicationArguments);

    // 2. Create ApplicationContext (AnnotationConfigServletWebServerApplicationContext for web apps)
    context = createApplicationContext();

    // 3. Prepare context (register bean definitions)
    prepareContext(context, environment, listeners, applicationArguments, printedBanner);

    // 4. Refresh context (instantiate all beans)
    refreshContext(context);

    // 5. Post-processing
    afterRefresh(context, applicationArguments);

    stopWatch.stop();
    return context;
}
```

#### Step 2.1: Environment Preparation

```java
private ConfigurableEnvironment prepareEnvironment(...) {
    // 1. Create environment
    ConfigurableEnvironment environment = new StandardServletEnvironment();

    // 2. Load property sources in this order (later sources override earlier):
    //    a. Command-line arguments (--server.port=9090)
    //    b. JVM system properties (System.getProperty())
    //    c. OS environment variables (System.getenv())
    //    d. application.properties / application.yml
    //    e. @PropertySource annotations

    // 3. Process profiles (spring.profiles.active)
    configureProfiles(environment, applicationArguments.getSourceArgs());

    return environment;
}
```

**In your application**, this loads:

```properties
# From application.properties
spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/blog}
spring.datasource.username=${DB_USERNAME:blog}
spring.datasource.password=${DB_PASSWORD:password}
security.jwt.secret-key=${JWT_SECRET_KEY}
```

**Resolution example**:
```
spring.datasource.url = ${DB_URL:jdbc:postgresql://localhost:5432/blog}
                         ↓
1. Check command-line: --DB_URL=... (not found)
2. Check System.getProperty("DB_URL") (not found)
3. Check System.getenv("DB_URL") (found! = postgresql://dispatch-postgres/blog)
4. Final value: "postgresql://dispatch-postgres/blog"
```

#### Step 2.2: ApplicationContext Creation

```java
protected ConfigurableApplicationContext createApplicationContext() {
    // For web applications, creates:
    return new AnnotationConfigServletWebServerApplicationContext();
}
```

This context class contains:
- **BeanFactory**: The bean container
- **Environment**: Property sources
- **ResourceLoader**: File/classpath resource loader
- **ApplicationEventPublisher**: Event system

### Phase 3: Component Scanning & Bean Definition Loading (Milliseconds 100-300)

#### Step 3.1: Register Configuration Classes

```java
private void prepareContext(...) {
    // Register BlogApplication.class as a bean definition
    context.register(BlogApplication.class);
}
```

#### Step 3.2: Component Scanning (The Deep Part!)

When Spring processes `@ComponentScan` on `BlogApplication`, it triggers `ClassPathBeanDefinitionScanner`:

```java
// Internal Spring Boot code
public class ClassPathBeanDefinitionScanner {

    public Set<BeanDefinition> scan(String... basePackages) {
        // basePackages = ["_blog.blog"]
        Set<BeanDefinition> beanDefinitions = new LinkedHashSet<>();

        for (String basePackage : basePackages) {
            // 1. Convert package to classpath search path
            String packageSearchPath = "classpath*:_blog/blog/**/*.class";

            // 2. Find all .class files using PathMatchingResourcePatternResolver
            Resource[] resources = resourcePatternResolver.getResources(packageSearchPath);

            // 3. For each .class file, use ASM to read annotations WITHOUT loading the class
            for (Resource resource : resources) {
                MetadataReader metadataReader = metadataReaderFactory.getMetadataReader(resource);

                // 4. Check if class has @Component, @Service, @Repository, or @Controller
                if (isCandidateComponent(metadataReader)) {
                    BeanDefinition bd = createBeanDefinition(metadataReader);
                    beanDefinitions.add(bd);
                }
            }
        }

        return beanDefinitions;
    }
}
```

**Using ASM (Bytecode Library)**:

Spring uses **ASM** to read class files without loading them into JVM (loading would be slow and memory-intensive):

```java
// Reading UserController.class using ASM
ClassReader classReader = new ClassReader(classBytes);
AnnotationMetadataReadingVisitor visitor = new AnnotationMetadataReadingVisitor();
classReader.accept(visitor, ClassReader.SKIP_DEBUG);

// Now we know:
// - Class name: _blog.blog.controller.UserController
// - Annotations: @RestController, @RequestMapping("/users")
// - Constructor parameters: (UserService, PostService, SubscriptionService)
```

**What gets scanned in your app**:

```
Scanning _blog.blog/**/*.class...

Found: _blog/blog/controller/UserController.class
  → Has @RestController
  → Register as bean: userController

Found: _blog/blog/service/UserServiceImpl.class
  → Has @Service
  → Register as bean: userServiceImpl

Found: _blog/blog/repository/UserRepository.class
  → Is interface extending JpaRepository
  → Register as bean: userRepository (Spring Data creates proxy)

Found: _blog/blog/config/SecurityConfig.class
  → Has @Configuration
  → Register as bean: securityConfig

Found: _blog/blog/entity/User.class
  → Has @Entity (but not @Component)
  → Skip (entities are not beans)
```

**Result**: Spring now has ~50 `BeanDefinition` objects in memory, but **NO beans instantiated yet**.

### Phase 4: Auto-Configuration Loading (Milliseconds 300-500)

Now `@EnableAutoConfiguration` kicks in:

```java
// Triggered by @EnableAutoConfiguration
public class AutoConfigurationImportSelector {

    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
        // 1. Load all auto-configuration classes from spring.factories
        List<String> configurations = SpringFactoriesLoader.loadFactoryNames(
            EnableAutoConfiguration.class,
            classLoader
        );
        // Result: 150+ class names like:
        // - org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
        // - org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration

        // 2. Filter based on @Conditional annotations
        configurations = filter(configurations, autoConfigurationMetadata);

        // 3. Return filtered list
        return configurations.toArray(new String[0]);
    }
}
```

**Example: HibernateJpaAutoConfiguration**

```java
@Configuration
@ConditionalOnClass({ LocalContainerEntityManagerFactoryBean.class, EntityManager.class })
@ConditionalOnMissingBean(type = "javax.persistence.EntityManagerFactory")
@EnableConfigurationProperties(JpaProperties.class)
@Import({ HibernateJpaConfiguration.class })
public class HibernateJpaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(...) {
        // Creates EntityManagerFactory only if user didn't define one
    }
}
```

**Condition evaluation**:

1. `@ConditionalOnClass(EntityManager.class)`: Is `jakarta.persistence.EntityManager` on classpath?
   - **Yes** (you have spring-boot-starter-data-jpa)
   - ✅ Condition passes

2. `@ConditionalOnMissingBean(type = "EntityManagerFactory")`: Did user define EntityManagerFactory bean?
   - **No** (you didn't create one manually)
   - ✅ Condition passes

**Result**: `HibernateJpaAutoConfiguration` is included, and it will create:
- `EntityManagerFactory`
- `TransactionManager`
- `JpaVendorAdapter` (HibernateJpaVendorAdapter)

**In your application**, these auto-configurations activate:

| Auto-Configuration | Why It Activates | What It Creates |
|-------------------|------------------|-----------------|
| `JpaRepositoriesAutoConfiguration` | You have `@EnableJpaRepositories` (via @SpringBootApplication) | Repository proxies (`UserRepository`, etc.) |
| `HibernateJpaAutoConfiguration` | You have `jakarta.persistence.EntityManager` on classpath | `EntityManagerFactory`, `TransactionManager` |
| `DataSourceAutoConfiguration` | You have `spring.datasource.url` property | `HikariDataSource` connection pool |
| `SecurityAutoConfiguration` | You have Spring Security on classpath | `SecurityFilterChain` (default config) |
| `WebMvcAutoConfiguration` | You have `DispatcherServlet` on classpath | `DispatcherServlet`, view resolvers, message converters |

### Phase 5: Bean Instantiation (Milliseconds 500-2000)

Now comes the actual bean creation:

```java
public void refresh() throws BeansException {
    // 1. Prepare BeanFactory
    prepareBeanFactory(beanFactory);

    // 2. Invoke BeanFactoryPostProcessors (modify bean definitions)
    invokeBeanFactoryPostProcessors(beanFactory);

    // 3. Register BeanPostProcessors (will wrap beans in proxies later)
    registerBeanPostProcessors(beanFactory);

    // 4. Initialize message source
    initMessageSource();

    // 5. Instantiate all singletons
    finishBeanFactoryInitialization(beanFactory);

    // 6. Publish ContextRefreshedEvent
    finishRefresh();
}
```

#### Step 5.1: Resolve Bean Dependencies

Spring builds a **dependency graph**:

```
SecurityConfig
  ├─ requires → UserDetailsService (CustomUserDetailsService)
  │              └─ requires → UserRepository
  │                             └─ requires → EntityManagerFactory
  ├─ requires → JwtAuthenticationFilter
  │              └─ requires → JwtService
  │                             └─ requires → (no dependencies)
  └─ requires → PasswordEncoder (no dependencies)
```

**Instantiation order** (dependencies first):

1. `EntityManagerFactory` (no dependencies)
2. `UserRepository` (needs EntityManagerFactory)
3. `CustomUserDetailsService` (needs UserRepository)
4. `PasswordEncoder` (no dependencies)
5. `JwtService` (no dependencies)
6. `JwtAuthenticationFilter` (needs JwtService, CustomUserDetailsService)
7. `SecurityConfig` (needs all of the above)

#### Step 5.2: Actual Bean Creation (Using Reflection)

```java
// Simplified bean creation process
protected Object createBean(String beanName, BeanDefinition bd) {
    // 1. Determine bean class
    Class<?> beanClass = resolveBeanClass(bd); // e.g., UserServiceImpl.class

    // 2. Find constructor
    Constructor<?> constructor = determineConstructor(beanClass);
    // For UserServiceImpl: Constructor with 9 parameters

    // 3. Resolve constructor arguments (recursive bean creation)
    Object[] args = resolveConstructorArguments(constructor);
    // args[0] = userRepository bean
    // args[1] = postRepository bean
    // ... 7 more dependencies

    // 4. Instantiate using reflection
    Object bean = constructor.newInstance(args);

    // 5. Apply BeanPostProcessors (PROXY CREATION HAPPENS HERE)
    bean = applyBeanPostProcessorsBeforeInitialization(bean, beanName);

    // 6. Call initialization methods (@PostConstruct)
    invokeInitMethods(bean, beanName);

    // 7. Apply BeanPostProcessors again (more proxies if needed)
    bean = applyBeanPostProcessorsAfterInitialization(bean, beanName);

    return bean;
}
```

**Example: Creating UserServiceImpl**

```java
// Your UserServiceImpl constructor
public UserServiceImpl(
    UserRepository userRepository,
    PostRepository postRepository,
    CommentRepository commentRepository,
    SubscriptionRepository subscriptionRepository,
    LikeRepository likeRepository,
    PostMapper postMapper,
    CommentMapper commentMapper,
    PasswordEncoder passwordEncoder,
    CustomUserDetailsService customUserDetailsService
) {
    // Spring calls this constructor with all 9 dependencies resolved
}
```

**Behind the scenes**:

```java
// Spring's reflection call (simplified)
Constructor<UserServiceImpl> ctor = UserServiceImpl.class.getConstructor(
    UserRepository.class,
    PostRepository.class,
    CommentRepository.class,
    SubscriptionRepository.class,
    LikeRepository.class,
    PostMapper.class,
    CommentMapper.class,
    PasswordEncoder.class,
    CustomUserDetailsService.class
);

Object userService = ctor.newInstance(
    userRepositoryBean,      // Already created
    postRepositoryBean,      // Already created
    commentRepositoryBean,   // Already created
    subscriptionRepositoryBean, // Already created
    likeRepositoryBean,      // Already created
    postMapperBean,          // Already created
    commentMapperBean,       // Already created
    passwordEncoderBean,     // Already created
    customUserDetailsServiceBean  // Already created
);
```

### Phase 6: Proxy Creation (For @Transactional, @Async, Security)

After bean instantiation, `BeanPostProcessor`s wrap beans in **proxies**.

#### What Gets Proxied?

```java
// BeanPostProcessor interface
public interface BeanPostProcessor {
    default Object postProcessAfterInitialization(Object bean, String beanName) {
        // Create proxy if needed
        return bean;
    }
}
```

**In your application**:

1. **UserRepository** → Proxied by Spring Data JPA
2. **UserServiceImpl** → Proxied for `@Transactional` (if you had it)
3. **SecurityConfig.securityFilterChain()** method → NOT proxied (returns object, not service)

#### CGLIB vs JDK Proxies

**CGLIB Proxy** (default for Spring Boot):
- Used when class doesn't implement an interface
- Creates a **subclass** of your bean
- Uses bytecode generation

```java
// Your actual class
public class UserServiceImpl implements UserService {
    public UserResponse getUserById(Long id) { ... }
}

// CGLIB creates this at runtime:
public class UserServiceImpl$$EnhancerBySpringCGLIB$$12345 extends UserServiceImpl {

    private UserServiceImpl target; // The real instance

    @Override
    public UserResponse getUserById(Long id) {
        // Proxy logic (transaction management, etc.)
        try {
            transactionManager.beginTransaction();
            UserResponse result = target.getUserById(id); // Call real method
            transactionManager.commit();
            return result;
        } catch (Exception e) {
            transactionManager.rollback();
            throw e;
        }
    }
}
```

**JDK Dynamic Proxy**:
- Used when class implements an interface
- Creates proxy implementing the same interface
- Uses `java.lang.reflect.Proxy`

```java
// Your interface
public interface UserService {
    UserResponse getUserById(Long id);
}

// JDK creates this:
public class $Proxy123 implements UserService {
    private InvocationHandler handler;

    @Override
    public UserResponse getUserById(Long id) {
        return (UserResponse) handler.invoke(this, getUserByIdMethod, new Object[]{id});
    }
}
```

**Why proxies?**

Proxies allow Spring to add behavior **without modifying your code**:
- `@Transactional` → Wrap method in transaction
- `@Async` → Execute method in thread pool
- `@Cacheable` → Check cache before calling method
- Security → Check permissions before allowing method execution

### Phase 7: @PostConstruct Execution

After bean creation and proxy wrapping, Spring calls `@PostConstruct` methods:

```java
// In your JwtService
@PostConstruct
public void validateSecretKey() {
    if (secretKey == null || secretKey.trim().isEmpty()) {
        throw new IllegalStateException("JWT_SECRET_KEY must be set!");
    }
    // This runs AFTER:
    // 1. JwtService constructor called
    // 2. @Value fields injected (secretKey, jwtExpiration)
    // 3. Bean fully initialized
}
```

**Execution order**:

1. Constructor: `new JwtService()` (fields are null)
2. Field injection: `secretKey = environment.getProperty("security.jwt.secret-key")`
3. `@PostConstruct`: `validateSecretKey()` runs (secretKey is now set)

**Common mistake**:

```java
@Service
public class MyService {
    @Value("${my.property}")
    private String myProperty;

    // ❌ WRONG: myProperty is null here (not injected yet)
    public MyService() {
        System.out.println(myProperty); // null
    }

    // ✅ CORRECT: myProperty is injected now
    @PostConstruct
    public void init() {
        System.out.println(myProperty); // "actual value"
    }
}
```

### Phase 8: Embedded Tomcat Startup (Milliseconds 2000-3000)

Finally, Spring Boot starts the embedded web server:

```java
private void refreshContext(ConfigurableApplicationContext context) {
    refresh(context);

    if (context instanceof ServletWebServerApplicationContext) {
        // Start embedded Tomcat
        ((ServletWebServerApplicationContext) context).startWebServer();
    }
}
```

**What happens**:

1. Create `TomcatServletWebServerFactory`
2. Create `Tomcat` instance
3. Add `Connector` (HTTP on port 8080)
4. Register `DispatcherServlet` at `/*`
5. Register all `Filter`s (including `JwtAuthenticationFilter`)
6. Start Tomcat

**Filter registration order**:

```
Client Request
  ↓
[Tomcat Connector]
  ↓
[SecurityContextPersistenceFilter]  ← Spring Security
  ↓
[JwtAuthenticationFilter]           ← Your custom filter (from SecurityConfig)
  ↓
[FilterSecurityInterceptor]         ← Spring Security authorization
  ↓
[DispatcherServlet]                 ← Spring MVC
  ↓
[UserController.getUser()]          ← Your controller
```

---

## 3. Runtime Behavior: How @Value Works

Let's trace how `@Value("${security.jwt.secret-key}")` gets resolved:

### Step 1: Environment Preparation (Startup)

```java
// Spring loads properties in this order (later overrides earlier):
PropertySources propertySources = new MutablePropertySources();

// 1. Command-line args
propertySources.addFirst(new SimpleCommandLinePropertySource(args));

// 2. System properties
propertySources.addLast(new PropertiesPropertySource("systemProperties", System.getProperties()));

// 3. Environment variables
propertySources.addLast(new SystemEnvironmentPropertySource("systemEnvironment", System.getenv()));

// 4. application.properties
propertySources.addLast(new ResourcePropertySource("application.properties"));
```

### Step 2: Property Resolution

```java
// JwtService field
@Value("${security.jwt.secret-key}")
private String secretKey;

// How Spring resolves this:
String resolveProperty(String placeholder) {
    // placeholder = "security.jwt.secret-key"

    // 1. Check command-line: --security.jwt.secret-key=... (not found)
    // 2. Check System.getProperty("security.jwt.secret-key") (not found)
    // 3. Check System.getenv("SECURITY_JWT_SECRET_KEY") (not found - but different format)
    // 4. Check System.getenv("security.jwt.secret-key") (not found)
    // 5. Check application.properties: security.jwt.secret-key=${JWT_SECRET_KEY} (FOUND!)
    // 6. Now resolve ${JWT_SECRET_KEY}:
    //    - Check System.getenv("JWT_SECRET_KEY") (FOUND!)

    return resolvedValue;
}
```

**Property resolution with environment variables**:

Spring converts property names to environment variable format:

```
security.jwt.secret-key
  ↓ (replace . and - with _)
SECURITY_JWT_SECRET_KEY

spring.datasource.url
  ↓
SPRING_DATASOURCE_URL
```

### Step 3: Value Injection (Bean Creation)

```java
// During bean creation
Object jwtService = new JwtService();

// Spring uses reflection to set field
Field secretKeyField = JwtService.class.getDeclaredField("secretKey");
secretKeyField.setAccessible(true);
secretKeyField.set(jwtService, resolvedValue); // Sets the value
```

---

## 4. Runtime Behavior: How @Transactional Works

Let's see how `@Transactional` adds transaction management without you writing any transaction code:

### Without @Transactional (Manual Transaction Management)

```java
@Service
public class UserServiceImpl {

    private EntityManager entityManager;
    private PlatformTransactionManager transactionManager;

    public void updateUser(User user) {
        TransactionStatus transaction = transactionManager.getTransaction(new DefaultTransactionDefinition());

        try {
            entityManager.merge(user);
            transactionManager.commit(transaction);
        } catch (Exception e) {
            transactionManager.rollback(transaction);
            throw e;
        }
    }
}
```

### With @Transactional (Automatic via Proxy)

```java
@Service
public class UserServiceImpl {

    @Transactional
    public void updateUser(User user) {
        userRepository.save(user);
        // Transaction automatically managed!
    }
}
```

### How It Works Internally

**Step 1: Proxy Creation**

When Spring creates `UserServiceImpl` bean, `TransactionInterceptor` (a `BeanPostProcessor`) wraps it in a proxy:

```java
// Simplified proxy creation
public Object createTransactionalProxy(Object target) {
    ProxyFactory proxyFactory = new ProxyFactory(target);
    proxyFactory.addAdvice(new TransactionInterceptor(transactionManager));
    return proxyFactory.getProxy();
}
```

**Step 2: Method Interception**

When you call `userService.updateUser(user)`, the proxy intercepts:

```java
// The actual proxy code (simplified)
public class UserServiceImpl$$Proxy extends UserServiceImpl {

    private UserServiceImpl target; // Real instance
    private TransactionInterceptor transactionInterceptor;

    @Override
    public void updateUser(User user) {
        // 1. Check if method has @Transactional
        if (hasTransactional(updateUserMethod)) {
            // 2. Start transaction
            TransactionStatus tx = transactionManager.getTransaction(new DefaultTransactionDefinition());

            try {
                // 3. Call real method
                target.updateUser(user);

                // 4. Commit if no exception
                transactionManager.commit(tx);
            } catch (Exception e) {
                // 5. Rollback on exception
                transactionManager.rollback(tx);
                throw e;
            }
        } else {
            // No @Transactional, just call method
            target.updateUser(user);
        }
    }
}
```

**Why you need @Transactional on public methods**:

Proxies are created by **subclassing** (CGLIB) or **implementing interface** (JDK). Private/protected methods can't be overridden, so proxy can't intercept them!

```java
@Service
public class MyService {

    @Transactional
    private void saveUser(User user) {
        // ❌ DOESN'T WORK! Proxy can't override private method
    }

    @Transactional
    public void saveUser(User user) {
        // ✅ WORKS! Proxy overrides this and adds transaction
    }

    public void doSomething() {
        saveUser(user); // ❌ Doesn't go through proxy (internal call)
    }
}
```

**Internal calls don't go through proxy**:

```java
@Service
public class MyService {

    public void methodA() {
        methodB(); // ❌ Internal call - doesn't go through proxy
    }

    @Transactional
    public void methodB() {
        // Transaction NOT started because methodB() was called directly
    }
}

// How Spring sees it:
MyService proxy = new MyService$$Proxy();
proxy.methodA(); // Goes through proxy
  └─ calls target.methodA()
      └─ calls target.methodB() // Direct call, NOT proxy.methodB()!
```

**Solution**: Inject self-reference:

```java
@Service
public class MyService {

    @Autowired
    private MyService self; // This is the PROXY, not the target

    public void methodA() {
        self.methodB(); // ✅ Goes through proxy - transaction works!
    }

    @Transactional
    public void methodB() {
        // Transaction started correctly
    }
}
```

---

## 5. How Spring Data JPA Creates Repository Implementations

You write:

```java
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
}
```

**Question**: Where's the implementation? How does Spring create it?

### Step 1: Repository Detection

During component scanning, `JpaRepositoriesAutoConfiguration` triggers:

```java
@Configuration
@EnableJpaRepositories(basePackages = "_blog.blog.repository")
public class JpaRepositoriesAutoConfiguration {
    // Scans for interfaces extending JpaRepository
}
```

### Step 2: Proxy Creation

Spring creates a **JDK dynamic proxy** (because it's an interface):

```java
// Simplified proxy creation
public Object createRepositoryProxy(Class<?> repositoryInterface) {
    return Proxy.newProxyInstance(
        classLoader,
        new Class<?>[] { repositoryInterface },
        new JpaRepositoryInvocationHandler(entityManager)
    );
}
```

### Step 3: Method Invocation

When you call `userRepository.findByUsername("john")`:

```java
public class JpaRepositoryInvocationHandler implements InvocationHandler {

    private EntityManager entityManager;

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        // method = findByUsername(String)
        // args = ["john"]

        // 1. Parse method name
        if (method.getName().startsWith("findBy")) {
            String propertyName = extractProperty(method); // "username"

            // 2. Generate JPQL query
            String jpql = "SELECT u FROM User u WHERE u." + propertyName + " = :value";

            // 3. Execute query
            TypedQuery<User> query = entityManager.createQuery(jpql, User.class);
            query.setParameter("value", args[0]); // args[0] = "john"

            // 4. Return result
            List<User> results = query.getResultList();
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        }

        // Handle other methods (save, delete, etc.)
    }
}
```

**Method name parsing**:

```
findByUsername → WHERE username = ?
findByUsernameAndEmail → WHERE username = ? AND email = ?
findByUsernameOrEmail → WHERE username = ? OR email = ?
findByUsernameIgnoreCase → WHERE LOWER(username) = LOWER(?)
findByAgeGreaterThan → WHERE age > ?
findByAgeLessThanEqual → WHERE age <= ?
findByUsernameStartingWith → WHERE username LIKE 'value%'
findByUsernameContaining → WHERE username LIKE '%value%'
```

---

## 6. How Security Filters Work at Runtime

When a request comes in, it goes through multiple filters:

### Step 1: Filter Chain Construction

Your `SecurityConfig` creates a `SecurityFilterChain`:

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
        .authorizeHttpRequests(authz -> authz
            .requestMatchers("/auth/**").permitAll()
            .anyRequest().authenticated()
        )
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
}
```

**Spring Security builds this filter chain**:

```
FilterChainProxy
  ├─ [SecurityContextPersistenceFilter]
  ├─ [LogoutFilter]
  ├─ [JwtAuthenticationFilter]               ← Your custom filter
  ├─ [UsernamePasswordAuthenticationFilter]
  ├─ [RequestCacheAwareFilter]
  ├─ [SecurityContextHolderAwareRequestFilter]
  ├─ [AnonymousAuthenticationFilter]
  ├─ [ExceptionTranslationFilter]
  └─ [FilterSecurityInterceptor]             ← Authorization check
```

### Step 2: Request Processing

**Example request**: `GET /users/5`

```java
// 1. SecurityContextPersistenceFilter
// - Loads SecurityContext from session (if exists)

// 2. JwtAuthenticationFilter (YOUR FILTER)
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, ...) {
        // 1. Extract JWT from cookie or header
        String token = extractToken(request);

        if (token != null && jwtService.isTokenValid(token)) {
            // 2. Load user from token
            String username = jwtService.extractUsername(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            // 3. Create Authentication object
            UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

            // 4. Store in SecurityContext (thread-local storage)
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        // 5. Continue filter chain
        filterChain.doFilter(request, response);
    }
}

// 3. FilterSecurityInterceptor
// - Checks if authenticated user has access to /users/5
// - Reads your .authorizeHttpRequests() configuration
// - If authorized, continues to DispatcherServlet
// - If denied, throws AccessDeniedException → returns 403

// 4. DispatcherServlet
// - Routes to UserController.getUser()

// 5. UserController
@GetMapping("/{id}")
public UserResponse getUser(@PathVariable Long id, Authentication auth) {
    // auth parameter is automatically injected from SecurityContext
    // auth.getName() = "john" (the authenticated username)
}
```

### Step 3: SecurityContext Storage

```java
// SecurityContext is stored in ThreadLocal
public class SecurityContextHolder {

    private static ThreadLocal<SecurityContext> contextHolder = new ThreadLocal<>();

    public static void setContext(SecurityContext context) {
        contextHolder.set(context);
    }

    public static SecurityContext getContext() {
        return contextHolder.get();
    }
}
```

**Why ThreadLocal?**

Each HTTP request is handled by a different thread. ThreadLocal ensures:
- Request A (Thread 1) has User John's authentication
- Request B (Thread 2) has User Jane's authentication
- They don't interfere with each other

```java
// Request 1 (Thread pool-1-thread-1)
SecurityContextHolder.getContext() → User: john

// Request 2 (Thread pool-1-thread-2)
SecurityContextHolder.getContext() → User: jane

// Same JVM, different threads, different contexts!
```

---

## 7. Complete Request Flow with All Details

Let's trace a **complete request** through your application:

**Request**: `POST /posts` with JWT cookie

### Step-by-Step:

```
[1] Client sends request
  ↓
[2] Tomcat receives on port 8080
  ↓
[3] SecurityContextPersistenceFilter
    - Checks session for existing SecurityContext (none for stateless JWT)
  ↓
[4] JwtAuthenticationFilter.doFilterInternal()
    - Extracts JWT from cookie: "jwt=eyJhbGci..."
    - Validates token: jwtService.isTokenValid(token, userDetails)
    - Loads user: customUserDetailsService.loadUserByUsername("john")
    - Creates Authentication: UsernamePasswordAuthenticationToken
    - Stores in SecurityContext: SecurityContextHolder.getContext().setAuthentication(auth)
  ↓
[5] FilterSecurityInterceptor
    - Checks: Is user authenticated? ✅ Yes
    - Checks: Does /posts require authentication? ✅ Yes (anyRequest().authenticated())
    - Allows request to proceed
  ↓
[6] DispatcherServlet.doDispatch()
    - Finds handler: PostController.createPost()
    - Resolves @RequestBody: Uses Jackson to deserialize JSON → CreatePostRequest object
    - Resolves Authentication: Injects from SecurityContext
  ↓
[7] PostController.createPost(request, auth)
    - Calls: postService.createPost(request, auth.getName())
  ↓
[8] PostService.createPost() [PROXY INTERCEPTS HERE]
    - ProxyFactory intercepts call
    - TransactionInterceptor starts transaction:
        transactionManager.getTransaction()
    - Calls real method: target.createPost()
  ↓
[9] PostServiceImpl.createPost() (actual implementation)
    - Creates Post entity
    - Calls: postRepository.save(post)
  ↓
[10] PostRepository.save(post) [PROXY INTERCEPTS HERE]
     - Spring Data JPA proxy intercepts
     - Calls: entityManager.persist(post)
  ↓
[11] EntityManager (Hibernate)
     - Adds entity to Persistence Context
     - Generates: INSERT INTO posts (title, content, author_id) VALUES (?, ?, ?)
     - Executes SQL (within transaction)
  ↓
[12] Transaction committed (proxy interceptor)
     - transactionManager.commit()
     - Hibernate flushes changes to database
  ↓
[13] Return to PostController
     - Maps entity to DTO: PostMapper.toResponse(post)
     - Returns ResponseEntity with PostResponse
  ↓
[14] DispatcherServlet
     - Uses HttpMessageConverter (Jackson) to serialize PostResponse → JSON
     - Sets response headers: Content-Type: application/json
  ↓
[15] Response sent to client
     - HTTP 201 Created
     - Body: {"id": 123, "title": "...", ...}
```

---

## 8. Key Takeaways

### What Actually Happens at Startup

1. **SpringApplication.run()** starts the process
2. **spring.factories** loaded from all JARs (150+ auto-configurations)
3. **Component scanning** uses ASM to read `_blog.blog/**/*.class` files
4. **Bean definitions** registered (but not instantiated yet)
5. **Auto-configurations** filtered by `@Conditional` annotations
6. **Dependency graph** built to determine bean creation order
7. **Beans instantiated** using reflection (constructors called)
8. **Proxies created** by `BeanPostProcessor`s (for @Transactional, etc.)
9. **@PostConstruct** methods called
10. **Tomcat started** and DispatcherServlet registered

### How Runtime Works

- **@Value**: Resolved from Environment (command-line → system props → env vars → properties file)
- **@Transactional**: Proxy intercepts method calls and adds transaction management
- **Repositories**: Dynamic proxies created at runtime, parse method names to generate JPQL
- **Security filters**: Chain of filters intercept requests before reaching controllers
- **SecurityContext**: ThreadLocal storage ensures each request has isolated authentication

### Why Understanding Internals Matters

- **Debugging**: When things don't work, you know where to look
- **Performance**: Understanding proxy creation helps optimize bean design
- **Configuration**: Knowing auto-configuration lets you override smartly
- **Best practices**: Understanding internals explains WHY certain patterns exist (@Transactional on public methods, etc.)

---

## What's Next?

You now understand the **deep internals** of Spring Boot. Next documents will cover:
- Component scanning and classpath analysis in detail
- Bean lifecycle hooks and extension points
- Transaction management internals
- JPA/Hibernate session and caching mechanics

**Completed deep-dives**:
- ✅ Spring Boot runtime startup process
- ✅ Component scanning with ASM
- ✅ Bean instantiation and dependency resolution
- ✅ Proxy creation (CGLIB vs JDK)
- ✅ @Value resolution
- ✅ @Transactional internals
- ✅ Spring Data JPA proxy generation
- ✅ Security filter chain processing
- ✅ Complete request flow

Continue to `21-COMPONENT-SCANNING-INTERNALS.md` for even deeper analysis of classpath scanning mechanics.
