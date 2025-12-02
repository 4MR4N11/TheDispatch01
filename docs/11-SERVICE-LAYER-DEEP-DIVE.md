# Service Layer - Deep Dive: Business Logic & Transactions

## What Is the Service Layer?

The Service Layer contains **business logic** - the rules and operations that make your application work.

```
Controller → Service → Repository → Database
   ↓          ↓          ↓
Handle      Business   Database
HTTP        Logic      Access
```

**Responsibilities:**
- Validate business rules
- Coordinate multiple repositories
- Manage transactions
- Transform data (Entity ↔ DTO)
- Trigger side effects (notifications, emails)

---

## UserServiceImpl.java - Line by Line

**Location**: `backend/src/main/java/_blog/blog/service/UserServiceImpl.java`

### Lines 26-27: @Service Annotation

```java
26 @Service
27 public class UserServiceImpl implements UserService {
```

**What @Service does:**

1. **Marks as Spring Bean**
```java
// Spring creates:
UserServiceImpl bean = new UserServiceImpl(...dependencies...);
// Stores in ApplicationContext
```

2. **Enables Component Scanning**
```java
@SpringBootApplication  // Triggers scan
↓
Finds all @Service classes
↓
Creates beans automatically
```

3. **Semantic Meaning**
- `@Service` = Business logic layer
- `@Repository` = Data access layer
- `@Controller` = Web layer
- All do the same thing (create bean), different names for clarity

---

### Lines 29-37: Dependencies (Constructor Injection)

```java
29 private final UserRepository userRepository;
30 private final PasswordEncoder passwordEncoder;
31 private final AuthenticationManager authenticationManager;
32 private final PostService postService;
33 private final ReportRepository reportRepository;
34 private final NotificationRepository notificationRepository;
35 private final CommentRepository commentRepository;
36 private final SubscriptionRepository subscriptionRepository;
37 private final PostRepository postRepository;
```

**Why `private final`?**

```java
// final = immutable after construction
private final UserRepository userRepository;

public UserServiceImpl(UserRepository userRepository) {
    this.userRepository = userRepository;
    // After this, userRepository can NEVER change
}

// Trying to reassign:
this.userRepository = someOtherRepo;  // ❌ Compilation error!
```

**Benefits:**
- ✅ Thread-safe (immutable)
- ✅ Can't be changed after creation
- ✅ Forces initialization in constructor
- ✅ Clear dependencies (all in constructor)

---

### Lines 39-59: Constructor Injection Pattern

```java
39 public UserServiceImpl(
40     UserRepository userRepository,
41     AuthenticationManager authenticationManager,
42     PasswordEncoder passwordEncoder,
43     PostService postService,
44     ReportRepository reportRepository,
45     NotificationRepository notificationRepository,
46     CommentRepository commentRepository,
47     SubscriptionRepository subscriptionRepository,
48     PostRepository postRepository
49 ) {
50     this.authenticationManager = authenticationManager;
51     this.userRepository = userRepository;
52     this.passwordEncoder = passwordEncoder;
53     this.postService = postService;
54     this.reportRepository = reportRepository;
55     this.notificationRepository = notificationRepository;
56     this.commentRepository = commentRepository;
57     this.subscriptionRepository = subscriptionRepository;
58     this.postRepository = postRepository;
59 }
```

**How Spring calls this constructor:**

```java
// At application startup, Spring does:

// 1. Create all dependencies first
UserRepository userRepo = new UserRepositoryImpl();
PasswordEncoder passEnc = new BCryptPasswordEncoder();
AuthenticationManager authMgr = new ProviderManager(...);
PostService postSvc = new PostServiceImpl(...);
// ... all dependencies

// 2. Call constructor with all dependencies
UserServiceImpl userService = new UserServiceImpl(
    userRepo,
    authMgr,
    passEnc,
    postSvc,
    reportRepo,
    notifRepo,
    commentRepo,
    subsRepo,
    postRepo
);

// 3. Register as bean
applicationContext.registerBean("userServiceImpl", userService);
```

**Dependency Resolution Order:**

