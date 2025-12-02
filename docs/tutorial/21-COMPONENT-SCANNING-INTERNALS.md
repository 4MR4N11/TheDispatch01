# Component Scanning Internals: How Spring Finds Your Beans

## Overview

This document explains **exactly how** Spring Boot scans your classpath and discovers components. We'll trace through:
- Classpath resource pattern matching
- ASM bytecode reading (without loading classes)
- Annotation detection and filtering
- BeanDefinition creation from metadata

**What you'll learn**: The low-level algorithms Spring uses to find your `@Component`, `@Service`, `@Repository`, and `@Controller` classes.

---

## 1. The @ComponentScan Trigger

When Spring processes your `BlogApplication` class:

```java
@SpringBootApplication  // Contains @ComponentScan
public class BlogApplication {
    public static void main(String[] args) {
        SpringApplication.run(BlogApplication.class, args);
    }
}
```

The `@SpringBootApplication` annotation expands to:

```java
@ComponentScan(
    excludeFilters = {
        @Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
        @Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class)
    }
)
```

**Default behavior**: No `basePackages` specified, so Spring uses the package of the annotated class:
```
BlogApplication is in: _blog.blog
Scan starts from: _blog.blog
Includes all sub-packages: _blog.blog.controller, _blog.blog.service, etc.
```

---

## 2. Component Scanning Algorithm

### High-Level Flow

```java
// Simplified Spring source code
public class ClassPathBeanDefinitionScanner {

    public Set<BeanDefinition> scan(String... basePackages) {
        Set<BeanDefinition> beanDefinitions = new LinkedHashSet<>();

        for (String basePackage : basePackages) {
            // 1. Find all candidate components
            Set<BeanDefinition> candidates = findCandidateComponents(basePackage);

            // 2. For each candidate, create bean definition
            for (BeanDefinition candidate : candidates) {
                // Process scope, lazy-init, etc.
                processBeanDefinition(candidate);
                beanDefinitions.add(candidate);
            }
        }

        return beanDefinitions;
    }
}
```

### Detailed Step-by-Step

Let's trace how Spring finds your `UserController`:

```java
@RestController
@RequestMapping("/users")
public class UserController {
    // ...
}
```

---

## 3. Step 1: Resource Pattern Resolution

### Convert Package to Classpath Pattern

```java
// Input
String basePackage = "_blog.blog";

// Convert to classpath search pattern
String packageSearchPath = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX +
    resolveBasePackage(basePackage) + "/" + DEFAULT_RESOURCE_PATTERN;

// Result
// "classpath*:_blog/blog/**/*.class"
//  ↑          ↑          ↑     ↑
//  |          |          |     └─ All .class files
//  |          |          └─ Recursively
//  |          └─ Package path (. replaced with /)
//  └─ Search all classpath roots (including JARs)
```

**Why `classpath*:`?**
- `classpath:` → Search only the first match
- `classpath*:` → Search **all** classpath roots (target/classes + all JARs)

### Find Matching Resources

Spring uses `PathMatchingResourcePatternResolver`:

```java
public class PathMatchingResourcePatternResolver {

    public Resource[] getResources(String locationPattern) throws IOException {
        // locationPattern = "classpath*:_blog/blog/**/*.class"

        // 1. Get all classpath roots
        Set<Resource> roots = getClasspathRoots();
        // In your case:
        // - file:/home/amrani/Desktop/theDispatch/backend/target/classes/
        // - jar:file:/home/.m2/repository/org/springframework/spring-core/6.1.0.jar!/
        // - jar:file:/home/.m2/repository/org/hibernate/hibernate-core/6.3.0.jar!/
        // ... (all dependency JARs)

        Set<Resource> result = new LinkedHashSet<>();

        // 2. For each root, find matching files
        for (Resource root : roots) {
            result.addAll(findMatchingResources(root, "_blog/blog/**/*.class"));
        }

        return result.toArray(new Resource[0]);
    }

    private Set<Resource> findMatchingResources(Resource rootDir, String subPattern) {
        // For file system (target/classes):
        File rootFile = rootDir.getFile(); // /home/amrani/.../target/classes/
        File packageDir = new File(rootFile, "_blog/blog");

        if (packageDir.exists()) {
            // Recursively scan directory
            return scanDirectory(packageDir, "**/*.class");
        }

        // For JARs:
        // Open JAR file and scan entries matching _blog/blog/**/*.class
        // (Your JARs don't contain _blog.blog, so nothing found)

        return Collections.emptySet();
    }
}
```

