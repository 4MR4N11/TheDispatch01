# Spring Data JPA Repositories: Automatic Query Implementation

> **Core Concept**: Understand how Spring Data JPA generates repository implementations automatically from method names and custom queries.

---

## Table of Contents
1. [What is Spring Data JPA?](#1-what-is-spring-data-jpa)
2. [JpaRepository Interface](#2-jparepository-interface)
3. [Query Method Derivation](#3-query-method-derivation)
4. [Custom JPQL Queries with @Query](#4-custom-jpql-queries-with-query)
5. [JOIN FETCH for Performance](#5-join-fetch-for-performance)
6. [Method Return Types](#6-method-return-types)
7. [@Modifying for Updates and Deletes](#7-modifying-for-updates-and-deletes)
8. [Query Parameters](#8-query-parameters)
9. [Pagination and Sorting](#9-pagination-and-sorting)
10. [Best Practices](#10-best-practices)

---

## 1. What is Spring Data JPA?

### 1.1 The Problem Without Spring Data JPA

**Without Spring Data JPA (manual repository):**

```java
@Repository
public class UserRepositoryImpl {
    @PersistenceContext
    private EntityManager entityManager;

    public Optional<User> findByUsername(String username) {
        TypedQuery<User> query = entityManager.createQuery(
            "SELECT u FROM User u WHERE u.username = :username",
            User.class
        );
        query.setParameter("username", username);
        try {
            return Optional.of(query.getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    public Optional<User> findByEmail(String email) {
        TypedQuery<User> query = entityManager.createQuery(
            "SELECT u FROM User u WHERE u.email = :email",
            User.class
        );
        query.setParameter("email", email);
        try {
            return Optional.of(query.getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    public List<User> findAll() {
        return entityManager.createQuery("SELECT u FROM User u", User.class)
            .getResultList();
    }

    public User save(User user) {
        if (user.getId() == null) {
            entityManager.persist(user);
            return user;
        } else {
            return entityManager.merge(user);
        }
    }

    // ... 50+ more methods
}
```

**Problems:**
- Repetitive boilerplate code
- Error-prone (typos in queries)
- Every entity needs its own repository implementation

### 1.2 With Spring Data JPA (Your Code)

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

**Benefits:**
- **Just an interface** - no implementation needed
- Spring generates implementation at runtime
- Type-safe queries (compile-time checking)
- Consistent API across all repositories

### 1.3 How Spring Data JPA Works

```
┌─────────────────────────────────────────────────────────┐
│  Your Code: UserRepository interface                   │
│  (Just method signatures, no implementation)            │
└────────────────────┬────────────────────────────────────┘
                     │
                     ↓ At application startup
┌─────────────────────────────────────────────────────────┐
│  Spring Data JPA analyzes method name:                 │
│  "findByUsername" → generates JPQL query                │
│  "findByEmail" → generates JPQL query                   │
└────────────────────┬────────────────────────────────────┘
                     │
                     ↓ Creates proxy class
┌─────────────────────────────────────────────────────────┐
│  SimpleJpaRepository (generated implementation)         │
│  - Uses EntityManager internally                        │
│  - Executes generated queries                           │
└─────────────────────────────────────────────────────────┘
```

---

## 2. JpaRepository Interface

### 2.1 Repository Hierarchy

```
CrudRepository<T, ID>
    ↓ extends
PagingAndSortingRepository<T, ID>
    ↓ extends
JpaRepository<T, ID>  ← Your repositories extend this
```

### 2.2 What JpaRepository Provides

**Your UserRepository:**
```java
public interface UserRepository extends JpaRepository<User, Long> {
    //                                                    ↑     ↑
    //                                            Entity Type  ID Type
}
```

**Inherited Methods (you get for free):**

```java
// Save operations
User save(User user);                    // INSERT or UPDATE
List<User> saveAll(Iterable<User> users);
User saveAndFlush(User user);            // Save and flush immediately

// Find operations
Optional<User> findById(Long id);        // Find by primary key
List<User> findAll();                    // Find all users
List<User> findAllById(Iterable<Long> ids);
boolean existsById(Long id);             // Check if exists

// Delete operations
void deleteById(Long id);                // Delete by ID
void delete(User user);                  // Delete entity
void deleteAll();                        // Delete all (dangerous!)
void deleteAllInBatch();                 // Batch delete (faster)

// Count operations
long count();                            // Count all users

// Paging and Sorting
Page<User> findAll(Pageable pageable);   // Paginated results
List<User> findAll(Sort sort);           // Sorted results
```

### 2.3 Using Inherited Methods

**Save:**
```java
@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;

    public User createUser(String username, String email) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        return userRepository.save(user);  // Inherited from JpaRepository
    }
}
```

**Find:**
```java
public User getUser(Long id) {
    return userRepository.findById(id)
        .orElseThrow(() -> new UserNotFoundException(id));
}
```

**Delete:**
```java
public void deleteUser(Long id) {
    userRepository.deleteById(id);
}
```

---

## 3. Query Method Derivation

### 3.1 How Method Names Become Queries

Spring Data JPA parses method names and generates JPQL queries automatically.

**Pattern:**
```
find + [Distinct] + By + Property + [Comparison] + [OrderBy + Property + Direction]
```

### 3.2 Your Repository Examples

#### Simple Property Queries

**`UserRepository.java:14-15`**
```java
Optional<User> findByEmail(String email);
Optional<User> findByUsername(String username);
```

**Generated JPQL:**
```sql
-- findByEmail:
SELECT u FROM User u WHERE u.email = ?1

-- findByUsername:
SELECT u FROM User u WHERE u.username = ?1
```

**Usage:**
```java
Optional<User> user = userRepository.findByUsername("john");
```

#### Nested Property Queries

**`CommentRepository.java:15-17`**
```java
List<Comment> findByPostId(Long postId);
List<Comment> findByAuthorId(Long authorId);
```

**Generated JPQL:**
```sql
-- findByPostId:
SELECT c FROM Comment c WHERE c.post.id = ?1

-- findByAuthorId:
SELECT c FROM Comment c WHERE c.author.id = ?1
```

**Why not `findByPost`?**
```java
// ❌ Wrong:
List<Comment> findByPost(Post post);  // Compares entire Post object

// ✅ Correct:
List<Comment> findByPostId(Long postId);  // Compares just ID (efficient)
```

#### Multiple Parameters

**`SubscriptionRepository.java:21`**
```java
Optional<Subscription> findBySubscriberAndSubscribedTo(User subscriber, User subscribedTo);
```

**Generated JPQL:**
```sql
SELECT s FROM Subscription s
WHERE s.subscriber = ?1 AND s.subscribedTo = ?2
```

**Usage:**
```java
User john = userRepository.findByUsername("john").get();
User jane = userRepository.findByUsername("jane").get();

Optional<Subscription> sub = subscriptionRepository
    .findBySubscriberAndSubscribedTo(john, jane);

if (sub.isPresent()) {
    System.out.println("John follows Jane");
}
```

### 3.3 Query Method Keywords

#### Comparison Keywords

```java
// Equality
findByUsername(String username)                 // WHERE username = ?
findByUsernameNot(String username)             // WHERE username <> ?

// Like / Contains
findByUsernameLike(String pattern)             // WHERE username LIKE ?
findByUsernameContaining(String substring)     // WHERE username LIKE %?%
findByUsernameStartingWith(String prefix)      // WHERE username LIKE ?%
findByUsernameEndingWith(String suffix)        // WHERE username LIKE %?

// Comparisons
findByAgeGreaterThan(Integer age)              // WHERE age > ?
findByAgeLessThan(Integer age)                 // WHERE age < ?
findByAgeGreaterThanEqual(Integer age)         // WHERE age >= ?
findByAgeLessThanEqual(Integer age)            // WHERE age <= ?
findByAgeBetween(Integer start, Integer end)   // WHERE age BETWEEN ? AND ?

// Null checks
findByEmailIsNull()                            // WHERE email IS NULL
findByEmailIsNotNull()                         // WHERE email IS NOT NULL

// Boolean
findByBanned(boolean banned)                   // WHERE banned = ?
findByBannedTrue()                             // WHERE banned = true
findByBannedFalse()                            // WHERE banned = false

// Collections
findByRoleIn(List<Role> roles)                 // WHERE role IN (?)
findByRoleNotIn(List<Role> roles)              // WHERE role NOT IN (?)

// Ordering
findByUsernameOrderByCreatedAtDesc(String username)  // ORDER BY created_at DESC
```

#### Logical Operators

```java
// AND
findByUsernameAndEmail(String username, String email)
// WHERE username = ? AND email = ?

// OR
findByUsernameOrEmail(String username, String email)
// WHERE username = ? OR email = ?

// Complex
findByUsernameAndBannedFalseOrderByCreatedAtDesc(String username)
// WHERE username = ? AND banned = false ORDER BY created_at DESC
```

### 3.4 Count and Exists Methods

**`CommentRepository.java:19`**
```java
long countByPostId(Long postId);
```

**Generated JPQL:**
```sql
SELECT COUNT(c) FROM Comment c WHERE c.post.id = ?1
```

**`SubscriptionRepository.java:23-26`**
```java
boolean existsBySubscriberAndSubscribedTo(User subscriber, User subscribedTo);
long countBySubscriber(User subscriber);
long countBySubscribedTo(User subscribedTo);
```

**Generated JPQL:**
```sql
-- existsBySubscriberAndSubscribedTo:
SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END
FROM Subscription s
WHERE s.subscriber = ?1 AND s.subscribedTo = ?2

-- countBySubscriber:
SELECT COUNT(s) FROM Subscription s WHERE s.subscriber = ?1

-- countBySubscribedTo:
SELECT COUNT(s) FROM Subscription s WHERE s.subscribedTo = ?1
```

**Usage:**
```java
// Check if already following
if (subscriptionRepository.existsBySubscriberAndSubscribedTo(john, jane)) {
    throw new AlreadyFollowingException();
}

// Get follower count
long followerCount = subscriptionRepository.countBySubscribedTo(jane);

// Get comment count for post
long commentCount = commentRepository.countByPostId(postId);
```

---

## 4. Custom JPQL Queries with @Query

### 4.1 When to Use @Query

**Use @Query when:**
- Query is too complex for method naming
- Need JOINs or subqueries
- Want to optimize performance (JOIN FETCH)
- Method name becomes too long/unreadable

### 4.2 Simple @Query Example

**`CommentRepository.java:21-22`**
```java
@Query("SELECT c FROM Comment c WHERE c.post.id = :postId ORDER BY c.createdAt DESC")
List<Comment> findByPostIdOrderByCreatedAtDesc(@Param("postId") Long postId);
```

**Why @Query here?**
- Could use: `findByPostIdOrderByCreatedAtDesc` (method derivation)
- But @Query is clearer and more explicit

### 4.3 @Query with Named Parameters

**`UserRepository.java:16-22`**
```java
@Query("""
    SELECT u FROM User u
    LEFT JOIN FETCH u.subscriptions s
    LEFT JOIN FETCH u.Posts p
    WHERE u.username = :username
""")
Optional<User> findByUsernameWithSubscriptionsAndPosts(@Param("username") String username);
```

**Named Parameters (`:paramName`):**
- More readable than positional parameters (`?1`, `?2`)
- Order doesn't matter
- Must use `@Param("paramName")` annotation

**Positional Parameters (alternative):**
```java
@Query("SELECT u FROM User u WHERE u.username = ?1")
Optional<User> findByUsername(String username);
//                             No @Param needed with positional
```

### 4.4 Text Blocks (Java 15+)

**Your code uses text blocks (`"""`)**
```java
@Query("""
    SELECT u FROM User u
    LEFT JOIN FETCH u.subscriptions s
    WHERE u.username = :username
""")
```

**Benefits:**
- Multi-line strings without concatenation
- Better readability
- Preserves formatting

**Old way (before Java 15):**
```java
@Query("SELECT u FROM User u " +
       "LEFT JOIN FETCH u.subscriptions s " +
       "WHERE u.username = :username")
```

### 4.5 Complex @Query Examples

#### Selecting Specific Fields

**`PostRepository.java:19-20`**
```java
@Query("SELECT p.id FROM Post p JOIN p.likedBy u WHERE u.id = :userId AND p.hidden = false")
List<Long> findPostsLikedByUser(@Param("userId") Long userId);
```

**Returns only IDs (not full Post objects):**
```java
List<Long> likedPostIds = postRepository.findPostsLikedByUser(userId);
// [1, 5, 7, 12] - just IDs, not Post objects
```

**Why?**
- More efficient (less data transferred)
- Use when you only need IDs

#### Multiple Conditions

**`PostRepository.java:28-29`**
```java
@Query("SELECT p FROM Post p LEFT JOIN FETCH p.comments LEFT JOIN FETCH p.likedBy WHERE p.author.id = :authorId AND p.hidden = false ORDER BY p.createdAt DESC")
List<Post> findAllVisibleByAuthorIdWithCommentsAndLikes(@Param("authorId") Long authorId);
```

**Breakdown:**
- `WHERE p.author.id = :authorId` - filter by author
- `AND p.hidden = false` - only visible posts
- `ORDER BY p.createdAt DESC` - newest first
- `LEFT JOIN FETCH` - eagerly load comments and likes

#### IN Clause with List Parameter

**`PostRepository.java:37-38`**
```java
@Query("SELECT p FROM Post p LEFT JOIN FETCH p.comments LEFT JOIN FETCH p.likedBy WHERE p.author.id IN :authorIds AND p.hidden = false ORDER BY p.createdAt DESC")
List<Post> findPostsByAuthorIdsWithCommentsAndLikes(@Param("authorIds") List<Long> authorIds);
```

**Usage:**
```java
// Get posts from followed users
User user = userRepository.findById(userId).get();
List<Long> followingIds = user.getSubscriptions().stream()
    .map(sub -> sub.getSubscribedTo().getId())
    .toList();

List<Post> feedPosts = postRepository
    .findPostsByAuthorIdsWithCommentsAndLikes(followingIds);
```

**Generated SQL:**
```sql
SELECT p.*, c.*, u.*
FROM posts p
LEFT JOIN comments c ON c.post_id = p.id
LEFT JOIN post_likes pl ON pl.post_id = p.id
LEFT JOIN users u ON u.id = pl.user_id
WHERE p.author_id IN (1, 2, 3, 5, 7)
  AND p.hidden = false
ORDER BY p.created_at DESC;
```

---

## 5. JOIN FETCH for Performance

### 5.1 The N+1 Problem Revisited

**Without JOIN FETCH:**
```java
List<Post> posts = postRepository.findAll();  // 1 query

for (Post post : posts) {
    Set<User> likes = post.getLikedBy();  // N queries (1 per post)
    System.out.println(likes.size());
}
// Total: 1 + N queries
```

### 5.2 Using JOIN FETCH

**`PostRepository.java:16-17`**
```java
@Query("SELECT p FROM Post p LEFT JOIN FETCH p.likedBy WHERE p.id = :postId AND p.hidden = false")
Optional<Post> findByIdWithLikes(@Param("postId") Long postId);
```

**Generated SQL:**
```sql
SELECT
    p.id, p.title, p.content, p.author_id,
    u.id, u.username, u.email  -- Users who liked loaded in same query
FROM posts p
LEFT JOIN post_likes pl ON pl.post_id = p.id
LEFT JOIN users u ON u.id = pl.user_id
WHERE p.id = :postId AND p.hidden = false;
```

**Result:**
```java
Post post = postRepository.findByIdWithLikes(1L).get();
Set<User> likes = post.getLikedBy();  // No additional query!
System.out.println(likes.size());
```

### 5.3 Multiple JOIN FETCH

**`PostRepository.java:22-23`**
```java
@Query("SELECT p FROM Post p LEFT JOIN FETCH p.comments LEFT JOIN FETCH p.likedBy WHERE p.id = :postId AND p.hidden = false")
Optional<Post> findByIdWithCommentsAndLikes(@Param("postId") Long postId);
```

**Loads post with both comments and likes in one query:**
```java
Post post = postRepository.findByIdWithCommentsAndLikes(1L).get();

List<Comment> comments = post.getComments();  // Already loaded
Set<User> likes = post.getLikedBy();          // Already loaded

// No additional queries!
```

### 5.4 JOIN FETCH Best Practices

**✅ Good:**
```java
// Fetch one-to-many relationships
@Query("SELECT u FROM User u LEFT JOIN FETCH u.Posts WHERE u.id = :id")

// Fetch many-to-one relationships
@Query("SELECT p FROM Post p LEFT JOIN FETCH p.author WHERE p.id = :id")

// Multiple relationships
@Query("SELECT p FROM Post p LEFT JOIN FETCH p.comments LEFT JOIN FETCH p.author")
```

**❌ Avoid:**
```java
// Multiple collections (cartesian product)
@Query("SELECT u FROM User u " +
       "LEFT JOIN FETCH u.Posts " +
       "LEFT JOIN FETCH u.comments " +
       "LEFT JOIN FETCH u.likedPosts")
// Problem: Returns duplicate users, massive result set
```

**Solution for Multiple Collections:**
```java
// Fetch collections in separate queries
@Query("SELECT u FROM User u LEFT JOIN FETCH u.Posts WHERE u.id = :id")
@Query("SELECT u FROM User u LEFT JOIN FETCH u.comments WHERE u.id = :id")
```

---

## 6. Method Return Types

### 6.1 Optional vs Entity

**Optional (Recommended):**
```java
Optional<User> findByUsername(String username);

// Usage:
User user = userRepository.findByUsername("john")
    .orElseThrow(() -> new UserNotFoundException("john"));
```

**Direct Entity (Risky):**
```java
User findByUsername(String username);  // Returns null if not found

// Usage:
User user = userRepository.findByUsername("john");
if (user == null) {  // Easy to forget null check!
    throw new UserNotFoundException("john");
}
```

**Why Optional?**
- Explicit contract: "might not exist"
- Forces handling of missing case
- Functional programming style

### 6.2 Collection Return Types

**List (Most Common):**
```java
List<Post> findAllByAuthorId(Long authorId);
// Returns empty list if no results (never null)
```

**Set (For Unique Results):**
```java
Set<User> findDistinctByRole(Role role);
```

**Stream (Memory Efficient for Large Results):**
```java
Stream<User> findAllByBannedFalse();

// Usage:
try (Stream<User> users = userRepository.findAllByBannedFalse()) {
    users.filter(u -> u.getRole() == Role.ADMIN)
         .forEach(u -> System.out.println(u.getUsername()));
}  // Stream closed automatically
```

### 6.3 Primitive Return Types

**Boolean:**
```java
boolean existsByUsername(String username);

// Usage:
if (userRepository.existsByUsername("john")) {
    throw new UsernameAlreadyExistsException();
}
```

**Long/Integer:**
```java
long countByRole(Role role);
long countByBannedTrue();

// Usage:
long adminCount = userRepository.countByRole(Role.ADMIN);
```

### 6.4 Page and Slice

**Page (with total count):**
```java
Page<User> findAll(Pageable pageable);

// Usage:
Pageable pageable = PageRequest.of(0, 10);  // Page 0, 10 items
Page<User> page = userRepository.findAll(pageable);

System.out.println("Total users: " + page.getTotalElements());
System.out.println("Total pages: " + page.getTotalPages());
System.out.println("Current page: " + page.getNumber());
List<User> users = page.getContent();
```

**Slice (without total count - faster):**
```java
Slice<User> findByBannedFalse(Pageable pageable);

// Usage:
Slice<User> slice = userRepository.findByBannedFalse(pageable);
System.out.println("Has next: " + slice.hasNext());
```

---

## 7. @Modifying for Updates and Deletes

### 7.1 Understanding @Modifying

**For non-SELECT queries:**
- UPDATE
- DELETE
- INSERT

**Must be used with `@Transactional`**

### 7.2 Delete Example

**`CommentRepository.java:27-29`**
```java
@Modifying
@Query("DELETE FROM Comment c WHERE c.author = :user")
void deleteByAuthor(@Param("user") User user);
```

**Usage:**
```java
@Service
public class UserService {
    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId).get();

        // Delete all user's comments
        commentRepository.deleteByAuthor(user);

        // Delete user
        userRepository.delete(user);
    }
}
```

**Generated SQL:**
```sql
DELETE FROM comments WHERE author_id = ?;
```

### 7.3 Complex Delete with OR

**`SubscriptionRepository.java:30-32`**
```java
@Modifying
@Query("DELETE FROM Subscription s WHERE s.subscriber = :user OR s.subscribedTo = :user")
void deleteByUser(@Param("user") User user);
```

**What this does:**
- Deletes subscriptions where user is the subscriber (user follows others)
- Deletes subscriptions where user is subscribed to (others follow user)
- Single query, very efficient

**Usage:**
```java
@Transactional
public void deleteUser(Long userId) {
    User user = userRepository.findById(userId).get();

    // Delete all subscriptions (following and followers)
    subscriptionRepository.deleteByUser(user);

    userRepository.delete(user);
}
```

### 7.4 Update Example

```java
@Modifying
@Query("UPDATE User u SET u.banned = true WHERE u.id = :userId")
int banUser(@Param("userId") Long userId);
//  ↑ Returns number of rows updated
```

**Usage:**
```java
@Transactional
public void banUser(Long userId) {
    int updated = userRepository.banUser(userId);
    if (updated == 0) {
        throw new UserNotFoundException(userId);
    }
}
```

### 7.5 @Modifying Best Practices

**❌ Without @Transactional (ERROR):**
```java
public void deleteComments(User user) {
    commentRepository.deleteByAuthor(user);
    // TransactionRequiredException: Executing an update/delete query
}
```

**✅ With @Transactional:**
```java
@Transactional
public void deleteComments(User user) {
    commentRepository.deleteByAuthor(user);  // Works!
}
```

**Clearing EntityManager:**
```java
@Modifying(clearAutomatically = true)
@Query("UPDATE User u SET u.banned = true WHERE u.id = :userId")
int banUser(@Param("userId") Long userId);
```

**Why clear?**
- EntityManager caches entities
- After bulk update, cache is stale
- `clearAutomatically = true` clears cache

---

## 8. Query Parameters

### 8.1 Named Parameters (@Param)

**Recommended approach:**
```java
@Query("SELECT u FROM User u WHERE u.username = :username AND u.email = :email")
Optional<User> findByUsernameAndEmail(
    @Param("username") String username,
    @Param("email") String email
);
```

**Benefits:**
- Parameter order doesn't matter
- More readable
- Refactoring-safe

### 8.2 Positional Parameters

```java
@Query("SELECT u FROM User u WHERE u.username = ?1 AND u.email = ?2")
Optional<User> findByUsernameAndEmail(String username, String email);
```

**Positional:**
- `?1` = first parameter
- `?2` = second parameter
- Order matters!

### 8.3 Collection Parameters

**`PostRepository.java:37-38`**
```java
@Query("... WHERE p.author.id IN :authorIds ...")
List<Post> findPostsByAuthorIdsWithCommentsAndLikes(@Param("authorIds") List<Long> authorIds);
```

**Usage:**
```java
List<Long> ids = List.of(1L, 2L, 3L, 5L);
List<Post> posts = postRepository.findPostsByAuthorIdsWithCommentsAndLikes(ids);
```

**Generated SQL:**
```sql
WHERE p.author_id IN (1, 2, 3, 5)
```

### 8.4 Like Queries with Parameters

```java
@Query("SELECT u FROM User u WHERE u.username LIKE %:searchTerm%")
List<User> searchByUsername(@Param("searchTerm") String searchTerm);

// Usage:
List<User> users = userRepository.searchByUsername("john");
// Finds: john, johnny, johnson, etc.
```

**Alternative (safer):**
```java
@Query("SELECT u FROM User u WHERE u.username LIKE :searchTerm")
List<User> searchByUsername(@Param("searchTerm") String searchTerm);

// Usage (add wildcards in code):
List<User> users = userRepository.searchByUsername("%" + searchTerm + "%");
```

---

## 9. Pagination and Sorting

### 9.1 Simple Sorting

**Method naming:**
```java
List<Post> findAllByAuthorIdOrderByCreatedAtDesc(Long authorId);
```

**Programmatic sorting:**
```java
Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
List<User> users = userRepository.findAll(sort);
```

**Multiple sort fields:**
```java
Sort sort = Sort.by("lastName").ascending()
                .and(Sort.by("firstName").ascending());
List<User> users = userRepository.findAll(sort);
```

### 9.2 Pagination

**Create Pageable:**
```java
Pageable pageable = PageRequest.of(
    0,      // Page number (0-indexed)
    10,     // Page size
    Sort.by(Sort.Direction.DESC, "createdAt")
);
```

**Use with repository:**
```java
Page<User> page = userRepository.findAll(pageable);

// Access results:
List<User> users = page.getContent();
long total = page.getTotalElements();
int totalPages = page.getTotalPages();
boolean hasNext = page.hasNext();
boolean hasPrevious = page.hasPrevious();
```

### 9.3 Custom Pageable Queries

```java
@Query("SELECT p FROM Post p LEFT JOIN FETCH p.comments LEFT JOIN FETCH p.likedBy WHERE p.hidden = false")
Page<Post> findAllVisible(Pageable pageable);
```

**Usage:**
```java
Pageable pageable = PageRequest.of(0, 20, Sort.by("createdAt").descending());
Page<Post> posts = postRepository.findAllVisible(pageable);
```

### 9.4 REST Controller with Pagination

```java
@RestController
@RequestMapping("/api/posts")
public class PostController {

    @GetMapping
    public Page<PostResponse> getPosts(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(defaultValue = "createdAt,desc") String[] sort
    ) {
        // Parse sort parameters
        List<Sort.Order> orders = Arrays.stream(sort)
            .map(s -> {
                String[] parts = s.split(",");
                String property = parts[0];
                Sort.Direction direction = parts.length > 1 && parts[1].equals("desc")
                    ? Sort.Direction.DESC
                    : Sort.Direction.ASC;
                return new Sort.Order(direction, property);
            })
            .toList();

        Pageable pageable = PageRequest.of(page, size, Sort.by(orders));
        Page<Post> posts = postRepository.findAllVisible(pageable);

        return posts.map(PostResponse::fromEntity);
    }
}
```

**API Usage:**
```
GET /api/posts?page=0&size=20&sort=createdAt,desc
GET /api/posts?page=1&size=10&sort=title,asc
GET /api/posts?page=0&size=50&sort=createdAt,desc&sort=title,asc
```

---

## 10. Best Practices

### 10.1 Choose the Right Approach

**Query Method Derivation - Use When:**
- Query is simple
- Method name is readable
- Standard CRUD operations

```java
✅ Optional<User> findByUsername(String username);
✅ List<Comment> findByPostId(Long postId);
✅ boolean existsByEmail(String email);
```

**@Query - Use When:**
- Query is complex
- Need JOINs or subqueries
- Performance optimization (JOIN FETCH)
- Method name too long

```java
✅ @Query("SELECT p FROM Post p LEFT JOIN FETCH p.comments LEFT JOIN FETCH p.likedBy WHERE p.id = :id")
   Optional<Post> findByIdWithCommentsAndLikes(@Param("id") Long id);
```

### 10.2 Return Optional for Single Results

```java
✅ Optional<User> findByUsername(String username);
❌ User findByUsername(String username);  // Returns null
```

### 10.3 Use JOIN FETCH to Avoid N+1

```java
✅ @Query("SELECT p FROM Post p LEFT JOIN FETCH p.author WHERE p.id = :id")
❌ Post findById(Long id);  // Author loaded lazily (N+1 problem)
```

### 10.4 Be Specific with Queries

```java
✅ List<Post> findAllVisibleByAuthorIdOrderByCreatedAtDesc(Long authorId);
❌ List<Post> findByAuthor(Long authorId);  // Unclear, includes hidden posts
```

### 10.5 Use Appropriate Return Types

```java
✅ boolean existsByUsername(String username);  // Fast, returns boolean
❌ Optional<User> findByUsername(String username).isPresent();  // Loads entity

✅ long countByRole(Role role);  // Fast, returns count
❌ userRepository.findAll().stream().filter(u -> u.getRole() == role).count();  // Loads all!
```

### 10.6 Limit Result Sets

```java
✅ Page<User> findAll(Pageable pageable);
✅ List<Post> findTop10ByOrderByCreatedAtDesc();
❌ List<Post> findAll();  // Could return millions of rows
```

### 10.7 Use @Transactional for @Modifying

```java
✅ @Transactional
   @Modifying
   @Query("DELETE FROM Comment c WHERE c.author = :user")
   void deleteByAuthor(@Param("user") User user);

❌ @Modifying (no @Transactional - ERROR!)
   @Query("DELETE FROM Comment c WHERE c.author = :user")
   void deleteByAuthor(@Param("user") User user);
```

---

## Key Takeaways

### What You Learned

1. **Spring Data JPA Magic**
   - Generates repository implementations automatically
   - Just define interface, no implementation needed
   - Parses method names into JPQL queries

2. **JpaRepository**
   - Provides CRUD operations out of the box
   - `save()`, `findById()`, `delete()`, `count()`, etc.
   - Pagination and sorting support

3. **Query Method Derivation**
   - Method names follow pattern: `findBy` + Property + Comparison
   - Keywords: `And`, `Or`, `Like`, `GreaterThan`, `Between`, etc.
   - `exists`, `count`, `delete` variants

4. **@Query for Custom Queries**
   - Use JPQL for complex queries
   - Named parameters with `@Param`
   - JOIN FETCH to avoid N+1 problem

5. **@Modifying**
   - Required for UPDATE and DELETE queries
   - Must be used with `@Transactional`
   - Returns number of affected rows

6. **Performance Optimization**
   - JOIN FETCH for eager loading
   - Pagination for large result sets
   - `exists` and `count` instead of loading entities
   - Return only needed fields (projections)

---

## What's Next?

You now understand how repositories work. Next:

**→ [07-REST-CONTROLLERS-HTTP-FLOW.md](./07-REST-CONTROLLERS-HTTP-FLOW.md)** - Building REST APIs with Spring MVC

**Key Questions for Next Section:**
- How does `@RestController` handle HTTP requests?
- What is `@RequestMapping` and how does it route URLs?
- How are request bodies converted to Java objects?
- What is the complete flow from HTTP request to database?

**Completed**:
- ✅ Java Essentials
- ✅ Spring Core (IoC, DI)
- ✅ Spring Boot Essentials
- ✅ JPA & Hibernate Basics
- ✅ JPA Relationships
- ✅ Spring Data JPA Repositories

**Next**: REST Controllers & HTTP Flow 🎯
