# JPA & Hibernate Internals: Persistence Context, Caching, and Lazy Loading

## Overview

This document explains **how Hibernate actually works** behind the scenes when you use JPA. We'll explore:
- Persistence Context (first-level cache)
- Entity states and state transitions
- Dirty checking mechanism
- Lazy loading with proxies
- Second-level cache
- Flush modes and timing
- Query execution and SQL generation

**Prerequisites**: Understanding of JPA annotations, JDBC, and database concepts.

---

## 1. The Persistence Context

### What is Persistence Context?

The **Persistence Context** is Hibernate's **first-level cache** - a map that tracks all entities loaded in the current transaction.

```java
// Simplified Persistence Context
public class PersistenceContext {
    // EntityKey = (Entity class + ID)
    private Map<EntityKey, Object> entities = new HashMap<>();

    // Track which entities are dirty (modified)
    private Set<Object> dirtyEntities = new HashSet<>();

    // Original state for dirty checking
    private Map<Object, Object[]> entitySnapshots = new HashMap<>();
}
```

**In your application**:

```java
@Transactional
public void exampleMethod() {
    // Persistence Context created when transaction starts

    Post post = postRepository.findById(1L).orElseThrow();
    // Post is now in Persistence Context

    Post samePost = postRepository.findById(1L).orElseThrow();
    // NO database query! Returns from Persistence Context

    System.out.println(post == samePost); // true (same object instance!)

    // Persistence Context cleared when transaction ends
}
```

### How EntityManager Works

```java
// EntityManager is your interface to Persistence Context
EntityManager em = entityManagerFactory.createEntityManager();

// 1. Find entity
Post post = em.find(Post.class, 1L);
// Hibernate checks Persistence Context first:
//   Key: (Post.class, 1L)
//   Found? Return it
//   Not found? Query database, store in context, return it

// 2. Modify entity
post.setTitle("New Title");
// Hibernate marks entity as DIRTY in Persistence Context

// 3. Flush (write to database)
em.flush();
// Hibernate compares current state with snapshot
// Generates UPDATE SQL for dirty entities

// 4. Clear context
em.clear();
// All entities detached, Persistence Context emptied
```

---

## 2. Entity States

Every entity is in one of 4 states:

### State Diagram

```
NEW (Transient)
  ↓ persist()
MANAGED (Persistent)
  ↓ transaction commit / detach()
DETACHED
  ↓ merge()
MANAGED
  ↓ remove()
REMOVED
  ↓ flush()
(deleted from database)
```

### 1. NEW (Transient)

Entity exists in memory but not in database or Persistence Context.

```java
Post post = new Post();
post.setTitle("My Post");
// State: NEW
// Not tracked by Hibernate
// Not in database
```

### 2. MANAGED (Persistent)

Entity is tracked by Persistence Context. **Changes are automatically synced to database.**

```java
@Transactional
public void managedExample() {
    // Load entity (becomes MANAGED)
    Post post = postRepository.findById(1L).orElseThrow();

    // Modify entity
    post.setTitle("Updated Title");
    // NO need to call save()!
    // Hibernate automatically detects change and generates UPDATE

    // At transaction end: UPDATE posts SET title = ? WHERE id = 1
}
```

### 3. DETACHED

Entity was MANAGED but no longer tracked (transaction ended or `detach()` called).

```java
@Transactional
public Post loadPost() {
    Post post = postRepository.findById(1L).orElseThrow();
    return post; // MANAGED inside transaction
}

// Outside transaction:
Post post = loadPost();
// State: DETACHED (transaction ended)

post.setTitle("New Title");
// Change NOT tracked by Hibernate! ❌
// No UPDATE will be generated
```

**Re-attach detached entity**:

```java
@Transactional
public void updatePost(Post detachedPost) {
    // Option 1: merge (creates new MANAGED entity)
    Post managedPost = entityManager.merge(detachedPost);
    managedPost.setTitle("Updated");
    // UPDATE generated at flush

    // Option 2: Use repository (internally calls merge)
    postRepository.save(detachedPost);
}
```

