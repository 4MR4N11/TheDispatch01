# Transaction Management Internals: How @Transactional Really Works

## Overview

This document explains **exactly how** Spring manages database transactions when you use `@Transactional`. We'll trace through:
- Transaction lifecycle (begin, commit, rollback)
- Connection pool management (HikariCP)
- Transaction propagation behaviors
- Isolation levels and their impact
- Read-only optimizations
- Transaction synchronization
- Performance implications

**Prerequisites**: Understanding of JDBC, database transactions, and Spring proxies.

---

## 1. The Database Transaction Basics

### ACID Properties

Every database transaction must be:

| Property | Meaning | Example |
|----------|---------|---------|
| **Atomicity** | All-or-nothing | Transfer money: debit AND credit must both succeed |
| **Consistency** | Database constraints maintained | Foreign keys, unique constraints not violated |
| **Isolation** | Concurrent transactions don't interfere | Transaction A doesn't see uncommitted changes from Transaction B |
| **Durability** | Committed changes survive crashes | After commit, data is written to disk |

### JDBC Transaction Management

**Manual transaction management** (what Spring does for you):

```java
// Without Spring
Connection conn = dataSource.getConnection();

try {
    // 1. Start transaction
    conn.setAutoCommit(false);

    // 2. Execute SQL
    PreparedStatement stmt = conn.prepareStatement("INSERT INTO users (username) VALUES (?)");
    stmt.setString(1, "john");
    stmt.executeUpdate();

    // 3. Commit
    conn.commit();

} catch (Exception e) {
    // 4. Rollback on error
    conn.rollback();
    throw e;

} finally {
    // 5. Return connection to pool
    conn.setAutoCommit(true);
    conn.close(); // Returns to pool (doesn't actually close)
}
```

**With Spring** (automatic):

```java
@Transactional
public void createUser(String username) {
    userRepository.save(new User(username));
    // Spring handles: connection, begin, commit/rollback, connection return
}
```

---

## 2. Spring Transaction Infrastructure

### Key Components

```
@Transactional method call
  ↓
[TransactionInterceptor] (Proxy)
  ↓
[PlatformTransactionManager] (Interface)
  ↓ (Implementation)
[JpaTransactionManager] (Your app uses this)
  ↓
[EntityManager] (Hibernate)
  ↓
[Connection] (JDBC)
  ↓
[HikariCP Connection Pool]
  ↓
[PostgreSQL Database]
```

### PlatformTransactionManager

The core interface:

```java
public interface PlatformTransactionManager {

    // Start a transaction
    TransactionStatus getTransaction(TransactionDefinition definition) throws TransactionException;

    // Commit the transaction
    void commit(TransactionStatus status) throws TransactionException;

    // Rollback the transaction
    void rollback(TransactionStatus status) throws TransactionException;
}
```

**Your application** uses `JpaTransactionManager`:

```java
@Configuration
@EnableTransactionManagement
public class JpaConfig {

    @Bean
    public PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }
}
```

---

## 3. Transaction Lifecycle

Let's trace a **complete transaction** for your `UserServiceImpl.getCurrentUser()`:

```java
@Service
public class UserServiceImpl {

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String username) {
        User user = userRepository.findByUsernameWithSubscriptionsAndPosts(username)
            .orElseThrow(() -> new RuntimeException("User not found"));

        // ... mapping code ...

        return userResponse;
    }
}
```

### Step 1: Method Invocation (Proxy)

```java
// Client code
UserResponse response = userService.getCurrentUser("john");

// What actually happens:
UserServiceImpl$$Proxy proxy = (UserServiceImpl$$Proxy) userService;
response = proxy.getCurrentUser("john");
```

**Proxy intercepts**:

```java
public class UserServiceImpl$$Proxy extends UserServiceImpl {

    private TransactionInterceptor transactionInterceptor;
    private UserServiceImpl target;

    @Override
    public UserResponse getCurrentUser(String username) {
        // 1. Get method metadata
        Method method = UserServiceImpl.class.getMethod("getCurrentUser", String.class);

        // 2. Get @Transactional annotation
        Transactional txAnnotation = method.getAnnotation(Transactional.class);

        // 3. Delegate to TransactionInterceptor
        return (UserResponse) transactionInterceptor.invoke(() -> {
            return target.getCurrentUser(username);
        }, txAnnotation);
    }
}
```