```
Spring needs: UserServiceImpl
    ↓
UserServiceImpl needs: PostService
    ↓
PostService needs: PostRepository
    ↓
PostRepository needs: EntityManager
    ↓
EntityManager provided by JPA
    ↓
Spring creates in order:
1. EntityManager
2. PostRepository
3. PostService
4. UserServiceImpl
```

---

### Lines 61-68: Register Method (No @Transactional)

```java
61 @Override
62 public User register(RegisterRequest request) {
63     User user = UserMapper.toEntity(request, passwordEncoder);
64     if (user.getAvatar() == null) {
65         user.setAvatar("");
66     }
67     return userRepository.save(user);
68 }
```

**Why no @Transactional here?**

`userRepository.save()` is **ALREADY transactional**.

```java
// Spring Data JPA's save() is internally:
@Transactional
public <S extends T> S save(S entity) {
    // Transaction starts here
    if (entity.getId() == null) {
        entityManager.persist(entity);
    } else {
        entityManager.merge(entity);
    }
    // Transaction commits here
    return entity;
}
```

**When to add @Transactional in service:**
- Multiple repository calls that should be atomic
- Need to coordinate operations
- Want to control transaction boundaries

---

### Lines 70-90: Authentication (Complex Business Logic)

```java
70 @Override
71 public User authenticate(LoginRequest request) {
72     // Find user by email or username
73     User user = findUserByEmailOrUsername(request.getUsernameOrEmail())
74             .orElseThrow(() -> new RuntimeException("User not found"));
75
76     // Check if user is banned
77     if (user.isBanned()) {
78         throw new RuntimeException("Your account has been banned. Please contact support.");
79     }
80
81     // Authenticate using the user's email as the principal
82     authenticationManager.authenticate(
83         new UsernamePasswordAuthenticationToken(
84             user.getEmail(), // Use email as consistent identifier for Spring Security
85             request.getPassword()
86         )
87     );
88
89     return user;
90 }
```

**Step-by-step execution:**

#### Line 73-74: Find user
```java
User user = findUserByEmailOrUsername(request.getUsernameOrEmail())
    .orElseThrow(() -> new RuntimeException("User not found"));

// Calls private method (lines 194-203)
private Optional<User> findUserByEmailOrUsername(String identifier) {
    // Try email first
    Optional<User> userByEmail = userRepository.findByEmail(identifier);
    if (userByEmail.isPresent()) {
        return userByEmail;
    }
    // Then try username
    return userRepository.findByUsername(identifier);
}

// SQL executed (max 2 queries):
// Query 1: SELECT * FROM users WHERE email = ?
// Query 2 (if not found): SELECT * FROM users WHERE username = ?
```

#### Line 77-79: Business rule validation
```java
if (user.isBanned()) {
    throw new RuntimeException("Your account has been banned. Please contact support.");
}
```

**This is business logic!**
- Not just database access
- Enforces rule: "Banned users can't login"
- Throws exception to stop authentication

#### Lines 82-87: Spring Security authentication

```java
authenticationManager.authenticate(
    new UsernamePasswordAuthenticationToken(
        user.getEmail(),        // Principal (username)
        request.getPassword()   // Credentials (password)
    )
);
```

**What happens internally:**

```java
// 1. AuthenticationManager receives token
UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
    "john@example.com",
    "password123"
);

// 2. Calls AuthenticationProvider
DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
provider.setUserDetailsService(customUserDetailsService);
provider.setPasswordEncoder(passwordEncoder);

// 3. Load user from database
UserDetails userDetails = customUserDetailsService.loadUserByUsername("john@example.com");
// Returns: User object (implements UserDetails)

// 4. Check password
boolean passwordMatches = passwordEncoder.matches(
    "password123",                // Entered password
    userDetails.getPassword()     // Stored hash
);

// 5. If matches:
Authentication auth = new UsernamePasswordAuthenticationToken(
    userDetails,
    null,
    userDetails.getAuthorities()  // Roles: [ROLE_USER]
);
SecurityContextHolder.getContext().setAuthentication(auth);

// 6. If doesn't match:
throw new BadCredentialsException("Bad credentials");
```

**Why return User at the end?**
```java
89 return user;
```

Controller needs user to:
- Generate JWT token
- Return user info to frontend

---

