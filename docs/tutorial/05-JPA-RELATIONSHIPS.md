# JPA Relationships: Connecting Your Entities

> **Core Concept**: Understand how entities reference each other, how relationships map to database foreign keys and join tables, and how Hibernate manages these connections.

---

## Table of Contents
1. [Types of Relationships](#1-types-of-relationships)
2. [@ManyToOne - The "Belongs To" Relationship](#2-manytoone---the-belongs-to-relationship)
3. [@OneToMany - The "Has Many" Relationship](#3-onetomany---the-has-many-relationship)
4. [Bidirectional Relationships and mappedBy](#4-bidirectional-relationships-and-mappedby)
5. [@ManyToMany - Many-to-Many Relationships](#5-manytomany---many-to-many-relationships)
6. [Self-Referencing Relationships](#6-self-referencing-relationships)
7. [Cascade Types](#7-cascade-types)
8. [Orphan Removal](#8-orphan-removal)
9. [Fetch Types - LAZY vs EAGER](#9-fetch-types---lazy-vs-eager)
10. [N+1 Query Problem and Solutions](#10-n1-query-problem-and-solutions)

---

## 1. Types of Relationships

### 1.1 Relationship Cardinality

Your blog application has all four types:

```
@ManyToOne:   Comment → User     (many comments belong to one user)
@OneToMany:   User → Posts       (one user has many posts)
@ManyToMany:  Post ↔ User        (posts liked by users)
Self-Ref:     User ↔ User        (users follow users via Subscription)
```

### 1.2 Database Representation

**Entity Relationships → Database Foreign Keys and Join Tables:**

```
┌────────────────┐        ┌────────────────┐
│     users      │        │     posts      │
├────────────────┤        ├────────────────┤
│ id (PK)        │◄───────┤ author_id (FK) │  @ManyToOne
│ username       │        │ title          │
│ email          │        │ content        │
└────────────────┘        └────────────────┘
        │                         │
        │                         │
        │  ┌──────────────────┐   │
        └──┤  post_likes      │───┘  @ManyToMany
           ├──────────────────┤      (join table)
           │ post_id (FK)     │
           │ user_id (FK)     │
           └──────────────────┘
```

---

## 2. @ManyToOne - The "Belongs To" Relationship

### 2.1 Understanding @ManyToOne

**Concept**: Many entities reference one entity.

**Your Example - Post belongs to User:**

**`Post.java:41-43`**
```java
@ManyToOne
@JoinColumn(name = "author_id", nullable = false)
private User author;
```

**What this means:**
- Many posts belong to ONE user
- Each post has exactly one author
- Database: `posts` table has `author_id` foreign key

### 2.2 Database Schema

**Generated SQL:**
```sql
CREATE TABLE posts (
    id BIGSERIAL PRIMARY KEY,
    author_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    -- ... other fields
    CONSTRAINT fk_author
        FOREIGN KEY (author_id)
        REFERENCES users(id)
);
```

**Foreign Key Constraint:**
```sql
-- This prevents:
INSERT INTO posts (author_id, title, content)
VALUES (999, 'Title', 'Content');
-- ERROR: insert or update on table "posts" violates foreign key constraint
-- Key (author_id)=(999) is not present in table "users".
```

### 2.3 @JoinColumn Explained

**`Post.java:42`**
```java
@JoinColumn(name = "author_id", nullable = false)
```

**Attributes:**
- `name = "author_id"` - Foreign key column name in `posts` table
- `nullable = false` - NOT NULL constraint (post must have author)
- `referencedColumnName = "id"` - (optional) column in `users` table (defaults to primary key)

**Without @JoinColumn:**
```java
@ManyToOne
private User author;
// Hibernate generates: author_id column (field name + _id)
```

### 2.4 Using @ManyToOne

**Creating a post:**
```java
User user = userRepository.findById(1L).get();
//   ↑ User(id=1, username="john")

Post post = new Post();
post.setAuthor(user);  // Set the relationship
post.setTitle("Hello World");
post.setContent("My first post");

postRepository.save(post);
```

**Generated SQL:**
```sql
INSERT INTO posts (author_id, title, content, created_at, updated_at)
VALUES (1, 'Hello World', 'My first post', NOW(), NOW());
--      ↑ author.getId()
```

**Accessing the relationship:**
```java
Post post = postRepository.findById(1L).get();
User author = post.getAuthor();  // Might trigger SQL query
System.out.println(author.getUsername());  // "john"
```

**Generated SQL (if author not already loaded):**
```sql
SELECT * FROM users WHERE id = 1;
```

### 2.5 Comment Entity - Multiple @ManyToOne

**`Comment.java:33-39`**
```java
@ManyToOne
@JoinColumn(name = "author_id", nullable = false)
private User author;

@ManyToOne
@JoinColumn(name = "post_id", nullable = false)
private Post post;
```

**Database Schema:**
```sql
CREATE TABLE comments (
    id BIGSERIAL PRIMARY KEY,
    author_id BIGINT NOT NULL,    -- FK to users
    post_id BIGINT NOT NULL,      -- FK to posts
    content VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_comment_author FOREIGN KEY (author_id) REFERENCES users(id),
    CONSTRAINT fk_comment_post FOREIGN KEY (post_id) REFERENCES posts(id)
);
```

**Diagram:**
```
┌────────────┐       ┌──────────────┐       ┌────────────┐
│   users    │       │   comments   │       │   posts    │
├────────────┤       ├──────────────┤       ├────────────┤
│ id         │◄──────┤ author_id FK │       │ id         │
│ username   │       │ post_id FK   │───────►│ title      │
└────────────┘       │ content      │       │ content    │
                     └──────────────┘       └────────────┘
```

**Creating a comment:**
```java
User user = userRepository.findById(1L).get();
Post post = postRepository.findById(5L).get();

Comment comment = new Comment();
comment.setAuthor(user);  // Set author relationship
comment.setPost(post);    // Set post relationship
comment.setContent("Great post!");

commentRepository.save(comment);
```

**Generated SQL:**
```sql
INSERT INTO comments (author_id, post_id, content, created_at)
VALUES (1, 5, 'Great post!', NOW());
```

---

## 3. @OneToMany - The "Has Many" Relationship

### 3.1 Understanding @OneToMany

**Concept**: One entity references many entities.

**Your Example - User has many Posts:**

**`User.java:76-77`**
```java
@OneToMany(mappedBy = "author", cascade = CascadeType.ALL, orphanRemoval = true)
private List<Post> Posts;
```

**What this means:**
- One user has many posts
- `mappedBy = "author"` - refers to the `author` field in `Post` class
- This is the **inverse side** of the relationship

### 3.2 The "mappedBy" Attribute

**Key Concept**: In bidirectional relationships, one side is the owner, the other is the inverse.

```
┌─────────────────────────────────────────────────────────┐
│  @ManyToOne (Post.author)                               │
│  "Owning side" - has @JoinColumn                        │
│  Controls the foreign key                               │
└─────────────────────────────────────────────────────────┘
                        ↕
┌─────────────────────────────────────────────────────────┐
│  @OneToMany(mappedBy = "author")                        │
│  "Inverse side" - no @JoinColumn                        │
│  Refers to the field on the owning side                 │
└─────────────────────────────────────────────────────────┘
```

**Why mappedBy?**
- Tells Hibernate: "This relationship is already mapped by the `author` field in Post"
- Prevents duplicate foreign key columns
- Only the @ManyToOne side controls the database

**Without mappedBy (wrong):**
```java
@OneToMany
private List<Post> posts;
// Hibernate thinks: this is a separate relationship
// Creates: user_posts table (join table) - NOT WHAT WE WANT
```

**With mappedBy (correct):**
```java
@OneToMany(mappedBy = "author")
private List<Post> posts;
// Hibernate knows: use the author_id foreign key in posts table
```

### 3.3 Using @OneToMany

**Loading posts for a user:**
```java
User user = userRepository.findById(1L).get();
List<Post> posts = user.getPosts();  // Triggers SQL query
System.out.println(posts.size());    // 5 posts
```

**Generated SQL:**
```sql
SELECT * FROM posts WHERE author_id = 1;
```

**Adding a post to the collection:**
```java
User user = userRepository.findById(1L).get();
Post post = new Post();
post.setTitle("New Post");
post.setContent("Content");
post.setAuthor(user);  // ⚠️ IMPORTANT: set the owning side!

user.getPosts().add(post);  // Add to collection (optional)
postRepository.save(post);
```

**Why set both sides?**
```java
// Only setting collection side (WRONG - won't save):
user.getPosts().add(post);  // ❌ Doesn't set author_id in database

// Only setting owning side (CORRECT - saves):
post.setAuthor(user);       // ✅ Sets author_id in database

// Best practice - set both for in-memory consistency:
post.setAuthor(user);       // Database
user.getPosts().add(post);  // In-memory Java object
```

---

## 4. Bidirectional Relationships and mappedBy

### 4.1 Complete Bidirectional Example

**Post → User (owning side):**
```java
@Entity
public class Post {
    @ManyToOne
    @JoinColumn(name = "author_id", nullable = false)
    private User author;  // Owning side (has foreign key)
}
```

**User → Posts (inverse side):**
```java
@Entity
public class User {
    @OneToMany(mappedBy = "author", cascade = CascadeType.ALL)
    private List<Post> Posts;  // Inverse side (refers to Post.author)
}
```

### 4.2 Helper Methods for Bidirectional Relationships

**Best Practice - Add helper methods to keep both sides in sync:**

```java
@Entity
public class User {
    @OneToMany(mappedBy = "author", cascade = CascadeType.ALL)
    private List<Post> posts = new ArrayList<>();

    // Helper method to add post
    public void addPost(Post post) {
        posts.add(post);       // Add to collection
        post.setAuthor(this);  // Set owning side
    }

    // Helper method to remove post
    public void removePost(Post post) {
        posts.remove(post);    // Remove from collection
        post.setAuthor(null);  // Clear owning side
    }
}
```

**Usage:**
```java
User user = userRepository.findById(1L).get();
Post post = new Post();
post.setTitle("New Post");

user.addPost(post);  // Sets both sides automatically
userRepository.save(user);  // Cascades to post
```

### 4.3 Post → Comments Bidirectional Relationship

**Post side:**
**`Post.java:68-69`**
```java
@OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
private List<Comment> comments;
```

**Comment side:**
**`Comment.java:37-39`**
```java
@ManyToOne
@JoinColumn(name = "post_id", nullable = false)
private Post post;
```

**Complete Diagram:**
```
┌───────────────────────────┐
│         Post              │
├───────────────────────────┤
│ id (PK)                   │
│ @ManyToOne                │
│   User author             │  ─────► users.id
│                           │
│ @OneToMany(mappedBy="post")│
│   List<Comment> comments  │  ◄───── comments.post_id
└───────────────────────────┘
```

---

## 5. @ManyToMany - Many-to-Many Relationships

### 5.1 Understanding @ManyToMany

**Concept**: Many entities reference many entities (requires join table).

**Your Example - Posts liked by Users:**

**`Post.java:59-66`**
```java
@ManyToMany
@JoinTable(
    name = "post_likes",
    joinColumns = @JoinColumn(name = "post_id"),
    inverseJoinColumns = @JoinColumn(name = "user_id")
)
private Set<User> likedBy = new HashSet<>();
```

**User side:**
**`User.java:99-101`**
```java
@ManyToMany(mappedBy = "likedBy")
private Set<Post> likedPosts = new HashSet<>();
```

### 5.2 Join Table

**Generated Schema:**
```sql
CREATE TABLE post_likes (
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (post_id, user_id),  -- Composite primary key
    CONSTRAINT fk_post FOREIGN KEY (post_id) REFERENCES posts(id),
    CONSTRAINT fk_user FOREIGN KEY (user_id) REFERENCES users(id)
);
```

**Diagram:**
```
┌────────────┐      ┌──────────────┐      ┌────────────┐
│   posts    │      │  post_likes  │      │   users    │
├────────────┤      ├──────────────┤      ├────────────┤
│ id (PK)    │◄─────┤ post_id (FK) │      │ id (PK)    │
│ title      │      │ user_id (FK) │─────►│ username   │
└────────────┘      └──────────────┘      └────────────┘
                     Join Table (no entity class needed)
```

### 5.3 @JoinTable Explained

```java
@JoinTable(
    name = "post_likes",                            // Join table name
    joinColumns = @JoinColumn(name = "post_id"),    // Column for THIS entity (Post)
    inverseJoinColumns = @JoinColumn(name = "user_id")  // Column for OTHER entity (User)
)
```

**Without @JoinTable:**
```java
@ManyToMany
private Set<User> likedBy;
// Hibernate generates: post_user table (entity names concatenated)
// Columns: post_id, user_id
```

### 5.4 Using @ManyToMany

**User likes a post:**
```java
User user = userRepository.findById(1L).get();
Post post = postRepository.findById(5L).get();

// Add to owning side (Post.likedBy):
post.getLikedBy().add(user);
postRepository.save(post);

// OR add to inverse side (User.likedPosts):
user.getLikedPosts().add(post);
userRepository.save(user);
```

**Generated SQL:**
```sql
INSERT INTO post_likes (post_id, user_id)
VALUES (5, 1);
```

**Removing a like:**
```java
Post post = postRepository.findById(5L).get();
User user = userRepository.findById(1L).get();

post.getLikedBy().remove(user);
postRepository.save(post);
```

**Generated SQL:**
```sql
DELETE FROM post_likes
WHERE post_id = 5 AND user_id = 1;
```

**Querying likes:**
```java
// Get all users who liked a post:
Post post = postRepository.findById(5L).get();
Set<User> likers = post.getLikedBy();

// Get all posts a user liked:
User user = userRepository.findById(1L).get();
Set<Post> likedPosts = user.getLikedPosts();
```

### 5.5 Why Set instead of List?

**`Post.java:65`**
```java
private Set<User> likedBy = new HashSet<>();
//      ↑ Set, not List
```

**Reasons:**
1. **No duplicates** - user can't like the same post twice
2. **Performance** - `contains()` is O(1) with HashSet vs O(n) with List
3. **Semantics** - "liked by" is a mathematical set, not an ordered list

**With List (problems):**
```java
private List<User> likedBy = new ArrayList<>();

post.getLikedBy().add(user);  // Added
post.getLikedBy().add(user);  // Added again (duplicate!)
// Database: duplicate entry error
```

---

## 6. Self-Referencing Relationships

### 6.1 User Subscriptions - Self-Referencing @ManyToMany

**Problem**: Users follow other users (User → User relationship).

**Solution**: Use a join entity (Subscription).

**`Subscription.java:21-44`**
```java
@Entity
@Table(name = "subscriptions")
public class Subscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "subscriber_id", nullable = false)
    private User subscriber;  // User who follows

    @ManyToOne
    @JoinColumn(name = "subscribed_to_id", nullable = false)
    private User subscribedTo;  // User being followed

    @CreationTimestamp
    private Date createdAt;
}
```

**User side:**
**`User.java:80-85`**
```java
@OneToMany(mappedBy = "subscriber", cascade = CascadeType.ALL)
private Set<Subscription> subscriptions = new HashSet<>();  // Users I follow

@OneToMany(mappedBy = "subscribedTo", cascade = CascadeType.ALL)
private Set<Subscription> followers = new HashSet<>();  // Users who follow me
```

### 6.2 Database Schema

```sql
CREATE TABLE subscriptions (
    id BIGSERIAL PRIMARY KEY,
    subscriber_id BIGINT NOT NULL,     -- User who follows
    subscribed_to_id BIGINT NOT NULL,  -- User being followed
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_subscriber FOREIGN KEY (subscriber_id) REFERENCES users(id),
    CONSTRAINT fk_subscribed_to FOREIGN KEY (subscribed_to_id) REFERENCES users(id),
    CONSTRAINT uk_subscription UNIQUE (subscriber_id, subscribed_to_id)  -- Can't follow twice
);
```

**Diagram:**
```
┌──────────────┐
│    users     │
├──────────────┤
│ id           │◄─────┐
│ username     │      │
└──────────────┘      │
    ↑     ↑           │
    │     │           │
    │     └───────────┼────────────┐
    │                 │            │
┌───┴─────────────────┴───┐        │
│     subscriptions       │        │
├─────────────────────────┤        │
│ subscriber_id (FK)      │────────┘
│ subscribed_to_id (FK)   │
└─────────────────────────┘
```

### 6.3 Using Self-Referencing Relationships

**User follows another user:**
```java
User john = userRepository.findByUsername("john").get();
User jane = userRepository.findByUsername("jane").get();

Subscription subscription = new Subscription();
subscription.setSubscriber(john);     // John follows
subscription.setSubscribedTo(jane);   // Jane
subscriptionRepository.save(subscription);
```

**Generated SQL:**
```sql
INSERT INTO subscriptions (subscriber_id, subscribed_to_id, created_at)
VALUES (1, 2, NOW());
--      ↑  ↑
--   john jane
```

**Query subscriptions:**
```java
// Get users John follows:
User john = userRepository.findByUsername("john").get();
Set<Subscription> following = john.getSubscriptions();
for (Subscription sub : following) {
    System.out.println(john.getUsername() + " follows " + sub.getSubscribedTo().getUsername());
}

// Get John's followers:
Set<Subscription> followers = john.getFollowers();
for (Subscription sub : followers) {
    System.out.println(sub.getSubscriber().getUsername() + " follows " + john.getUsername());
}
```

**Helper methods in User entity:**
```java
@Entity
public class User {
    public Set<User> getFollowing() {
        return subscriptions.stream()
            .map(Subscription::getSubscribedTo)
            .collect(Collectors.toSet());
    }

    public Set<User> getFollowerUsers() {
        return followers.stream()
            .map(Subscription::getSubscriber)
            .collect(Collectors.toSet());
    }
}
```

---

## 7. Cascade Types

### 7.1 Understanding Cascade

**Concept**: Operations on parent entity cascade to child entities.

**Your Example:**
**`User.java:76`**
```java
@OneToMany(mappedBy = "author", cascade = CascadeType.ALL, orphanRemoval = true)
private List<Post> Posts;
```

**What is cascading?**
```java
User user = userRepository.findById(1L).get();
Post post = new Post();
post.setAuthor(user);
user.getPosts().add(post);

// Save user → automatically saves post (cascades)
userRepository.save(user);  // Saves user AND all posts
```

### 7.2 Cascade Types

```java
public enum CascadeType {
    PERSIST,   // Cascade save
    MERGE,     // Cascade update
    REMOVE,    // Cascade delete
    REFRESH,   // Cascade refresh
    DETACH,    // Cascade detach
    ALL        // All of the above (PERSIST + MERGE + REMOVE + REFRESH + DETACH)
}
```

#### CascadeType.PERSIST

```java
@OneToMany(cascade = CascadeType.PERSIST)
private List<Post> posts;

// Usage:
User user = new User();
user.setUsername("john");

Post post = new Post();
post.setAuthor(user);
post.setTitle("Hello");

user.getPosts().add(post);

userRepository.save(user);  // Saves user AND post
```

**Without PERSIST:**
```java
@OneToMany
private List<Post> posts;

userRepository.save(user);  // Saves only user
// post is NOT saved - must call postRepository.save(post) manually
```

#### CascadeType.REMOVE

```java
@OneToMany(cascade = CascadeType.REMOVE)
private List<Post> posts;

// Usage:
User user = userRepository.findById(1L).get();  // Has 5 posts
userRepository.delete(user);  // Deletes user AND all 5 posts
```

**Generated SQL:**
```sql
DELETE FROM posts WHERE author_id = 1;  -- Delete all posts first
DELETE FROM users WHERE id = 1;         -- Then delete user
```

#### CascadeType.ALL (Your Code)

**`User.java:76`**
```java
@OneToMany(mappedBy = "author", cascade = CascadeType.ALL)
private List<Post> Posts;
```

**Equivalent to:**
```java
cascade = {
    CascadeType.PERSIST,
    CascadeType.MERGE,
    CascadeType.REMOVE,
    CascadeType.REFRESH,
    CascadeType.DETACH
}
```

**What this means:**
- Save user → save posts
- Update user → update posts
- Delete user → delete posts
- Refresh user → refresh posts
- Detach user → detach posts

### 7.3 When to Use Cascade

**Use CASCADE for:**
- Parent-child relationships (User → Posts, Post → Comments)
- Child has no meaning without parent
- "Composition" relationships

**DON'T use CASCADE for:**
- Many-to-many relationships (usually)
- Shared entities (tags, categories)
- "Aggregation" relationships

**Example - Incorrect Cascade:**
```java
@ManyToMany(cascade = CascadeType.ALL)  // ❌ WRONG
private Set<User> likedBy;

// Problem:
postRepository.delete(post);
// Deletes post AND all users who liked it! (disaster)
```

**Correct:**
```java
@ManyToMany  // ✅ No cascade
private Set<User> likedBy;

postRepository.delete(post);
// Deletes post and removes rows from post_likes join table
// Users are not deleted
```

---

## 8. Orphan Removal

### 8.1 Understanding orphanRemoval

**`User.java:76`**
```java
@OneToMany(mappedBy = "author", cascade = CascadeType.ALL, orphanRemoval = true)
private List<Post> Posts;
```

**What is an orphan?**
An entity that no longer has a parent reference.

**Example:**
```java
User user = userRepository.findById(1L).get();
Post post = user.getPosts().get(0);

// Remove post from user's collection:
user.getPosts().remove(post);

// With orphanRemoval = true:
userRepository.save(user);  // Post is DELETED from database

// With orphanRemoval = false:
userRepository.save(user);  // Post still exists, author_id set to NULL
```

### 8.2 orphanRemoval vs CascadeType.REMOVE

**Different behaviors:**

```java
// orphanRemoval = true
user.getPosts().remove(post);  // Removes from collection
userRepository.save(user);     // Post DELETED from database

// vs

// CascadeType.REMOVE
userRepository.delete(user);   // User deleted → posts deleted
```

**orphanRemoval:**
- Deletes child when removed from collection
- Works on individual removals

**CascadeType.REMOVE:**
- Deletes all children when parent is deleted
- Works on parent deletion

### 8.3 When to Use orphanRemoval

**Use orphanRemoval = true when:**
- Child cannot exist without parent
- Removing from collection should delete from database
- Example: User → Posts, Post → Comments

**Your User → Posts:**
**`User.java:76`**
```java
@OneToMany(mappedBy = "author", cascade = CascadeType.ALL, orphanRemoval = true)
private List<Post> Posts;
```

**Behavior:**
```java
User user = userRepository.findById(1L).get();
Post post = user.getPosts().get(0);

// Remove post from user:
user.getPosts().remove(post);
userRepository.save(user);

// Generated SQL:
DELETE FROM posts WHERE id = ?;
```

**Your Post → Comments:**
**`Post.java:68`**
```java
@OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
private List<Comment> comments;
```

**Behavior:**
```java
Post post = postRepository.findById(5L).get();
Comment comment = post.getComments().get(0);

// Remove comment from post:
post.getComments().remove(comment);
postRepository.save(post);

// Generated SQL:
DELETE FROM comments WHERE id = ?;
```

---

## 9. Fetch Types - LAZY vs EAGER

### 9.1 Understanding Fetch Types

**Concept**: When should Hibernate load related entities?

**Two strategies:**
- **EAGER** - Load immediately with parent
- **LAZY** - Load only when accessed

**Default fetch types:**
```java
@ManyToOne   → EAGER (default)
@OneToMany   → LAZY (default)
@ManyToMany  → LAZY (default)
```

### 9.2 EAGER Fetching

**`Post.java:41-43`** (implicitly EAGER)
```java
@ManyToOne
@JoinColumn(name = "author_id", nullable = false)
private User author;
```

**Query:**
```java
Post post = postRepository.findById(1L).get();
```

**Generated SQL:**
```sql
SELECT
    p.id, p.title, p.content, p.author_id,
    u.id, u.username, u.email  -- Author loaded immediately
FROM posts p
LEFT JOIN users u ON p.author_id = u.id
WHERE p.id = 1;
```

**Accessing author (no additional query):**
```java
String authorName = post.getAuthor().getUsername();
// No SQL query - author already loaded
```

### 9.3 LAZY Fetching

**`User.java:76`** (explicitly LAZY by default)
```java
@OneToMany(mappedBy = "author", cascade = CascadeType.ALL)
private List<Post> Posts;
```

**Query:**
```java
User user = userRepository.findById(1L).get();
```

**Generated SQL:**
```sql
SELECT
    u.id, u.username, u.email, u.password, ...
FROM users u
WHERE u.id = 1;
-- Posts NOT loaded
```

**Accessing posts (triggers query):**
```java
List<Post> posts = user.getPosts();  // Triggers SQL query here
System.out.println(posts.size());
```

**Generated SQL (when accessed):**
```sql
SELECT
    p.id, p.title, p.content, p.author_id, ...
FROM posts p
WHERE p.author_id = 1;
```

### 9.4 LazyInitializationException

**Common error:**
```java
@Service
public class UserService {
    public User getUser(Long id) {
        return userRepository.findById(id).get();
    }  // Transaction ends here
}

@RestController
public class UserController {
    @GetMapping("/users/{id}")
    public UserResponse getUser(@PathVariable Long id) {
        User user = userService.getUser(id);

        // Try to access posts outside transaction:
        List<Post> posts = user.getPosts();
        // Exception: LazyInitializationException
        // "could not initialize proxy - no Session"
    }
}
```

**Solutions:**

#### Solution 1: Join Fetch
```java
@Query("SELECT u FROM User u LEFT JOIN FETCH u.Posts WHERE u.id = :id")
Optional<User> findByIdWithPosts(@Param("id") Long id);
```

#### Solution 2: @Transactional on controller method
```java
@GetMapping("/users/{id}")
@Transactional  // Keep transaction open during request
public UserResponse getUser(@PathVariable Long id) {
    User user = userService.getUser(id);
    List<Post> posts = user.getPosts();  // OK - transaction still open
    return new UserResponse(user, posts);
}
```

#### Solution 3: DTOs (Recommended)
```java
@Service
public class UserService {
    @Transactional(readOnly = true)
    public UserResponse getUser(Long id) {
        User user = userRepository.findById(id).get();
        List<Post> posts = user.getPosts();  // Load inside transaction
        return new UserResponse(user, posts);  // Return DTO
    }
}
```

### 9.5 When to Use EAGER vs LAZY

**Use EAGER:**
- Relationship almost always needed
- Small collections
- @ManyToOne relationships (default)

**Use LAZY (Recommended for most cases):**
- Large collections
- Optional relationships
- @OneToMany, @ManyToMany (default)
- Performance critical

**Example - Make @ManyToOne LAZY:**
```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "author_id")
private User author;
```

---

## 10. N+1 Query Problem and Solutions

### 10.1 The N+1 Problem

**Scenario**: Loading all posts and their authors.

**Naive approach:**
```java
List<Post> posts = postRepository.findAll();  // 1 query

for (Post post : posts) {
    System.out.println(post.getAuthor().getUsername());  // N queries
}
```

**Generated SQL:**
```sql
-- 1 query to get all posts:
SELECT * FROM posts;  -- Returns 100 posts

-- N queries to get authors (1 per post):
SELECT * FROM users WHERE id = 1;
SELECT * FROM users WHERE id = 2;
SELECT * FROM users WHERE id = 1;  -- Duplicate!
SELECT * FROM users WHERE id = 3;
-- ... 100 queries total!
```

**Total: 1 + N = 101 queries** (terrible performance!)

### 10.2 Solution 1: JOIN FETCH

**`UserRepository.java:16-22`** (Your code uses this!)
```java
@Query("""
    SELECT u FROM User u
    LEFT JOIN FETCH u.subscriptions s
    LEFT JOIN FETCH u.Posts p
    WHERE u.username = :username
""")
Optional<User> findByUsernameWithSubscriptionsAndPosts(@Param("username") String username);
```

**How JOIN FETCH works:**
```java
@Query("SELECT p FROM Post p LEFT JOIN FETCH p.author")
List<Post> findAllWithAuthors();
```

**Generated SQL:**
```sql
SELECT
    p.id, p.title, p.content, p.author_id,
    u.id, u.username, u.email  -- Author joined
FROM posts p
LEFT JOIN users u ON p.author_id = u.id;

-- 1 query instead of 101!
```

### 10.3 Solution 2: @EntityGraph

```java
@EntityGraph(attributePaths = {"author"})
@Query("SELECT p FROM Post p")
List<Post> findAllWithAuthors();
```

**Equivalent to JOIN FETCH** but cleaner for simple cases.

### 10.4 Solution 3: Batch Fetching

```java
@Entity
@BatchSize(size = 10)  // Fetch up to 10 authors at once
public class User {
    // ...
}
```

**Generated SQL (with batch fetching):**
```sql
-- 1 query to get all posts:
SELECT * FROM posts;  -- Returns 100 posts

-- Fewer queries with batch fetching:
SELECT * FROM users WHERE id IN (1, 2, 3, 4, 5, 6, 7, 8, 9, 10);  -- Batch 1
SELECT * FROM users WHERE id IN (11, 12, 13, 14, 15, 16, 17, 18, 19, 20);  -- Batch 2
-- ... 10 queries total (instead of 100)
```

---

## Key Takeaways

### What You Learned

1. **Relationship Types**
   - `@ManyToOne` - Foreign key in current table (Comment → User)
   - `@OneToMany` - Collection of entities (User → Posts)
   - `@ManyToMany` - Join table (Post ↔ User likes)
   - Self-referencing - Entity referencing itself (User → User subscriptions)

2. **Bidirectional Relationships**
   - `mappedBy` - marks inverse side, refers to owning side field
   - Owning side has `@JoinColumn`, controls database
   - Inverse side has `mappedBy`, no database control
   - Always update owning side to persist changes

3. **Cascade Types**
   - `CascadeType.PERSIST` - save parent → save children
   - `CascadeType.REMOVE` - delete parent → delete children
   - `CascadeType.ALL` - cascade all operations
   - Use for parent-child, not for many-to-many

4. **Orphan Removal**
   - `orphanRemoval = true` - removing from collection deletes from database
   - Use for entities that can't exist without parent
   - Different from `CascadeType.REMOVE`

5. **Fetch Types**
   - `EAGER` - load immediately (default for @ManyToOne)
   - `LAZY` - load when accessed (default for @OneToMany, @ManyToMany)
   - `LazyInitializationException` - accessing LAZY outside transaction
   - Use JOIN FETCH to eagerly load specific relationships

6. **N+1 Problem**
   - Occurs when loading collections with LAZY fetching
   - Solution: JOIN FETCH, @EntityGraph, or batch fetching
   - Always test queries with large datasets

---

## What's Next?

You now understand entity relationships. Next:

**→ [06-SPRING-DATA-JPA-REPOSITORIES.md](./06-SPRING-DATA-JPA-REPOSITORIES.md)** - Repository methods, query derivation, custom queries

**Key Questions for Next Section:**
- How does `findByUsername` work without implementation?
- How to write custom JPQL queries?
- What are specifications and criteria queries?
- How to implement pagination and sorting?

**Completed**:
- ✅ Java Essentials
- ✅ Spring Core (IoC, DI)
- ✅ Spring Boot Essentials
- ✅ JPA & Hibernate Basics
- ✅ JPA Relationships

**Next**: Spring Data JPA Repositories 🎯