**Result for your application**:

```
Found 50+ resources in target/classes:
  file://.../target/classes/_blog/blog/controller/UserController.class
  file://.../target/classes/_blog/blog/controller/PostController.class
  file://.../target/classes/_blog/blog/controller/AuthController.class
  file://.../target/classes/_blog/blog/service/UserServiceImpl.class
  file://.../target/classes/_blog/blog/service/PostServiceImpl.class
  file://.../target/classes/_blog/blog/repository/UserRepository.class
  file://.../target/classes/_blog/blog/config/SecurityConfig.class
  file://.../target/classes/_blog/blog/entity/User.class
  file://.../target/classes/_blog/blog/dto/UserResponse.class
  file://.../target/classes/_blog/blog/mapper/UserMapper.class
  ... (all .class files)
```

---

## 4. Step 2: ASM Bytecode Reading

Now Spring has 50+ `Resource` objects pointing to `.class` files. Next: **Read annotations without loading classes**.

### Why Not Use Class.forName()?

```java
// ❌ BAD APPROACH: Load all classes
for (Resource resource : resources) {
    String className = getClassName(resource); // "_blog.blog.entity.User"
    Class<?> clazz = Class.forName(className); // Loads class into JVM!

    // Problems:
    // 1. Loading 50+ classes is SLOW (class initialization, static blocks run)
    // 2. Memory intensive (all classes stay loaded)
    // 3. Can cause side effects (static initializers execute)
}
```

**Better approach**: Use **ASM** to read bytecode directly.

### What is ASM?

ASM is a Java bytecode manipulation library that can:
- **Read** bytecode without loading classes
- **Parse** annotations from constant pool
- **Extract** method signatures and metadata

### How Spring Uses ASM

```java
public class SimpleMetadataReaderFactory {

    public MetadataReader getMetadataReader(Resource resource) throws IOException {
        // 1. Read .class file as byte array
        InputStream inputStream = resource.getInputStream();
        byte[] classBytes = FileCopyUtils.copyToByteArray(inputStream);

        // 2. Create ASM ClassReader
        ClassReader classReader = new ClassReader(classBytes);

        // 3. Create visitor to collect metadata
        SimpleAnnotationMetadataReadingVisitor visitor =
            new SimpleAnnotationMetadataReadingVisitor(classLoader);

        // 4. Parse class file (ASM calls visitor methods)
        classReader.accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_CODE);
        //                           ↑               ↑
        //                           |               └─ Don't parse method bodies (faster)
        //                           └─ Don't parse debug info (faster)

        // 5. Return metadata (class name, annotations, methods)
        return new SimpleMetadataReader(resource, visitor.getMetadata());
    }
}
```

### ASM Visitor Pattern

ASM uses the **Visitor Pattern** to parse class files:

```java
public class SimpleAnnotationMetadataReadingVisitor extends ClassVisitor {

    private String className;
    private Set<String> annotations = new LinkedHashSet<>();
    private Set<String> interfaces = new LinkedHashSet<>();

    @Override
    public void visit(int version, int access, String name, String signature,
                      String superName, String[] interfaces) {
        // Called once per class
        this.className = name.replace('/', '.'); // "_blog/blog/controller/UserController"
        this.interfaces = Arrays.asList(interfaces);

        // access = public, private, abstract, etc. (as bit flags)
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        // Called once per annotation on the class

        // descriptor = "Lorg/springframework/web/bind/annotation/RestController;"
        String annotationType = Type.getType(descriptor).getClassName();
        // annotationType = "org.springframework.web.bind.annotation.RestController"

        annotations.add(annotationType);

        // Return visitor for annotation attributes (e.g., @RequestMapping(value="/users"))
        return new SimpleAnnotationAttributesReadingVisitor();
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor,
                                      String signature, String[] exceptions) {
        // Called once per method
        // We skip this for component scanning (not needed)
        return null;
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor,
                                    String signature, Object value) {
        // Called once per field
        return null;
    }
}
```

### Parsing UserController.class

Let's see what ASM extracts from `UserController.class`:

```java
// Your source code
@RestController
@RequestMapping("/users")
@CrossOrigin(origins = "http://localhost:4200", allowCredentials = "true")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService, ...) {
        this.userService = userService;
    }

    @GetMapping("/{id}")
    public UserResponse getUser(@PathVariable Long id, Authentication auth) {
        // ...
    }
}
```

**ASM reads the bytecode**:

```
// Bytecode constant pool (simplified)
Constant Pool:
   #1 = Class              #2             // _blog/blog/controller/UserController
   #2 = Utf8               _blog/blog/controller/UserController
   #3 = Class              #4             // java/lang/Object
   #4 = Utf8               java/lang/Object
   #5 = Utf8               userService
   #6 = Utf8               L_blog/blog/service/UserService;
   #7 = Utf8               Lorg/springframework/web/bind/annotation/RestController;
   #8 = Utf8               Lorg/springframework/web/bind/annotation/RequestMapping;
   #9 = Utf8               value
  #10 = Utf8               /users
  ...

RuntimeVisibleAnnotations:
  0: #7() // @RestController
  1: #8(#9=[#10]) // @RequestMapping(value="/users")
```

**Metadata extracted** (WITHOUT loading class):

```java
ClassMetadata:
  className: "_blog.blog.controller.UserController"
  isInterface: false
  isAbstract: false
  isFinal: false
  superClassName: "java.lang.Object"
  interfaces: []

AnnotationMetadata:
  annotations: [
    "org.springframework.web.bind.annotation.RestController",
    "org.springframework.web.bind.annotation.RequestMapping",
    "org.springframework.web.bind.annotation.CrossOrigin"
  ]
  annotationAttributes: {
    "RequestMapping": {
      "value": ["/users"],
      "method": []
    },
    "CrossOrigin": {
      "origins": ["http://localhost:4200"],
      "allowCredentials": "true"
    }
  }

MethodMetadata:
  methods: [
    {
      name: "getUser",
      returnType: "_blog.blog.dto.UserResponse",
      parameters: ["java.lang.Long", "org.springframework.security.core.Authentication"],
      annotations: ["org.springframework.web.bind.annotation.GetMapping"]
    }
  ]
```

**Performance benefit**:
- **Without ASM**: Loading 50 classes takes ~200ms, uses 5MB RAM
- **With ASM**: Reading 50 bytecode files takes ~50ms, uses 1MB RAM

---

## 5. Step 3: Candidate Component Detection

Now Spring has metadata for all 50+ classes. Next: **Filter to find components**.

### Include Filters

Spring checks if a class has **stereotype annotations**:

```java
public class ClassPathScanningCandidateComponentProvider {

    private List<TypeFilter> includeFilters = new ArrayList<>();

    public ClassPathScanningCandidateComponentProvider(boolean useDefaultFilters) {
        if (useDefaultFilters) {
            registerDefaultFilters();
        }
    }

    protected void registerDefaultFilters() {
        // 1. @Component
        includeFilters.add(new AnnotationTypeFilter(Component.class));

        // Meta-annotations are also checked:
        // @Service has @Component
        // @Repository has @Component
        // @Controller has @Component
        // @RestController has @Controller (which has @Component)

        // 2. JSR-330 annotations
        if (jsr330Present) {
            includeFilters.add(new AnnotationTypeFilter(Named.class));
        }

        // 3. JPA entities are NOT included (no @Component)
        // 4. DTOs are NOT included (no @Component)
    }

    private boolean isCandidateComponent(MetadataReader metadataReader) {
        for (TypeFilter filter : includeFilters) {
            if (filter.match(metadataReader, metadataReaderFactory)) {
                return true; // Found a match!
            }
        }
        return false;
    }
}
```

### AnnotationTypeFilter Logic

```java
public class AnnotationTypeFilter implements TypeFilter {

    private final Class<? extends Annotation> annotationType; // Component.class

    @Override
    public boolean match(MetadataReader metadataReader,
                         MetadataReaderFactory metadataReaderFactory) {
        AnnotationMetadata metadata = metadataReader.getAnnotationMetadata();

        // Check if class has @Component (or meta-annotation)
        if (metadata.hasAnnotation(annotationType.getName())) {
            return true;
        }

        // Check meta-annotations (annotations on annotations)
        if (metadata.hasMetaAnnotation(annotationType.getName())) {
            return true;
        }

        return false;
    }
}
```

### Meta-Annotation Resolution

