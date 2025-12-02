# Repository Layer - Deep Dive: How Spring Data JPA Works

## UserRepository.java - Line by Line with Internals

**Location**: `backend/src/main/java/_blog/blog/repository/UserRepository.java`

---

### Lines 1-11: Imports and Package

```java
1  package _blog.blog.repository;
2
3  import java.util.Optional;
4
5  import org.springframework.data.jpa.repository.JpaRepository;
6  import org.springframework.data.jpa.repository.Query;
7  import org.springframework.data.repository.query.Param;
8  import org.springframework.stereotype.Repository;
9
10 import _blog.blog.entity.User;
```

**Line 3**: `Optional<T>` - Java's way of handling null safely
```java
// Without Optional (old way):
User user = userRepository.findByUsername("john");
if (user == null) {  // Can forget null check → NullPointerException!
    throw new Exception("Not found");
}

// With Optional (modern way):
Optional<User> userOpt = userRepository.findByUsername("john");
User user = userOpt.orElseThrow(() -> new Exception("Not found"));
// Forces you to handle the "not found" case
```

**Line 5**: `JpaRepository<User, Long>`
- `User` = Entity type
- `Long` = ID type (primary key)

**What you get from JpaRepository:**
```java
// Automatically provided methods (you don't write these!):
save(User user)                    // INSERT or UPDATE
findById(Long id)                  // SELECT by ID
findAll()                          // SELECT all
delete(User user)                  // DELETE
deleteById(Long id)                // DELETE by ID
count()                            // SELECT COUNT(*)
existsById(Long id)                // Check if exists
flush()                            // Force write to database
saveAndFlush(User user)            // Save + flush immediately
// ... 20+ more methods!
```

**Line 6-7**: Custom query annotations

**Line 8**: `@Repository` - Marks this as a repository bean
- Spring creates implementation automatically
- Adds exception translation (SQLException → DataAccessException)

---

### Lines 12-13: Interface Declaration

```java
12 @Repository
13 public interface UserRepository extends JpaRepository<User, Long> {
```

**Why interface not class?**

You **don't implement** this interface. Spring Data JPA **generates the implementation** at runtime using **dynamic proxies**.

**What Spring does internally:**
```java
// At startup, Spring generates something like this:
@Component
public class UserRepositoryImpl implements UserRepository {

    @Autowired
    private EntityManager entityManager;

    @Override
    public Optional<User> findByUsername(String username) {
        try {
            TypedQuery<User> query = entityManager.createQuery(
                "SELECT u FROM User u WHERE u.username = :username",
                User.class
            );
            query.setParameter("username", username);
            User user = query.getSingleResult();
            return Optional.of(user);
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    // ... all other methods
}
```

---

### Line 14: Query Method - findByEmail

```java
14 Optional<User> findByEmail(String email);
```

**How Spring Data JPA parses this:**

```
Method Name: findByEmail
    ↓
1. "find" → SELECT query
2. "By" → WHERE clause
3. "Email" → Property name (matches User.email field)
4. Parameter: String email → value for WHERE clause

Generated JPQL:
SELECT u FROM User u WHERE u.email = :email

Generated SQL (by Hibernate):
SELECT id, username, email, password, first_name, last_name, role, banned,
       created_at, updated_at, avatar
FROM users
WHERE email = ?
```

**Step-by-step execution:**
```java
// 1. Call method
Optional<User> user = userRepository.findByEmail("john@example.com");

// 2. Spring proxy intercepts call

// 3. EntityManager creates query
TypedQuery<User> query = em.createQuery(
    "SELECT u FROM User u WHERE u.email = :email",
    User.class
);

// 4. Set parameter
query.setParameter("email", "john@example.com");

// 5. Hibernate converts JPQL to SQL
String sql = "SELECT * FROM users WHERE email = ?";

// 6. JDBC executes query
PreparedStatement stmt = connection.prepareStatement(sql);
stmt.setString(1, "john@example.com");
ResultSet rs = stmt.executeQuery();

// 7. Hibernate maps ResultSet to User entity
User user = new User();
user.setId(rs.getLong("id"));
user.setEmail(rs.getString("email"));
// ... all fields

// 8. Check persistence context first!
if (persistenceContext.contains(user.getId())) {
    return persistenceContext.get(user.getId());  // Return cached!
}

// 9. Add to persistence context
persistenceContext.put(user.getId(), user);

// 10. Return as Optional
return Optional.of(user);
```