### Step 2: TransactionInterceptor

```java
public class TransactionInterceptor {

    private PlatformTransactionManager transactionManager;

    public Object invoke(Callable<?> callback, Transactional txAnnotation) throws Throwable {
        // 1. Create transaction definition
        TransactionDefinition definition = createDefinition(txAnnotation);
        // definition:
        //   propagation = REQUIRED (default)
        //   isolation = DEFAULT (database default)
        //   readOnly = true
        //   timeout = -1 (no timeout)

        // 2. Get or create transaction
        TransactionStatus status = transactionManager.getTransaction(definition);

        try {
            // 3. Execute business logic
            Object result = callback.call();

            // 4. Commit transaction
            transactionManager.commit(status);

            return result;

        } catch (Exception ex) {
            // 5. Rollback transaction
            transactionManager.rollback(status);
            throw ex;
        }
    }
}
```

### Step 3: JpaTransactionManager.getTransaction()

```java
public class JpaTransactionManager extends AbstractPlatformTransactionManager {

    private EntityManagerFactory entityManagerFactory;

    @Override
    protected Object doGetTransaction() {
        // 1. Check if transaction already exists (thread-local)
        EntityManagerHolder emHolder = (EntityManagerHolder)
            TransactionSynchronizationManager.getResource(entityManagerFactory);

        if (emHolder != null) {
            // Transaction exists - use it (propagation behavior applies here)
            return new JpaTransactionObject(emHolder);
        }

        // 2. Create new EntityManager
        EntityManager em = entityManagerFactory.createEntityManager();

        // 3. Get database connection from EntityManager
        SessionImpl session = em.unwrap(SessionImpl.class);
        Connection connection = session.connection();
        //  ↑ Gets connection from HikariCP pool

        // 4. Start database transaction
        connection.setAutoCommit(false); // BEGIN TRANSACTION

        // 5. Apply read-only optimization
        if (definition.isReadOnly()) {
            connection.setReadOnly(true); // Hint to driver
            // PostgreSQL uses this for query optimization
        }

        // 6. Set isolation level (if specified)
        if (definition.getIsolationLevel() != TransactionDefinition.ISOLATION_DEFAULT) {
            connection.setTransactionIsolation(definition.getIsolationLevel());
        }

        // 7. Store in thread-local (for current thread)
        EntityManagerHolder holder = new EntityManagerHolder(em);
        TransactionSynchronizationManager.bindResource(entityManagerFactory, holder);

        return new JpaTransactionObject(holder);
    }
}
```

**Key point**: Connection is stored in `ThreadLocal`, so **same connection** is used for entire transaction.

### Step 4: Business Logic Execution

```java
// Your code executes
User user = userRepository.findByUsernameWithSubscriptionsAndPosts(username)
    .orElseThrow();
```

**What happens**:

```java
// userRepository.findByUsernameWithSubscriptionsAndPosts(username)
//  ↓
[Spring Data JPA Proxy]
  ↓
@Query("SELECT u FROM User u LEFT JOIN FETCH u.subscriptions WHERE u.username = :username")
  ↓
[EntityManager.createQuery()]
  ↓
[Hibernate Session]
  ↓ (uses existing connection from thread-local!)
[Connection] (from HikariCP pool)
  ↓
// SQL: SELECT u.*, s.* FROM users u LEFT JOIN subscriptions s ON ... WHERE u.username = ?
  ↓
[PostgreSQL]
```

**Important**: The **same connection** is used because it's stored in ThreadLocal!

### Step 5: Commit

```java
public class JpaTransactionManager {

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
        JpaTransactionObject txObject = (JpaTransactionObject) status.getTransaction();

        // 1. Flush EntityManager (write to database)
        EntityManager em = txObject.getEntityManagerHolder().getEntityManager();
        em.flush();
        // Hibernate executes: INSERT, UPDATE, DELETE statements

        // 2. Commit JDBC transaction
        Connection connection = em.unwrap(SessionImpl.class).connection();
        connection.commit();
        // Database: COMMIT

        // 3. Clear EntityManager (first-level cache)
        em.clear();

        // 4. Return connection to pool
        connection.setAutoCommit(true); // Reset
        connection.setReadOnly(false);  // Reset
        em.close(); // Returns connection to HikariCP

        // 5. Remove from thread-local
        TransactionSynchronizationManager.unbindResource(entityManagerFactory);
    }
}
```

