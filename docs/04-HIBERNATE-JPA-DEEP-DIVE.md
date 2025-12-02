# Hibernate & JPA - Deep Dive: How Database Magic Happens

## What Is JPA? What Is Hibernate?

**JPA** = Java Persistence API (specification/interface)
**Hibernate** = Implementation of JPA (actual code)

```
JPA (Interface)          Hibernate (Implementation)
    ↓                            ↓
"Here's how ORM            "Here's the actual code
 should work"               that does it"

Think of it like:
USB Standard (JPA)     vs    Actual USB Drive (Hibernate)
```

**Why this matters:**
- You write code using JPA interfaces
- Hibernate does the actual work
- You could swap Hibernate for EclipseLink (another JPA implementation)

---

## Part 1: What Is ORM (Object-Relational Mapping)?

### The Problem ORM Solves

**Without ORM** (manual JDBC):
```java
// Query database
String sql = "SELECT * FROM users WHERE username = ?";
PreparedStatement stmt = connection.prepareStatement(sql);
stmt.setString(1, "john");
ResultSet rs = stmt.executeQuery();

// Manually map results to objects
User user = new User();
if (rs.next()) {
    user.setId(rs.getLong("id"));
    user.setUsername(rs.getString("username"));
    user.setEmail(rs.getString("email"));
    user.setPassword(rs.getString("password"));
    user.setFirstName(rs.getString("first_name"));
    user.setLastName(rs.getString("last_name"));
    user.setRole(Role.valueOf(rs.getString("role")));
    user.setBanned(rs.getBoolean("banned"));
    user.setCreatedAt(rs.getTimestamp("created_at"));
    user.setUpdatedAt(rs.getTimestamp("updated_at"));
    // Imagine doing this for every query! 😱
}
```

**With ORM** (Hibernate):
```java
// That's it! Hibernate does everything
User user = userRepository.findByUsername("john");
```

### How ORM Works

```
Java Objects          ORM (Hibernate)           Database Tables
┌──────────┐              ↕️                    ┌──────────┐
│  User    │         Translates                 │  users   │
│  .id     │  ←─────────────────────────→      │  id      │
│  .name   │         Automatically              │  name    │
└──────────┘                                    └──────────┘
```

---

## Part 2: The Persistence Context (First-Level Cache)

### What Is It?

The **Persistence Context** is a cache of entities in memory. Think of it as Hibernate's short-term memory.

```java
// Persistence Context is like a Map<Class, Map<ID, Object>>
Map<User.class, Map<
    1L → User(id=1, username="john", ...),
    2L → User(id=2, username="jane", ...)
>>
```

### Why Does It Exist?

1. **Avoid duplicate queries**
2. **Track changes to entities**
3. **Ensure identity** (same ID = same object)
4. **Batch updates** (write to DB only when needed)

### How It Works

```java
// Example transaction
@Transactional
public void example() {
    // 1. First findById - Queries database
    User user1 = userRepository.findById(1L).get();
    // Persistence Context: {1 → User(id=1)}
    // SQL: SELECT * FROM users WHERE id = 1

    // 2. Second findById - NO database query!
    User user2 = userRepository.findById(1L).get();
    // Persistence Context: {1 → User(id=1)}
    // NO SQL! Returns cached object

    // 3. They're the SAME object
    System.out.println(user1 == user2);  // true (same reference!)

    // 4. Change the entity
    user1.setUsername("newname");
    // Persistence Context marks it as "dirty"

    // 5. Transaction commits
    // Hibernate: "user1 changed, write to database"
    // SQL: UPDATE users SET username='newname' WHERE id=1
}
```

### Persistence Context Lifecycle

```java
@Transactional
public void lifecycle() {
    // ┌─────────────────────────────┐
    // │  Transaction starts         │
    // │  Persistence Context created│
    // └─────────────────────────────┘

    User user = new User();
    user.setUsername("john");
    // State: TRANSIENT (not in persistence context)

    userRepository.save(user);
    // State: MANAGED (in persistence context)
    // Hibernate tracks all changes

    user.setEmail("john@example.com");
    // Change tracked! No save() needed

    userRepository.delete(user);
    // State: REMOVED (marked for deletion)

    // ┌─────────────────────────────┐
    // │  Transaction commits        │
    // │  Changes written to DB      │
    // │  Persistence Context closed │
    // └─────────────────────────────┘

    // State: DETACHED (no longer tracked)
    user.setUsername("jane");  // Change NOT tracked!
}
```

### Entity States Diagram