**@RestController Example**:

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Controller   // ← @Controller is a meta-annotation
@ResponseBody
public @interface RestController {
    @AliasFor(annotation = Controller.class)
    String value() default "";
}
```

**@Controller Definition**:

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component   // ← @Component is a meta-annotation
public @interface Controller {
    @AliasFor(annotation = Component.class)
    String value() default "";
}
```

**Resolution chain**:

```
UserController
  has @RestController
    ↓ (meta-annotation)
  has @Controller
    ↓ (meta-annotation)
  has @Component
    ↓
✅ MATCH! UserController is a candidate component
```

### Filtering Your Classes

Let's see which classes are candidates:

| Class | Annotations | Candidate? | Why? |
|-------|-------------|------------|------|
| `UserController` | @RestController | ✅ Yes | @RestController → @Controller → @Component |
| `PostController` | @RestController | ✅ Yes | @RestController → @Controller → @Component |
| `AuthController` | @RestController | ✅ Yes | @RestController → @Controller → @Component |
| `UserServiceImpl` | @Service | ✅ Yes | @Service → @Component |
| `PostServiceImpl` | @Service | ✅ Yes | @Service → @Component |
| `JwtService` | @Service | ✅ Yes | @Service → @Component |
| `SecurityConfig` | @Configuration | ✅ Yes | @Configuration → @Component |
| `WebConfig` | @Configuration | ✅ Yes | @Configuration → @Component |
| `UserRepository` | (interface) | ✅ Yes | Handled by `@EnableJpaRepositories` |
| `PostRepository` | (interface) | ✅ Yes | Handled by `@EnableJpaRepositories` |
| `User` | @Entity | ❌ No | @Entity is NOT @Component |
| `Post` | @Entity | ❌ No | @Entity is NOT @Component |
| `UserResponse` | (none) | ❌ No | No stereotype annotation |
| `PostResponse` | (none) | ❌ No | No stereotype annotation |
| `UserMapper` | @Component | ✅ Yes | @Component directly |
| `PostMapper` | @Component | ✅ Yes | @Component directly |

**Result**: ~30 candidate components found.

---

## 6. Step 4: BeanDefinition Creation

For each candidate, Spring creates a `BeanDefinition`:

```java
public class ScannedGenericBeanDefinition extends GenericBeanDefinition {

    public ScannedGenericBeanDefinition(MetadataReader metadataReader) {
        // 1. Set bean class name
        AnnotationMetadata metadata = metadataReader.getAnnotationMetadata();
        setBeanClassName(metadata.getClassName());
        // "_blog.blog.controller.UserController"

        // 2. Determine scope
        AnnotationAttributes scopeAttributes = metadata.getAnnotationAttributes(Scope.class.getName());
        if (scopeAttributes != null) {
            setScope(scopeAttributes.getString("value")); // "singleton" or "prototype"
        } else {
            setScope(SCOPE_SINGLETON); // Default
        }

        // 3. Determine lazy initialization
        AnnotationAttributes lazyAttributes = metadata.getAnnotationAttributes(Lazy.class.getName());
        if (lazyAttributes != null) {
            setLazyInit(lazyAttributes.getBoolean("value"));
        }

        // 4. Determine primary
        if (metadata.hasAnnotation(Primary.class.getName())) {
            setPrimary(true);
        }

        // 5. Determine bean name
        String beanName = determineBeanName(metadata);
        // "userController" (class name with lowercase first letter)
    }
}
```

### Bean Name Generation

```java
public class AnnotationBeanNameGenerator {

    public String generateBeanName(BeanDefinition definition, BeanDefinitionRegistry registry) {
        // 1. Check if @Component has explicit name
        AnnotationMetadata metadata = (AnnotationMetadata) definition.getSource();
        Set<String> types = metadata.getAnnotationTypes();

        for (String type : types) {
            AnnotationAttributes attributes = metadata.getAnnotationAttributes(type);
            if (isStereotypeWithNameValue(type, attributes)) {
                String value = attributes.getString("value");
                if (StringUtils.hasText(value)) {
                    return value; // Explicit name: @Service("myService")
                }
            }
        }

        // 2. Generate default name from class name
        String className = definition.getBeanClassName();
        // "_blog.blog.controller.UserController"

        String shortName = ClassUtils.getShortName(className);
        // "UserController"

        return Introspector.decapitalize(shortName);
        // "userController"
    }
}
```

