# Bean Lifecycle, Proxies & AOP Internals

## Overview

This document explains the **complete bean lifecycle** in Spring, from instantiation to destruction, and how Spring creates proxies for AOP features like `@Transactional`.

**What you'll learn**:
- Bean instantiation algorithm
- Constructor selection and dependency resolution
- Circular dependency handling
- BeanPostProcessor pipeline
- CGLIB vs JDK proxy creation
- AOP method interception mechanics
- Transaction proxy implementation

---

## 1. Bean Lifecycle Overview

### The Complete Lifecycle

```
[Phase 1] BeanDefinition registered
           ↓
[Phase 2] Bean instantiation
           ├─ Select constructor
           ├─ Resolve dependencies (recursive)
           └─ Call constructor via reflection
           ↓
[Phase 3] Dependency injection
           ├─ Field injection (@Autowired fields)
           └─ Setter injection (@Autowired setters)
           ↓
[Phase 4] BeanPostProcessor.postProcessBeforeInitialization()
           ├─ Inject @Value fields
           ├─ Inject @Resource fields
           └─ Process other annotations
           ↓
[Phase 5] Initialization callbacks
           ├─ @PostConstruct methods
           └─ InitializingBean.afterPropertiesSet()
           ↓
[Phase 6] BeanPostProcessor.postProcessAfterInitialization()
           ├─ Create AOP proxies (@Transactional, @Async, @Cacheable)
           ├─ Create Security proxies (@PreAuthorize, @Secured)
           └─ Wrap bean if needed
           ↓
[Phase 7] Bean ready for use
           (stored in ApplicationContext)
           ↓
[Phase 8] Bean in use
           (injected into other beans, method calls)
           ↓
[Phase 9] Destruction (application shutdown)
           ├─ @PreDestroy methods
           └─ DisposableBean.destroy()
```

---

## 2. Phase 2: Bean Instantiation

### Constructor Selection Algorithm

Spring needs to choose **which constructor** to use. Let's see how it works with your `UserServiceImpl`:

```java
@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final PostService postService;
    private final ReportRepository reportRepository;
    private final NotificationRepository notificationRepository;
    private final CommentRepository commentRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PostRepository postRepository;

    // Only one constructor with 9 parameters
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
        // Constructor code
    }
}
```

**Spring's constructor selection logic**:

```java
public class ConstructorResolver {

    public Constructor<?>[] determineCandidateConstructors(Class<?> beanClass) {
        Constructor<?>[] constructors = beanClass.getDeclaredConstructors();

        // Rule 1: Only one constructor? Use it!
        if (constructors.length == 1) {
            return new Constructor<?>[] { constructors[0] };
        }

        // Rule 2: Constructor with @Autowired? Use it!
        for (Constructor<?> ctor : constructors) {
            if (ctor.isAnnotationPresent(Autowired.class)) {
                return new Constructor<?>[] { ctor };
            }
        }

        // Rule 3: Default no-arg constructor? Use it!
        try {
            Constructor<?> defaultCtor = beanClass.getDeclaredConstructor();
            return new Constructor<?>[] { defaultCtor };
        } catch (NoSuchMethodException e) {
            // No default constructor
        }

        // Rule 4: Multiple constructors, none with @Autowired → ERROR!
        throw new BeanCreationException("No default constructor found; " +
            "and no constructor with @Autowired");
    }
}
```

**Your UserServiceImpl** matches **Rule 1** (only one constructor), so Spring uses it.

### Multiple Constructor Example

```java
@Service
public class MyService {

    private final Dependency1 dep1;
    private final Dependency2 dep2;

    // ✅ This constructor will be used (has @Autowired)
    @Autowired
    public MyService(Dependency1 dep1, Dependency2 dep2) {
        this.dep1 = dep1;
        this.dep2 = dep2;
    }

    // This constructor is ignored
    public MyService(Dependency1 dep1) {
        this.dep1 = dep1;
        this.dep2 = null;
    }
}
```

**Without @Autowired**:

```java
@Service
public class MyService {

    // ❌ ERROR: Multiple constructors, none with @Autowired
    public MyService(Dependency1 dep1) { }

    public MyService(Dependency1 dep1, Dependency2 dep2) { }
}
```

---

## 3. Dependency Resolution

Once constructor is selected, Spring resolves **all parameters**.

### Dependency Resolution Algorithm