```
NEW/TRANSIENT
(just created with new)
        ↓ save()
    MANAGED
(tracked by persistence context)
        ↓ delete()
    REMOVED
(marked for deletion)
        ↓ commit()
   DELETED FROM DB

MANAGED → (close context) → DETACHED
(no longer tracked)
```

---

## Part 3: How save() Actually Works

### What Happens Internally

```java
userRepository.save(user);
```

**Step-by-step**:

```java
// 1. Check if entity has ID
if (user.getId() == null) {
    // NEW entity - INSERT

    // 2. Generate ID (if @GeneratedValue)
    Long id = idGenerator.generateId();
    user.setId(id);

    // 3. Add to persistence context
    persistenceContext.put(User.class, id, user);

    // 4. Mark for INSERT
    persistenceContext.markAsInsert(user);

    // 5. Don't write to DB yet! Wait for transaction commit
} else {
    // Existing entity - UPDATE

    // 6. Check if in persistence context
    User existing = persistenceContext.get(User.class, user.getId());

    if (existing == null) {
        // 7. MERGE - bring into persistence context
        existing = loadFromDatabase(user.getId());
        copyProperties(user, existing);
        persistenceContext.put(User.class, user.getId(), existing);
    }

    // 8. Mark for UPDATE (if dirty)
    if (hasChanged(existing)) {
        persistenceContext.markAsUpdate(existing);
    }
}

// 9. On transaction commit:
//    - Execute all INSERTs
//    - Execute all UPDATEs
//    - Execute all DELETEs
//    - Clear persistence context
```

### save() vs persist() vs merge()

```java
// save() = persist() + merge()
userRepository.save(user);  // Works for new AND existing

// persist() = only for new entities
entityManager.persist(user);  // Throws exception if ID exists

// merge() = only for existing entities
entityManager.merge(user);  // Updates existing or inserts if not found
```

---

## Part 4: Lazy Loading vs Eager Loading

### The Problem

```java
@Entity
public class Post {
    @ManyToOne
    private User author;  // Should we load author immediately?

    @OneToMany
    private List<Comment> comments;  // Should we load all comments?
}
```

If we load everything immediately:
```java
Post post = postRepository.findById(1L);
// Loads:
// - Post
// - Author (User)
// - Author's posts (List<Post>)
// - Author's followers (Set<User>)
// - Comments (List<Comment>)
// - Comment authors (List<User>)
// - ... infinite recursion! 😱
```

### Lazy Loading (Default for Collections)

```java
@OneToMany(fetch = FetchType.LAZY)  // Default
private List<Comment> comments;
```

**How it works**:
```java
Post post = postRepository.findById(1L);
// SQL: SELECT * FROM posts WHERE id = 1
// Comments NOT loaded yet!

// Accessing comments triggers query
List<Comment> comments = post.getComments();
// SQL: SELECT * FROM comments WHERE post_id = 1
// Now comments are loaded
```

**Lazy loading is implemented using PROXIES**:

```java
// Hibernate creates a proxy object
List<Comment> comments = new HibernateProxyList() {
    boolean loaded = false;
    List<Comment> realList = null;

    @Override
    public int size() {
        if (!loaded) {
            // Trigger database query
            realList = database.query("SELECT * FROM comments WHERE post_id = ?");
            loaded = true;
        }
        return realList.size();
    }
};
```

### Eager Loading

```java
@ManyToOne(fetch = FetchType.EAGER)
private User author;
```

**How it works**:
```java
Post post = postRepository.findById(1L);
// SQL: SELECT p.*, u.* FROM posts p JOIN users u ON p.author_id = u.id WHERE p.id = 1
// Author is loaded immediately!

User author = post.getAuthor();
// No additional query! Already in memory
```

### The N+1 Problem

```java
// Get all posts
List<Post> posts = postRepository.findAll();
// SQL: SELECT * FROM posts  (1 query)

// Loop through posts
for (Post post : posts) {
    String author = post.getAuthor().getUsername();
    // SQL: SELECT * FROM users WHERE id = ?  (N queries!)
}

// If 100 posts → 1 + 100 = 101 queries! 😱
```

**Solution - JOIN FETCH**:
```java
@Query("SELECT p FROM Post p JOIN FETCH p.author")
List<Post> findAllWithAuthors();
// SQL: SELECT p.*, u.* FROM posts p JOIN users u ON p.author_id = u.id
// Only 1 query! All authors loaded at once
```

### Lazy Loading Outside Transaction (LazyInitializationException)