### Lines 92-114: getCurrentUser with @Transactional(readOnly = true)

```java
92  @Override
93  @Transactional(readOnly = true)
94  public UserResponse getCurrentUser(String username) {
95      User user = userRepository.findByUsernameWithSubscriptionsAndPosts(username)
96          .orElseThrow(() -> new RuntimeException("User not found"));
97
98      var subscriptions = user.getSubscriptions().stream()
99          .map(sub -> sub.getSubscribedTo().getUsername())
100         .toList();
101
102     return new UserResponse(
103         user.getId(),
104         user.getFirstName(),
105         user.getLastName(),
106         user.getUsername(),
107         user.getEmail(),
108         user.getAvatar(),
109         user.getRole().name(),
110         user.isBanned(),
111         subscriptions,
112         postService.getPostsRespByUserId(user.getId())
113     );
114 }
```

**@Transactional(readOnly = true) - Why and What?**

**Why `readOnly = true`?**

1. **Performance Optimization**
```java
// With readOnly = true:
// - Hibernate skips dirty checking (no need to track changes)
// - Database sets transaction as read-only (some DBs optimize)
// - No flush at end (saves time)
```

2. **Safety**
```java
// With readOnly = true:
user.setUsername("hacker");
// Changes are NOT saved to database!
// Transaction is read-only
```

3. **Intent Documentation**
```java
@Transactional(readOnly = true)
// Tells developers: "This method only READS, never WRITES"
```

**What does @Transactional do?** (Detailed)

Spring creates a **proxy** around your service:

```java
// Your code:
@Service
public class UserServiceImpl {
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String username) {
        // Your code
    }
}

// Spring creates proxy:
public class UserServiceImplProxy extends UserServiceImpl {

    private TransactionManager txManager;
    private EntityManager entityManager;

    @Override
    public UserResponse getCurrentUser(String username) {
        TransactionStatus tx = null;
        try {
            // 1. START TRANSACTION
            tx = txManager.getTransaction(new TransactionDefinition() {
                readOnly = true;
            });

            // 2. OPEN PERSISTENCE CONTEXT
            entityManager.openPersistenceContext();

            // 3. CALL YOUR METHOD
            UserResponse result = super.getCurrentUser(username);

            // 4. COMMIT (for read-only, just closes transaction)
            txManager.commit(tx);

            return result;

        } catch (Exception e) {
            // 5. ROLLBACK ON ERROR
            if (tx != null) {
                txManager.rollback(tx);
            }
            throw e;

        } finally {
            // 6. CLOSE PERSISTENCE CONTEXT
            entityManager.close();
        }
    }
}
```

**Line 95: JOIN FETCH query**
```java
User user = userRepository.findByUsernameWithSubscriptionsAndPosts(username)
```

This uses the custom repository method:
```java
@Query("""
    SELECT u FROM User u
    LEFT JOIN FETCH u.subscriptions s
    LEFT JOIN FETCH u.Posts p
    WHERE u.username = :username
""")
```

**Why JOIN FETCH here?**

Lines 98-100 and 112 access lazy-loaded collections:
```java
user.getSubscriptions().stream()  // Would trigger query without JOIN FETCH
postService.getPostsRespByUserId(user.getId())  // Needs posts
```

**With JOIN FETCH:**
```java
// 1 query loads: User + Subscriptions + Posts
User user = repo.findByUsernameWithSubscriptionsAndPosts("john");

// No additional queries:
user.getSubscriptions();  // Already loaded ✅
user.getPosts();          // Already loaded ✅
```

**Lines 98-100: Stream transformation**
```java
var subscriptions = user.getSubscriptions().stream()
    .map(sub -> sub.getSubscribedTo().getUsername())
    .toList();
```

Breaking down:
```java
// Input: Set<Subscription>
Set<Subscription> subs = user.getSubscriptions();

// Stream: Convert to stream
Stream<Subscription> stream = subs.stream();

// Map: Transform each Subscription to username string
Stream<String> usernames = stream.map(sub -> {
    User subscribedToUser = sub.getSubscribedTo();
    String username = subscribedToUser.getUsername();
    return username;
});

// Collect: Convert stream to List
List<String> subscriptions = usernames.toList();

// Result: ["jane", "bob", "alice"]
```