### 4. REMOVED

Entity marked for deletion.

```java
@Transactional
public void deletePost(Long id) {
    Post post = postRepository.findById(id).orElseThrow();
    // State: MANAGED

    postRepository.delete(post);
    // State: REMOVED
    // Entity still in memory but marked for deletion

    // At transaction end: DELETE FROM posts WHERE id = ?
}
```

---

## 3. Dirty Checking Mechanism

### How Hibernate Detects Changes

When you load an entity, Hibernate stores a **snapshot** of its state:

```java
// Simplified dirty checking
public class SessionImpl {

    private Map<Object, Object[]> entitySnapshots = new HashMap<>();

    public Object find(Class<?> entityClass, Object id) {
        // 1. Query database
        Object entity = loadFromDatabase(entityClass, id);

        // 2. Store snapshot (copy of all field values)
        Object[] snapshot = extractState(entity);
        entitySnapshots.put(entity, snapshot);

        return entity;
    }

    public void flush() {
        // Compare current state with snapshot for ALL entities
        for (Object entity : managedEntities) {
            Object[] currentState = extractState(entity);
            Object[] originalState = entitySnapshots.get(entity);

            if (!Arrays.equals(currentState, originalState)) {
                // Entity is DIRTY → generate UPDATE
                generateUpdateSQL(entity, currentState, originalState);
            }
        }
    }

    private Object[] extractState(Object entity) {
        // Use reflection to get all field values
        // For Post entity: [id, title, content, mediaUrl, mediaType, hidden, createdAt, updatedAt]
        return new Object[] {
            post.getId(),
            post.getTitle(),
            post.getContent(),
            post.getMediaUrl(),
            post.getMediaType(),
            post.isHidden(),
            post.getCreatedAt(),
            post.getUpdatedAt()
        };
    }
}
```

### Example: Automatic UPDATE

```java
@Transactional
public void updatePost(Long postId) {
    // 1. Load post
    Post post = postRepository.findById(postId).orElseThrow();
    // Hibernate stores snapshot: [1, "Original Title", "Original Content", null, null, false, ...]

    // 2. Modify entity
    post.setTitle("New Title");
    post.setContent("New Content");

    // 3. No explicit save() needed!
    // At transaction commit (or explicit flush):

    // Hibernate compares:
    // Current:  [1, "New Title", "New Content", null, null, false, ...]
    // Snapshot: [1, "Original Title", "Original Content", null, null, false, ...]
    // Difference: title and content changed

    // Generates SQL:
    // UPDATE posts SET title = 'New Title', content = 'New Content', updated_at = NOW() WHERE id = 1
}
```

### Performance Impact

**Problem**: Dirty checking ALL entities before every flush!

```java
@Transactional
public void massLoad() {
    List<Post> posts = postRepository.findAll(); // 10,000 posts

    // Each post stored in Persistence Context with snapshot
    // Memory: 10,000 entities × ~2KB = 20MB

    // At flush: Hibernate checks ALL 10,000 entities for changes!
    // Time: ~50ms (reflection + comparison)
}
```

**Solution 1**: Use `@Transactional(readOnly = true)`

```java
@Transactional(readOnly = true)
public List<Post> getAllPosts() {
    List<Post> posts = postRepository.findAll();

    // Flush mode set to MANUAL → no dirty checking!
    // Performance: Much faster
}
```

**Solution 2**: Detach entities you won't modify

```java
@Transactional
public void processLargeDataset() {
    List<Post> posts = postRepository.findAll();

    for (Post post : posts) {
        processPost(post);
        entityManager.detach(post); // Remove from Persistence Context
        // Reduces memory and dirty checking cost
    }
}
```

**Solution 3**: Use native queries for bulk operations

```java
@Modifying
@Query("UPDATE Post p SET p.hidden = true WHERE p.author.id = :authorId")
int hideAllPostsByAuthor(@Param("authorId") Long authorId);
// Executes UPDATE directly, bypasses Persistence Context entirely
```