**Why Optional?**
- Email might not exist in database
- `Optional.empty()` = not found
- `Optional.of(user)` = found

---

### Line 15: Query Method - findByUsername

```java
15 Optional<User> findByUsername(String username);
```

**Same process as findByEmail**

**Generated SQL:**
```sql
SELECT * FROM users WHERE username = ?
```

**Usage patterns:**
```java
// Pattern 1: orElseThrow (fail if not found)
User user = userRepository.findByUsername("john")
    .orElseThrow(() -> new UsernameNotFoundException("User not found"));

// Pattern 2: orElse (default value)
User user = userRepository.findByUsername("john")
    .orElse(createDefaultUser());

// Pattern 3: ifPresent (do something if exists)
userRepository.findByUsername("john")
    .ifPresent(user -> sendEmail(user));

// Pattern 4: isPresent (check)
if (userRepository.findByUsername("john").isPresent()) {
    // User exists
}
```

---

### Lines 16-22: Custom JPQL Query with JOIN FETCH ⭐ IMPORTANT

```java
16 @Query("""
17     SELECT u FROM User u
18     LEFT JOIN FETCH u.subscriptions s
19     LEFT JOIN FETCH u.Posts p
20     WHERE u.username = :username
21 """)
22 Optional<User> findByUsernameWithSubscriptionsAndPosts(@Param("username") String username);
```

**Breaking this down:**

#### Line 16: `@Query("""...""")`
- Custom JPQL query (not generated from method name)
- `"""` = Text block (Java 15+) for multi-line strings

#### Line 17: `SELECT u FROM User u`
- `u` = alias for User entity
- Not SQL! This is JPQL (Java Persistence Query Language)
- Works with entities, not tables

#### Line 18: `LEFT JOIN FETCH u.subscriptions s`

**What is JOIN FETCH?**

JOIN FETCH tells Hibernate: "Load this relationship IMMEDIATELY in the SAME query"

**Without JOIN FETCH (lazy loading - N+1 problem):**
```java
User user = userRepository.findByUsername("john");
// SQL 1: SELECT * FROM users WHERE username = 'john'

Set<Subscription> subs = user.getSubscriptions();
// SQL 2: SELECT * FROM subscriptions WHERE subscriber_id = 1
// Triggers separate query!

List<Post> posts = user.getPosts();
// SQL 3: SELECT * FROM posts WHERE author_id = 1
// Another separate query!

// Total: 3 queries! 😱
```

**With JOIN FETCH (eager loading - solved!):**
```java
Optional<User> userOpt = userRepository.findByUsernameWithSubscriptionsAndPosts("john");
// SQL: ONE query that joins everything:
```
```sql
SELECT u.*, s.*, p.*
FROM users u
LEFT JOIN subscriptions s ON u.id = s.subscriber_id
LEFT JOIN posts p ON u.id = p.author_id
WHERE u.username = 'john'
```
```java
// Total: 1 query! ✅
```

**LEFT JOIN vs INNER JOIN:**
```java
// LEFT JOIN FETCH: Include user even if no subscriptions/posts
User user = ...;  // Still returned even if:
user.getSubscriptions().isEmpty();  // No subscriptions
user.getPosts().isEmpty();          // No posts

// INNER JOIN FETCH: Only if subscriptions/posts exist
// If user has no subscriptions, user not returned at all!
```

**Why FETCH?**
```java
// JOIN without FETCH:
SELECT u FROM User u LEFT JOIN u.subscriptions s
// Returns User, but subscriptions still lazy (will trigger query later)

// JOIN FETCH:
SELECT u FROM User u LEFT JOIN FETCH u.subscriptions s
// Returns User WITH subscriptions loaded (no additional query)
```

#### Line 19: `LEFT JOIN FETCH u.Posts p`
- Same as subscriptions
- Loads all user's posts in same query

#### Line 20: `WHERE u.username = :username`
- `:username` = named parameter (not ?)
- Bound with `@Param("username")`

#### Line 22: `@Param("username")`
- Links method parameter to `:username` in query