```java
public class ConstructorResolver {

    public Object[] resolveConstructorArguments(Constructor<?> constructor,
                                                 String beanName) {
        // Get parameter types
        Class<?>[] paramTypes = constructor.getParameterTypes();
        // For UserServiceImpl:
        // [UserRepository.class, AuthenticationManager.class, PasswordEncoder.class, ...]

        Object[] args = new Object[paramTypes.length];

        for (int i = 0; i < paramTypes.length; i++) {
            Class<?> paramType = paramTypes[i];

            // Resolve dependency (may trigger recursive bean creation!)
            Object dependency = resolveDependency(paramType, beanName);
            args[i] = dependency;
        }

        return args;
    }

    private Object resolveDependency(Class<?> dependencyType, String requestingBean) {
        // 1. Find beans of this type
        String[] candidateBeanNames = beanFactory.getBeanNamesForType(dependencyType);

        if (candidateBeanNames.length == 0) {
            throw new NoSuchBeanDefinitionException(dependencyType);
        }

        if (candidateBeanNames.length > 1) {
            // Multiple candidates - use @Primary or @Qualifier
            candidateBeanNames = filterWithPrimary(candidateBeanNames);

            if (candidateBeanNames.length > 1) {
                throw new NoUniqueBeanDefinitionException(dependencyType, candidateBeanNames);
            }
        }

        String beanName = candidateBeanNames[0];

        // 2. Get or create the bean (RECURSIVE!)
        return beanFactory.getBean(beanName);
    }
}
```

### Dependency Graph for UserServiceImpl

```
Creating bean: userServiceImpl
  ↓ Needs: UserRepository
    ↓ Needs: EntityManagerFactory
      ↓ Needs: DataSource
        ✅ Created: dataSource
      ✅ Created: entityManagerFactory
    ✅ Created: userRepository (JPA proxy)
  ↓ Needs: AuthenticationManager
    ↓ Needs: UserDetailsService
      ↓ Needs: UserRepository
        ✅ Already created! (reuse)
      ✅ Created: customUserDetailsService
    ✅ Created: authenticationManager
  ↓ Needs: PasswordEncoder
    ✅ Created: passwordEncoder (from SecurityConfig.@Bean)
  ↓ Needs: PostService
    ↓ Needs: PostRepository
      ↓ Needs: EntityManagerFactory
        ✅ Already created! (reuse)
      ✅ Created: postRepository
    ↓ Needs: UserRepository
      ✅ Already created! (reuse)
    ↓ Needs: ... (more dependencies)
    ✅ Created: postServiceImpl
  ↓ Needs: ReportRepository
    ✅ Created: reportRepository
  ↓ Needs: NotificationRepository
    ✅ Created: notificationRepository
  ↓ Needs: CommentRepository
    ✅ Created: commentRepository
  ↓ Needs: SubscriptionRepository
    ✅ Created: subscriptionRepository
  ↓ Needs: PostRepository
    ✅ Already created! (reuse)
✅ Created: userServiceImpl (with all 9 dependencies)
```

### Bean Creation Order

Spring determines creation order using **topological sort** of the dependency graph:

```
1. dataSource
2. entityManagerFactory
3. userRepository, postRepository, commentRepository, ... (all repositories)
4. passwordEncoder
5. customUserDetailsService
6. authenticationManager
7. postServiceImpl
8. userServiceImpl  ← Last (has most dependencies)
```

---

## 4. Circular Dependencies

### Problem

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
```

**Dependency cycle**:
```
Creating serviceA
  ↓ Needs: serviceB
    ↓ Needs: serviceA
      ↓ Needs: serviceB
        ↓ Needs: serviceA
          ↓ ... INFINITE LOOP!
```

**Error**:
```
BeanCurrentlyInCreationException: Error creating bean with name 'serviceA':
Requested bean is currently in creation: Is there an unresolvable circular reference?
```

### Solution 1: Field Injection (Not Recommended)

```java
@Service
public class ServiceA {
    @Autowired // Field injection breaks cycle
    private ServiceB serviceB;

    public ServiceA() { } // No-arg constructor
}
```

**How it works**:
```
1. Create ServiceA instance (empty constructor)
2. Create ServiceB instance
   ↓ Needs: ServiceA (already created in step 1! Use that)