---

## 4. Lazy Loading and Proxies

### What is Lazy Loading?

**LAZY** = Load data only when accessed (default for `@OneToMany`, `@ManyToMany`)

**EAGER** = Load data immediately (default for `@ManyToOne`, `@OneToOne`)

### Your Post Entity

```java
@Entity
public class Post {
    @Id
    private Long id;

    @ManyToOne // EAGER by default
    @JoinColumn(name="author_id")
    private User author;

    @ManyToMany // LAZY by default
    @JoinTable(name = "post_likes")
    private Set<User> likedBy = new HashSet<>();

    @OneToMany(mappedBy="post") // LAZY by default
    private List<Comment> comments;
}
```

### How Lazy Loading Works

When you load a `Post`, Hibernate creates **proxies** for lazy collections:

```java
Post post = postRepository.findById(1L).orElseThrow();

// What you see:
System.out.println(post.getClass());
// Output: class _blog.blog.entity.Post

// What Hibernate actually returns:
System.out.println(post.getComments().getClass());
// Output: class org.hibernate.collection.internal.PersistentBag$$EnhancerByHibernate

// The "comments" field is a PROXY (not a real List!)
```

### Proxy Implementation

```java
// Simplified Hibernate proxy
public class PersistentBag extends AbstractPersistentCollection implements List {

    private List<Comment> actualList; // Real data (not loaded yet)
    private boolean initialized = false;

    @Override
    public int size() {
        if (!initialized) {
            initialize(); // Trigger lazy loading!
        }
        return actualList.size();
    }

    @Override
    public Comment get(int index) {
        if (!initialized) {
            initialize(); // Trigger lazy loading!
        }
        return actualList.get(index);
    }

    private void initialize() {
        // Execute SQL to load comments
        actualList = session.createQuery(
            "SELECT c FROM Comment c WHERE c.post.id = :postId"
        ).setParameter("postId", post.getId()).getResultList();

        initialized = true;
    }
}
```

### LazyInitializationException

**The infamous exception**:

```java
@Transactional
public Post loadPost() {
    return postRepository.findById(1L).orElseThrow();
}

// Outside transaction:
Post post = loadPost();
post.getComments().size(); // ❌ LazyInitializationException!
// Why? Transaction ended → EntityManager closed → can't load comments
```

**Error message**:

```
org.hibernate.LazyInitializationException:
failed to lazily initialize a collection of role: _blog.blog.entity.Post.comments,
could not initialize proxy - no Session
```

### Solutions to LazyInitializationException

#### Solution 1: Use JOIN FETCH (Best)

```java
// Your PostRepository already does this!
@Query("SELECT p FROM Post p LEFT JOIN FETCH p.comments LEFT JOIN FETCH p.likedBy WHERE p.id = :postId")
Optional<Post> findByIdWithCommentsAndLikes(@Param("postId") Long postId);

// Generates single SQL:
// SELECT p.*, c.*, u.*
// FROM posts p
// LEFT JOIN comments c ON c.post_id = p.id
// LEFT JOIN post_likes pl ON pl.post_id = p.id
// LEFT JOIN users u ON u.id = pl.user_id
// WHERE p.id = ?
```

**Result**: All data loaded in one query, no proxies, no lazy loading issues!

#### Solution 2: Open Session in View (Anti-Pattern)

```properties
# application.properties
spring.jpa.open-in-view=true # Default in Spring Boot
```

**What it does**: Keeps EntityManager open until view is rendered.

**Problems**:
- Database connections held longer
- Can hide N+1 query problems
- Performance issues in production

**Recommendation**: Disable it!

```properties
spring.jpa.open-in-view=false
```

#### Solution 3: Use DTOs (Recommended)

```java
@Transactional
public PostResponse getPost(Long id) {
    Post post = postRepository.findByIdWithCommentsAndLikes(id).orElseThrow();

    // Map to DTO inside transaction
    PostResponse dto = new PostResponse(post);
    // All data copied to DTO

    return dto; // DTO is safe to use outside transaction
}
```