**Line 112: Service-to-Service call**
```java
postService.getPostsRespByUserId(user.getId())
```

**Transaction propagation:**
```java
@Transactional(readOnly = true)
public UserResponse getCurrentUser(...) {
    // Transaction 1 is active

    postService.getPostsRespByUserId(user.getId());
    // Does PostService start new transaction?
}

// In PostService.getPostsRespByUserId (no @Transactional):
public List<PostResponse> getPostsRespByUserId(Long userId) {
    // Uses existing transaction from caller
    // Propagation.REQUIRED (default)
}
```

---

### Lines 136-169: deleteUser with @Transactional ⭐ CRITICAL

```java
136 @Override
137 @Transactional
138 public boolean deleteUser(Long userId) {
139     User user = userRepository.findById(userId)
140         .orElseThrow(() -> new RuntimeException("User not found"));
141
142     // 1. Delete all reports (where user is reporter or reported user)
143     reportRepository.deleteByUser(user);
144
145     // 2. Delete all notifications (where user is recipient or actor)
146     notificationRepository.deleteByUser(user);
147
148     // 3. Delete all comments by this user
149     commentRepository.deleteByAuthor(user);
150
151     // 4. Delete all subscriptions (both as follower and following)
152     subscriptionRepository.deleteByUser(user);
153
154     // 5. Remove user from all liked posts
155     for (Post post : user.getLikedPosts()) {
156         post.getLikedBy().remove(user);
157     }
158
159     // 6. Delete all posts by this user (this will cascade delete comments and likes on those posts)
160     List<Post> userPosts = postRepository.findAllByAuthorId(userId);
161     for (Post post : userPosts) {
162         // Delete notifications for this post first
163         notificationRepository.deleteByPost(post);
164         postRepository.delete(post);
165     }
166
167     // 7. Finally, delete the user
168     userRepository.delete(user);
169     return true;
170 }
```

**Why @Transactional is CRITICAL here:**

**Without @Transactional (BAD):**
```java
public boolean deleteUser(Long userId) {
    reportRepository.deleteByUser(user);           // Transaction 1: COMMIT
    notificationRepository.deleteByUser(user);     // Transaction 2: COMMIT
    commentRepository.deleteByAuthor(user);        // Transaction 3: COMMIT
    subscriptionRepository.deleteByUser(user);     // Transaction 4: COMMIT

    // ERROR occurs here! 💥
    throw new RuntimeException("Disk full!");

    userRepository.delete(user);                   // Never executes

    // Result: Reports, notifications, comments, subscriptions deleted
    //         But USER still exists!
    //         Database is inconsistent! 😱
}
```

**With @Transactional (GOOD):**
```java
@Transactional
public boolean deleteUser(Long userId) {
    // Transaction starts
    reportRepository.deleteByUser(user);           // Pending...
    notificationRepository.deleteByUser(user);     // Pending...
    commentRepository.deleteByAuthor(user);        // Pending...
    subscriptionRepository.deleteByUser(user);     // Pending...

    // ERROR occurs here! 💥
    throw new RuntimeException("Disk full!");

    // Spring rolls back ALL operations
    // Nothing is deleted
    // Database remains consistent! ✅
}
```

**Order matters!**

```java
// 1. Delete reports first (no foreign key to user yet)
reportRepository.deleteByUser(user);

// 2. Delete notifications
notificationRepository.deleteByUser(user);

// 3. Delete comments
commentRepository.deleteByAuthor(user);

// 4. Delete subscriptions
subscriptionRepository.deleteByUser(user);

// 5. Remove from liked posts (many-to-many relationship)
for (Post post : user.getLikedPosts()) {
    post.getLikedBy().remove(user);
}

// 6. Delete user's posts
List<Post> userPosts = postRepository.findAllByAuthorId(userId);
for (Post post : userPosts) {
    notificationRepository.deleteByPost(post);  // Notifications about this post
    postRepository.delete(post);                // Post itself (cascades to comments)
}

// 7. Finally delete user
userRepository.delete(user);

// If you delete user first:
// ❌ ERROR: Foreign key constraint violation
// Other tables still reference this user!
```