**Examples**:

```java
// Explicit names
@Service("customUserService")
class UserServiceImpl { } // Bean name: "customUserService"

@RestController("userApi")
class UserController { } // Bean name: "userApi"

// Default names (generated)
@Service
class UserServiceImpl { } // Bean name: "userServiceImpl"

@RestController
class UserController { } // Bean name: "userController"

@Configuration
class SecurityConfig { } // Bean name: "securityConfig"

@Component
class UserMapper { } // Bean name: "userMapper"
```

### BeanDefinition Registry

All `BeanDefinition`s are stored in a registry:

```java
public class DefaultListableBeanFactory implements BeanDefinitionRegistry {

    private final Map<String, BeanDefinition> beanDefinitionMap = new ConcurrentHashMap<>(256);

    @Override
    public void registerBeanDefinition(String beanName, BeanDefinition beanDefinition) {
        BeanDefinition existingDefinition = beanDefinitionMap.get(beanName);

        if (existingDefinition != null) {
            // Conflict! Two beans with same name
            throw new BeanDefinitionOverrideException(beanName, beanDefinition, existingDefinition);
        }

        beanDefinitionMap.put(beanName, beanDefinition);
    }
}
```

**After component scanning**, your registry contains:

```java
beanDefinitionMap = {
    "userController" → BeanDefinition[class=UserController, scope=singleton, ...],
    "postController" → BeanDefinition[class=PostController, scope=singleton, ...],
    "authController" → BeanDefinition[class=AuthController, scope=singleton, ...],
    "userServiceImpl" → BeanDefinition[class=UserServiceImpl, scope=singleton, ...],
    "postServiceImpl" → BeanDefinition[class=PostServiceImpl, scope=singleton, ...],
    "jwtService" → BeanDefinition[class=JwtService, scope=singleton, ...],
    "securityConfig" → BeanDefinition[class=SecurityConfig, scope=singleton, ...],
    "userMapper" → BeanDefinition[class=UserMapper, scope=singleton, ...],
    // ... 30+ more
}
```

**Important**: At this point, **NO beans are instantiated**. Only metadata is stored.

---

## 7. Repository Scanning (Special Case)

### How @EnableJpaRepositories Works

```java
@Configuration
@EnableJpaRepositories  // This is in @SpringBootApplication by default
public class JpaConfig {
    // Spring Data JPA scans for Repository interfaces
}
```

**Different scanning mechanism**:

```java
public class JpaRepositoriesRegistrar implements ImportBeanDefinitionRegistrar {

    @Override
    public void registerBeanDefinitions(AnnotationMetadata metadata,
                                        BeanDefinitionRegistry registry) {
        // 1. Find all interfaces extending Repository
        Set<BeanDefinition> repositories = findRepositoryInterfaces("_blog.blog.repository");

        // 2. For each repository, register a proxy factory bean
        for (BeanDefinition repo : repositories) {
            registerRepositoryProxyFactoryBean(repo, registry);
        }
    }

    private void registerRepositoryProxyFactoryBean(BeanDefinition repo,
                                                     BeanDefinitionRegistry registry) {
        // Create a factory bean that will create a proxy
        RootBeanDefinition factoryDef = new RootBeanDefinition(JpaRepositoryFactoryBean.class);
        factoryDef.getConstructorArgumentValues().addGenericArgumentValue(repo.getBeanClassName());
        // Constructor arg: UserRepository.class

        String beanName = generateRepositoryBeanName(repo); // "userRepository"
        registry.registerBeanDefinition(beanName, factoryDef);
    }
}
```

### Repository Interface Detection

```java
// Spring Data scans for interfaces like this:
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
}

// Detection logic:
boolean isRepository(MetadataReader metadataReader) {
    ClassMetadata classMetadata = metadataReader.getClassMetadata();

    // 1. Must be an interface
    if (!classMetadata.isInterface()) {
        return false;
    }

    // 2. Must extend Repository (or sub-interface)
    String[] interfaceNames = classMetadata.getInterfaceNames();
    for (String iface : interfaceNames) {
        if (isRepositoryInterface(iface)) {
            return true;
        }
    }

    return false;
}

boolean isRepositoryInterface(String interfaceName) {
    // Check if extends:
    // - Repository
    // - CrudRepository
    // - PagingAndSortingRepository
    // - JpaRepository
    return REPOSITORY_TYPES.contains(interfaceName);
}
```

