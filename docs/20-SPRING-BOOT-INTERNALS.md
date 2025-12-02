# Spring Boot Internals - Under the Hood
## From Application Startup to Request Handling

> **Target Audience**: Developers who understand Spring Boot basics and want to know **HOW** it works internally, not just **HOW TO USE** it.

> **What You'll Learn**: ApplicationContext lifecycle, bean creation process, auto-configuration magic, proxy generation, and the complete flow from `main()` to handling your first HTTP request.

---

## Table of Contents

1. [Overview: Spring Boot Architecture](#overview-spring-boot-architecture)
2. [Application Startup: The Complete Journey](#application-startup-the-complete-journey)
3. [ApplicationContext Deep Dive](#applicationcontext-deep-dive)
4. [Bean Lifecycle and Creation](#bean-lifecycle-and-creation)
5. [Dependency Injection Mechanisms](#dependency-injection-mechanisms)
6. [Component Scanning Process](#component-scanning-process)
7. [Auto-Configuration Magic](#auto-configuration-magic)
8. [Proxy Creation and AOP](#proxy-creation-and-aop)
9. [Request Handling Flow](#request-handling-flow)
10. [Performance Implications](#performance-implications)

---

## Overview: Spring Boot Architecture

### The Spring Container Hierarchy

```
┌─────────────────────────────────────────────────────────┐
│                    SpringApplication                     │
│                                                          │
│  ┌────────────────────────────────────────────────────┐│
│  │         AnnotationConfigApplicationContext          ││
│  │                                                      ││
│  │  ┌─────────────────────────────────────────────┐  ││
│  │  │     DefaultListableBeanFactory              │  ││
│  │  │                                              │  ││
│  │  │  - Bean Definitions Registry                │  ││
│  │  │  - Bean Post Processors                     │  ││
│  │  │  - Singleton Bean Cache                     │  ││
│  │  │  - Dependency Resolution                    │  ││
│  │  └─────────────────────────────────────────────┘  ││
│  │                                                      ││
│  │  Environment Properties                             ││
│  │  Resource Loaders                                   ││
│  │  Event Publishers                                   ││
│  └────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────┘
```

### Key Components:

**SpringApplication**: The entry point that bootstraps everything
**ApplicationContext**: The container that holds and manages all beans
**BeanFactory**: The core factory that creates and wires beans
**BeanDefinition**: Metadata about how to create a bean
**BeanPostProcessor**: Hooks to customize bean creation

---

## Application Startup: The Complete Journey

### The Main Method - Your Entry Point

```java
// backend/src/main/java/_blog/blog/BlogApplication.java
@SpringBootApplication
public class BlogApplication {
    public static void main(String[] args) {
        SpringApplication.run(BlogApplication.class, args);
    }
}
```

**What seems simple is actually 50+ steps internally. Let's break it down.**

---

### Step-by-Step Startup Sequence

#### Phase 1: SpringApplication Initialization

```java
// What happens when you call SpringApplication.run()
public static ConfigurableApplicationContext run(Class<?> primarySource, String... args) {
    return new SpringApplication(primarySource).run(args);
}
```

**Step 1.1**: Create `SpringApplication` instance
```java
public SpringApplication(Class<?>... primarySources) {
    this.primarySources = new LinkedHashSet<>(Arrays.asList(primarySources));

    // Step 1: Deduce web application type
    this.webApplicationType = WebApplicationType.deduceFromClasspath();
    // Checks classpath for:
    // - javax.servlet.Servlet → SERVLET (our case)
    // - org.springframework.web.reactive → REACTIVE
    // - neither → NONE

    // Step 2: Load ApplicationContextInitializers
    setInitializers(getSpringFactoriesInstances(ApplicationContextInitializer.class));
    // Reads from META-INF/spring.factories files across all JARs

    // Step 3: Load ApplicationListeners
    setListeners(getSpringFactoriesInstances(ApplicationListener.class));

    // Step 4: Deduce main application class
    this.mainApplicationClass = deduceMainApplicationClass();
    // Walks stack trace to find class with main() method
}
```

---

#### Phase 2: Running the Application

```java
public ConfigurableApplicationContext run(String... args) {
    // STEP 1: Create StopWatch for startup timing
    StopWatch stopWatch = new StopWatch();
    stopWatch.start();

    // STEP 2: Create Bootstrap Context (temporary lightweight context)
    DefaultBootstrapContext bootstrapContext = createBootstrapContext();
    ConfigurableApplicationContext context = null;

    // STEP 3: Configure headless property (for server environments)
    configureHeadlessProperty();

    // STEP 4: Get run listeners
    SpringApplicationRunListeners listeners = getRunListeners(args);
    // These listeners broadcast events during startup

    // STEP 5: Fire starting event
    listeners.starting(bootstrapContext, this.mainApplicationClass);

    try {
        // STEP 6: Parse command-line arguments
        ApplicationArguments applicationArguments = new DefaultApplicationArguments(args);

        // STEP 7: Prepare environment (loads application.properties/yaml)
        ConfigurableEnvironment environment = prepareEnvironment(listeners, bootstrapContext, applicationArguments);

        // STEP 8: Print banner (Spring Boot logo)
        Banner printedBanner = printBanner(environment);

        // STEP 9: CREATE APPLICATION CONTEXT 🎯 **CRITICAL STEP**
        context = createApplicationContext();

        // STEP 10: Prepare context (register bean definitions)
        prepareContext(bootstrapContext, context, environment, listeners, applicationArguments, printedBanner);

        // STEP 11: REFRESH CONTEXT 🎯 **MOST IMPORTANT STEP**
        refreshContext(context);

        // STEP 12: After refresh (post-processing)
        afterRefresh(context, applicationArguments);

        // STEP 13: Stop timing
        stopWatch.stop();

        // STEP 14: Log startup time
        if (this.logStartupInfo) {
            new StartupInfoLogger(this.mainApplicationClass).logStarted(getApplicationLog(), stopWatch);
        }

        // STEP 15: Fire started event
        listeners.started(context, Duration.ofMillis(stopWatch.getTotalTimeMillis()));

        // STEP 16: Call runners (CommandLineRunner, ApplicationRunner)
        callRunners(context, applicationArguments);
    }
    catch (Throwable ex) {
        handleRunFailure(context, ex, listeners);
        throw new IllegalStateException(ex);
    }

    // STEP 17: Fire running event
    listeners.running(context, Duration.ofMillis(stopWatch.getTotalTimeMillis()));

    return context;
}
```

---

### What Just Happened?

Let's visualize the startup timeline:

```
Time: 0ms
│
├─ [0-50ms] Initialize SpringApplication
│   ├─ Deduce web type (SERVLET)
│   ├─ Load spring.factories
│   └─ Find main class
│
├─ [50-100ms] Prepare Environment
│   ├─ Load application.properties
│   ├─ Resolve ${} placeholders
│   └─ Activate profiles
│
├─ [100-200ms] Create ApplicationContext
│   └─ Instantiate AnnotationConfigServletWebServerApplicationContext
│
├─ [200-800ms] Refresh Context ⏱️ **SLOWEST PART**
│   ├─ [200-300ms] Scan components (@ComponentScan)
│   ├─ [300-500ms] Register bean definitions
│   ├─ [500-700ms] Instantiate beans
│   └─ [700-800ms] Start embedded Tomcat
│
└─ [800ms] Application Started ✅
```

---

## ApplicationContext Deep Dive

### What IS ApplicationContext?

```java
ApplicationContext context = SpringApplication.run(BlogApplication.class, args);
```

This `context` object is **THE** container holding your entire application.

```
ApplicationContext
├─ Contains all your beans (@Service, @Repository, @Controller)
├─ Manages bean lifecycle (creation → init → destruction)
├─ Resolves dependencies
├─ Publishes events
├─ Manages resources (files, URLs)
└─ Handles internationalization (i18n)
```

---

### Creating ApplicationContext

```java
protected ConfigurableApplicationContext createApplicationContext() {
    // For web applications (our case):
    return new AnnotationConfigServletWebServerApplicationContext();

    // This context class provides:
    // 1. Annotation-based configuration (@Configuration, @Bean)
    // 2. Servlet web server (Tomcat)
    // 3. Bean factory for dependency injection
}
```

**AnnotationConfigServletWebServerApplicationContext** hierarchy:

```
GenericApplicationContext
 └─ AnnotationConfigApplicationContext
     └─ ServletWebServerApplicationContext
         └─ AnnotationConfigServletWebServerApplicationContext
```

Each layer adds functionality:
- **GenericApplicationContext**: Core context with BeanFactory
- **AnnotationConfigApplicationContext**: Annotation scanning
- **ServletWebServerApplicationContext**: Embedded web server
- **AnnotationConfigServletWebServerApplicationContext**: Combines all

---

### The `refresh()` Method - Where the Magic Happens

This is **THE** most important method in Spring. Everything happens here.

```java
@Override
public void refresh() throws BeansException, IllegalStateException {
    synchronized (this.startupShutdownMonitor) {
        // PHASE 1: Prepare for refresh
        prepareRefresh();

        // PHASE 2: Get the bean factory
        ConfigurableListableBeanFactory beanFactory = obtainFreshBeanFactory();

        // PHASE 3: Prepare the bean factory
        prepareBeanFactory(beanFactory);

        try {
            // PHASE 4: Post-process bean factory (subclass hook)
            postProcessBeanFactory(beanFactory);

            // PHASE 5: Invoke bean factory post-processors 🎯
            invokeBeanFactoryPostProcessors(beanFactory);

            // PHASE 6: Register bean post-processors 🎯
            registerBeanPostProcessors(beanFactory);

            // PHASE 7: Initialize message source (i18n)
            initMessageSource();

            // PHASE 8: Initialize event multicaster
            initApplicationEventMulticaster();

            // PHASE 9: Refresh specific beans (embedded Tomcat starts here!) 🚀
            onRefresh();

            // PHASE 10: Register listeners
            registerListeners();

            // PHASE 11: Instantiate all remaining singletons 🎯 **CRITICAL**
            finishBeanFactoryInitialization(beanFactory);

            // PHASE 12: Publish context refreshed event
            finishRefresh();
        }
        catch (BeansException ex) {
            // Destroy already created singletons
            destroyBeans();
            throw ex;
        }
        finally {
            // Reset caches
            resetCommonCaches();
        }
    }
}
```

---

### Phase 5: Bean Factory Post-Processors

**What are BeanFactoryPostProcessors?**

They modify **bean definitions** BEFORE beans are created.

```java
@FunctionalInterface
public interface BeanFactoryPostProcessor {
    void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException;
}
```

**Most important one: ConfigurationClassPostProcessor**

This processor:
1. Finds all `@Configuration` classes
2. Finds all `@Bean` methods
3. Registers them as bean definitions

```java
// Example: BlogApplication.java
@SpringBootApplication  // Contains @Configuration
public class BlogApplication {
    public static void main(String[] args) {
        SpringApplication.run(BlogApplication.class, args);
    }
}

// What ConfigurationClassPostProcessor does:
// 1. Finds @SpringBootApplication annotation
// 2. Sees it contains @ComponentScan
// 3. Scans package: _blog.blog
// 4. Finds all @Component, @Service, @Repository, @Controller classes
// 5. Registers each as a BeanDefinition
```

---

### Phase 6: Bean Post-Processors

**What are BeanPostProcessors?**

They modify **actual bean instances** AFTER creation.

```java
public interface BeanPostProcessor {
    // Called BEFORE @PostConstruct
    default Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    // Called AFTER @PostConstruct
    default Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }
}
```

**Key Bean Post-Processors:**

1. **AutowiredAnnotationBeanPostProcessor**
   - Injects `@Autowired` dependencies
   - Handles constructor, setter, and field injection

2. **CommonAnnotationBeanPostProcessor**
   - Handles `@PostConstruct` and `@PreDestroy`
   - Handles `@Resource` injection

3. **ApplicationContextAwareProcessor**
   - Injects ApplicationContext into beans implementing `ApplicationContextAware`

4. **AbstractAutoProxyCreator** (for AOP)
   - Creates proxies for `@Transactional`, `@Async`, etc.
   - Wraps beans with interceptors

---

### Phase 11: Instantiate Beans

```java
protected void finishBeanFactoryInitialization(ConfigurableListableBeanFactory beanFactory) {
    // Initialize conversion service
    // ...

    // Stop using temp ClassLoader
    beanFactory.setTempClassLoader(null);

    // Freeze all bean definitions (no more changes allowed)
    beanFactory.freezeConfiguration();

    // 🎯 INSTANTIATE ALL SINGLETONS
    beanFactory.preInstantiateSingletons();
}
```

**preInstantiateSingletons() - The Money Method:**

```java
@Override
public void preInstantiateSingletons() throws BeansException {
    // Get all bean names
    List<String> beanNames = new ArrayList<>(this.beanDefinitionNames);

    // Instantiate each bean
    for (String beanName : beanNames) {
        RootBeanDefinition bd = getMergedLocalBeanDefinition(beanName);

        // Only singletons, not lazy, not abstract
        if (!bd.isAbstract() && bd.isSingleton() && !bd.isLazyInit()) {
            if (isFactoryBean(beanName)) {
                // Handle FactoryBeans
                Object bean = getBean(FACTORY_BEAN_PREFIX + beanName);
                // ...
            } else {
                // 🎯 CREATE THE BEAN
                getBean(beanName);
            }
        }
    }

    // Trigger post-initialization callbacks
    for (String beanName : beanNames) {
        Object singletonInstance = getSingleton(beanName);
        if (singletonInstance instanceof SmartInitializingSingleton) {
            SmartInitializingSingleton smartSingleton = (SmartInitializingSingleton) singletonInstance;
            smartSingleton.afterSingletonsInstantiated();
        }
    }
}
```

---

## Bean Lifecycle and Creation

### The Complete Bean Creation Process

When you call `getBean("userService")`, here's what happens:

```
getBean("userService")
  │
  ├─ Check singleton cache (already created?)
  │  └─ If found: return cached instance ✅
  │
  ├─ Not found: Create new bean
  │  │
  │  ├─ STEP 1: Get bean definition
  │  │   └─ BeanDefinition contains: class, scope, dependencies, etc.
  │  │
  │  ├─ STEP 2: Resolve dependencies
  │  │   ├─ Find @Autowired fields/constructors
  │  │   ├─ Recursively getBean() for each dependency
  │  │   └─ Detect circular dependencies
  │  │
  │  ├─ STEP 3: Create instance
  │  │   ├─ Choose constructor (no-args or @Autowired constructor)
  │  │   ├─ Instantiate using reflection: clazz.newInstance()
  │  │   └─ Bean is now in "early reference" state
  │  │
  │  ├─ STEP 4: Populate properties
  │  │   ├─ Inject @Autowired fields
  │  │   ├─ Inject @Value fields
  │  │   └─ Call setter methods
  │  │
  │  ├─ STEP 5: Initialize bean
  │  │   ├─ Call BeanPostProcessor.postProcessBeforeInitialization()
  │  │   │   └─ @PostConstruct methods called here!
  │  │   ├─ Call InitializingBean.afterPropertiesSet() (if implemented)
  │  │   ├─ Call custom init-method (if defined)
  │  │   └─ Call BeanPostProcessor.postProcessAfterInitialization()
  │  │       └─ AOP proxies created here!
  │  │
  │  ├─ STEP 6: Register destruction callbacks
  │  │   └─ @PreDestroy methods registered
  │  │
  │  └─ STEP 7: Return fully initialized bean
  │      └─ Cache in singleton registry
  │
  └─ Return bean instance ✅
```

---

### Real Example: Creating UserService

```java
// backend/src/main/java/_blog/blog/service/impl/UserServiceImpl.java
@Service
@Transactional
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public UserServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostConstruct
    public void init() {
        System.out.println("UserServiceImpl initialized!");
    }

    @PreDestroy
    public void cleanup() {
        System.out.println("UserServiceImpl destroyed!");
    }
}
```

**Let's trace the creation:**

```
1. Spring sees @Service annotation
2. Registers bean definition: beanName="userServiceImpl"
3. During refresh(), calls getBean("userServiceImpl")

4. Resolves dependencies:
   ├─ getBean("userRepository")
   │  ├─ Check cache → not found
   │  ├─ Create UserRepository (JPA proxy)
   │  └─ Cache it
   │
   └─ getBean("passwordEncoder")
      ├─ Check cache → not found
      ├─ Create BCryptPasswordEncoder
      └─ Cache it

5. Create UserServiceImpl instance:
   ├─ Find constructor with @Autowired
   ├─ Call: new UserServiceImpl(userRepository, passwordEncoder)
   └─ Instance created ✅

6. Call BeanPostProcessors (before):
   └─ CommonAnnotationBeanPostProcessor calls init() (@PostConstruct)
   → Output: "UserServiceImpl initialized!"

7. Call BeanPostProcessors (after):
   └─ AbstractAutoProxyCreator creates CGLIB proxy
   └─ Wraps UserServiceImpl with transaction interceptor

8. Return proxy instance (not actual UserServiceImpl!)
   └─ All future calls go through proxy first

9. Cache in singleton map:
   singletonObjects.put("userServiceImpl", proxyInstance)

10. Return to caller ✅
```

---

### Circular Dependency Resolution

**Problem:**
```java
@Service
public class ServiceA {
    @Autowired
    private ServiceB serviceB;  // ServiceA needs ServiceB
}

@Service
public class ServiceB {
    @Autowired
    private ServiceA serviceA;  // ServiceB needs ServiceA 😱
}
```

**How Spring Solves It:**

Spring uses **three-level cache**:

```java
// Level 1: Fully initialized singletons
private final Map<String, Object> singletonObjects = new ConcurrentHashMap<>();

// Level 2: Early singleton references (for circular dependency)
private final Map<String, Object> earlySingletonObjects = new HashMap<>();

// Level 3: Singleton factories (can create early reference)
private final Map<String, ObjectFactory<?>> singletonFactories = new HashMap<>();
```

**Resolution Flow:**

```
Create ServiceA:
1. Instantiate ServiceA (constructor called, object created)
2. Put in singletonFactories (level 3) 🔑 **KEY STEP**
3. Try to populate properties
4. Need ServiceB → call getBean("serviceB")

Create ServiceB:
5. Instantiate ServiceB
6. Put in singletonFactories (level 3)
7. Try to populate properties
8. Need ServiceA → call getBean("serviceA")

Resolve Circular Dependency:
9. getBean("serviceA") → check level 1 cache → not found
10. Check level 2 cache → not found
11. Check level 3 cache → FOUND! 🎉
12. Call singletonFactory.getObject() → returns early ServiceA
13. Move to level 2 cache (earlySingletonObjects)
14. Inject early ServiceA into ServiceB
15. Finish creating ServiceB
16. Move ServiceB to level 1 cache
17. Inject ServiceB into ServiceA
18. Finish creating ServiceA
19. Move ServiceA to level 1 cache
20. ✅ Both beans fully initialized!
```

**Important**: This only works for **setter or field injection**, NOT constructor injection!

```java
// ❌ FAILS - Can't resolve circular dependency
@Service
public class ServiceA {
    @Autowired
    public ServiceA(ServiceB serviceB) { }  // Constructor injection
}

@Service
public class ServiceB {
    @Autowired
    public ServiceB(ServiceA serviceA) { }  // Constructor injection
}

// ✅ WORKS - Can resolve
@Service
public class ServiceA {
    @Autowired
    private ServiceB serviceB;  // Field injection
}

@Service
public class ServiceB {
    @Autowired
    private ServiceA serviceA;  // Field injection
}
```

---

## Dependency Injection Mechanisms

### Three Ways to Inject Dependencies

#### 1. Constructor Injection (Recommended ✅)

```java
@Service
public class PostService {
    private final PostRepository postRepository;
    private final UserService userService;

    @Autowired  // Optional in Spring 4.3+ if only one constructor
    public PostService(PostRepository postRepository, UserService userService) {
        this.postRepository = postRepository;
        this.userService = userService;
    }
}
```

**How it works internally:**

```java
// Spring's bean creation code:
Class<?> clazz = Class.forName("_blog.blog.service.PostService");
Constructor<?> constructor = clazz.getDeclaredConstructor(PostRepository.class, UserService.class);

// Resolve constructor parameters
Object postRepository = getBean("postRepository");
Object userService = getBean("userService");

// Create instance
Object postService = constructor.newInstance(postRepository, userService);
```

**Advantages:**
- Immutable (fields can be `final`)
- Required dependencies are explicit
- Easy to test (just call constructor)
- No reflection needed after construction

---

#### 2. Setter Injection

```java
@Service
public class PostService {
    private PostRepository postRepository;

    @Autowired
    public void setPostRepository(PostRepository postRepository) {
        this.postRepository = postRepository;
    }
}
```

**How it works:**

```java
// Spring's bean creation:
PostService postService = new PostService();  // No-args constructor
Method setter = clazz.getMethod("setPostRepository", PostRepository.class);
setter.invoke(postService, getBean("postRepository"));
```

**Advantages:**
- Optional dependencies
- Can reconfigure at runtime

**Disadvantages:**
- Mutable
- Bean can be in invalid state

---

#### 3. Field Injection (Convenient but not recommended)

```java
@Service
public class PostService {
    @Autowired
    private PostRepository postRepository;
}
```

**How it works:**

```java
// Spring uses reflection:
PostService postService = new PostService();
Field field = clazz.getDeclaredField("postRepository");
field.setAccessible(true);  // Break encapsulation!
field.set(postService, getBean("postRepository"));
```

**Disadvantages:**
- Can't use `final`
- Harder to test (need reflection or Spring context)
- Breaks encapsulation
- Hides dependencies

---

### Injection by Type vs by Name

**By Type (default):**
```java
@Autowired
private PostRepository postRepository;  // Inject bean of type PostRepository
```

**By Name:**
```java
@Autowired
@Qualifier("postRepositoryImpl")
private PostRepository postRepository;  // Inject bean named "postRepositoryImpl"
```

**Resolution Algorithm:**

```
1. Find all beans of required type
   ├─ If 0 found → throw NoSuchBeanDefinitionException
   ├─ If 1 found → inject it ✅
   └─ If 2+ found → go to step 2

2. Look for @Qualifier annotation
   ├─ If present → inject bean with matching name ✅
   └─ If not present → go to step 3

3. Look for @Primary annotation
   ├─ If found → inject primary bean ✅
   └─ If not found → go to step 4

4. Match by field/parameter name
   ├─ If matches → inject ✅
   └─ If no match → throw NoUniqueBeanDefinitionException
```

---

## Component Scanning Process

### How @ComponentScan Works

```java
@SpringBootApplication  // Contains @ComponentScan
public class BlogApplication { }
```

**@SpringBootApplication expands to:**
```java
@Configuration
@EnableAutoConfiguration
@ComponentScan(basePackages = "_blog.blog")  // Scans this package
public class BlogApplication { }
```

---

### Scanning Process:

```
1. Find all .class files in package "_blog.blog"
   ├─ Blog Application.class
   ├─ entity/User.class
   ├─ entity/Post.class
   ├─ repository/UserRepository.class
   ├─ service/impl/UserServiceImpl.class
   ├─ controller/UserController.class
   └─ ... (all classes)

2. For each class, check annotations:
   ├─ @Component → register as bean
   ├─ @Service → register as bean (specialization of @Component)
   ├─ @Repository → register as bean + add exception translation
   ├─ @Controller → register as bean + add request mapping
   └─ @Configuration → register as bean + process @Bean methods

3. Create BeanDefinition for each:
   BeanDefinition {
       beanClass: UserServiceImpl.class
       scope: singleton
       lazyInit: false
       autowireMode: constructor
       dependencyCheck: none
   }

4. Register in BeanFactory:
   beanFactory.registerBeanDefinition("userServiceImpl", beanDefinition);
```

---

### ClassPathBeanDefinitionScanner

The actual scanner implementation:

```java
public class ClassPathBeanDefinitionScanner {
    public int scan(String... basePackages) {
        int beanCountAtScanStart = this.registry.getBeanDefinitionCount();

        // Scan each package
        doScan(basePackages);

        // Register annotation config processors
        if (this.includeAnnotationConfig) {
            AnnotationConfigUtils.registerAnnotationConfigProcessors(this.registry);
        }

        return (this.registry.getBeanDefinitionCount() - beanCountAtScanStart);
    }

    protected Set<BeanDefinitionHolder> doScan(String... basePackages) {
        Set<BeanDefinitionHolder> beanDefinitions = new LinkedHashSet<>();

        for (String basePackage : basePackages) {
            // Find candidate components
            Set<BeanDefinition> candidates = findCandidateComponents(basePackage);

            for (BeanDefinition candidate : candidates) {
                // Resolve scope
                ScopeMetadata scopeMetadata = this.scopeMetadataResolver.resolveScopeMetadata(candidate);
                candidate.setScope(scopeMetadata.getScopeName());

                // Generate bean name
                String beanName = this.beanNameGenerator.generateBeanName(candidate, this.registry);

                // Register bean definition
                registerBeanDefinition(definitionHolder, this.registry);
                beanDefinitions.add(definitionHolder);
            }
        }

        return beanDefinitions;
    }
}
```

---

## Auto-Configuration Magic

### The @SpringBootApplication Annotation

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@SpringBootConfiguration  // Same as @Configuration
@EnableAutoConfiguration  // 🎯 **THE MAGIC**
@ComponentScan(
    excludeFilters = {
        @Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
        @Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class)
    }
)
public @interface SpringBootApplication { }
```

---

### @EnableAutoConfiguration Breakdown

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@AutoConfigurationPackage
@Import(AutoConfigurationImportSelector.class)  // 🎯 **IMPORTS ALL AUTO-CONFIG**
public @interface EnableAutoConfiguration { }
```

---

### Auto-Configuration Import Selector

**This class is responsible for loading ALL auto-configuration classes:**

```java
public class AutoConfigurationImportSelector implements DeferredImportSelector {

    @Override
    public String[] selectImports(AnnotationMetadata annotationMetadata) {
        if (!isEnabled(annotationMetadata)) {
            return NO_IMPORTS;
        }

        // Load auto-configurations
        AutoConfigurationEntry autoConfigurationEntry = getAutoConfigurationEntry(annotationMetadata);
        return StringUtils.toStringArray(autoConfigurationEntry.getConfigurations());
    }

    protected AutoConfigurationEntry getAutoConfigurationEntry(AnnotationMetadata annotationMetadata) {
        // 🎯 LOAD FROM META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
        List<String> configurations = getCandidateConfigurations(annotationMetadata, attributes);

        // Remove duplicates
        configurations = removeDuplicates(configurations);

        // Apply exclusions
        Set<String> exclusions = getExclusions(annotationMetadata, attributes);
        configurations.removeAll(exclusions);

        // Filter (check @Conditional annotations)
        configurations = getConfigurationClassFilter().filter(configurations);

        // Fire event
        fireAutoConfigurationImportEvents(configurations, exclusions);

        return new AutoConfigurationEntry(configurations, exclusions);
    }
}
```

---

### Where Auto-Configurations Come From

**Spring Boot JARs contain this file:**

```
spring-boot-autoconfigure-3.x.x.jar
└── META-INF
    └── spring
        └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

**Content (excerpt):**
```
org.springframework.boot.autoconfigure.admin.SpringApplicationAdminJmxAutoConfiguration
org.springframework.boot.autoconfigure.aop.AopAutoConfiguration
org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration
org.springframework.boot.autoconfigure.data.jdbc.JdbcRepositoriesAutoConfiguration
org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration
org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration
org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration
... (100+ more)
```

**Spring Boot loads ALL of these**, then uses `@Conditional` annotations to decide which to activate.

---

### Conditional Auto-Configuration Example

Let's look at `DataSourceAutoConfiguration`:

```java
@AutoConfiguration(before = SqlInitializationAutoConfiguration.class)
@ConditionalOnClass({ DataSource.class, EmbeddedDatabaseType.class })
@ConditionalOnMissingBean(type = "io.r2dbc.spi.ConnectionFactory")
@EnableConfigurationProperties(DataSourceProperties.class)
@Import(DataSourcePoolMetadataProvidersConfiguration.class)
public class DataSourceAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @Conditional(EmbeddedDatabaseCondition.class)
    @ConditionalOnMissingBean({ DataSource.class, XADataSource.class })
    @Import(EmbeddedDataSourceConfiguration.class)
    protected static class EmbeddedDatabaseConfiguration { }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(HikariDataSource.class)
    @ConditionalOnMissingBean(DataSource.class)
    @ConditionalOnProperty(name = "spring.datasource.type", havingValue = "com.zaxxer.hikari.HikariDataSource", matchIfMissing = true)
    static class Hikari {
        @Bean
        @ConfigurationProperties(prefix = "spring.datasource.hikari")
        HikariDataSource dataSource(DataSourceProperties properties) {
            HikariDataSource dataSource = createDataSource(properties, HikariDataSource.class);
            if (StringUtils.hasText(properties.getName())) {
                dataSource.setPoolName(properties.getName());
            }
            return dataSource;
        }
    }
}
```

**Translation:**

```
IF:
  ✅ DataSource.class exists on classpath (you have JDBC)
  ✅ EmbeddedDatabaseType.class exists
  ✅ No R2DBC ConnectionFactory (not using reactive)

THEN:
  Create DataSource bean

  HOW?
  IF:
    ✅ HikariDataSource.class exists
    ✅ No DataSource bean already defined
    ✅ spring.datasource.type not explicitly set
  THEN:
    Create HikariDataSource
    Configure with spring.datasource.* properties
```

---

### Common @Conditional Annotations

| Annotation | Condition |
|-----------|-----------|
| `@ConditionalOnClass` | Class must be on classpath |
| `@ConditionalOnMissingClass` | Class must NOT be on classpath |
| `@ConditionalOnBean` | Bean must exist in context |
| `@ConditionalOnMissingBean` | Bean must NOT exist |
| `@ConditionalOnProperty` | Property must have value |
| `@ConditionalOnResource` | Resource must exist |
| `@ConditionalOnWebApplication` | Must be web application |
| `@ConditionalOnExpression` | SpEL expression must be true |

---

### Example: Why PostgreSQL Driver is Required

```java
// application.properties
spring.datasource.url=jdbc:postgresql://localhost:5432/blog
spring.datasource.driver-class-name=org.postgresql.Driver
```

**Without PostgreSQL JAR:**
```
@ConditionalOnClass(org.postgresql.Driver.class)  // ❌ NOT FOUND
→ DataSource auto-configuration skips
→ Application fails to start
```

**With PostgreSQL JAR:**
```
@ConditionalOnClass(org.postgresql.Driver.class)  // ✅ FOUND
→ DataSource auto-configuration activates
→ Creates HikariDataSource
→ Registers DataSource bean
→ JPA uses it
```

---

## Proxy Creation and AOP

### What Are Proxies?

**A proxy is a wrapper around your bean that intercepts method calls.**

```
Without Proxy:
  Controller → UserService.deleteUser() → [Execute method]

With Proxy:
  Controller → PROXY → [Start transaction] → UserService.deleteUser() → [Commit transaction]
```

---

### When Are Proxies Created?

Proxies are created by **BeanPostProcessors** during bean initialization:

```java
@Override
public Object postProcessAfterInitialization(Object bean, String beanName) {
    if (bean instanceof Advised) {
        return bean;  // Already proxied
    }

    // Check if bean needs proxy
    if (shouldProxy(bean, beanName)) {
        // Create proxy
        return createProxy(bean, beanName);
    }

    return bean;
}
```

---

### Types of Proxies

#### 1. JDK Dynamic Proxy (Interface-based)

**Used when bean implements an interface:**

```java
public interface UserService {
    void deleteUser(Long id);
}

@Service
public class UserServiceImpl implements UserService {
    public void deleteUser(Long id) {
        // implementation
    }
}
```

**Spring creates JDK proxy:**

```java
Object proxy = Proxy.newProxyInstance(
    classLoader,
    new Class<?>[] { UserService.class },  // Interface
    invocationHandler  // Intercepts calls
);
```

**Result:**
```
Proxy class: $Proxy123 implements UserService
```

---

#### 2. CGLIB Proxy (Subclass-based)

**Used when bean does NOT implement interface:**

```java
@Service
public class NotificationService {  // No interface
    public void sendNotification(String message) {
        // implementation
    }
}
```

**Spring creates CGLIB proxy by subclassing:**

```java
Enhancer enhancer = new Enhancer();
enhancer.setSuperclass(NotificationService.class);
enhancer.setCallback(methodInterceptor);
Object proxy = enhancer.create();
```

**Result:**
```
Proxy class: NotificationService$$EnhancerBySpringCGLIB$$12345678
```

---

### @Transactional Proxy Example

```java
@Service
public class UserServiceImpl implements UserService {

    @Transactional
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }
}
```

**Spring creates proxy:**

```java
class UserServiceProxy implements UserService {
    private UserService target;  // Real UserServiceImpl
    private PlatformTransactionManager txManager;

    public void deleteUser(Long id) {
        TransactionStatus status = null;
        try {
            // 1. Start transaction
            status = txManager.getTransaction(new DefaultTransactionDefinition());

            // 2. Call real method
            target.deleteUser(id);

            // 3. Commit transaction
            txManager.commit(status);
        } catch (RuntimeException ex) {
            // 4. Rollback on exception
            if (status != null) {
                txManager.rollback(status);
            }
            throw ex;
        }
    }
}
```

**This is why `@Transactional` works - it's not magic, it's a proxy!**

---

### Important: Internal Calls Don't Use Proxy

```java
@Service
public class PostService {

    @Transactional
    public void createPost(Post post) {
        postRepository.save(post);
        updateStatistics();  // ❌ CALLS INTERNAL METHOD
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void updateStatistics() {
        // This method is called directly, not through proxy
        // So @Transactional annotation is IGNORED!
    }
}
```

**Why?**
```
External call: Controller → PROXY → createPost()  ✅ Proxy intercepts
Internal call: createPost() → updateStatistics()  ❌ Direct call, no proxy
```

**Solution:**
```java
// Inject self-reference
@Service
public class PostService {
    @Autowired
    private PostService self;  // This is the proxy!

    @Transactional
    public void createPost(Post post) {
        postRepository.save(post);
        self.updateStatistics();  // ✅ Calls through proxy
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateStatistics() {
        // Now @Transactional works!
    }
}
```

---

## Request Handling Flow

### From HTTP Request to Your Controller

```
1. HTTP Request arrives at Tomcat
   │
   ├─ Tomcat receives: GET /posts/all
   │  Protocol: HTTP/1.1
   │  Headers: Authorization, Content-Type, etc.
   │
2. Request enters Filter Chain
   │
   ├─ Filter 1: CharacterEncodingFilter (sets UTF-8)
   ├─ Filter 2: CorsFilter (handles CORS)
   ├─ Filter 3: JwtAuthenticationFilter (extracts JWT) 🎯
   │  ├─ Extract JWT from cookie
   │  ├─ Validate token
   │  ├─ Load user details
   │  └─ Set SecurityContext
   ├─ Filter 4: FilterSecurityInterceptor (authorization)
   │
3. Request reaches DispatcherServlet
   │
   ├─ STEP 1: Find handler (controller method)
   │  ├─ HandlerMapping searches for @RequestMapping
   │  ├─ Finds: PostController.getAllPosts()
   │  └─ Returns HandlerMethod
   │
   ├─ STEP 2: Find handler adapter
   │  └─ RequestMappingHandlerAdapter
   │
   ├─ STEP 3: Execute interceptors (pre-handle)
   │
   ├─ STEP 4: Invoke controller method
   │  ├─ Resolve parameters:
   │  │  ├─ @RequestBody → Parse JSON to DTO
   │  │  ├─ @PathVariable → Extract from URL
   │  │  ├─ Authentication → Get from SecurityContext
   │  │  └─ @RequestParam → Get query parameters
   │  │
   │  ├─ Validate parameters (@Valid)
   │  │
   │  └─ Call method: postController.getAllPosts()
   │     │
   │     └─ Your code executes here! 🎯
   │
   ├─ STEP 5: Process return value
   │  ├─ @ResponseBody detected
   │  ├─ Convert to JSON (Jackson)
   │  └─ Set Content-Type: application/json
   │
   ├─ STEP 6: Execute interceptors (post-handle)
   │
   └─ STEP 7: Render response
      └─ Write JSON to HTTP response
```

---

### DispatcherServlet Internals

```java
@Override
protected void doDispatch(HttpServletRequest request, HttpServletResponse response) throws Exception {
    HttpServletRequest processedRequest = request;
    HandlerExecutionChain mappedHandler = null;

    try {
        ModelAndView mv = null;
        Exception dispatchException = null;

        try {
            // Check for multipart request
            processedRequest = checkMultipart(request);

            // STEP 1: Determine handler for current request
            mappedHandler = getHandler(processedRequest);
            if (mappedHandler == null) {
                noHandlerFound(processedRequest, response);
                return;
            }

            // STEP 2: Determine handler adapter
            HandlerAdapter ha = getHandlerAdapter(mappedHandler.getHandler());

            // STEP 3: Process last-modified header
            String method = request.getMethod();
            boolean isGet = "GET".equals(method);
            if (isGet || "HEAD".equals(method)) {
                long lastModified = ha.getLastModified(request, mappedHandler.getHandler());
                if (new ServletWebRequest(request, response).checkNotModified(lastModified) && isGet) {
                    return;
                }
            }

            // STEP 4: Apply preHandle methods (interceptors)
            if (!mappedHandler.applyPreHandle(processedRequest, response)) {
                return;
            }

            // STEP 5: Actually invoke the handler 🎯 **YOUR CONTROLLER METHOD**
            mv = ha.handle(processedRequest, response, mappedHandler.getHandler());

            // STEP 6: Apply postHandle methods (interceptors)
            mappedHandler.applyPostHandle(processedRequest, response, mv);
        }
        catch (Exception ex) {
            dispatchException = ex;
        }

        // STEP 7: Process dispatch result (render view or handle exception)
        processDispatchResult(processedRequest, response, mappedHandler, mv, dispatchException);
    }
    finally {
        // Clean up
    }
}
```

---

### Handler Method Invocation

```java
// RequestMappingHandlerAdapter.invokeHandlerMethod()

protected ModelAndView invokeHandlerMethod(HttpServletRequest request,
                                           HttpServletResponse response,
                                           HandlerMethod handlerMethod) throws Exception {

    ServletWebRequest webRequest = new ServletWebRequest(request, response);

    try {
        // Create invocable method
        ServletInvocableHandlerMethod invocableMethod = createInvocableHandlerMethod(handlerMethod);

        // Set argument resolvers (for @RequestBody, @PathVariable, etc.)
        invocableMethod.setHandlerMethodArgumentResolvers(this.argumentResolvers);

        // Set return value handlers (for @ResponseBody, etc.)
        invocableMethod.setHandlerMethodReturnValueHandlers(this.returnValueHandlers);

        // Invoke the method 🎯
        invocableMethod.invokeAndHandle(webRequest, mavContainer);

        return getModelAndView(mavContainer, modelFactory, webRequest);
    }
    finally {
        webRequest.requestCompleted();
    }
}
```

---

## Performance Implications

### Startup Time Optimization

**Problem:** Application takes 10 seconds to start.

**Analysis:**

```bash
# Enable startup logging
java -jar app.jar --debug

# Look for slow beans:
# Bean 'dataSource' created in 2341ms
# Bean 'entityManagerFactory' created in 3122ms
# Bean 'jwtService' created in 52ms
```

**Optimizations:**

1. **Lazy Initialization**
```java
@Configuration
@Lazy  // Don't create beans until needed
public class ExpensiveConfig {
    @Bean
    public ExpensiveBean expensiveBean() {
        return new ExpensiveBean();
    }
}
```

2. **Exclude Unnecessary Auto-Configurations**
```java
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class
})
```

3. **Use Profiles**
```java
@Profile("production")  // Only create in production
@Configuration
public class ProductionConfig { }
```

---

### Runtime Performance

#### Bean Lookup Performance

```java
// ❌ SLOW - lookup every time
public void process() {
    UserService service = context.getBean(UserService.class);
    service.doSomething();
}

// ✅ FAST - inject once
@Autowired
private UserService service;

public void process() {
    service.doSomething();
}
```

---

#### Proxy Overhead

**Proxies add ~1-5% overhead per method call.**

```java
// No proxy: ~0.001ms per call
public void simpleMethod() {
    // direct call
}

// With proxy: ~0.001ms + ~0.00005ms per call
@Transactional
public void proxiedMethod() {
    // proxy intercepts, starts TX, calls method, commits TX
}
```

**For 1 million calls:**
- No proxy: 1 second
- With proxy: 1.05 seconds

**Worth it for transactions? YES!**

---

### Memory Usage

**Each singleton bean:**
- Bean instance: varies
- BeanDefinition: ~1KB
- Proxy (if created): ~2-5KB

**For 100 beans:**
- ~100KB for definitions
- ~200-500KB for proxies
- Variable for instances

**ApplicationContext overhead: ~10-50MB**

---

## Summary: Key Takeaways

### 🎯 Critical Concepts:

1. **ApplicationContext is THE container**
   - Holds all beans
   - Manages lifecycle
   - Resolves dependencies

2. **Bean creation is multi-phase**
   - Instantiation → Population → Initialization → Ready
   - BeanPostProcessors customize each phase

3. **Auto-configuration uses `@Conditional`**
   - Loads 100+ configurations
   - Activates based on classpath and properties
   - You can override any

4. **Proxies enable AOP**
   - Created by BeanPostProcessors
   - JDK (interface) or CGLIB (subclass)
   - Internal calls skip proxy!

5. **Request handling has many layers**
   - Filters → DispatcherServlet → HandlerMapping → Controller
   - Each layer can intercept

---

### 🔧 Debugging Tips:

**See all beans:**
```java
@Autowired
private ApplicationContext context;

public void printBeans() {
    String[] beans = context.getBeanDefinitionNames();
    Arrays.stream(beans).sorted().forEach(System.out::println);
}
```

**See bean creation order:**
```
# application.properties
logging.level.org.springframework.beans.factory.support.DefaultListableBeanFactory=DEBUG
```

**See auto-configuration report:**
```
# application.properties
debug=true
```

**See which proxies were created:**
```
logging.level.org.springframework.aop=DEBUG
```

---

**Next:** [21-HIBERNATE-INTERNALS-AND-OPTIMIZATION.md](./21-HIBERNATE-INTERNALS-AND-OPTIMIZATION.md)

---

## Interview Questions You Can Now Answer:

1. ✅ Explain how Spring Boot's `main()` method starts an application
2. ✅ What is the difference between BeanFactory and ApplicationContext?
3. ✅ How does Spring resolve circular dependencies?
4. ✅ Explain the complete bean lifecycle
5. ✅ How does `@ComponentScan` work internally?
6. ✅ What is the difference between JDK and CGLIB proxies?
7. ✅ Why doesn't `@Transactional` work on private methods?
8. ✅ How does Spring Boot auto-configuration work?
9. ✅ What happens when a request reaches your application?
10. ✅ How can you optimize Spring Boot startup time?

---

**You now understand Spring Boot at a senior developer level!** 🚀