3. Inject ServiceB into ServiceA (field injection)
✅ Both beans created
```

**Why not recommended**: Field injection makes testing harder and hides dependencies.

### Solution 2: @Lazy (Recommended)

```java
@Service
public class ServiceA {
    private final ServiceB serviceB;

    public ServiceA(@Lazy ServiceB serviceB) {
        this.serviceB = serviceB; // Injected as PROXY
    }
}

@Service
public class ServiceB {
    private final ServiceA serviceA;

    public ServiceB(ServiceA serviceA) {
        this.serviceA = serviceA;
    }
}
```

**How it works**:
```
1. Creating serviceA
   ↓ Needs: ServiceB (@Lazy)
     → Inject PROXY instead of real bean
2. Creating serviceB
   ↓ Needs: ServiceA (now exists!)
✅ Both beans created
3. When serviceA.serviceB.method() is called:
   → Proxy resolves real ServiceB bean
```

### Solution 3: Redesign (Best)

Circular dependencies often indicate design problems. Refactor to break the cycle:

```java
@Service
public class ServiceA {
    private final CommonDependency common;

    public ServiceA(CommonDependency common) {
        this.common = common;
    }
}

@Service
public class ServiceB {
    private final CommonDependency common;

    public ServiceB(CommonDependency common) {
        this.common = common;
    }
}

@Service
public class CommonDependency {
    // Shared logic extracted here
}
```

---

## 5. Bean Instantiation via Reflection

Once dependencies are resolved, Spring creates the bean:

```java
public class SimpleInstantiationStrategy {

    public Object instantiate(RootBeanDefinition bd, String beanName, BeanFactory owner) {
        Constructor<?> constructorToUse = bd.getResolvedConstructor();

        if (constructorToUse == null) {
            // Determine constructor (algorithm from section 2)
            constructorToUse = determineConstructorToUse(bd);
        }

        // Resolve constructor arguments (algorithm from section 3)
        Object[] args = resolveConstructorArguments(constructorToUse);

        // Instantiate using reflection
        try {
            constructorToUse.setAccessible(true); // Allow private constructors
            return constructorToUse.newInstance(args);
        } catch (Exception ex) {
            throw new BeanCreationException("Failed to instantiate bean: " + beanName, ex);
        }
    }
}
```

**For UserServiceImpl**:

```java
// Spring does this internally:
Constructor<UserServiceImpl> constructor = UserServiceImpl.class.getConstructor(
    UserRepository.class,
    AuthenticationManager.class,
    PasswordEncoder.class,
    PostService.class,
    ReportRepository.class,
    NotificationRepository.class,
    CommentRepository.class,
    SubscriptionRepository.class,
    PostRepository.class
);

Object userService = constructor.newInstance(
    userRepositoryBean,
    authenticationManagerBean,
    passwordEncoderBean,
    postServiceBean,
    reportRepositoryBean,
    notificationRepositoryBean,
    commentRepositoryBean,
    subscriptionRepositoryBean,
    postRepositoryBean
);
```

---

## 6. BeanPostProcessor Pipeline

After instantiation, Spring passes the bean through **BeanPostProcessors**:

```java
public interface BeanPostProcessor {

    // Called BEFORE @PostConstruct
    default Object postProcessBeforeInitialization(Object bean, String beanName) {
        return bean;
    }

    // Called AFTER @PostConstruct (PROXY CREATION HERE!)
    default Object postProcessAfterInitialization(Object bean, String beanName) {
        return bean;
    }
}
```

### Built-in BeanPostProcessors

Spring has many built-in post-processors:

| Post-Processor | When Applied | What It Does |
|---------------|-------------|--------------|
| `CommonAnnotationBeanPostProcessor` | Before init | Processes @Resource, @PostConstruct, @PreDestroy |
| `AutowiredAnnotationBeanPostProcessor` | Before init | Processes @Autowired, @Value, @Inject |
| `ApplicationContextAwareProcessor` | Before init | Injects ApplicationContext if bean implements aware interfaces |
| `AbstractAutoProxyCreator` | After init | Creates AOP proxies (@Transactional, @Async, @Cacheable) |
| `AsyncAnnotationBeanPostProcessor` | After init | Creates proxies for @Async methods |

### Example: @Value Injection

```java
public class AutowiredAnnotationBeanPostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        // Find all fields with @Value
        Class<?> clazz = bean.getClass();
        for (Field field : clazz.getDeclaredFields()) {
            Value valueAnnotation = field.getAnnotation(Value.class);
            if (valueAnnotation != null) {
                String placeholder = valueAnnotation.value(); // "${security.jwt.secret-key}"

                // Resolve placeholder
                String resolvedValue = environment.resolvePlaceholders(placeholder);

                // Inject value
                field.setAccessible(true);
                field.set(bean, resolvedValue);
            }
        }

        return bean;
    }
}
```

**In your JwtService**:

```java
@Service
public class JwtService {