**Lines 154-157: Managing many-to-many**
```java
for (Post post : user.getLikedPosts()) {
    post.getLikedBy().remove(user);
}
```

**Why manually remove?**

```java
// User has many liked posts (many-to-many)
// Post is the owner of relationship (@JoinTable in Post.java)
// We need to remove user from ALL posts they liked

// Before deletion:
post_likes table:
| post_id | user_id |
|---------|---------|
| 1       | 5       |  ← User 5 liked Post 1
| 2       | 5       |  ← User 5 liked Post 2

// After removing:
for (Post post : user.getLikedPosts()) {
    post.getLikedBy().remove(user);
}

// post_likes table:
| post_id | user_id |
|---------|---------|
(empty - user 5's likes removed)

// Now safe to delete user 5
userRepository.delete(user);  // ✅ No foreign key issues
```

**Lines 160-165: Nested loop with careful ordering**
```java
List<Post> userPosts = postRepository.findAllByAuthorId(userId);
for (Post post : userPosts) {
    // Delete notifications for this post first
    notificationRepository.deleteByPost(post);
    postRepository.delete(post);
}
```

**Why delete notifications first?**

```java
// Notification has optional foreign key to Post
@ManyToOne
@JoinColumn(name = "post_id")  // CAN be null
private Post post;

// If post is deleted first:
// Notifications would have post_id pointing to non-existent post
// Some databases allow this (NULL), others throw error

// Safer: Delete notifications first
notificationRepository.deleteByPost(post);  // Remove notifications
postRepository.delete(post);                // Then delete post
```

---

### Lines 206-267: updateProfile with Complex Validation

```java
206 @Override
207 @Transactional
208 public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
209     User user = userRepository.findById(userId)
210         .orElseThrow(() -> new RuntimeException("User not found"));
211
212     // Validate current password if changing password
213     if (request.getNewPassword() != null && !request.getNewPassword().isEmpty()) {
214         if (request.getCurrentPassword() == null || request.getCurrentPassword().isEmpty()) {
215             throw new RuntimeException("Current password is required to change password");
216         }
217         if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
218             throw new RuntimeException("Current password is incorrect");
219         }
220         user.setPassword(passwordEncoder.encode(request.getNewPassword()));
221     }
222
223     // Check if username is unique (if changed)
224     if (request.getUsername() != null && !request.getUsername().equals(user.getUsername())) {
225         if (userRepository.findByUsername(request.getUsername()).isPresent()) {
226             throw new RuntimeException("Username is already taken");
227         }
228         user.setUsername(request.getUsername());
229     }
230
231     // Check if email is unique (if changed)
232     if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
233         if (userRepository.findByEmail(request.getEmail()).isPresent()) {
234             throw new RuntimeException("Email is already taken");
235         }
236         user.setEmail(request.getEmail());
237     }
238
239     // Update other fields
240     if (request.getFirstName() != null && !request.getFirstName().isEmpty()) {
241         user.setFirstName(request.getFirstName());
242     }
243
244     if (request.getLastName() != null && !request.getLastName().isEmpty()) {
245         user.setLastName(request.getLastName());
246     }
247
248     if (request.getAvatar() != null) {
249         user.setAvatar(request.getAvatar());
250     }
251
252     User updatedUser = userRepository.save(user);
```

**Business Logic Validation Pattern**

This method demonstrates **defensive programming**:

#### Lines 213-221: Password change validation

```java
if (request.getNewPassword() != null && !request.getNewPassword().isEmpty()) {
    // User wants to change password

    // Rule 1: Must provide current password
    if (request.getCurrentPassword() == null || request.getCurrentPassword().isEmpty()) {
        throw new RuntimeException("Current password is required to change password");
    }

    // Rule 2: Current password must be correct
    if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
        throw new RuntimeException("Current password is incorrect");
    }

    // Rule 3: Encode new password
    user.setPassword(passwordEncoder.encode(request.getNewPassword()));
}
```

**Why passwordEncoder.matches()?**