**Generated SQL (approximation):**
```sql
SELECT
    u.id, u.username, u.email, u.password, u.first_name, u.last_name,
    u.role, u.banned, u.created_at, u.updated_at, u.avatar,
    s.id, s.subscriber_id, s.subscribed_to_id, s.created_at,
    p.id, p.author_id, p.title, p.content, p.created_at, p.updated_at
FROM users u
LEFT JOIN subscriptions s ON u.id = s.subscriber_id
LEFT JOIN posts p ON u.id = p.author_id
WHERE u.username = ?
```

**What Hibernate does with results:**
```java
// ResultSet might have multiple rows (if user has multiple posts/subscriptions):
// Row 1: user_id=1, sub_id=1, post_id=1
// Row 2: user_id=1, sub_id=1, post_id=2
// Row 3: user_id=1, sub_id=2, post_id=1
// Row 4: user_id=1, sub_id=2, post_id=2

// Hibernate intelligently combines into ONE User object:
User user = new User();
user.setId(1);
user.setSubscriptions(Set.of(sub1, sub2));  // Deduplicated!
user.setPosts(List.of(post1, post2));       // Deduplicated!

return Optional.of(user);
```

**Why is this method useful?**
```java
// Use case: User profile page
// Need: user + their subscriptions + their posts
// Without this method: 3 queries
// With this method: 1 query

User user = userRepository.findByUsernameWithSubscriptionsAndPosts("john")
    .orElseThrow();

// No additional queries needed:
int subCount = user.getSubscriptions().size();   // Already loaded
int postCount = user.getPosts().size();          // Already loaded
```

---

## PostRepository.java - Advanced Queries

### Line 14: Simple Query Method

```java
14 List<Post> findAllByAuthorId(Long authorId);
```

**Generated JPQL:**
```java
SELECT p FROM Post p WHERE p.author.id = :authorId
```

**How Spring Data JPA parses:**
```
findAll      → SELECT *
By           → WHERE
Author       → Navigation to User entity (via p.author)
Id           → .id property
```

**Generated SQL:**
```sql
SELECT * FROM posts WHERE author_id = ?
```

**Note**: It navigates relationships!
```java
// Post entity has:
@ManyToOne
private User author;

// Query accesses author.id without explicit join
// Hibernate adds join if needed
```

---

### Lines 16-17: Custom Query with JOIN FETCH

```java
16 @Query("SELECT p FROM Post p LEFT JOIN FETCH p.likedBy WHERE p.id = :postId AND p.hidden = false")
17 Optional<Post> findByIdWithLikes(@Param("postId") Long postId);
```

**Why this query?**

Normal `findById()` would lazy-load `likedBy`:
```java
Post post = postRepository.findById(1L).get();
// SQL: SELECT * FROM posts WHERE id = 1

Set<User> likes = post.getLikedBy();
// SQL: SELECT * FROM post_likes WHERE post_id = 1
// SQL: SELECT * FROM users WHERE id IN (...)
// Multiple queries!
```

**This method**:
```sql
-- One query:
SELECT p.*, u.*
FROM posts p
LEFT JOIN post_likes pl ON p.id = pl.post_id
LEFT JOIN users u ON pl.user_id = u.id
WHERE p.id = ? AND p.hidden = FALSE
```

**AND p.hidden = false**
- Business logic in query
- Only returns visible posts
- Security: prevents accessing hidden/deleted posts

---

### Lines 19-20: Returning IDs Only

```java
19 @Query("SELECT p.id FROM Post p JOIN p.likedBy u WHERE u.id = :userId AND p.hidden = false")
20 List<Long> findPostsLikedByUser(@Param("userId") Long userId);
```

**Why return only IDs?**

**Performance optimization:**
```java
// This query returns ONLY post IDs
List<Long> postIds = postRepository.findPostsLikedByUser(userId);
// SQL: SELECT p.id FROM posts p
//      JOIN post_likes pl ON p.id = pl.post_id
//      WHERE pl.user_id = ? AND p.hidden = FALSE

// Lightweight! Only numbers returned

// vs returning full Post objects:
// Would need to hydrate entire Post entity
// Would load title, content, author, comments, etc.
// Much heavier!
```

**JOIN (not LEFT JOIN)**
- INNER JOIN = only posts that are actually liked
- If post has no likes from this user, not returned

---

### Lines 22-23: Multiple JOIN FETCHes