    @Value("${security.jwt.secret-key}")
    private String secretKey; // Injected by AutowiredAnnotationBeanPostProcessor

    @Value("${security.jwt.expiration-time}")
    private long jwtExpiration; // Injected by AutowiredAnnotationBeanPostProcessor
}
```

---

## 7. Initialization Callbacks

### @PostConstruct

After dependency injection, Spring calls `@PostConstruct` methods:

```java
public class CommonAnnotationBeanPostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        // Find @PostConstruct methods
        Class<?> clazz = bean.getClass();
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(PostConstruct.class)) {
                try {
                    method.setAccessible(true);
                    method.invoke(bean); // Call the method
                } catch (Exception ex) {
                    throw new BeanCreationException("@PostConstruct method failed", ex);
                }
            }
        }

        return bean;
    }
}
```

**In your JwtService**:

```java
@Service
public class JwtService {

    @Value("${security.jwt.secret-key}")
    private String secretKey;

    @PostConstruct
    public void validateSecretKey() {
        // This runs AFTER secretKey is injected
        if (secretKey == null || secretKey.trim().isEmpty()) {
            throw new IllegalStateException("JWT_SECRET_KEY must be set!");
        }

        System.out.println("✅ JWT secret key validated successfully");
    }
}
```

**Execution timeline**:

```
1. new JwtService()                        // Constructor (secretKey is null)
2. field.set(bean, resolvedValue)          // Inject secretKey value
3. validateSecretKey()                     // @PostConstruct (secretKey is set)
4. Bean ready for use
```

### Initialization Order

```java
@Service
public class MyService implements InitializingBean {

    @Value("${my.property}")
    private String property;

    // 1. Constructor
    public MyService() {
        System.out.println("1. Constructor called - property: " + property); // null
    }

    // 2. @PostConstruct
    @PostConstruct
    public void init1() {
        System.out.println("2. @PostConstruct called - property: " + property); // set
    }

    // 3. InitializingBean.afterPropertiesSet()
    @Override
    public void afterPropertiesSet() {
        System.out.println("3. afterPropertiesSet called - property: " + property); // set
    }

    // 4. @Bean(initMethod = "customInit")
    public void customInit() {
        System.out.println("4. customInit called - property: " + property); // set
    }
}
```

**Order**:
```
Constructor → @PostConstruct → InitializingBean → @Bean(initMethod)
```

---

## 8. Proxy Creation (The Interesting Part!)

### When Are Proxies Created?

Proxies are created in `postProcessAfterInitialization`:

```java
public class AbstractAutoProxyCreator implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        // Check if bean needs proxying
        if (shouldCreateProxy(bean, beanName)) {
            return createProxy(bean, beanName);
        }

        return bean; // No proxy needed
    }

    private boolean shouldCreateProxy(Object bean, String beanName) {
        // Check if bean has @Transactional, @Async, @Cacheable, etc.
        Class<?> targetClass = bean.getClass();

        // Check class-level annotations
        if (hasProxyAnnotation(targetClass)) {
            return true;
        }

        // Check method-level annotations
        for (Method method : targetClass.getMethods()) {
            if (hasProxyAnnotation(method)) {
                return true;
            }
        }

        return false;
    }
}
```

**Your UserServiceImpl** has `@Transactional` methods → **proxy created!**

### CGLIB vs JDK Proxies

Spring chooses proxy type based on the bean:

```java
public class ProxyFactory {

    public Object createProxy(Object target) {
        // Rule 1: If target implements interface → JDK dynamic proxy
        if (hasInterfaces(target)) {
            return createJdkDynamicProxy(target);
        }

        // Rule 2: Otherwise → CGLIB proxy
        return createCglibProxy(target);
    }
}
```

**Your UserServiceImpl**:
- Implements `UserService` interface → **Could use JDK proxy**
- But Spring Boot defaults to CGLIB → **Uses CGLIB proxy**

**Force JDK proxies**:
```properties
spring.aop.proxy-target-class=false
```

### CGLIB Proxy Creation

```java
public class CglibAopProxy {