---

## 5. Query Execution Internals

### How Hibernate Generates SQL

Let's trace a **complete query execution**:

```java
@Query("SELECT p FROM Post p LEFT JOIN FETCH p.likedBy WHERE p.id = :postId")
Optional<Post> findByIdWithLikes(@Param("postId") Long postId);
```

#### Step 1: Parse JPQL

```java
// Hibernate's query parser
String jpql = "SELECT p FROM Post p LEFT JOIN FETCH p.likedBy WHERE p.id = :postId";

// Parse to AST (Abstract Syntax Tree)
HqlParser parser = new HqlParser(jpql);
Statement ast = parser.parseStatement();

// AST:
// SelectStatement
//   ├─ FROM: Post (alias: p)
//   ├─ JOIN: p.likedBy (fetch: true)
//   └─ WHERE: p.id = :postId
```

#### Step 2: Generate SQL

```java
// Hibernate's SQL generator
public class QueryTranslator {

    public String translateToSQL(Statement ast) {
        // 1. FROM clause
        String from = "FROM posts p";

        // 2. JOIN clause
        // p.likedBy is @ManyToMany with @JoinTable
        String join =
            "LEFT JOIN post_likes pl ON pl.post_id = p.id " +
            "LEFT JOIN users u ON u.id = pl.user_id";

        // 3. WHERE clause
        String where = "WHERE p.id = ?";

        // 4. SELECT clause
        // Include ALL columns from Post + User (because of FETCH)
        String select =
            "SELECT p.id, p.title, p.content, p.media_url, p.media_type, " +
            "p.hidden, p.created_at, p.updated_at, p.author_id, " +
            "u.id, u.username, u.email, u.avatar, u.role, u.banned, " +
            "u.created_at, u.updated_at";

        return select + " " + from + " " + join + " " + where;
    }
}
```

**Final SQL**:

```sql
SELECT
    p.id, p.title, p.content, p.media_url, p.media_type, p.hidden,
    p.created_at, p.updated_at, p.author_id,
    u.id, u.username, u.email, u.avatar, u.role, u.banned,
    u.created_at, u.updated_at
FROM posts p
LEFT JOIN post_likes pl ON pl.post_id = p.id
LEFT JOIN users u ON u.id = pl.user_id
WHERE p.id = ?
```

#### Step 3: Execute Query

```java
PreparedStatement ps = connection.prepareStatement(sql);
ps.setLong(1, postId); // Bind parameter
ResultSet rs = ps.executeQuery();
```

#### Step 4: Process ResultSet

```java
// Hibernate's result transformer
public Object transformResultSet(ResultSet rs) {
    // ResultSet may have multiple rows (because of LEFT JOIN with collection)
    // Row 1: Post 1, User A (first liked user)
    // Row 2: Post 1, User B (second liked user)
    // Row 3: Post 1, User C (third liked user)

    Post post = null;

    while (rs.next()) {
        if (post == null) {
            // First row: create Post entity
            post = new Post();
            post.setId(rs.getLong(1));
            post.setTitle(rs.getString(2));
            post.setContent(rs.getString(3));
            // ... set all fields

            post.setLikedBy(new HashSet<>());
        }

        // Each row: add User to likedBy collection
        if (rs.getLong(10) != 0) { // Check if user exists (LEFT JOIN may return null)
            User user = new User();
            user.setId(rs.getLong(10));
            user.setUsername(rs.getString(11));
            // ... set all fields

            post.getLikedBy().add(user);
        }
    }

    return post;
}
```

**Result**: Single `Post` with `Set<User> likedBy` populated.

---

## 6. N+1 Query Problem

### The Problem

```java
@Transactional
public List<PostResponse> getAllPosts() {
    List<Post> posts = postRepository.findAll(); // 1 query

    return posts.stream()
        .map(post -> {
            String authorName = post.getAuthor().getUsername(); // N queries!
            return new PostResponse(post, authorName);
        })
        .toList();
    // Total: 1 + N queries ❌
}
```

**What happens**:

```sql
-- Query 1: Load all posts
SELECT * FROM posts;

-- Query 2: Load author for post 1
SELECT * FROM users WHERE id = 1;

-- Query 3: Load author for post 2
SELECT * FROM users WHERE id = 2;

-- Query 4: Load author for post 3
SELECT * FROM users WHERE id = 3;

-- ... N queries total!
```

**If you have 100 posts → 101 queries!**

### Solution: JOIN FETCH

```java
@Query("SELECT p FROM Post p LEFT JOIN FETCH p.author")
List<Post> findAllWithAuthors();

@Transactional
public List<PostResponse> getAllPosts() {
    List<Post> posts = postRepository.findAllWithAuthors(); // 1 query

    return posts.stream()
        .map(post -> new PostResponse(post, post.getAuthor().getUsername()))
        .toList();
    // Total: 1 query ✅
}
```

**SQL**:

```sql
SELECT p.*, u.*
FROM posts p
LEFT JOIN users u ON u.id = p.author_id;
```

### Detecting N+1 Queries

**Enable Hibernate statistics**:

```properties
# application.properties
spring.jpa.properties.hibernate.generate_statistics=true
logging.level.org.hibernate.stat=DEBUG
```

**Output**:

```
Statistics:
  Session Metrics {
    117 nanoseconds spent acquiring 1 JDBC connections;
    0 nanoseconds spent releasing 0 JDBC connections;
    42380958 nanoseconds spent preparing 101 JDBC statements;  ← 101 statements!
    1134895 nanoseconds spent executing 101 JDBC statements;   ← N+1 problem!
  }
```

---

## 7. Flush Modes and Timing

### When Does Hibernate Flush?

**Flush** = Write changes from Persistence Context to database (execute INSERT/UPDATE/DELETE)

**Default flush timing**:

1. **Before query execution** (if query might be affected by pending changes)
2. **On transaction commit**
3. **Manual flush**: `entityManager.flush()`

### Auto Flush Example

```java
@Transactional
public void autoFlushExample() {
    // 1. Create new post
    Post post = new Post();
    post.setTitle("New Post");
    postRepository.save(post);
    // State: MANAGED (in Persistence Context, NOT yet in database)

    // 2. Query posts
    List<Post> posts = postRepository.findAll();
    // Hibernate flushes BEFORE executing query!
    // Why? Query needs to see the new post

    // SQL execution order:
    // 1. INSERT INTO posts (...) VALUES (...)  ← Auto flush
    // 2. SELECT * FROM posts                     ← Query
}
```

### Flush Modes

#### AUTO (Default)

Flush before queries that might be affected.

```java
entityManager.setFlushMode(FlushModeType.AUTO);

Post post = new Post();
post.setTitle("Test");
entityManager.persist(post);
// Not flushed yet

List<Post> posts = entityManager.createQuery("SELECT p FROM Post p").getResultList();
// Flushed before query (so new post is included)
```

#### COMMIT

Only flush on commit.

```java
entityManager.setFlushMode(FlushModeType.COMMIT);

Post post = new Post();
post.setTitle("Test");
entityManager.persist(post);
// Not flushed yet

List<Post> posts = entityManager.createQuery("SELECT p FROM Post p").getResultList();
// NOT flushed! New post NOT in query results!

// Flushed only when transaction commits
```

**Use case**: Performance optimization (avoid unnecessary flushes).

#### MANUAL (with @Transactional(readOnly = true))

Never flush automatically.

```java
@Transactional(readOnly = true)
public List<Post> getAllPosts() {
    // Flush mode set to MANUAL automatically

    List<Post> posts = postRepository.findAll();

    posts.get(0).setTitle("Modified");
    // Change NOT flushed (even at commit!)

    // Good for read-only operations (performance boost)
}
```

---

## 8. Second-Level Cache

### Cache Hierarchy

```
[Second-Level Cache] (Application-wide, shared across sessions)
  ↓ (miss)
[First-Level Cache] (Persistence Context, per-session)
  ↓ (miss)
[Database]
```