**Result for your application**:

```java
Found Repository interfaces:
  - UserRepository extends JpaRepository<User, Long>
  - PostRepository extends JpaRepository<Post, Long>
  - CommentRepository extends JpaRepository<Comment, Long>
  - SubscriptionRepository extends JpaRepository<Subscription, Long>
  - LikeRepository extends JpaRepository<Like, Long>
  - NotificationRepository extends JpaRepository<Notification, Long>
  - PostReportRepository extends JpaRepository<PostReport, Long>
  - CommentReportRepository extends JpaRepository<CommentReport, Long>

Registered BeanDefinitions (proxy factories):
  - userRepository → JpaRepositoryFactoryBean(UserRepository.class)
  - postRepository → JpaRepositoryFactoryBean(PostRepository.class)
  - ... (8 total)
```

---

## 8. Exclude Filters

Spring also has **exclude filters** to skip certain classes:

```java
@SpringBootApplication // Contains these filters:
@ComponentScan(
    excludeFilters = {
        @Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
        @Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class)
    }
)
```

### Why Exclude Filters?

**Prevent duplicate beans**:
- `AutoConfigurationExcludeFilter`: Don't scan auto-configuration classes as components
  ```java
  // Example: HibernateJpaAutoConfiguration
  // Should be loaded via @EnableAutoConfiguration, NOT component scanning
  ```

**Allow test-specific exclusions**:
- `TypeExcludeFilter`: Allows test slices (@WebMvcTest, @DataJpaTest) to exclude beans
  ```java
  @WebMvcTest(UserController.class) // Only load web layer beans
  // Excludes: @Service, @Repository beans
  // Includes: @Controller, @RestController beans
  ```

### Custom Exclude Example

```java
@ComponentScan(
    basePackages = "_blog.blog",
    excludeFilters = {
        // Exclude all classes in .test package
        @Filter(type = FilterType.REGEX, pattern = ".*\\.test\\..*"),

        // Exclude specific class
        @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SomeConfig.class)
    }
)
```

---

## 9. Performance Optimizations

### Spring Boot 3 Optimizations

1. **Indexed Components** (Spring 5.0+):
   - Add dependency: `spring-context-indexer`
   - At compile time, creates `META-INF/spring.components` with all candidates
   - At runtime, Spring reads index instead of scanning classpath
   - **Result**: 10x faster startup for large applications

   ```properties
   # META-INF/spring.components (generated at compile time)
   _blog.blog.controller.UserController=org.springframework.stereotype.Component
   _blog.blog.service.UserServiceImpl=org.springframework.stereotype.Component
   # ... all components listed
   ```

2. **Conditional Scanning**:
   - `@Conditional` on `@ComponentScan` skips entire packages
   - Example: Skip admin package if admin.enabled=false

3. **Lazy Initialization** (Spring Boot 2.2+):
   ```properties
   spring.main.lazy-initialization=true
   ```
   - Beans created only when first used
   - Faster startup, slower first request

### Memory Optimizations

**Problem**: Scanning large classpaths (500+ JARs) uses memory

**Solution**: Spring uses `SoftReference` for metadata cache:

```java
public class CachingMetadataReaderFactory {

    // Metadata cached with SoftReference (GC can reclaim if memory low)
    private Map<Resource, MetadataReader> metadataReaderCache =
        new ConcurrentReferenceHashMap<>(256, ReferenceType.SOFT);

    public MetadataReader getMetadataReader(Resource resource) {
        MetadataReader cached = metadataReaderCache.get(resource);
        if (cached != null) {
            return cached;
        }

        MetadataReader metadataReader = createMetadataReader(resource);
        metadataReaderCache.put(resource, metadataReader);
        return metadataReader;
    }
}
```

---

## 10. Debugging Component Scanning

### Enable Debug Logging

```properties
# application.properties
logging.level.org.springframework.context.annotation=DEBUG
```

**Output**:

```
DEBUG o.s.c.a.ClassPathBeanDefinitionScanner : Identified candidate component class: file [.../UserController.class]
DEBUG o.s.c.a.ClassPathBeanDefinitionScanner : Identified candidate component class: file [.../PostController.class]
DEBUG o.s.c.a.ClassPathBeanDefinitionScanner : Identified candidate component class: file [.../UserServiceImpl.class]
...
```

### Common Issues