    public Object createProxyInstance(Object target, List<Advisor> advisors) {
        Enhancer enhancer = new Enhancer();

        // 1. Set superclass (subclass the target)
        enhancer.setSuperclass(target.getClass()); // UserServiceImpl.class

        // 2. Set callback (method interceptor)
        enhancer.setCallback(new DynamicAdvisedInterceptor(target, advisors));

        // 3. Create proxy
        Object proxy = enhancer.create();
        //  proxy is instance of: UserServiceImpl$$EnhancerBySpringCGLIB$$12345

        return proxy;
    }
}
```

**Generated proxy class** (simplified):

```java
// Generated at runtime by CGLIB
public class UserServiceImpl$$EnhancerBySpringCGLIB$$12345 extends UserServiceImpl {

    private UserServiceImpl target; // The real bean
    private List<MethodInterceptor> interceptors;

    // Constructor
    public UserServiceImpl$$EnhancerBySpringCGLIB$$12345(
            UserServiceImpl target,
            List<MethodInterceptor> interceptors) {
        super(null, null, null, null, null, null, null, null, null); // Call super with nulls
        this.target = target;
        this.interceptors = interceptors;
    }

    @Override
    public UserResponse getCurrentUser(String username) {
        // 1. Check if method has interceptors (@Transactional, etc.)
        Method method = UserServiceImpl.class.getMethod("getCurrentUser", String.class);

        // 2. Build interceptor chain
        InterceptorChain chain = new InterceptorChain(target, method, new Object[]{username}, interceptors);

        // 3. Execute chain
        return (UserResponse) chain.proceed();
    }

    @Override
    public User register(RegisterRequest request) {
        Method method = UserServiceImpl.class.getMethod("register", RegisterRequest.class);
        InterceptorChain chain = new InterceptorChain(target, method, new Object[]{request}, interceptors);
        return (User) chain.proceed();
    }

    // All other methods similarly overridden
}
```

### JDK Dynamic Proxy

```java
public class JdkDynamicProxy {

    public Object createProxy(Object target, Class<?>[] interfaces) {
        return Proxy.newProxyInstance(
            target.getClass().getClassLoader(),
            interfaces, // UserService.class
            new TransactionInvocationHandler(target)
        );
    }
}

class TransactionInvocationHandler implements InvocationHandler {

    private final Object target;

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // Check if method has @Transactional
        if (method.isAnnotationPresent(Transactional.class)) {
            // Start transaction
            TransactionStatus tx = transactionManager.getTransaction(new DefaultTransactionDefinition());

            try {
                // Call real method
                Object result = method.invoke(target, args);

                // Commit
                transactionManager.commit(tx);
                return result;
            } catch (Exception e) {
                // Rollback
                transactionManager.rollback(tx);
                throw e;
            }
        } else {
            // No transaction, just call method
            return method.invoke(target, args);
        }
    }
}
```

**JDK proxy limitations**:
- Can only proxy **interfaces**
- Cannot proxy **final** classes
- Cannot proxy **final** methods

**CGLIB proxy advantages**:
- Can proxy **concrete classes**
- Can proxy **non-interface methods**

---

## 9. Method Interception Chain

When you call a proxied method, it goes through an **interceptor chain**:

```java
public class ReflectiveMethodInvocation implements MethodInvocation {

    private final Object target;
    private final Method method;
    private final Object[] arguments;
    private final List<MethodInterceptor> interceptors;
    private int currentInterceptorIndex = -1;

    @Override
    public Object proceed() throws Throwable {
        // All interceptors executed? Call target method
        if (currentInterceptorIndex == interceptors.size() - 1) {
            return method.invoke(target, arguments);
        }

        // Get next interceptor
        MethodInterceptor interceptor = interceptors.get(++currentInterceptorIndex);

        // Call interceptor (which may call proceed() again)
        return interceptor.invoke(this);
    }
}
```

### Example: @Transactional Method Call

```java
// Your code
@Transactional(readOnly = true)
public UserResponse getCurrentUser(String username) {
    User user = userRepository.findByUsername(username).orElseThrow();
    return new UserResponse(user);
}
```

**Interceptor chain**:

```
Client calls: userService.getCurrentUser("john")
  ↓