```java
22 @Query("SELECT p FROM Post p LEFT JOIN FETCH p.comments LEFT JOIN FETCH p.likedBy WHERE p.id = :postId AND p.hidden = false")
23 Optional<Post> findByIdWithCommentsAndLikes(@Param("postId") Long postId);
```

**Why multiple JOIN FETCHes?**

Use case: Post detail page needs:
- Post
- All comments
- All likes

**Without this**:
```java
Post post = postRepository.findById(1L).get();       // Query 1
List<Comment> comments = post.getComments();         // Query 2
Set<User> likes = post.getLikedBy();                 // Query 3
// 3 queries!
```

**With this**:
```sql
-- One query:
SELECT p.*, c.*, u.*
FROM posts p
LEFT JOIN comments c ON p.id = c.post_id
LEFT JOIN post_likes pl ON p.id = pl.post_id
LEFT JOIN users u ON pl.user_id = u.id
WHERE p.id = ? AND p.hidden = FALSE
```

**Cartesian product issue:**
```java
// If post has:
// - 3 comments
// - 5 likes

// ResultSet has: 3 × 5 = 15 rows!
// Hibernate deduplicates into 1 Post object
```

**⚠️ Warning: Don't JOIN FETCH too many collections**
```java
// BAD:
SELECT p FROM Post p
LEFT JOIN FETCH p.comments
LEFT JOIN FETCH p.likedBy
LEFT JOIN FETCH p.author
LEFT JOIN FETCH p.author.posts
LEFT JOIN FETCH p.author.followers
// Cartesian explosion! Millions of rows!
```

---

### Lines 37-38: IN Clause with JOIN FETCH

```java
37 @Query("SELECT p FROM Post p LEFT JOIN FETCH p.comments LEFT JOIN FETCH p.likedBy WHERE p.author.id IN :authorIds AND p.hidden = false ORDER BY p.createdAt DESC")
38 List<Post> findPostsByAuthorIdsWithCommentsAndLikes(@Param("authorIds") List<Long> authorIds);
```

**Use case: Feed page**
- Get posts from users you follow
- Load all at once with comments and likes

**Generated SQL:**
```sql
SELECT p.*, c.*, u.*
FROM posts p
LEFT JOIN comments c ON p.id = c.post_id
LEFT JOIN post_likes pl ON p.id = pl.post_id
LEFT JOIN users u ON pl.user_id = u.id
WHERE p.author_id IN (1, 2, 3, 4, 5)  -- List of author IDs
  AND p.hidden = FALSE
ORDER BY p.created_at DESC
```

**How to call:**
```java
// Get all users current user follows
Set<Subscription> subscriptions = currentUser.getSubscriptions();
List<Long> followedUserIds = subscriptions.stream()
    .map(sub -> sub.getSubscribedTo().getId())
    .toList();

// Get all their posts in ONE query
List<Post> feedPosts = postRepository
    .findPostsByAuthorIdsWithCommentsAndLikes(followedUserIds);
```

---

## CommentRepository.java

### Line 15: Simple Query Method

```java
15 List<Comment> findByPostId(Long postId);
```

**Generated JPQL:**
```java
SELECT c FROM Comment c WHERE c.post.id = :postId
```

**Generated SQL:**
```sql
SELECT * FROM comments WHERE post_id = ?
```

**Navigation through relationships:**
```java
// Comment entity has:
@ManyToOne
@JoinColumn(name="post_id")
private Post post;

// Query uses: c.post.id
// Hibernate knows: post_id column
```

---

### Line 19: Count Query

```java
19 long countByPostId(Long postId);
```

**Spring Data JPA magic:**
- Method starts with `count` → generates COUNT query
- Returns `long` → count result

**Generated SQL:**
```sql
SELECT COUNT(*) FROM comments WHERE post_id = ?
```

**Not**:
```sql
-- Doesn't do this:
SELECT * FROM comments WHERE post_id = ?
-- Then count in Java

-- Does this:
SELECT COUNT(*) FROM comments WHERE post_id = ?
-- Efficient database COUNT
```

---

### Lines 21-22: ORDER BY in Query

```java
21 @Query("SELECT c FROM Comment c WHERE c.post.id = :postId ORDER BY c.createdAt DESC")
22 List<Comment> findByPostIdOrderByCreatedAtDesc(@Param("postId") Long postId);
```