```java
@Transactional
public Post getPost(Long id) {
    return postRepository.findById(id).get();
    // Transaction ends here
    // Persistence context closed
}

// Controller
public String showPost(Long id) {
    Post post = postService.getPost(id);
    // Try to access lazy-loaded comments
    List<Comment> comments = post.getComments();
    // ERROR: LazyInitializationException!
    // Persistence context is closed!
}
```

**Solution 1 - JOIN FETCH**:
```java
@Query("SELECT p FROM Post p JOIN FETCH p.comments WHERE p.id = :id")
Post findByIdWithComments(@Param("id") Long id);
```

**Solution 2 - @Transactional on controller**:
```java
@Transactional(readOnly = true)
public String showPost(Long id) {
    Post post = postService.getPost(id);
    List<Comment> comments = post.getComments();  // ✅ Works!
}
```

---

## Part 5: Transactions

### What Is a Transaction?

A transaction is a **unit of work** that either:
- **Completes fully** (COMMIT), or
- **Reverts completely** (ROLLBACK)

```java
@Transactional
public void transferMoney(User from, User to, int amount) {
    from.setBalance(from.getBalance() - amount);  // Withdraw
    to.setBalance(to.getBalance() + amount);      // Deposit

    if (from.getBalance() < 0) {
        throw new RuntimeException("Insufficient funds");
        // ROLLBACK - both operations undone!
    }
    // COMMIT - both operations saved
}
```

### How @Transactional Works

```java
@Service
public class UserService {

    @Transactional
    public void updateUser(Long id, String name) {
        User user = userRepository.findById(id).get();
        user.setUsername(name);
    }
}
```

**What Spring does** (simplified):
```java
// Spring creates a PROXY of UserService
public class UserServiceProxy extends UserService {

    private EntityManager entityManager;
    private TransactionManager transactionManager;

    @Override
    public void updateUser(Long id, String name) {
        // 1. Begin transaction
        transactionManager.begin();

        // 2. Open persistence context
        entityManager.openPersistenceContext();

        try {
            // 3. Call actual method
            super.updateUser(id, name);

            // 4. Flush changes to database
            entityManager.flush();

            // 5. Commit transaction
            transactionManager.commit();

        } catch (Exception e) {
            // 6. Rollback on error
            transactionManager.rollback();
            throw e;
        } finally {
            // 7. Close persistence context
            entityManager.close();
        }
    }
}
```

### Transaction Propagation

```java
@Transactional(propagation = Propagation.REQUIRED)  // Default
public void methodA() {
    methodB();
}

@Transactional(propagation = Propagation.REQUIRED)
public void methodB() {
    // Uses same transaction as methodA
}
```

**Propagation types**:

| Type | Behavior |
|------|----------|
| `REQUIRED` (default) | Use existing transaction, or create new one |
| `REQUIRES_NEW` | Always create new transaction (suspend existing) |
| `MANDATORY` | Must be called within transaction (error if not) |
| `NEVER` | Must NOT be called within transaction |
| `SUPPORTS` | Use transaction if exists, run without if not |

```java
@Transactional
public void methodA() {
    createUser();  // Part of same transaction
    createPost();  // Part of same transaction
    // If createPost() fails, createUser() is rolled back too
}

@Transactional(propagation = Propagation.REQUIRES_NEW)
public void createPost() {
    // New transaction - independent of methodA
    // If this fails, createUser() is NOT rolled back
}
```

---

## Part 6: How Repositories Work

### What Is Spring Data JPA?

Spring Data JPA **generates repository implementations** automatically.

```java
// You write:
public interface UserRepository extends JpaRepository<User, Long> {
    User findByUsername(String username);
}

// Spring generates (simplified):
public class UserRepositoryImpl implements UserRepository {

    @Autowired
    private EntityManager em;

    @Override
    public User findByUsername(String username) {
        TypedQuery<User> query = em.createQuery(
            "SELECT u FROM User u WHERE u.username = :username",
            User.class
        );
        query.setParameter("username", username);
        return query.getSingleResult();
    }

    // ... all other methods
}
```

### Query Method Keywords

Spring Data JPA parses method names:

```java
findByUsername          → WHERE username = ?
findByUsernameAndEmail  → WHERE username = ? AND email = ?
findByUsernameOrEmail   → WHERE username = ? OR email = ?
findByCreatedAtBefore   → WHERE created_at < ?
findByCreatedAtAfter    → WHERE created_at > ?
findByUsernameLike      → WHERE username LIKE ?
findByUsernameContaining → WHERE username LIKE %?%
findByUsernameStartingWith → WHERE username LIKE ?%
findByUsernameEndingWith → WHERE username LIKE %?
findByUsernameIgnoreCase → WHERE LOWER(username) = LOWER(?)
findByIdIn(List<Long>)  → WHERE id IN (?, ?, ?)
findTop10By...          → LIMIT 10
findFirstBy...          → LIMIT 1
countByUsername         → SELECT COUNT(*) WHERE username = ?
deleteByUsername        → DELETE WHERE username = ?
```