### Step 6: Rollback (on exception)

```java
@Override
protected void doRollback(DefaultTransactionStatus status) {
    JpaTransactionObject txObject = (JpaTransactionObject) status.getTransaction();

    // 1. Rollback JDBC transaction (DON'T flush!)
    EntityManager em = txObject.getEntityManagerHolder().getEntityManager();
    Connection connection = em.unwrap(SessionImpl.class).connection();
    connection.rollback();
    // Database: ROLLBACK

    // 2. Clear EntityManager (discard changes)
    em.clear();

    // 3. Return connection to pool
    connection.setAutoCommit(true);
    em.close();

    // 4. Remove from thread-local
    TransactionSynchronizationManager.unbindResource(entityManagerFactory);
}
```

---

## 4. Connection Pool (HikariCP)

### Why Connection Pools?

**Without pool** (slow):

```java
// Every transaction:
Connection conn = DriverManager.getConnection("jdbc:postgresql://localhost:5432/blog", "user", "pass");
// Takes 50-100ms to establish TCP connection + authenticate
```

**With pool** (fast):

```java
// Pre-created connections ready to use:
Connection conn = hikariDataSource.getConnection();
// Takes <1ms (just borrows from pool)
```

### HikariCP Configuration

Spring Boot auto-configures HikariCP:

```java
// Auto-configured by Spring Boot
@Bean
public DataSource dataSource() {
    HikariConfig config = new HikariConfig();

    // Your properties
    config.setJdbcUrl("jdbc:postgresql://localhost:5432/blog");
    config.setUsername("blog");
    config.setPassword("blog");

    // HikariCP defaults (optimized!)
    config.setMaximumPoolSize(10);        // Max 10 connections
    config.setMinimumIdle(10);            // Keep 10 connections ready
    config.setConnectionTimeout(30000);   // Wait max 30s for connection
    config.setIdleTimeout(600000);        // Close idle connections after 10min
    config.setMaxLifetime(1800000);       // Recreate connections after 30min
    config.setLeakDetectionThreshold(0);  // Detect connection leaks

    return new HikariDataSource(config);
}
```

### Connection Lifecycle

```
[HikariCP Pool]
  │
  ├─ [Connection 1] ← IDLE (waiting)
  ├─ [Connection 2] ← IN_USE (borrowed by transaction)
  ├─ [Connection 3] ← IN_USE (borrowed by transaction)
  ├─ [Connection 4] ← IDLE
  ├─ [Connection 5] ← IDLE
  ├─ [Connection 6] ← IDLE
  ├─ [Connection 7] ← IDLE
  ├─ [Connection 8] ← IDLE
  ├─ [Connection 9] ← IDLE
  └─ [Connection 10] ← IDLE
```

**When transaction starts**:

```java
// Thread 1: userService.getCurrentUser("john")
Connection conn = hikariPool.getConnection();
// Borrows Connection 2, marks as IN_USE
```

**When transaction ends**:

```java
conn.close(); // Doesn't actually close!
// Returns Connection 2 to pool, marks as IDLE
```

### Pool Exhaustion

If all 10 connections are in use, **new requests wait**:

```java
// Thread 11 tries to start transaction
Connection conn = hikariPool.getConnection();
// Waits up to 30 seconds (connectionTimeout)
// If no connection available after 30s → SQLTimeoutException
```

**Solution**: Increase pool size or optimize slow queries.

---

## 5. Transaction Propagation

Propagation defines behavior when `@Transactional` method calls another `@Transactional` method.

### REQUIRED (Default)

```java
@Transactional
public void methodA() {
    methodB(); // Uses same transaction
}

@Transactional(propagation = Propagation.REQUIRED)
public void methodB() {
    // Uses transaction from methodA
}
```

**What happens**:

```
methodA() starts
  ↓
[Transaction T1] BEGIN
  ↓
methodA() calls methodB()
  ↓
methodB() checks: Transaction exists? YES (T1)
  ↓
methodB() reuses T1 (NO new transaction)
  ↓
methodB() completes
  ↓
methodA() completes
  ↓
[Transaction T1] COMMIT
```