**Issue 1: Component not found**

```java
@Service
class UserService { } // ❌ Not in _blog.blog package!

// Solution: Move to _blog.blog or add to @ComponentScan
@ComponentScan(basePackages = {"_blog.blog", "com.other.package"})
```

**Issue 2: Circular dependencies**

```
The dependencies of some of the beans in the application context form a cycle:

   userController
   ↓
   userService
   ↓
   postService
   ↓
   userService (circular reference)
```

**Solution**: Use `@Lazy` to break cycle:

```java
@Service
public class UserService {
    private final PostService postService;

    public UserService(@Lazy PostService postService) {
        this.postService = postService;
    }
}
```

**Issue 3: Multiple beans of same type**

```
NoUniqueBeanDefinitionException: No qualifying bean of type 'UserService' available:
expected single matching bean but found 2: userServiceImpl, userServiceV2
```

**Solution**: Use `@Primary` or `@Qualifier`:

```java
@Service
@Primary
class UserServiceImpl implements UserService { }

@Service("userServiceV2")
class UserServiceV2 implements UserService { }

// Inject
@Autowired
@Qualifier("userServiceV2")
private UserService userService;
```

---

## 11. Complete Scanning Timeline

Let's trace the **complete component scanning** for your application:

```
[0ms] Application starts: SpringApplication.run(BlogApplication.class)

[10ms] Component scanning triggered: @ComponentScan on BlogApplication

[20ms] Resource pattern resolution:
  - Pattern: "classpath*:_blog/blog/**/*.class"
  - Found: 52 .class files in target/classes

[50ms] ASM bytecode reading:
  - UserController.class → annotations: [@RestController, @RequestMapping]
  - PostController.class → annotations: [@RestController, @RequestMapping]
  - UserServiceImpl.class → annotations: [@Service]
  - User.class → annotations: [@Entity, @Table] (no @Component)
  ... (52 files processed)

[80ms] Candidate filtering:
  - UserController: ✅ (has @RestController → @Component)
  - PostController: ✅ (has @RestController → @Component)
  - UserServiceImpl: ✅ (has @Service → @Component)
  - User: ❌ (has @Entity, not @Component)
  - UserResponse: ❌ (no annotations)
  Result: 32 candidates

[100ms] BeanDefinition creation:
  - Created BeanDefinition for each candidate
  - Generated bean names (userController, postController, ...)
  - Registered in BeanDefinitionRegistry

[120ms] Repository scanning:
  - Found 8 Repository interfaces
  - Created JpaRepositoryFactoryBean for each

[150ms] Component scanning complete
  - Total: 40 BeanDefinitions registered
  - Memory used: ~2MB for metadata
  - Next phase: Bean instantiation
```

---

## 12. Key Takeaways

### How Component Scanning Works

1. **Package to classpath**: `_blog.blog` → `classpath*:_blog/blog/**/*.class`
2. **Find resources**: PathMatchingResourcePatternResolver scans filesystem and JARs
3. **Read bytecode**: ASM reads .class files WITHOUT loading into JVM
4. **Extract metadata**: Annotations, class info, methods
5. **Filter candidates**: Check for @Component (or meta-annotations)
6. **Create BeanDefinitions**: Store metadata in registry
7. **Generate bean names**: `UserController` → `userController`

### Why This Design?

- **Fast**: ASM is faster than Class.forName()
- **Memory efficient**: Don't load all classes
- **Flexible**: Custom filters, multiple scan paths
- **Extensible**: Meta-annotations, custom stereotypes

### Performance Tips

1. **Use specific base packages**: `@ComponentScan("_blog.blog.service")` faster than `@ComponentScan("_blog")`
2. **Use component index**: Add `spring-context-indexer` for large apps
3. **Avoid scanning third-party packages**: Don't scan org.springframework.*
4. **Use explicit filters**: Exclude test packages, utilities

---

## What's Next?

Continue to `22-BEAN-LIFECYCLE-AND-PROXIES.md` to learn how Spring instantiates beans and creates proxies.

**Completed**:
- ✅ Spring Boot startup process
- ✅ Component scanning internals with ASM
- ✅ Resource pattern matching
- ✅ BeanDefinition creation

**Next**:
- Bean lifecycle and instantiation order
- Proxy creation (CGLIB vs JDK)
- BeanPostProcessor pipeline
- AOP internals