### How Method Names Become Queries

```java
// Method name:
User findByUsernameAndEmailIgnoreCase(String username, String email);

// Spring Data JPA parses:
findBy                  → SELECT
Username                → Property: username
And                     → Operator: AND
Email                   → Property: email
IgnoreCase              → Modifier: case-insensitive

// Generates JPQL:
SELECT u FROM User u
WHERE LOWER(u.username) = LOWER(:username)
  AND LOWER(u.email) = LOWER(:email)

// Hibernate converts to SQL:
SELECT * FROM users
WHERE LOWER(username) = LOWER(?)
  AND LOWER(email) = LOWER(?)
```

### Custom Queries

```java
@Query("SELECT u FROM User u WHERE u.username = :username")
User customFind(@Param("username") String username);

// Native SQL query
@Query(value = "SELECT * FROM users WHERE username = ?", nativeQuery = true)
User nativeFind(String username);
```

---

## Part 7: How Hibernate Generates SQL

### JPQL to SQL Translation

```java
// JPQL (Java Persistence Query Language):
SELECT u FROM User u WHERE u.username = :username

// Hibernate translates to SQL:
SELECT u.id, u.username, u.email, u.password, u.first_name, u.last_name,
       u.role, u.banned, u.created_at, u.updated_at
FROM users u
WHERE u.username = ?
```

### Join Translations

```java
// JPQL with JOIN FETCH:
SELECT p FROM Post p JOIN FETCH p.author WHERE p.id = :id

// SQL:
SELECT p.id, p.title, p.content, p.created_at,
       u.id, u.username, u.email, u.first_name, u.last_name
FROM posts p
INNER JOIN users u ON p.author_id = u.id
WHERE p.id = ?
```

### How INSERT Works

```java
User user = User.builder()
    .username("john")
    .email("john@example.com")
    .build();
userRepository.save(user);

// Hibernate generates:
INSERT INTO users (username, email, password, role, banned, created_at, updated_at)
VALUES (?, ?, ?, ?, ?, ?, ?)
RETURNING id;  // Get generated ID
```

### How UPDATE Works (Dirty Checking)

```java
@Transactional
public void updateUser(Long id) {
    User user = userRepository.findById(id).get();
    // Hibernate takes snapshot: {username: "john", email: "john@example.com"}

    user.setUsername("jane");
    // Transaction commits

    // Hibernate compares:
    // Snapshot: {username: "john"}
    // Current:  {username: "jane"}
    // Changed!

    // Generates UPDATE only for changed fields:
    UPDATE users SET username = ?, updated_at = ? WHERE id = ?
}
```

---

## Part 8: Second-Level Cache (Optional)

### First-Level vs Second-Level Cache

```
First-Level Cache (Persistence Context):
- Per transaction
- Automatic
- Short-lived

Second-Level Cache:
- Per application
- Optional (Ehcache, Redis)
- Long-lived
- Shared across transactions
```

```java
// Without second-level cache:
Transaction 1: userRepository.findById(1L);  // Queries DB
Transaction 2: userRepository.findById(1L);  // Queries DB again!

// With second-level cache:
Transaction 1: userRepository.findById(1L);  // Queries DB, caches result
Transaction 2: userRepository.findById(1L);  // Returns from cache!
```

---

## Summary: Hibernate/JPA Internals

| Concept | What It Is | Why It Matters |
|---------|------------|----------------|
| **Persistence Context** | First-level cache | Tracks entities, avoids duplicate queries |
| **Entity States** | Transient/Managed/Detached/Removed | Determines if changes are tracked |
| **Lazy Loading** | Load data on-demand | Performance (don't load everything) |
| **Eager Loading** | Load data immediately | Avoid LazyInitializationException |
| **Transactions** | Unit of work | All-or-nothing database operations |
| **Dirty Checking** | Detect changes automatically | Auto-generates UPDATEs |
| **Query Methods** | Method names → SQL | No need to write queries |
| **JOIN FETCH** | Optimize queries | Solve N+1 problem |

---

**Next**: I'll apply these concepts to our actual code, explaining every repository, service, and controller method with this level of depth.

Continue?