**Could also write as:**
```java
List<Comment> findByPostIdOrderByCreatedAtDesc(Long postId);
// Spring Data JPA parses method name:
// findBy → SELECT
// PostId → WHERE post_id = ?
// OrderBy → ORDER BY
// CreatedAt → created_at column
// Desc → DESC (descending)
```

**Both generate:**
```sql
SELECT * FROM comments
WHERE post_id = ?
ORDER BY created_at DESC
```

---

### Lines 27-29: @Modifying Query ⭐ IMPORTANT

```java
27 @Modifying
28 @Query("DELETE FROM Comment c WHERE c.author = :user")
29 void deleteByAuthor(@Param("user") User user);
```

**@Modifying annotation**

This tells Spring: "This query modifies data (INSERT/UPDATE/DELETE)"

**Why needed?**

```java
// Without @Modifying:
@Query("DELETE FROM Comment c WHERE c.author = :user")
void deleteByAuthor(User user);
// Spring thinks this is SELECT query
// ERROR! Throws exception

// With @Modifying:
@Modifying
@Query("DELETE FROM Comment c WHERE c.author = :user")
void deleteByAuthor(User user);
// Spring knows: this is DELETE query
// Executes as UPDATE/DELETE
```

**What happens internally:**
```java
@Modifying
@Query("DELETE FROM Comment c WHERE c.author = :user")
void deleteByAuthor(User user) {
    // Spring does:

    // 1. Start transaction (if not already started)
    // 2. Create query
    Query query = entityManager.createQuery("DELETE FROM Comment c WHERE c.author = :user");

    // 3. Set parameters
    query.setParameter("user", user);

    // 4. Execute UPDATE (not SELECT!)
    int deletedCount = query.executeUpdate();

    // 5. Clear persistence context (important!)
    entityManager.clear();
    // Why? Deleted entities might be in persistence context
    // Clearing ensures they're removed from cache

    // 6. Commit transaction
}
```

**Generated SQL:**
```sql
DELETE FROM comments WHERE author_id = ?
```

**@Modifying options:**
```java
@Modifying(clearAutomatically = true)  // Clear persistence context after
@Modifying(flushAutomatically = true)  // Flush before execution
@Query("UPDATE User u SET u.banned = true WHERE u.id = :id")
void banUser(@Param("id") Long id);
```

---

## NotificationRepository.java

### Lines 20-21: Pagination

```java
20 Page<Notification> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);
21 List<Notification> findByUserOrderByCreatedAtDesc(User user);
```

**Line 20: Returns `Page<T>`**

`Pageable` parameter enables pagination:

```java
// Usage:
Pageable pageable = PageRequest.of(
    0,    // Page number (0-indexed)
    10,   // Page size (10 items)
    Sort.by("createdAt").descending()  // Sort
);

Page<Notification> page = notificationRepository.findByUserOrderByCreatedAtDesc(user, pageable);

// Page object contains:
List<Notification> content = page.getContent();     // Actual data
int totalPages = page.getTotalPages();              // Total pages
long totalElements = page.getTotalElements();       // Total items
int pageNumber = page.getNumber();                  // Current page
boolean hasNext = page.hasNext();                   // Has next page?
boolean hasPrevious = page.hasPrevious();           // Has previous page?
```

**Generated SQL:**
```sql
-- Query 1: Get total count
SELECT COUNT(*) FROM notifications WHERE user_id = ?;

-- Query 2: Get page data
SELECT * FROM notifications
WHERE user_id = ?
ORDER BY created_at DESC
LIMIT 10 OFFSET 0;  -- Page 0, size 10
```

**Line 21: Returns `List<T>`**
- No pagination
- Returns all results

---

### Lines 24-25: COUNT Query

```java
24 @Query("SELECT COUNT(n) FROM Notification n WHERE n.user = :user AND n.read = false")
25 Long countUnreadByUser(@Param("user") User user);
```

**Why custom `@Query`?**

Could write as method name, but it's long:
```java
// Method name version:
Long countByUserAndReadFalse(User user);

// Custom query version (clearer intent):
@Query("SELECT COUNT(n) FROM Notification n WHERE n.user = :user AND n.read = false")
Long countUnreadByUser(User user);
```

**Generated SQL:**
```sql
SELECT COUNT(*) FROM notifications WHERE user_id = ? AND read = FALSE
```