### What is Second-Level Cache?

**First-level cache** = Persistence Context (per transaction)

**Second-level cache** = Shared cache (across all transactions)

### Configuration

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.hibernate</groupId>
    <artifactId>hibernate-jcache</artifactId>
</dependency>
<dependency>
    <groupId>org.ehcache</groupId>
    <artifactId>ehcache</artifactId>
</dependency>
```

```properties
# application.properties
spring.jpa.properties.hibernate.cache.use_second_level_cache=true
spring.jpa.properties.hibernate.cache.region.factory_class=org.hibernate.cache.jcache.JCacheRegionFactory
```

```java
@Entity
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Post {
    // Entity is now cached in second-level cache
}
```

### Cache Behavior

```java
// Transaction 1
@Transactional
public Post loadPost1() {
    Post post = postRepository.findById(1L).orElseThrow();
    // 1. Check second-level cache → miss
    // 2. Query database
    // 3. Store in second-level cache
    // 4. Return post
    return post;
}

// Transaction 2 (different session)
@Transactional
public Post loadPost2() {
    Post post = postRepository.findById(1L).orElseThrow();
    // 1. Check second-level cache → HIT!
    // 2. Return cached entity (no database query)
    return post;
}
```

### Cache Invalidation

```java
@Transactional
public void updatePost(Long id) {
    Post post = postRepository.findById(id).orElseThrow();
    post.setTitle("Updated");
    // At flush: UPDATE executed
    // Second-level cache entry for this post is INVALIDATED
}
```

### Cache Statistics

```properties
spring.jpa.properties.hibernate.cache.use_query_cache=true
spring.jpa.properties.hibernate.generate_statistics=true
```

```java
SessionFactory sessionFactory = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class);
Statistics stats = sessionFactory.getStatistics();

System.out.println("Second-level cache hit count: " + stats.getSecondLevelCacheHitCount());
System.out.println("Second-level cache miss count: " + stats.getSecondLevelCacheMissCount());
System.out.println("Second-level cache put count: " + stats.getSecondLevelCachePutCount());
```

---

## 9. Cascade Operations

### Cascade Types

Your `Post` entity uses cascade:

```java
@OneToMany(mappedBy="post", cascade=CascadeType.ALL, orphanRemoval=true)
private List<Comment> comments;
```

**What this means**:

```java
@Transactional
public void deletePost(Long postId) {
    Post post = postRepository.findById(postId).orElseThrow();
    postRepository.delete(post);

    // Hibernate automatically:
    // 1. DELETE FROM comments WHERE post_id = 1
    // 2. DELETE FROM posts WHERE id = 1
    // (because of CascadeType.ALL)
}
```

### Cascade Types Explained

| Cascade Type | What It Does |
|-------------|--------------|
| `PERSIST` | `persist(post)` also persists comments |
| `MERGE` | `merge(post)` also merges comments |
| `REMOVE` | `remove(post)` also removes comments |
| `REFRESH` | `refresh(post)` also refreshes comments |
| `DETACH` | `detach(post)` also detaches comments |
| `ALL` | All of the above |

### orphanRemoval

```java
@OneToMany(mappedBy="post", cascade=CascadeType.ALL, orphanRemoval=true)
private List<Comment> comments;