**If methodB() throws exception**:

```
methodA() → methodB() throws exception
            ↓
         ROLLBACK T1
            ↓
   methodA() also rolled back!
```

### REQUIRES_NEW

```java
@Transactional
public void methodA() {
    methodB(); // Creates NEW transaction
}

@Transactional(propagation = Propagation.REQUIRES_NEW)
public void methodB() {
    // NEW transaction, independent of methodA
}
```

**What happens**:

```
methodA() starts
  ↓
[Transaction T1] BEGIN
  ↓
methodA() calls methodB()
  ↓
methodB() suspends T1, starts T2
  ↓
[Transaction T2] BEGIN
  ↓
methodB() executes in T2
  ↓
[Transaction T2] COMMIT (T2 independent!)
  ↓
methodA() resumes T1
  ↓
[Transaction T1] COMMIT
```

**Use case**: Logging that must persist even if main transaction rolls back:

```java
@Transactional
public void processPayment(Payment payment) {
    // Main transaction

    auditService.logAttempt(payment); // Uses REQUIRES_NEW

    if (!validate(payment)) {
        throw new Exception("Invalid");
        // Main transaction rolls back
        // BUT audit log is already committed!
    }
}

@Service
public class AuditService {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAttempt(Payment payment) {
        auditRepository.save(new AuditLog(payment));
        // Commits immediately (independent transaction)
    }
}
```

### NESTED

```java
@Transactional
public void methodA() {
    methodB(); // Creates SAVEPOINT
}

@Transactional(propagation = Propagation.NESTED)
public void methodB() {
    // Nested transaction (using SAVEPOINT)
}
```

**What happens** (uses database savepoints):

```sql
-- methodA() starts
BEGIN;

-- methodA() calls methodB()
SAVEPOINT savepoint_1;

-- methodB() executes
INSERT INTO ...;

-- methodB() succeeds
RELEASE SAVEPOINT savepoint_1;

-- OR if methodB() fails:
ROLLBACK TO SAVEPOINT savepoint_1; -- Only rollback to savepoint

-- methodA() continues...
COMMIT; -- Commits everything (except rolled-back savepoint)
```

**Use case**: Try operation, rollback only that operation if it fails:

```java
@Transactional
public void importUsers(List<User> users) {
    for (User user : users) {
        try {
            userService.saveUser(user); // NESTED transaction
        } catch (Exception e) {
            // This user fails → rollback to savepoint
            // Other users still imported!
            log.error("Failed to import user: " + user.getUsername());
        }
    }
    // Commit all successful imports
}

@Service
public class UserService {

    @Transactional(propagation = Propagation.NESTED)
    public void saveUser(User user) {
        userRepository.save(user);
    }
}
```

### All Propagation Levels

| Propagation | Behavior |
|------------|----------|
| `REQUIRED` (default) | Join existing transaction or create new |
| `REQUIRES_NEW` | Always create new transaction, suspend current |
| `NESTED` | Create savepoint within existing transaction |
| `SUPPORTS` | Join transaction if exists, otherwise non-transactional |
| `NOT_SUPPORTED` | Execute non-transactionally, suspend current transaction |
| `NEVER` | Execute non-transactionally, throw exception if transaction exists |
| `MANDATORY` | Must have existing transaction, throw exception if none |

---

## 6. Isolation Levels

Isolation level controls **what one transaction can see** from other concurrent transactions.

### Read Phenomena

| Phenomenon | Description | Example |
|------------|-------------|---------|
| **Dirty Read** | Read uncommitted changes from other transaction | Transaction A reads data modified by Transaction B (not yet committed) |
| **Non-Repeatable Read** | Same query returns different results within transaction | Transaction A reads row twice, Transaction B updates row in between |
| **Phantom Read** | Same query returns different row count within transaction | Transaction A counts rows twice, Transaction B inserts row in between |

### Isolation Levels (From Least to Most Isolated)

#### READ_UNCOMMITTED (Don't use!)

```java
@Transactional(isolation = Isolation.READ_UNCOMMITTED)
public User getUser(Long id) {
    return userRepository.findById(id).orElseThrow();
}
```

**Allows**: Dirty reads, non-repeatable reads, phantom reads

**Example problem**:

```sql
-- Transaction A
BEGIN;
UPDATE users SET balance = 1000 WHERE id = 1;
-- NOT COMMITTED YET

-- Transaction B (READ_UNCOMMITTED)
SELECT balance FROM users WHERE id = 1;
-- Returns 1000 (dirty read!)

-- Transaction A
ROLLBACK; -- Oops! Transaction A rolled back

-- Transaction B used INCORRECT data (balance was never actually 1000)
```

#### READ_COMMITTED (PostgreSQL default)

```java
@Transactional(isolation = Isolation.READ_COMMITTED)
public User getUser(Long id) {
    return userRepository.findById(id).orElseThrow();
}
```

**Allows**: Non-repeatable reads, phantom reads
**Prevents**: Dirty reads

**Example**:

```sql
-- Transaction A
BEGIN;
SELECT username FROM users WHERE id = 1;
-- Returns: "john"

-- Transaction B
BEGIN;
UPDATE users SET username = 'jane' WHERE id = 1;
COMMIT;

-- Transaction A (still open)
SELECT username FROM users WHERE id = 1;
-- Returns: "jane" (NON-REPEATABLE READ! Different from first read)
```

#### REPEATABLE_READ

```java
@Transactional(isolation = Isolation.REPEATABLE_READ)
public User getUser(Long id) {
    return userRepository.findById(id).orElseThrow();
}
```

**Allows**: Phantom reads (in some databases)
**Prevents**: Dirty reads, non-repeatable reads

**Example**:

```sql
-- Transaction A (REPEATABLE_READ)
BEGIN;
SELECT username FROM users WHERE id = 1;
-- Returns: "john"

-- Transaction B
BEGIN;
UPDATE users SET username = 'jane' WHERE id = 1;
COMMIT;

-- Transaction A (still open)
SELECT username FROM users WHERE id = 1;
-- Returns: "john" (SAME as first read! Consistent snapshot)
```

**PostgreSQL**: Actually prevents phantom reads too (uses MVCC).

#### SERIALIZABLE (Strictest)

```java
@Transactional(isolation = Isolation.SERIALIZABLE)
public void transferMoney(Long fromId, Long toId, BigDecimal amount) {
    // Transactions execute as if serial (one after another)
}
```

**Prevents**: ALL anomalies (transactions are completely isolated)

**Cost**: Highest performance overhead, most locking

**Example**:

```sql
-- Transaction A (SERIALIZABLE)
BEGIN;
SELECT SUM(balance) FROM accounts WHERE user_id = 1;
-- Sum: 1000

-- Transaction B (SERIALIZABLE)
BEGIN;
INSERT INTO accounts (user_id, balance) VALUES (1, 500);
COMMIT;

-- Transaction A
SELECT SUM(balance) FROM accounts WHERE user_id = 1;
-- Still returns 1000 (doesn't see new row from Transaction B)
```

### Default in Your Application

```java
// Uses PostgreSQL default: READ_COMMITTED
@Transactional
public void myMethod() { }

// Override:
@Transactional(isolation = Isolation.SERIALIZABLE)
public void criticalMethod() { }
```

---

## 7. Read-Only Optimization

### What readOnly Does

```java
@Transactional(readOnly = true)
public UserResponse getCurrentUser(String username) {
    // This method only reads data
}
```

**Spring optimizations**:

1. **Flush mode**: Set to `MANUAL` (Hibernate won't check dirty objects)
```java
entityManager.setFlushMode(FlushModeType.MANUAL);
// Skips dirty checking → faster
```

2. **JDBC hint**: `connection.setReadOnly(true)`
```java
// PostgreSQL can route to read replica
```

3. **No write operations**: Spring may throw exception if you try to write
```java
@Transactional(readOnly = true)
public void readOnlyMethod() {
    userRepository.save(new User()); // ❌ May throw exception
}
```

### Read-Only Performance Impact

**Without readOnly**:

```java
@Transactional
public List<User> getAllUsers() {
    List<User> users = userRepository.findAll(); // 1000 users

    // Hibernate tracks ALL 1000 entities for changes (dirty checking)
    // Before commit: compares all 1000 entities to detect changes
    // Cost: Memory + CPU
}
```

**With readOnly**:

```java
@Transactional(readOnly = true)
public List<User> getAllUsers() {
    List<User> users = userRepository.findAll(); // 1000 users

    // Hibernate does NOT track changes (flush mode = MANUAL)
    // No dirty checking
    // Cost: Almost zero
}
```

**Benchmark**:
- Reading 1000 entities without `readOnly`: ~50ms (30ms query + 20ms dirty checking)
- Reading 1000 entities with `readOnly`: ~30ms (30ms query + 0ms overhead)

---

## 8. Transaction Rollback Rules

### Default Rollback Behavior

```java
@Transactional
public void myMethod() {
    // Rollback on: RuntimeException, Error
    // Commit on: Checked exceptions (Exception)
}
```

**Example**:

```java
@Transactional
public void createUser(User user) throws IOException {
    userRepository.save(user);

    if (someCondition) {
        throw new RuntimeException("Error");
        // ✅ ROLLBACK (RuntimeException)
    }

    if (anotherCondition) {
        throw new IOException("File error");
        // ❌ COMMIT! (Checked exception)
    }
}
```

### Custom Rollback Rules

```java
@Transactional(
    rollbackFor = Exception.class,  // Rollback on ANY exception
    noRollbackFor = SpecificException.class  // Except this one
)
public void myMethod() throws Exception {
    // ...
}
```

**Example**:

```java
@Transactional(rollbackFor = IOException.class)
public void importFile(File file) throws IOException {
    List<User> users = parseFile(file); // May throw IOException

    for (User user : users) {
        userRepository.save(user);
    }

    // IOException → ROLLBACK (all users discarded)
}
```

### Programmatic Rollback

```java
@Transactional
public void processPayment(Payment payment) {
    paymentRepository.save(payment);

    if (!validate(payment)) {
        // Mark transaction for rollback WITHOUT throwing exception
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        return; // Method completes normally, but transaction rolls back
    }

    sendConfirmation(payment);
}
```

---

## 9. Transaction Synchronization

### What is Synchronization?

Execute code **after transaction commit**:

```java
@Transactional
public void createUser(User user) {
    userRepository.save(user);

    // Register callback for AFTER commit
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // This runs AFTER database commit
                emailService.sendWelcomeEmail(user);
            }
        }
    );
}
```

**Why?**

Imagine sending email **before** commit:

```java
@Transactional
public void createUser(User user) {
    userRepository.save(user);

    emailService.sendWelcomeEmail(user); // ❌ Email sent

    // ... some code that throws exception
    throw new RuntimeException();
    // ROLLBACK → User not created
    // But email already sent! ❌ Inconsistent state
}
```

**Solution** (send email after commit):

```java
@Transactional
public void createUser(User user) {
    userRepository.save(user);

    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                emailService.sendWelcomeEmail(user); // ✅ Only if transaction succeeds
            }
        }
    );

    throw new RuntimeException(); // ROLLBACK
    // Email NOT sent ✅
}
```

### @TransactionalEventListener

Spring provides annotation-based synchronization:

```java
@Service
public class UserService {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Transactional
    public void createUser(User user) {
        userRepository.save(user);

        // Publish event
        eventPublisher.publishEvent(new UserCreatedEvent(user));
    }
}

@Component
public class EmailListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserCreated(UserCreatedEvent event) {
        // Runs AFTER transaction commits
        emailService.sendWelcomeEmail(event.getUser());
    }
}
```

**Transaction phases**:

- `BEFORE_COMMIT`: Before commit (still in transaction)
- `AFTER_COMMIT`: After successful commit
- `AFTER_ROLLBACK`: After rollback
- `AFTER_COMPLETION`: After commit OR rollback

---

## 10. Performance Considerations

### Transaction Too Long

**Problem**:

```java
@Transactional
public void processUsers() {
    List<User> users = userRepository.findAll(); // 10,000 users

    for (User user : users) {
        // Expensive operation (1 second per user)
        processUser(user);
    }
    // Transaction holds connection for 10,000 seconds! ❌
    // Connection pool exhaustion!
}
```

**Solution**: Smaller transactions

```java
public void processUsers() {
    List<User> users = userRepository.findAll();

    for (User user : users) {
        processUserInTransaction(user); // Separate transaction per user
    }
}

@Transactional
private void processUserInTransaction(User user) {
    // Transaction only for this user (short-lived)
    processUser(user);
}
```

### N+1 Query Problem

**Problem**:

```java
@Transactional(readOnly = true)
public List<PostResponse> getAllPosts() {
    List<Post> posts = postRepository.findAll(); // 1 query

    return posts.stream()
        .map(post -> {
            User author = post.getAuthor(); // N queries (lazy loading)
            return new PostResponse(post, author.getUsername());
        })
        .toList();
    // Total: 1 + N queries ❌
}
```

**Solution**: JOIN FETCH

```java
@Transactional(readOnly = true)
public List<PostResponse> getAllPosts() {
    // 1 query with JOIN
    List<Post> posts = postRepository.findAllWithAuthors();

    return posts.stream()
        .map(post -> new PostResponse(post, post.getAuthor().getUsername()))
        .toList();
    // Total: 1 query ✅
}

// Repository
@Query("SELECT p FROM Post p LEFT JOIN FETCH p.author")
List<Post> findAllWithAuthors();
```

### Connection Leak

**Problem**:

```java
@Transactional
public void buggyMethod() {
    userRepository.findById(1L);

    throw new Error(); // Error (not Exception)
    // Spring doesn't catch Error by default!
    // Transaction not rolled back
    // Connection not returned to pool ❌
}
```

**Solution**: Always rollback on Throwable

```java
@Transactional(rollbackFor = Throwable.class)
public void safeMethod() {
    userRepository.findById(1L);

    throw new Error(); // Now caught and rolled back ✅
}
```

---

## 11. Debugging Transactions

### Enable Transaction Logging

```properties
# application.properties
logging.level.org.springframework.transaction=TRACE
logging.level.org.springframework.orm.jpa=DEBUG
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE
```

**Output**:

```
TRACE o.s.t.i.TransactionInterceptor : Getting transaction for [UserServiceImpl.getCurrentUser]
DEBUG o.h.SQL : select user0_.id as id1_0_, user0_.username as username2_0_ from users user0_ where user0_.username=?
TRACE o.h.t.d.s.BasicBinder : binding parameter [1] as [VARCHAR] - [john]
TRACE o.s.t.i.TransactionInterceptor : Completing transaction for [UserServiceImpl.getCurrentUser]
```

### Detect Connection Leaks

```properties
spring.datasource.hikari.leak-detection-threshold=60000
# Warn if connection held for >60 seconds
```

**Output**:

```
WARN com.zaxxer.hikari.pool.ProxyLeakTask : Connection leak detection triggered for connection
    at UserServiceImpl.processUsers(UserServiceImpl.java:42)
```

---

## 12. Key Takeaways

### Transaction Lifecycle

1. **Proxy intercepts** `@Transactional` method
2. **Get connection** from HikariCP pool
3. **Begin transaction**: `connection.setAutoCommit(false)`
4. **Execute business logic** (all queries use same connection)
5. **Commit**: `entityManager.flush()` + `connection.commit()`
6. **Return connection** to pool

### Best Practices

- ✅ Use `@Transactional(readOnly = true)` for read-only methods
- ✅ Keep transactions short (minimize connection hold time)
- ✅ Use JOIN FETCH to avoid N+1 queries
- ✅ Use appropriate propagation (REQUIRES_NEW for independent operations)
- ✅ Use appropriate isolation (default READ_COMMITTED is usually fine)
- ❌ Don't hold transactions during slow operations (HTTP calls, file I/O)
- ❌ Don't call `@Transactional` methods internally (use self-injection)

### Common Pitfalls

1. **Internal calls** don't go through proxy → no transaction
2. **Private methods** can't be proxied → no transaction
3. **Checked exceptions** don't rollback by default → specify `rollbackFor`
4. **Long transactions** exhaust connection pool → break into smaller transactions
5. **N+1 queries** slow performance → use JOIN FETCH

---

## What's Next?

Continue to `24-JPA-HIBERNATE-INTERNALS.md` for deep dive into Hibernate's internal mechanisms.

**Completed**:
- ✅ Spring Boot startup
- ✅ Component scanning
- ✅ Bean lifecycle and proxies
- ✅ Transaction management

**Next**:
- JPA/Hibernate persistence context
- First and second-level cache
- Dirty checking mechanism
- Lazy loading proxies