**Use case:**
```java
// Notification badge count
Long unreadCount = notificationRepository.countUnreadByUser(currentUser);
// Display: 🔔 (5)
```

---

### Lines 27-29: Bulk UPDATE with @Modifying

```java
27 @Modifying
28 @Query("UPDATE Notification n SET n.read = true WHERE n.user = :user AND n.read = false")
29 void markAllAsReadByUser(@Param("user") User user);
```

**Bulk update - very efficient:**

**Without bulk update:**
```java
// BAD: Load all, update one by one
List<Notification> notifications = notificationRepository.findByUser(user);
for (Notification n : notifications) {
    n.setRead(true);
}
notificationRepository.saveAll(notifications);
// If 100 notifications:
// - 1 SELECT to load all
// - 100 UPDATEs (one per notification)
// - 101 queries total! 😱
```

**With bulk update:**
```java
// GOOD: One UPDATE query
notificationRepository.markAllAsReadByUser(user);
// 1 query! ✅
```

**Generated SQL:**
```sql
UPDATE notifications SET read = TRUE WHERE user_id = ? AND read = FALSE
```

**⚠️ Important:** Bulk updates bypass persistence context!

```java
// Scenario:
Notification n1 = notificationRepository.findById(1L).get();
// n1 is in persistence context: read = false

notificationRepository.markAllAsReadByUser(user);
// Database updated: read = true

System.out.println(n1.isRead());  // Prints: false ❌
// Persistence context still has old value!

// Solution:
entityManager.clear();  // Clear persistence context
n1 = notificationRepository.findById(1L).get();  // Reload
System.out.println(n1.isRead());  // Prints: true ✅
```

Spring automatically clears with `@Modifying(clearAutomatically = true)`.

---

## Summary: Repository Patterns

| Pattern | Example | When to Use |
|---------|---------|-------------|
| **Simple query method** | `findByUsername(String)` | Simple WHERE clause |
| **Complex query method** | `findByUsernameAndEmailIgnoreCase` | Multiple conditions |
| **@Query with JPQL** | `@Query("SELECT u FROM User u WHERE...")` | Complex queries |
| **JOIN FETCH** | `LEFT JOIN FETCH u.subscriptions` | Avoid N+1 problem |
| **Return IDs only** | `SELECT p.id FROM Post p` | Performance optimization |
| **COUNT query** | `countByPostId(Long)` | Get count efficiently |
| **@Modifying** | `@Modifying @Query("UPDATE...")` | Bulk updates/deletes |
| **Pagination** | `Page<T> findBy...(Pageable)` | Large result sets |

---

## Common Pitfalls

### 1. N+1 Query Problem
```java
// ❌ BAD:
List<Post> posts = postRepository.findAll();
for (Post post : posts) {
    User author = post.getAuthor();  // N queries!
}

// ✅ GOOD:
@Query("SELECT p FROM Post p LEFT JOIN FETCH p.author")
List<Post> findAllWithAuthors();
```

### 2. Too Many JOIN FETCHes
```java
// ❌ BAD: Cartesian explosion
SELECT p FROM Post p
LEFT JOIN FETCH p.comments
LEFT JOIN FETCH p.likedBy
LEFT JOIN FETCH p.author
// Results in massive result set!

// ✅ GOOD: Fetch only what you need
SELECT p FROM Post p LEFT JOIN FETCH p.author
// Fetch comments separately if needed
```

### 3. Forgetting @Modifying
```java
// ❌ ERROR:
@Query("DELETE FROM Comment c WHERE c.id = :id")
void deleteById(Long id);  // Throws exception!

// ✅ CORRECT:
@Modifying
@Query("DELETE FROM Comment c WHERE c.id = :id")
void deleteById(Long id);
```

### 4. LazyInitializationException
```java
// ❌ ERROR:
@Transactional
Post getPost(Long id) {
    return postRepository.findById(id).get();
}  // Transaction ends here

void displayPost(Long id) {
    Post post = getPost(id);
    post.getComments().size();  // LazyInitializationException!
}

// ✅ SOLUTION: Use JOIN FETCH
@Query("SELECT p FROM Post p LEFT JOIN FETCH p.comments WHERE p.id = :id")
Optional<Post> findByIdWithComments(Long id);
```

---

**Next**: Service Layer - Business logic with transactions explained!

Continue?