@Transactional
public void removeComment(Long postId, Long commentId) {
    Post post = postRepository.findById(postId).orElseThrow();

    // Remove comment from collection
    post.getComments().removeIf(c -> c.getId().equals(commentId));

    // Hibernate automatically:
    // DELETE FROM comments WHERE id = ?
    // (because of orphanRemoval=true)
}
```

**Without `orphanRemoval`**: Comment would still exist in database (orphaned).

**With `orphanRemoval`**: Comment is deleted when removed from collection.

---

## 10. Batch Operations

### Problem: Slow Inserts

```java
@Transactional
public void createManyPosts() {
    for (int i = 0; i < 1000; i++) {
        Post post = new Post();
        post.setTitle("Post " + i);
        postRepository.save(post);
    }
    // 1000 separate INSERT statements! Slow!
}
```

### Solution: Batch Inserts

```properties
# application.properties
spring.jpa.properties.hibernate.jdbc.batch_size=50
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true
```

```java
@Transactional
public void createManyPostsOptimized() {
    for (int i = 0; i < 1000; i++) {
        Post post = new Post();
        post.setTitle("Post " + i);
        entityManager.persist(post);

        if (i % 50 == 0) {
            entityManager.flush();  // Flush every 50 entities
            entityManager.clear();  // Clear Persistence Context
        }
    }
    // SQL: 20 batches of 50 inserts (much faster!)
}
```

**Generated SQL**:

```sql
-- Batch 1 (50 inserts)
INSERT INTO posts (title, content, ...) VALUES (?, ?, ...);
INSERT INTO posts (title, content, ...) VALUES (?, ?, ...);
... (50 times)

-- Batch 2 (50 inserts)
INSERT INTO posts (title, content, ...) VALUES (?, ?, ...);
... (50 times)
```

---

## 11. Optimistic Locking

### The Concurrent Update Problem

```java
// User A loads post
Post post = postRepository.findById(1L).orElseThrow();
post.setTitle("User A's Title");

// User B loads same post (sees old title)
Post post = postRepository.findById(1L).orElseThrow();
post.setTitle("User B's Title");

// User A saves
postRepository.save(post); // Title = "User A's Title"

// User B saves
postRepository.save(post); // Title = "User B's Title" (overwrites User A!)
```

### Solution: @Version

```java
@Entity
public class Post {
    @Id
    private Long id;

    @Version
    private Long version; // Hibernate manages this automatically

    private String title;
}
```

**How it works**:

```java
// User A loads post
Post post = postRepository.findById(1L).orElseThrow();
// post.version = 1

post.setTitle("User A's Title");
postRepository.save(post);
// UPDATE posts SET title = ?, version = 2 WHERE id = 1 AND version = 1
// Success! version = 2 now

// User B (still has version 1)
post.setTitle("User B's Title");
postRepository.save(post);
// UPDATE posts SET title = ?, version = 2 WHERE id = 1 AND version = 1
// No rows updated! (version is already 2)
// Throws: OptimisticLockException
```

---

## 12. Key Takeaways

### Persistence Context

- **First-level cache**: Per transaction, automatic
- **Tracks entity states**: NEW, MANAGED, DETACHED, REMOVED
- **Dirty checking**: Compares current state with snapshot
- **Automatic UPDATE**: No need to call `save()` for MANAGED entities

### Lazy Loading

- **LAZY**: Load data on access (can cause LazyInitializationException)
- **EAGER**: Load data immediately (can cause N+1 queries)
- **Best practice**: Use JOIN FETCH in queries

### Performance

- ✅ Use `@Transactional(readOnly = true)` for read-only methods
- ✅ Use JOIN FETCH to avoid N+1 queries
- ✅ Use batch operations for bulk inserts/updates
- ✅ Enable Hibernate statistics to detect problems
- ❌ Don't load large collections without pagination
- ❌ Don't use Open Session in View in production

### Common Mistakes

1. **Forgetting JOIN FETCH** → N+1 queries
2. **Accessing lazy collection outside transaction** → LazyInitializationException
3. **Not using readOnly** → Unnecessary dirty checking overhead
4. **Modifying detached entities** → Changes ignored
5. **Missing @Version** → Lost updates in concurrent scenarios

---

## What's Next?

Continue to `25-REQUEST-PROCESSING-PIPELINE.md` for deep dive into DispatcherServlet and HTTP request handling.

**Completed**:
- ✅ Spring Boot startup
- ✅ Component scanning
- ✅ Bean lifecycle and proxies
- ✅ Transaction management
- ✅ JPA/Hibernate internals

**Next**:
- DispatcherServlet request processing
- HandlerMapping and HandlerAdapter
- Argument resolution (@PathVariable, @RequestBody, etc.)
- Message converters (JSON serialization)