[Proxy] UserServiceImpl$$EnhancerBySpringCGLIB$$12345
  ↓
[Interceptor 1] TransactionInterceptor
  ├─ Start transaction (readOnly=true)
  ├─ Call proceed()
  │   ↓
  │ [Target] UserServiceImpl.getCurrentUser("john")
  │   └─ userRepository.findByUsername("john")
  │   └─ new UserResponse(user)
  │   └─ return result
  ├─ Commit transaction
  └─ Return result
  ↓
Client receives: UserResponse
```

**TransactionInterceptor implementation** (simplified):

```java
public class TransactionInterceptor implements MethodInterceptor {

    private PlatformTransactionManager transactionManager;

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Method method = invocation.getMethod();

        // Get @Transactional annotation
        Transactional txAnnotation = method.getAnnotation(Transactional.class);

        if (txAnnotation == null) {
            // No transaction needed
            return invocation.proceed();
        }

        // Create transaction definition
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setReadOnly(txAnnotation.readOnly()); // readOnly=true
        def.setPropagationBehavior(txAnnotation.propagation().value());
        def.setIsolationLevel(txAnnotation.isolation().value());

        // Start transaction
        TransactionStatus status = transactionManager.getTransaction(def);

        try {
            // Call target method
            Object result = invocation.proceed();

            // Commit
            transactionManager.commit(status);
            return result;

        } catch (Exception ex) {
            // Check rollback rules
            if (shouldRollback(ex, txAnnotation)) {
                transactionManager.rollback(status);
            } else {
                transactionManager.commit(status);
            }
            throw ex;
        }
    }

    private boolean shouldRollback(Exception ex, Transactional txAnnotation) {
        // Default: rollback on RuntimeException and Error
        if (ex instanceof RuntimeException || ex instanceof Error) {
            return true;
        }

        // Check rollbackFor attribute
        for (Class<? extends Throwable> rollbackClass : txAnnotation.rollbackFor()) {
            if (rollbackClass.isInstance(ex)) {
                return true;
            }
        }

        return false;
    }
}
```

---

## 10. Multiple Interceptors

If a method has multiple annotations, multiple interceptors execute:

```java
@Service
public class MyService {

    @Transactional
    @Async
    @Cacheable("users")
    public CompletableFuture<User> getUser(Long id) {
        return CompletableFuture.completedFuture(userRepository.findById(id).orElseThrow());
    }
}
```

**Interceptor chain**:

```
Client calls: myService.getUser(5)
  ↓
[Proxy]
  ↓
[CacheInterceptor]
  ├─ Check cache for key "users::5"
  ├─ Cache miss → proceed()
  │   ↓
  │ [AsyncInterceptor]
  │   ├─ Submit to thread pool
  │   ├─ Return CompletableFuture immediately
  │   └─ In background thread:
  │       ↓
  │     [TransactionInterceptor]
  │       ├─ Start transaction
  │       ├─ Call target method
  │       ├─ Commit transaction
  │       └─ Return result
  └─ Store result in cache
  └─ Return CompletableFuture
  ↓
Client receives: CompletableFuture<User>
```

**Order matters!** Defined by `@Order` or `Ordered` interface.

---

## 11. Proxy Gotchas

### Internal Calls Don't Go Through Proxy

```java
@Service
public class MyService {

    public void methodA() {
        methodB(); // ❌ Direct call - doesn't go through proxy!
    }

    @Transactional
    public void methodB() {
        // Transaction NOT started!
    }
}
```

**Why?**

```java
// The proxy
public class MyService$$Proxy extends MyService {

    private MyService target;

    @Override
    public void methodA() {
        // No interceptors for methodA
        target.methodA(); // Direct call to target
    }

    @Override
    public void methodB() {
        // Start transaction
        target.methodB(); // Transaction works
    }
}

// When you call methodA():
proxy.methodA()
  → target.methodA()
      → target.methodB() // Internal call, NOT proxy.methodB()!
```

**Solution**:

```java
@Service
public class MyService {

    @Autowired
    private MyService self; // Inject the proxy

    public void methodA() {
        self.methodB(); // ✅ Goes through proxy - transaction works!
    }

    @Transactional
    public void methodB() {
        // Transaction started correctly
    }
}
```

### Private Methods Can't Be Proxied

```java
@Service
public class MyService {