```java
// Stored password (hashed):
String storedHash = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

// User enters:
String enteredPassword = "MyPassword123";

// Can't compare directly:
if (enteredPassword.equals(storedHash)) {  // ❌ Always false!
    // Never executes
}

// Must use passwordEncoder:
if (passwordEncoder.matches(enteredPassword, storedHash)) {  // ✅ Correct!
    // BCrypt hashes enteredPassword and compares
}

// How BCrypt works:
// 1. Hash entered password with same salt: hash("MyPassword123")
// 2. Compare result with stored hash
// 3. If match → password correct
```

#### Lines 224-229: Username uniqueness check

```java
if (request.getUsername() != null && !request.getUsername().equals(user.getUsername())) {
    // Username is being changed

    if (userRepository.findByUsername(request.getUsername()).isPresent()) {
        // New username already taken by another user
        throw new RuntimeException("Username is already taken");
    }
    user.setUsername(request.getUsername());
}
```

**Race condition consideration:**

```java
// Thread 1:
if (userRepository.findByUsername("john").isPresent()) {  // Not found
    // ...
}
user.setUsername("john");
userRepository.save(user);

// Thread 2 (simultaneous):
if (userRepository.findByUsername("john").isPresent()) {  // Not found
    // ...
}
user.setUsername("john");
userRepository.save(user);

// Both threads think "john" is available!
// ❌ Duplicate username!
```

**Solution: Database constraint**
```sql
CREATE TABLE users (
    username VARCHAR(30) UNIQUE  -- Database enforces uniqueness
);

// If both threads try to save:
Thread 1: INSERT ... username = 'john'  -- ✅ Succeeds
Thread 2: INSERT ... username = 'john'  -- ❌ ERROR: duplicate key

// @Column(unique=true) in entity ensures this
```

#### Line 252: Dirty checking and save

```java
User updatedUser = userRepository.save(user);
```

**What happens in transaction:**

```java
@Transactional
public UserResponse updateProfile(...) {
    // 1. Transaction starts
    // 2. Persistence context opened

    User user = userRepository.findById(userId).get();
    // 3. User loaded into persistence context
    // 4. Hibernate takes snapshot: {username: "john", email: "john@example.com"}

    user.setUsername("jane");
    user.setEmail("jane@example.com");
    // 5. User modified (still in memory)

    User updatedUser = userRepository.save(user);
    // 6. Hibernate compares current state with snapshot:
    //    Snapshot: {username: "john", email: "john@example.com"}
    //    Current:  {username: "jane", email: "jane@example.com"}
    //    Changed fields: username, email

    // 7. Generates UPDATE for only changed fields:
    //    UPDATE users
    //    SET username = 'jane', email = 'jane@example.com', updated_at = NOW()
    //    WHERE id = ?

    // 8. Transaction commits
    // 9. SQL executed to database
    // 10. Persistence context closed

    return ...;
}
```

---

## PostServiceImpl.java

### Lines 138-149: deletePost with Transaction

```java
138 @Override
139 @Transactional
140 public boolean deletePost(Long postId) {
141     Post post = postRepository.findById(postId)
142             .orElseThrow(() -> new RuntimeException("Post not found"));
143
144     // Delete all notifications related to this post first
145     notificationRepository.deleteByPost(post);
146
147     // Then delete the post (comments and likes are already handled by cascade)
148     postRepository.delete(post);
149     return true;
150 }
```

**Why delete notifications first?**

```java
// Post entity has:
@OneToMany(cascade=CascadeType.ALL, orphanRemoval=true)
private List<Comment> comments;  // Cascade deletes comments ✅

@ManyToMany
private Set<User> likedBy;  // Removes rows from post_likes table ✅

// But Notification entity has:
@ManyToOne
@JoinColumn(name = "post_id")  // Optional reference
private Post post;  // NOT cascaded from Post

// So we must manually delete notifications
notificationRepository.deleteByPost(post);
// SQL: DELETE FROM notifications WHERE post_id = ?

// Then delete post
postRepository.delete(post);
// Cascade deletes:
// - All comments on this post
// - All likes (post_likes rows)
```

**Transaction ensures atomicity:**
```java
@Transactional
public boolean deletePost(Long postId) {
    notificationRepository.deleteByPost(post);  // Operation 1
    postRepository.delete(post);                // Operation 2

    // If Operation 2 fails:
    // - Operation 1 is rolled back
    // - Nothing is deleted
    // - Database remains consistent
}
```

---

### Lines 97-110: getFeedPosts (Service Coordination)

```java
97  @Override
98  public List<Post> getFeedPosts(Long userId) {
99      List<User> subscriptions = subscriptionService.getSubscriptions(userId);
100
101     // Include the user's own ID along with followed users
102     List<Long> authorIds = new ArrayList<>();
103     authorIds.add(userId); // Add user's own posts
104
105     // Add followed users' posts
106     authorIds.addAll(subscriptions.stream()
107             .map(User::getId)
108             .toList());
109
110     return postRepository.findPostsByAuthorIdsWithCommentsAndLikes(authorIds);
111 }
```

**Service coordination pattern:**

```java
// PostService coordinates with SubscriptionService

// 1. Get users that current user follows
List<User> subscriptions = subscriptionService.getSubscriptions(userId);
// subscriptionService does: SELECT * FROM subscriptions WHERE subscriber_id = ?

// 2. Extract IDs
authorIds.add(userId);  // Include own posts
authorIds.addAll(subscriptions.stream().map(User::getId).toList());
// Result: [5, 7, 12, 18] (user's ID + followed users' IDs)

// 3. Get all posts from these authors
return postRepository.findPostsByAuthorIdsWithCommentsAndLikes(authorIds);
// SQL: SELECT p.*, c.*, u.*
//      FROM posts p
//      LEFT JOIN comments c ON p.id = c.post_id
//      LEFT JOIN post_likes pl ON p.id = pl.post_id
//      LEFT JOIN users u ON pl.user_id = u.id
//      WHERE p.author_id IN (5, 7, 12, 18)
//      ORDER BY p.created_at DESC
```

**Why not do this in repository?**

```java
// Could create repository method:
@Query("SELECT p FROM Post p WHERE p.author IN (SELECT s.subscribedTo FROM Subscription s WHERE s.subscriber.id = :userId)")
List<Post> findFeedPosts(@Param("userId") Long userId);

// But:
// ❌ Complex JPQL
// ❌ Hard to test
// ❌ Not reusable (what if we want to filter authors differently?)

// Better: Service coordinates multiple repositories
// ✅ Clear logic
// ✅ Reusable components
// ✅ Easy to test
```

---

## Summary: Service Layer Patterns

| Pattern | Purpose | Example |
|---------|---------|---------|
| **@Service** | Mark as business logic bean | `@Service public class UserServiceImpl` |
| **Constructor Injection** | Inject dependencies immutably | `public UserServiceImpl(UserRepository repo)` |
| **@Transactional** | Ensure atomicity of operations | Multiple deletes in deleteUser() |
| **readOnly = true** | Optimize read-only operations | getCurrentUser() |
| **Business Validation** | Enforce rules before database | Check password, unique username |
| **Service Coordination** | Combine multiple repositories | getFeedPosts() uses multiple services |
| **Error Handling** | Throw meaningful exceptions | "User not found", "Username taken" |
| **DTO Transformation** | Convert Entity to Response | Entity → UserResponse |

---

## Transaction Propagation Deep Dive

```java
// Scenario 1: No @Transactional
public void methodA() {
    userRepository.save(user);        // Transaction 1
    postRepository.save(post);        // Transaction 2
    // Two separate transactions!
}

// Scenario 2: @Transactional
@Transactional
public void methodA() {
    userRepository.save(user);        // Part of same transaction
    postRepository.save(post);        // Part of same transaction
    // One transaction for both!
}

// Scenario 3: Nested transactions
@Transactional
public void methodA() {
    userRepository.save(user);
    methodB();  // What transaction does this use?
}

@Transactional(propagation = Propagation.REQUIRED)  // Default
public void methodB() {
    // Uses methodA's transaction (joins existing)
}

@Transactional(propagation = Propagation.REQUIRES_NEW)
public void methodC() {
    // Creates NEW transaction (suspends methodA's transaction)
    // If this fails, methodA's transaction is NOT rolled back
}
```

---

**Next**: Controller Layer - HTTP request handling, validation, response building!

Continue?