    @Transactional
    private void saveUser(User user) {
        // ❌ DOESN'T WORK! Private methods can't be overridden
    }
}
```

**Why?**

```java
// CGLIB tries to create:
public class MyService$$Proxy extends MyService {

    @Override
    private void saveUser(User user) {
        // ❌ ERROR: Can't override private method!
    }
}
```

**Solution**: Make method `public` or `protected`.

---

## 12. Bean Destruction

When application shuts down:

```java
public class DisposableBeanAdapter implements DisposableBean {

    @Override
    public void destroy() throws Exception {
        // 1. Call @PreDestroy methods
        for (Method method : bean.getClass().getMethods()) {
            if (method.isAnnotationPresent(PreDestroy.class)) {
                method.invoke(bean);
            }
        }

        // 2. Call DisposableBean.destroy()
        if (bean instanceof DisposableBean) {
            ((DisposableBean) bean).destroy();
        }

        // 3. Call custom destroy method (@Bean(destroyMethod))
        if (destroyMethodName != null) {
            Method destroyMethod = bean.getClass().getMethod(destroyMethodName);
            destroyMethod.invoke(bean);
        }
    }
}
```

**Example**:

```java
@Service
public class MyService implements DisposableBean {

    @PreDestroy
    public void cleanup() {
        System.out.println("1. @PreDestroy called");
    }

    @Override
    public void destroy() {
        System.out.println("2. DisposableBean.destroy() called");
    }
}
```

---

## 13. Complete Lifecycle Example

```java
@Service
public class LifecycleExample implements InitializingBean, DisposableBean {

    @Value("${my.property}")
    private String property;

    private Dependency dependency;

    // 1. Constructor
    public LifecycleExample() {
        System.out.println("1. Constructor: property=" + property); // null
    }

    // 2. Dependency injection
    @Autowired
    public void setDependency(Dependency dependency) {
        System.out.println("2. Dependency injection: property=" + property); // set
        this.dependency = dependency;
    }

    // 3. @PostConstruct
    @PostConstruct
    public void init() {
        System.out.println("3. @PostConstruct: property=" + property); // set
    }

    // 4. InitializingBean
    @Override
    public void afterPropertiesSet() {
        System.out.println("4. afterPropertiesSet: property=" + property); // set
    }

    // 5. Bean ready (proxy created if needed)

    // 6. @PreDestroy (on shutdown)
    @PreDestroy
    public void cleanup() {
        System.out.println("6. @PreDestroy");
    }

    // 7. DisposableBean (on shutdown)
    @Override
    public void destroy() {
        System.out.println("7. DisposableBean.destroy()");
    }
}
```

**Output**:

```
1. Constructor: property=null
2. Dependency injection: property=actual-value
3. @PostConstruct: property=actual-value
4. afterPropertiesSet: property=actual-value
... application runs ...
6. @PreDestroy
7. DisposableBean.destroy()
```

---

## 14. Key Takeaways

### Bean Lifecycle

1. **Instantiation**: Constructor called via reflection
2. **Dependency injection**: Dependencies injected (constructor/field/setter)
3. **BeanPostProcessors (before)**: @Value, @Resource injected
4. **Initialization**: @PostConstruct, InitializingBean called
5. **BeanPostProcessors (after)**: Proxies created
6. **Ready**: Bean available for use
7. **Destruction**: @PreDestroy, DisposableBean called

### Proxies

- **CGLIB**: Subclass the target (default in Spring Boot)
- **JDK**: Implement same interfaces as target
- **Created**: In `postProcessAfterInitialization`
- **Why**: Add cross-cutting concerns (transactions, async, caching, security)

### Important Rules

- ✅ Use constructor injection (dependencies immutable)
- ✅ Make proxied methods `public`
- ✅ Use `@Lazy` to break circular dependencies
- ❌ Don't call `@Transactional` methods internally (use self-injection)
- ❌ Don't use field injection (makes testing harder)

---

## What's Next?

Continue to `23-TRANSACTION-MANAGEMENT-INTERNALS.md` for deep dive into how `@Transactional` manages database transactions.

**Completed**:
- ✅ Spring Boot startup
- ✅ Component scanning with ASM
- ✅ Bean lifecycle and proxies

**Next**:
- Transaction management internals
- JPA/Hibernate session management
- Request processing pipeline
