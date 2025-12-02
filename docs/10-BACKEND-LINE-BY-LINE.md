# Backend Code - Line by Line Explanation

## Part 1: Entity Classes

Entity classes represent database tables. Each entity = one table.

---

## User.java - Complete Line-by-Line Explanation

**Location**: `backend/src/main/java/_blog/blog/entity/User.java`

This is the most important entity - it represents a user in our system.

---

### Lines 1-34: Imports and Package Declaration

```java
1  package _blog.blog.entity;
```
**Line 1**: Declares which "folder" this class belongs to
- Think of packages like folders organizing files
- `_blog.blog.entity` means: project → blog → entity folder
- All entity classes go in this package

---

```java
3  import java.util.Collection;
4  import java.util.Date;
5  import java.util.HashSet;
6  import java.util.List;
7  import java.util.Set;
```
**Lines 3-7**: Import Java's built-in collection types
- `Collection` - Generic interface for groups of objects
- `Date` - Represents a point in time (when user was created)
- `HashSet` - Unordered collection with no duplicates (for followers)
- `List` - Ordered collection that allows duplicates (for posts)
- `Set` - Collection with no duplicates (for subscriptions)

💡 **Why different types?**
- Use `List` when order matters (user's posts in chronological order)
- Use `Set` when uniqueness matters (can't follow same person twice)

---

```java
9  import org.hibernate.annotations.CreationTimestamp;
10 import org.hibernate.annotations.UpdateTimestamp;
```
**Lines 9-10**: Hibernate's automatic timestamp annotations
- `@CreationTimestamp` - Automatically sets timestamp when entity is created
- `@UpdateTimestamp` - Automatically updates timestamp when entity changes

**Without these**:
```java
// Manual way (error-prone)
user.setCreatedAt(new Date());  // You must remember every time!
userRepository.save(user);
```

**With these**:
```java
// Automatic (reliable)
userRepository.save(user);  // Timestamps set automatically!
```

---

```java
11 import org.springframework.security.core.GrantedAuthority;
12 import org.springframework.security.core.authority.SimpleGrantedAuthority;
13 import org.springframework.security.core.userdetails.UserDetails;
```
**Lines 11-13**: Spring Security interfaces
- `GrantedAuthority` - Represents a permission (like "ROLE_USER")
- `SimpleGrantedAuthority` - Basic implementation of GrantedAuthority
- `UserDetails` - Interface that Spring Security uses for authentication

**Why implement UserDetails?**
Spring Security needs to know:
- Username
- Password
- Authorities/Roles
- Is account enabled?
- Is account locked?

By implementing `UserDetails`, we tell Spring Security how to get this info from our User class.

---

```java
15 import _blog.blog.enums.Role;
```
**Line 15**: Import our custom Role enum
- `Role` is defined in `enums/Role.java`
- Contains: `USER`, `ADMIN`
- Using enum (not String) prevents typos: can't accidentally type "ADMN"

---

```java
16 import jakarta.persistence.CascadeType;
17 import jakarta.persistence.Column;
18 import jakarta.persistence.Entity;
19 import jakarta.persistence.EnumType;
20 import jakarta.persistence.Enumerated;
21 import jakarta.persistence.GeneratedValue;
22 import jakarta.persistence.GenerationType;
23 import jakarta.persistence.Id;
24 import jakarta.persistence.ManyToMany;
25 import jakarta.persistence.OneToMany;
26 import jakarta.persistence.Table;
```
**Lines 16-26**: JPA (Jakarta Persistence API) annotations
- `CascadeType` - What happens to related entities when this one changes
- `Column` - Customize database column settings
- `Entity` - Mark this class as a database table
- `EnumType` - How to store enums in database
- `Enumerated` - Mark field as enum
- `GeneratedValue` - How to generate ID values
- `GenerationType` - Strategy for ID generation
- `Id` - Mark field as primary key
- `ManyToMany` - Many users can like many posts
- `OneToMany` - One user has many posts
- `Table` - Customize table name

---

```java
27 import jakarta.validation.constraints.Email;
28 import jakarta.validation.constraints.Size;
```
**Lines 27-28**: Validation constraints
- `@Email` - Ensures field contains valid email format
- `@Size` - Enforces min/max length

**Example validation**:
```java
@Email
private String email;  // Must be valid email

// Valid: "user@example.com"
// Invalid: "notanemail"  ❌ Validation fails
```

---

```java
29 import lombok.AllArgsConstructor;
30 import lombok.Builder;
31 import lombok.Getter;
32 import lombok.NoArgsConstructor;
33 import lombok.Setter;
```
**Lines 29-33**: Lombok annotations to reduce boilerplate
- `@AllArgsConstructor` - Generates constructor with all fields
- `@Builder` - Generates builder pattern
- `@Getter` - Generates all getter methods
- `@NoArgsConstructor` - Generates empty constructor
- `@Setter` - Generates all setter methods

---

### Lines 36-43: Class Declaration and Annotations

```java
36 @Entity
```
**Line 36**: Tells JPA this is a database entity
- Hibernate will create a table for this class
- Each instance = one row in the table

---

```java
37 @Table(name = "users")
```
**Line 37**: Customize the table name
- Without this: table would be called "user" (class name)
- With this: table is called "users" (plural)
- SQL: `CREATE TABLE users (...)`

💡 **Best practice**: Use plural names for table names

---

```java
38 @Getter
39 @Setter
```
**Lines 38-39**: Lombok generates getters and setters for ALL fields

**Generated code** (you don't write this):
```java
public Long getId() { return id; }
public void setId(Long id) { this.id = id; }
public String getUsername() { return username; }
public void setUsername(String username) { this.username = username; }
// ... for every field
```

---

```java
40 @NoArgsConstructor
```
**Line 40**: Generates empty constructor

**Generated code**:
```java
public User() {
    // Empty constructor
}
```

**Why needed?** JPA requires a no-args constructor to create entities from database results.

---

```java
41 @AllArgsConstructor
```
**Line 41**: Generates constructor with all fields

**Generated code**:
```java
public User(Long id, String firstName, String lastName, String username,
           String password, String email, String avatar, Role role,
           boolean banned, List<Post> posts, ...) {
    this.id = id;
    this.firstName = firstName;
    // ... all fields
}
```

---

```java
42 @Builder
```
**Line 42**: Generates builder pattern

**Usage**:
```java
// Without builder (messy)
User user = new User();
user.setUsername("john");
user.setEmail("john@example.com");
user.setPassword("hashedpassword");
user.setRole(Role.USER);

// With builder (clean)
User user = User.builder()
    .username("john")
    .email("john@example.com")
    .password("hashedpassword")
    .role(Role.USER)
    .build();
```

---

```java
43 public class User implements UserDetails {
```
**Line 43**: Class declaration
- `public` - Can be accessed from anywhere
- `class User` - Class name is User
- `implements UserDetails` - Implements Spring Security interface

**What "implements" means**:
```java
// UserDetails is a contract (interface)
interface UserDetails {
    String getUsername();
    String getPassword();
    Collection<? extends GrantedAuthority> getAuthorities();
    boolean isAccountNonExpired();
    boolean isAccountNonLocked();
    boolean isCredentialsNonExpired();
    boolean isEnabled();
}

// Our User class MUST provide these methods
// Spring Security will call them to authenticate users
```

---

### Lines 44-46: Primary Key (ID)

```java
44 @Id
```
**Line 44**: Marks this field as the primary key
- Every entity MUST have exactly one `@Id`
- Primary key uniquely identifies each row

---

```java
45 @GeneratedValue(strategy = GenerationType.IDENTITY)
```
**Line 45**: How to generate ID values automatically
- `GenerationType.IDENTITY` - Database auto-increments the ID
- PostgreSQL uses SERIAL type internally

**What happens**:
```sql
-- Database automatically generates IDs
INSERT INTO users (username, email, ...) VALUES ('john', 'john@example.com', ...);
-- Database assigns id = 1

INSERT INTO users (username, email, ...) VALUES ('jane', 'jane@example.com', ...);
-- Database assigns id = 2
```

**Other strategies**:
- `AUTO` - Let JPA choose
- `SEQUENCE` - Use database sequence
- `TABLE` - Use special table for IDs
- `IDENTITY` - Use auto-increment (our choice)

---

```java
46 private Long id;
```
**Line 46**: The actual ID field
- `Long` - 64-bit integer (can store very large numbers)
- Why `Long` not `long`? `Long` can be null, `long` cannot
- When creating new user: `id = null`
- After saving: database assigns ID

---

### Lines 48-52: Name Fields

```java
48 @Column(name = "first_name", nullable=false, length=30)
49 private String firstName;
```
**Lines 48-49**: First name field
- `@Column` - Customize how this field is stored
- `name = "first_name"` - Column name in database (snake_case)
  - Java uses camelCase: `firstName`
  - Database uses snake_case: `first_name`
- `nullable=false` - This field is required (cannot be NULL)
  - SQL: `first_name VARCHAR(30) NOT NULL`
- `length=30` - Maximum 30 characters
- `private String firstName` - Java field name

**Generated SQL**:
```sql
CREATE TABLE users (
    ...
    first_name VARCHAR(30) NOT NULL,
    ...
);
```

**What happens if you try to save null?**
```java
User user = User.builder()
    .username("john")
    // firstName not set (null)
    .build();
userRepository.save(user);
// ❌ ERROR: null value in column "first_name" violates not-null constraint
```

---

```java
51 @Column(name = "last_name", nullable=false, length=30)
52 private String lastName;
```
**Lines 51-52**: Last name field
- Same rules as firstName
- Required, max 30 characters

---

### Lines 54-56: Username Field

```java
54 @Column(nullable=false, unique=true, length=30)
55 @Size(min=4, max=30)
56 private String username;
```
**Lines 54-56**: Username field with validation
- `nullable=false` - Username is required
- `unique=true` - No two users can have same username
  - Database creates unique index
  - Prevents duplicate usernames
- `length=30` - Max 30 characters
- `@Size(min=4, max=30)` - Validation: username must be 4-30 characters
  - Checked BEFORE hitting database
  - Fails fast with clear error message

**Generated SQL**:
```sql
CREATE TABLE users (
    ...
    username VARCHAR(30) NOT NULL UNIQUE,
    ...
);
CREATE UNIQUE INDEX idx_users_username ON users(username);
```

**What happens with duplicate usernames?**
```java
User user1 = User.builder().username("john").build();
userRepository.save(user1);  // ✅ Saved

User user2 = User.builder().username("john").build();
userRepository.save(user2);  // ❌ ERROR: duplicate key value violates unique constraint
```

---

### Lines 58-59: Password Field

```java
58 @Column(nullable=false)
59 private String password;
```
**Lines 58-59**: Password field
- Stores HASHED password, never plain text
- Required field
- No max length specified (bcrypt hashes are always 60 chars)

**Security flow**:
```java
// Registration
String plainPassword = "MyPassword123";  // User enters this
String hashedPassword = passwordEncoder.encode(plainPassword);
// hashedPassword = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"
user.setPassword(hashedPassword);  // Store hash, not plain text
userRepository.save(user);

// Login
String enteredPassword = "MyPassword123";
boolean matches = passwordEncoder.matches(enteredPassword, user.getPassword());
if (matches) {
    // ✅ Password correct
} else {
    // ❌ Password wrong
}
```

💡 **Security**: Even if database is hacked, passwords are safe because they're hashed.

---

### Lines 61-63: Email Field

```java
61 @Email
62 @Column(nullable=false, unique=true)
63 private String email;
```
**Lines 61-63**: Email field with validation
- `@Email` - Validates email format (must contain @, domain, etc.)
- `nullable=false` - Email is required
- `unique=true` - No two users can have same email

**Validation examples**:
```java
// Valid emails
"user@example.com"      ✅
"john.doe@company.co.uk" ✅
"admin@localhost"       ✅

// Invalid emails (validation fails)
"notanemail"           ❌ No @ symbol
"@example.com"         ❌ No username
"user@"                ❌ No domain
```

---

### Lines 65: Avatar Field

```java
65 private String avatar;
```
**Line 65**: User's profile picture URL
- Optional (can be null)
- Stores file path or URL
- Example: `/uploads/avatars/user123.jpg`

---

### Lines 67-70: Role Field

```java
67 @Enumerated(EnumType.STRING)
68 @Column(nullable = false)
69 @Builder.Default
70 private Role role = Role.USER;
```
**Lines 67-70**: User's role (USER or ADMIN)

**Line 67**: `@Enumerated(EnumType.STRING)`
- Stores enum as string in database
- Alternative: `EnumType.ORDINAL` (stores as number)

**Why STRING vs ORDINAL?**
```java
// EnumType.STRING (our choice)
enum Role { USER, ADMIN }
Database stores: "USER", "ADMIN"
✅ Safe: Adding new roles doesn't break existing data

// EnumType.ORDINAL (dangerous)
enum Role { USER, ADMIN }
Database stores: 0, 1
❌ Dangerous: If you reorder enum, data gets corrupted!
enum Role { ADMIN, USER }  // Reordered!
Now all users become admins! 😱
```

**Line 69**: `@Builder.Default`
- When using builder, use this default value
- New users get `Role.USER` automatically

**Line 70**: `= Role.USER`
- Default value when entity is created
- New users are regular users, not admins

**Usage**:
```java
// Default role
User user = User.builder()
    .username("john")
    .build();
// user.role = Role.USER (automatic)

// Override default
User admin = User.builder()
    .username("admin")
    .role(Role.ADMIN)  // Override to make admin
    .build();
```

---

### Lines 72-74: Banned Field

```java
72 @Builder.Default
73 @Column(nullable = false)
74 private boolean banned = false;
```
**Lines 72-74**: Is user banned?
- `boolean` - true/false value
- Default: `false` (not banned)
- Nullable: false (must always have value)

**Generated SQL**:
```sql
banned BOOLEAN NOT NULL DEFAULT FALSE
```

**Usage in security**:
```java
@Override
public boolean isEnabled() {
    return !banned;  // User enabled if NOT banned
}
```

If user is banned:
- `banned = true`
- `isEnabled()` returns false
- Spring Security blocks login

---

### Lines 76-77: Posts Relationship

```java
76 @OneToMany(mappedBy="author", cascade=CascadeType.ALL, orphanRemoval=true)
77 private List<Post> Posts;
```
**Lines 76-77**: One user can have many posts

**Breaking down the annotation**:

**`@OneToMany`** - Relationship type
- One User → Many Posts
- User is the "one" side
- Post is the "many" side

**`mappedBy="author"`** - Who owns the relationship?
- The `Post` entity has a field called `author`
- That field points back to User
- "mappedBy" means: "I don't own this relationship, Post does"

**In Post.java, you'll find**:
```java
@ManyToOne
private User author;  // This field is the owner
```

**`cascade=CascadeType.ALL`** - Operations cascade to posts
```java
// Save user → automatically saves all posts
user.getPosts().add(newPost);
userRepository.save(user);  // Saves user AND newPost

// Delete user → automatically deletes all posts
userRepository.delete(user);  // Deletes user AND all their posts
```

**Cascade types**:
- `CascadeType.ALL` - All operations cascade
- `CascadeType.PERSIST` - Only save cascades
- `CascadeType.REMOVE` - Only delete cascades
- `CascadeType.MERGE` - Only update cascades

**`orphanRemoval=true`** - Delete posts that lose their parent
```java
user.getPosts().remove(post);  // Remove post from list
userRepository.save(user);
// Post is now an "orphan" (no parent)
// orphanRemoval=true → Post gets deleted from database
```

**`List<Post> Posts`** - Collection of user's posts
- Why `List`? Order matters (chronological order)
- Not initialized here (can be null initially)

---

### Lines 79-81: Subscriptions Relationship

```java
79 @Builder.Default
80 @OneToMany(mappedBy = "subscriber", cascade = CascadeType.ALL)
81 private Set<Subscription> subscriptions = new HashSet<>();
```
**Lines 79-81**: Who this user is following

**`@OneToMany(mappedBy = "subscriber")`**
- One User → Many Subscriptions
- User subscribes to many other users
- In `Subscription` entity: `subscriber` field points to this user

**`cascade = CascadeType.ALL`** - Cascade operations
```java
// Delete user → delete all their subscriptions
userRepository.delete(user);  // User + all subscriptions deleted
```

**`Set<Subscription>`** - Why Set not List?
- Set prevents duplicates
- Can't follow same person twice
- Order doesn't matter

**`= new HashSet<>()`** - Initialize empty set
- Prevents null pointer exceptions
- Can immediately add subscriptions

**`@Builder.Default`** - Use this default when building
```java
User user = User.builder().username("john").build();
// user.subscriptions = new HashSet<>() (not null)
```

---

### Lines 83-85: Followers Relationship

```java
83 @Builder.Default
84 @OneToMany(mappedBy = "subscribedTo", cascade = CascadeType.ALL)
85 private Set<Subscription> followers = new HashSet<>();
```
**Lines 83-85**: Who is following this user

**The difference**:
- `subscriptions` = Users YOU follow
- `followers` = Users who follow YOU

**Both use the same `Subscription` entity**:
```java
// Subscription.java has two fields:
private User subscriber;     // Person following
private User subscribedTo;   // Person being followed
```

**Example**:
```java
// John follows Jane
Subscription sub = new Subscription();
sub.setSubscriber(john);      // john.subscriptions includes this
sub.setSubscribedTo(jane);    // jane.followers includes this
```

**Get follower count**:
```java
int followerCount = user.getFollowers().size();
```

---

### Lines 87-88: Comments Relationship

```java
87 @OneToMany(mappedBy = "author", cascade = CascadeType.ALL, orphanRemoval = true)
88 private List<Comment> comments;
```
**Lines 87-88**: User's comments on posts
- One User → Many Comments
- `orphanRemoval = true` - Delete comment if removed from list
- Why `List`? Order matters (chronological)

---

### Lines 90-92: Created Timestamp

```java
90 @CreationTimestamp
91 @Column(updatable=false, name = "created_at")
92 private Date createdAt;
```
**Lines 90-92**: When user account was created

**`@CreationTimestamp`** - Hibernate sets this automatically
```java
User user = new User();
// createdAt is null here
userRepository.save(user);
// Hibernate automatically sets createdAt = current time
```

**`updatable=false`** - Can NEVER be changed
```java
user.setCreatedAt(new Date());  // Try to change it
userRepository.save(user);
// Database IGNORES the change, keeps original value
```

**Why immutable?** Creation time should never change!

**`name = "created_at"`** - Database column name (snake_case)

---

### Lines 94-96: Updated Timestamp

```java
94 @UpdateTimestamp
95 @Column(name = "updated_at")
96 private Date updatedAt;
```
**Lines 94-96**: When user was last updated

**`@UpdateTimestamp`** - Updates automatically on every save
```java
// First save
userRepository.save(user);
// updatedAt = 2025-10-27 10:00:00

// Update user
user.setUsername("newname");
userRepository.save(user);
// updatedAt = 2025-10-27 11:00:00 (automatically updated!)
```

**No `updatable=false`** - This one SHOULD change!

---

### Lines 99-101: Liked Posts Relationship

```java
99  @ManyToMany(mappedBy = "likedBy")
100 @Builder.Default
101 private Set<Post> likedPosts = new HashSet<>();
```
**Lines 99-101**: Posts this user liked

**`@ManyToMany`** - Many users can like many posts
- Many Users → Many Posts
- Requires join table in database

**`mappedBy = "likedBy"`** - Post entity owns relationship
```java
// In Post.java:
@ManyToMany
private Set<User> likedBy;  // Owner of relationship
```

**Generated join table**:
```sql
CREATE TABLE post_liked_by (
    post_id BIGINT,
    user_id BIGINT,
    PRIMARY KEY (post_id, user_id),
    FOREIGN KEY (post_id) REFERENCES posts(id),
    FOREIGN KEY (user_id) REFERENCES users(id)
);
```

**Usage**:
```java
// User likes a post
post.getLikedBy().add(user);
postRepository.save(post);
// Automatically adds row to join table

// Check if user liked post
boolean liked = user.getLikedPosts().contains(post);

// Get all posts user liked
Set<Post> likedPosts = user.getLikedPosts();
```

---

### Lines 103-126: UserDetails Interface Methods

These methods are required by Spring Security's `UserDetails` interface.

```java
103 @Override
104 public boolean isAccountNonExpired() {
105     return true;
106 }
```
**Lines 103-106**: Is account expired?
- `@Override` - We're implementing interface method
- `return true` - Our accounts never expire
- If you wanted expiring accounts:
```java
public boolean isAccountNonExpired() {
    return new Date().before(this.expirationDate);
}
```

---

```java
108 @Override
109 public boolean isAccountNonLocked() {
110     return true;
111 }
```
**Lines 108-111**: Is account locked?
- `return true` - Accounts are never locked
- Different from banned!
  - Locked = temporary (like after failed login attempts)
  - Banned = permanent
- We use `banned` field instead

---

```java
113 @Override
114 public boolean isCredentialsNonExpired() {
115     return true;
116 }
```
**Lines 113-116**: Do credentials expire?
- `return true` - Passwords never expire
- Some systems force password changes every 90 days
- We don't do that

---

```java
118 @Override
119 public boolean isEnabled() {
120     return !banned;
121 }
```
**Lines 118-121**: Is account enabled?
- `return !banned` - Enabled if NOT banned
- This is where our `banned` field is used
- If `banned = true`:
  - `isEnabled()` returns `false`
  - Spring Security blocks login

**Login flow**:
```java
// User tries to login
String username = "john";
User user = userService.loadUserByUsername(username);

// Spring Security checks:
if (!user.isEnabled()) {
    throw new DisabledException("Account is disabled");
}
// Continue with authentication...
```

---

```java
123 @Override
124 public Collection<? extends GrantedAuthority> getAuthorities() {
125     return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
126 }
```
**Lines 123-126**: What permissions does user have?

**Breaking down the return statement**:

`role.name()` - Converts enum to string
```java
Role.USER.name()   // Returns "USER"
Role.ADMIN.name()  // Returns "ADMIN"
```

`"ROLE_" + role.name()` - Prefix with "ROLE_"
```java
"ROLE_" + "USER"   // Returns "ROLE_USER"
"ROLE_" + "ADMIN"  // Returns "ROLE_ADMIN"
```

**Why "ROLE_" prefix?** Spring Security convention. Allows:
```java
@PreAuthorize("hasRole('USER')")  // Notice: no ROLE_ prefix in annotation
public void someMethod() { }
// Spring automatically looks for "ROLE_USER"
```

`new SimpleGrantedAuthority(...)` - Create authority object
```java
GrantedAuthority authority = new SimpleGrantedAuthority("ROLE_USER");
```

`List.of(...)` - Return as immutable list
```java
// Returns: [GrantedAuthority("ROLE_USER")]
```

**Complete example**:
```java
User admin = new User();
admin.setRole(Role.ADMIN);

Collection<? extends GrantedAuthority> authorities = admin.getAuthorities();
// Returns: [SimpleGrantedAuthority("ROLE_ADMIN")]

// Spring Security uses this to check permissions:
@PreAuthorize("hasRole('ADMIN')")
public void deleteAnyPost() {
    // Only users with "ROLE_ADMIN" can call this
}
```

---

## Summary: What Each Annotation Does

| Annotation | Purpose | Example |
|------------|---------|---------|
| `@Entity` | Mark as database table | Creates `users` table |
| `@Table(name)` | Custom table name | `users` not `user` |
| `@Id` | Primary key | Unique identifier |
| `@GeneratedValue` | Auto-generate ID | 1, 2, 3, ... |
| `@Column` | Column settings | `nullable`, `unique`, `length` |
| `@OneToMany` | One-to-many relationship | One user, many posts |
| `@ManyToMany` | Many-to-many relationship | Many users like many posts |
| `@Enumerated` | Store enum | "USER" or "ADMIN" |
| `@CreationTimestamp` | Auto-set on create | When account created |
| `@UpdateTimestamp` | Auto-update on save | When account updated |
| `@Email` | Validate email format | Must contain @ |
| `@Size` | Validate length | Username 4-30 chars |
| `@Getter/@Setter` | Lombok: generate methods | `getUsername()` |
| `@Builder` | Lombok: builder pattern | `.username("john").build()` |
| `@Builder.Default` | Default value in builder | New users not banned |

---

## Database Representation

When you run the application, Hibernate creates this table:

```sql
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    first_name VARCHAR(30) NOT NULL,
    last_name VARCHAR(30) NOT NULL,
    username VARCHAR(30) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    avatar VARCHAR(255),
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    banned BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- Join table for many-to-many (liked posts)
CREATE TABLE post_liked_by (
    post_id BIGINT REFERENCES posts(id),
    user_id BIGINT REFERENCES users(id),
    PRIMARY KEY (post_id, user_id)
);
```

---

**Next**: I'll explain `Post.java` line by line. Would you like me to continue?

---

## Post.java - Complete Line-by-Line Explanation

**Location**: `backend/src/main/java/_blog/blog/entity/Post.java`

This entity represents blog posts - the core content of our application.

---

### Lines 1-28: Package and Imports

```java
1  package _blog.blog.entity;
```
**Line 1**: Package declaration - this class belongs to the entity package

---

```java
3  import java.util.Date;
4  import java.util.HashSet;
5  import java.util.List;
6  import java.util.Set;
```
**Lines 3-6**: Java's built-in types
- `Date` - Timestamp for when post was created/updated
- `HashSet` - Implementation of Set (no duplicate likes)
- `List` - Ordered collection for comments
- `Set` - Unordered collection with no duplicates for likes

---

```java
8  import org.hibernate.annotations.CreationTimestamp;
9  import org.hibernate.annotations.UpdateTimestamp;
```
**Lines 8-9**: Hibernate's automatic timestamp annotations
- Same as in User.java
- Automatically manage creation and update times

---

```java
11 import jakarta.persistence.CascadeType;
12 import jakarta.persistence.Column;
13 import jakarta.persistence.Entity;
14 import jakarta.persistence.GeneratedValue;
15 import jakarta.persistence.GenerationType;
16 import jakarta.persistence.Id;
17 import jakarta.persistence.JoinColumn;
18 import jakarta.persistence.JoinTable;
19 import jakarta.persistence.ManyToMany;
20 import jakarta.persistence.ManyToOne;
21 import jakarta.persistence.OneToMany;
22 import jakarta.persistence.Table;
```
**Lines 11-22**: JPA annotations for database mapping
- `JoinColumn` - NEW: Customize foreign key column
- `JoinTable` - NEW: Customize join table for many-to-many
- Rest are same as User.java

---

```java
23 import lombok.AllArgsConstructor;
24 import lombok.Builder;
25 import lombok.Getter;
26 import lombok.NoArgsConstructor;
27 import lombok.Setter;
```
**Lines 23-27**: Lombok annotations (same as User.java)

---

### Lines 29-36: Class Declaration

```java
29 @Entity
30 @Table(name = "posts")
31 @Getter
32 @Setter
33 @NoArgsConstructor
34 @AllArgsConstructor
35 @Builder
36 public class Post {
```
**Lines 29-36**: Standard entity setup
- `@Entity` - This is a database entity
- `@Table(name = "posts")` - Table is called "posts"
- Lombok annotations generate boilerplate code

---

### Lines 37-39: Primary Key

```java
37 @Id
38 @GeneratedValue(strategy=GenerationType.IDENTITY)
39 private Long id;
```
**Lines 37-39**: Auto-incrementing primary key
- Identical to User.java
- Database automatically assigns: 1, 2, 3, 4...

---

### Lines 41-43: Author Relationship ⭐ IMPORTANT

```java
41 @ManyToOne
42 @JoinColumn(name="author_id", nullable=false)
43 private User author;
```
**Lines 41-43**: Who wrote this post?

**`@ManyToOne`** - The inverse of `@OneToMany`
- Many Posts → One User
- Many posts can have the same author
- This is the OWNER side of the relationship

**Relationship sides**:
```java
// In User.java (NON-owner side):
@OneToMany(mappedBy="author")
private List<Post> posts;

// In Post.java (OWNER side):
@ManyToOne
@JoinColumn(name="author_id")
private User author;
```

**Who's the owner?**
- The side with `@JoinColumn` owns the relationship
- Owner side has the foreign key in database
- Non-owner side uses `mappedBy`

**`@JoinColumn(name="author_id", nullable=false)`**
- `name="author_id"` - Foreign key column name in posts table
- `nullable=false` - Every post MUST have an author

**Generated SQL**:
```sql
CREATE TABLE posts (
    id BIGSERIAL PRIMARY KEY,
    author_id BIGINT NOT NULL,
    ...
    FOREIGN KEY (author_id) REFERENCES users(id)
);
```

**Usage**:
```java
// Create post with author
Post post = Post.builder()
    .title("My Post")
    .content("Content here")
    .author(user)  // Link to user
    .build();
postRepository.save(post);

// Database stores:
// posts table:
// id | author_id | title      | content
// 1  | 5         | My Post    | Content here
//      ^
//      This is the user's ID
```

**What happens if author is deleted?**
```java
// With CascadeType.ALL in User.java:
userRepository.delete(user);
// All posts by this user are automatically deleted!

// Without cascade:
// ERROR: cannot delete user because posts reference them
```

---

### Lines 45-46: Title Field

```java
45 @Column(nullable=false, length=200)
46 private String title;
```
**Lines 45-46**: Post title
- `nullable=false` - Every post must have a title
- `length=200` - Maximum 200 characters
- Examples: "My First Post", "Spring Boot Tutorial"

**Generated SQL**:
```sql
title VARCHAR(200) NOT NULL
```

---

### Lines 48-49: Content Field

```java
48 @Column(nullable=false, columnDefinition = "TEXT")
49 private String content;
```
**Lines 48-49**: Post content (body)

**`@Column(nullable=false, columnDefinition = "TEXT")`**
- `nullable=false` - Content is required
- `columnDefinition = "TEXT"` - Use TEXT type, not VARCHAR

**Why TEXT instead of VARCHAR?**
```java
// VARCHAR has length limit:
@Column(length=5000)  // Max 5000 characters
// What if user writes more? Truncated or error!

// TEXT has no practical limit:
@Column(columnDefinition = "TEXT")  // Can store millions of characters
// User can write blog posts of any length
```

**Generated SQL**:
```sql
content TEXT NOT NULL
-- TEXT in PostgreSQL can store up to 1GB of text!
```

**Usage**:
```java
String longPost = "This is a very long blog post...";  // 10,000 characters
post.setContent(longPost);  // ✅ No problem with TEXT type
```

---

### Lines 51-52: Media Fields

```java
51 private String mediaUrl;
52 private String mediaType;
```
**Lines 51-52**: Optional media attachment

**`mediaUrl`** - Path or URL to media file
- Example: `/uploads/images/post123.jpg`
- Example: `https://cdn.example.com/video.mp4`
- Can be null (posts don't need media)

**`mediaType`** - Type of media
- Examples: `"image/jpeg"`, `"image/png"`, `"video/mp4"`
- Can be null

**Why no annotations?**
```java
// No @Column = use defaults:
// - nullable = true (can be null)
// - type = VARCHAR(255)
```

**Usage in frontend**:
```typescript
// Frontend checks type to display correctly
if (post.mediaType.startsWith('image/')) {
    // Display <img> tag
    <img [src]="post.mediaUrl" alt="Post image">
} else if (post.mediaType.startsWith('video/')) {
    // Display <video> tag
    <video [src]="post.mediaUrl" controls></video>
}
```

---

### Lines 54-56: Hidden Flag

```java
54 @Builder.Default
55 @Column(nullable = false)
56 private boolean hidden = false;
```
**Lines 54-56**: Is post hidden from public?

**Purpose**: Soft delete
- Instead of actually deleting: `postRepository.delete(post)`
- Just hide it: `post.setHidden(true)`

**Benefits of soft delete**:
```java
// Hard delete (permanent):
postRepository.delete(post);
// ❌ Post is gone forever
// ❌ Can't undo
// ❌ Breaks referential integrity if comments exist

// Soft delete (reversible):
post.setHidden(true);
postRepository.save(post);
// ✅ Post still in database
// ✅ Can restore: post.setHidden(false)
// ✅ Comments remain intact
```

**`@Builder.Default`** - Default value in builder
```java
Post post = Post.builder().title("Title").build();
// post.hidden = false (not hidden by default)
```

**Usage in queries**:
```java
// Only get visible posts
@Query("SELECT p FROM Post p WHERE p.hidden = false")
List<Post> findVisiblePosts();
```

---

### Lines 59-66: Likes Relationship ⭐ IMPORTANT

```java
59  @ManyToMany
60  @JoinTable(
61      name = "post_likes",
62      joinColumns = @JoinColumn(name = "post_id"),
63      inverseJoinColumns = @JoinColumn(name = "user_id")
64  )
65  @Builder.Default
66  private Set<User> likedBy = new HashSet<>();
```
**Lines 59-66**: Users who liked this post

**`@ManyToMany`** - Many-to-many relationship
- Many Posts can be liked by Many Users
- Many Users can like Many Posts
- Requires a join table (intermediate table)

**`@JoinTable`** - Customize the join table
This is the OWNER side (has `@JoinTable`, not `mappedBy`)

**Breaking down @JoinTable**:

**`name = "post_likes"`** - Join table name
- Could be auto-generated as `post_liked_by`
- We explicitly name it `post_likes`

**`joinColumns = @JoinColumn(name = "post_id")`**
- Column that references THIS entity (Post)
- Stores the post's ID

**`inverseJoinColumns = @JoinColumn(name = "user_id")`**
- Column that references the OTHER entity (User)
- Stores the user's ID

**Generated SQL**:
```sql
CREATE TABLE post_likes (
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (post_id, user_id),
    FOREIGN KEY (post_id) REFERENCES posts(id),
    FOREIGN KEY (user_id) REFERENCES users(id)
);
```

**Visualization**:
```
posts table:          post_likes (join):      users table:
┌─────┬───────┐      ┌─────────┬─────────┐   ┌─────┬──────────┐
│ id  │ title │      │ post_id │ user_id │   │ id  │ username │
├─────┼───────┤      ├─────────┼─────────┤   ├─────┼──────────┤
│ 1   │ Post1 │◄─────│    1    │    5    │───►│ 5   │ john     │
│ 2   │ Post2 │      │    1    │    7    │   │ 7   │ jane     │
└─────┴───────┘      │    2    │    5    │   └─────┴──────────┘
                     └─────────┴─────────┘
                     
John (id=5) liked Post1 (id=1)
Jane (id=7) liked Post1 (id=1)
John (id=5) liked Post2 (id=2)
```

**`Set<User> likedBy`** - Why Set?
- No duplicates: User can't like same post twice
- Fast lookup: `O(1)` to check if user liked post

**`= new HashSet<>()`** - Initialize to empty set
- Prevents null pointer exceptions
- Can immediately call `post.getLikedBy().add(user)`

**Usage**:
```java
// User likes a post
post.getLikedBy().add(user);
postRepository.save(post);
// Adds row to post_likes table

// User unlikes a post
post.getLikedBy().remove(user);
postRepository.save(post);
// Removes row from post_likes table

// Count likes
int likeCount = post.getLikedBy().size();

// Check if user liked post
boolean userLiked = post.getLikedBy().contains(user);
```

**Bidirectional relationship**:
```java
// From Post side:
post.getLikedBy().add(user);

// From User side (in User.java):
user.getLikedPosts().add(post);

// Both do the same thing!
// But you only need to save from owner side (Post)
```

---

### Lines 68-69: Comments Relationship

```java
68 @OneToMany(mappedBy="post", cascade=CascadeType.ALL, orphanRemoval=true)
69 private List<Comment> comments;
```
**Lines 68-69**: Comments on this post

**`@OneToMany(mappedBy="post")`**
- One Post → Many Comments
- In Comment.java: `private Post post;` (owner side)
- This is the non-owner side

**`cascade=CascadeType.ALL`** - Operations cascade to comments
```java
// Delete post → automatically delete all comments
postRepository.delete(post);
// All comments are deleted too
```

**`orphanRemoval=true`** - Remove orphaned comments
```java
// Remove comment from post
post.getComments().remove(comment);
postRepository.save(post);
// Comment is deleted from database automatically
```

**Why `List` not `Set`?**
- Comments have an order (chronological)
- Duplicate comments are allowed (same text, different time)

**Usage**:
```java
// Get all comments
List<Comment> comments = post.getComments();

// Count comments
int commentCount = post.getComments().size();

// Add new comment
Comment comment = new Comment();
comment.setContent("Great post!");
comment.setPost(post);
post.getComments().add(comment);
postRepository.save(post);  // Saves post and comment
```

---

### Lines 72-74: Created Timestamp

```java
72 @CreationTimestamp
73 @Column(updatable=false, name = "created_at")
74 private Date createdAt;
```
**Lines 72-74**: When post was created
- Identical to User.java
- Set automatically on first save
- Never changes (`updatable=false`)

---

### Lines 76-78: Updated Timestamp

```java
76 @UpdateTimestamp
77 @Column(name = "updated_at")
78 private Date updatedAt;
```
**Lines 76-78**: When post was last updated
- Updates automatically on every save
- Tracks when post was edited

**Example**:
```java
// Create post
Post post = Post.builder()
    .title("Original Title")
    .content("Content")
    .author(user)
    .build();
postRepository.save(post);
// createdAt = 2025-10-27 10:00:00
// updatedAt = 2025-10-27 10:00:00

// Edit post later
post.setTitle("Updated Title");
postRepository.save(post);
// createdAt = 2025-10-27 10:00:00 (unchanged)
// updatedAt = 2025-10-27 14:30:00 (updated!)
```

---

### Lines 80-82: Helper Method

```java
80 public String getAuthorUsername() {
81     return author.getUsername();
82 }
```
**Lines 80-82**: Convenience method to get author's username

**Why this method?**
```java
// Without helper method:
String username = post.getAuthor().getUsername();
// Problem: What if author is null?
// NullPointerException! 💥

// With helper method (could add null check):
public String getAuthorUsername() {
    return author != null ? author.getUsername() : "Unknown";
}

// Better yet, since author is @NotNull:
public String getAuthorUsername() {
    return author.getUsername();
}
```

**Usage**:
```java
// In DTOs or responses
String authorName = post.getAuthorUsername();
// Cleaner than post.getAuthor().getUsername()
```

---

## Complete Database Schema

When you run the application, here's what Hibernate creates:

```sql
-- Posts table
CREATE TABLE posts (
    id BIGSERIAL PRIMARY KEY,
    author_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    media_url VARCHAR(255),
    media_type VARCHAR(255),
    hidden BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    FOREIGN KEY (author_id) REFERENCES users(id)
);

-- Join table for likes (many-to-many)
CREATE TABLE post_likes (
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (post_id, user_id),
    FOREIGN KEY (post_id) REFERENCES posts(id),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Indexes for performance
CREATE INDEX idx_posts_author ON posts(author_id);
CREATE INDEX idx_posts_created ON posts(created_at DESC);
CREATE INDEX idx_posts_hidden ON posts(hidden);
```

---

## Relationship Summary

```
Post Relationships:
├── @ManyToOne → User (author)
│   └── Owner side: has @JoinColumn
│   └── Foreign key: author_id
│
├── @ManyToMany → User (likedBy)
│   └── Owner side: has @JoinTable
│   └── Join table: post_likes
│
└── @OneToMany → Comment (comments)
    └── Non-owner side: has mappedBy
    └── Comment owns the relationship
```

---

## Cascade Behavior

What happens when you delete a post?

```java
postRepository.delete(post);

// Because of cascade=CascadeType.ALL:
// 1. All comments are deleted (orphanRemoval=true)
// 2. All likes are removed (rows deleted from post_likes)
// 3. Post is deleted

// Author is NOT deleted (no cascade from post to user)
```

What happens when you delete a user?

```java
userRepository.delete(user);

// Because of cascade=CascadeType.ALL in User.java:
// 1. All posts by user are deleted
// 2. When posts are deleted, their comments are deleted
// 3. All likes by user are removed
// 4. All subscriptions are deleted
```

---

## Common Queries

```java
// Get all posts by user
List<Post> userPosts = postRepository.findByAuthor(user);

// Get all visible posts
@Query("SELECT p FROM Post p WHERE p.hidden = false")
List<Post> findVisiblePosts();

// Get posts with like count
@Query("SELECT p, SIZE(p.likedBy) FROM Post p")
List<Object[]> findPostsWithLikeCount();

// Get posts liked by user
@Query("SELECT p FROM Post p JOIN p.likedBy u WHERE u.id = :userId")
List<Post> findPostsLikedByUser(@Param("userId") Long userId);

// Get posts with comments
@Query("SELECT DISTINCT p FROM Post p LEFT JOIN FETCH p.comments")
List<Post> findAllWithComments();
```

---

## N+1 Query Problem ⚠️

```java
// BAD: N+1 queries
List<Post> posts = postRepository.findAll();  // 1 query
for (Post post : posts) {
    String author = post.getAuthor().getUsername();  // N queries!
    // If 100 posts → 101 total queries (1 + 100)
}

// GOOD: JOIN FETCH
@Query("SELECT p FROM Post p JOIN FETCH p.author")
List<Post> findAllWithAuthors();  // Only 1 query!
```

---

## Builder Pattern Examples

```java
// Create simple post
Post post = Post.builder()
    .title("My Post")
    .content("Content here")
    .author(user)
    .build();

// Create post with media
Post postWithImage = Post.builder()
    .title("My Photo")
    .content("Check out this image")
    .author(user)
    .mediaUrl("/uploads/image.jpg")
    .mediaType("image/jpeg")
    .build();

// Create hidden post (draft)
Post draft = Post.builder()
    .title("Draft Post")
    .content("Work in progress...")
    .author(user)
    .hidden(true)  // Not visible to public
    .build();
```

---

## Summary: Post Entity

| Field | Type | Purpose | Notes |
|-------|------|---------|-------|
| `id` | Long | Primary key | Auto-generated |
| `author` | User | Who wrote it | @ManyToOne, required |
| `title` | String | Post title | Max 200 chars, required |
| `content` | String | Post body | TEXT type, unlimited |
| `mediaUrl` | String | Media path | Optional |
| `mediaType` | String | Media MIME type | Optional |
| `hidden` | boolean | Soft delete flag | Default: false |
| `likedBy` | Set<User> | Who liked it | @ManyToMany |
| `comments` | List<Comment> | Post comments | @OneToMany |
| `createdAt` | Date | Creation time | Auto-set |
| `updatedAt` | Date | Last update | Auto-update |

---

**Next**: I'll explain `Comment.java`, `Subscription.java`, and `Notification.java` line by line. Continue?


---

## Comment.java - Complete Line-by-Line Explanation

**Location**: `backend/src/main/java/_blog/blog/entity/Comment.java`

Comments are user responses to posts. This is the simplest entity.

---

### Lines 33-35: Comment Author

```java
33 @ManyToOne
34 @JoinColumn(name="author_id", nullable=false)
35 private User author;
```
**Lines 33-35**: Who wrote this comment?
- Many Comments → One User
- Same pattern as Post's author
- Every comment must have an author

---

### Lines 37-39: Parent Post

```java
37 @ManyToOne
38 @JoinColumn(name="post_id", nullable=false)
39 private Post post;
```
**Lines 37-39**: Which post is this comment on?
- Many Comments → One Post
- Every comment must belong to a post
- Can't have orphan comments

**Foreign key relationships**:
```sql
CREATE TABLE comments (
    id BIGSERIAL PRIMARY KEY,
    author_id BIGINT NOT NULL,  -- References users(id)
    post_id BIGINT NOT NULL,    -- References posts(id)
    ...
    FOREIGN KEY (author_id) REFERENCES users(id),
    FOREIGN KEY (post_id) REFERENCES posts(id)
);
```

---

### Lines 41-42: Comment Content

```java
41 @Column(nullable=false, length=2000)
42 private String content;
```
**Lines 41-42**: The actual comment text
- Required field (can't be empty)
- Max 2000 characters
- Shorter than post content (comments are brief)

**Why 2000 not TEXT?**
```java
// Comments should be concise
// 2000 characters is plenty
// Encourages shorter, focused comments
// Better database performance (VARCHAR vs TEXT)
```

---

### Lines 45-47: Timestamp

```java
45 @CreationTimestamp
46 @Column(updatable=false, name = "created_at")
47 private Date createdAt;
```
**Lines 45-47**: When comment was posted
- No `updatedAt` - comments can't be edited
- This enforces immutability

💡 **Design decision**: Comments are permanent. Users can't edit them after posting.

---

### Lines 49-51: Helper Method

```java
49 public String getAuthorUsername() {
50     return author.getUsername();
51 }
```
**Lines 49-51**: Get author's username
- Same convenience method as in Post.java

---

## Usage Examples

```java
// Create comment
Comment comment = Comment.builder()
    .content("Great post!")
    .author(user)
    .post(post)
    .build();
commentRepository.save(comment);

// Get all comments on a post
List<Comment> comments = commentRepository.findByPost(post);

// Get all comments by user
List<Comment> userComments = commentRepository.findByAuthor(user);

// Delete comment (cascades from post)
post.getComments().remove(comment);
postRepository.save(post);
// Comment is automatically deleted
```

---

## Subscription.java - Complete Line-by-Line Explanation

**Location**: `backend/src/main/java/_blog/blog/entity/Subscription.java`

Subscriptions represent follow relationships between users.

---

### Lines 34-36: Subscriber (Follower)

```java
34 @ManyToOne
35 @JoinColumn(name = "subscriber_id", nullable = false)
36 private User subscriber;
```
**Lines 34-36**: The user who is following
- The person clicking "Follow"
- Many Subscriptions → One User
- One user can follow many others

---

### Lines 38-40: Subscribed To (Following)

```java
38 @ManyToOne
39 @JoinColumn(name = "subscribed_to_id", nullable = false)
40 private User subscribedTo;
```
**Lines 38-40**: The user being followed
- The person being followed
- Many Subscriptions → One User
- One user can be followed by many others

**The relationship**:
```
John follows Jane:
┌────────────┬──────────────┬────────────────┐
│ id         │ subscriber   │ subscribed_to  │
├────────────┼──────────────┼────────────────┤
│ 1          │ John (id=5)  │ Jane (id=7)    │
└────────────┴──────────────┴────────────────┘

subscriber.subscriptions  → includes this record
subscribedTo.followers    → includes this record
```

**Database representation**:
```sql
CREATE TABLE subscriptions (
    id BIGSERIAL PRIMARY KEY,
    subscriber_id BIGINT NOT NULL,     -- Who is following
    subscribed_to_id BIGINT NOT NULL,  -- Who is being followed
    created_at TIMESTAMP NOT NULL,
    FOREIGN KEY (subscriber_id) REFERENCES users(id),
    FOREIGN KEY (subscribed_to_id) REFERENCES users(id),
    UNIQUE(subscriber_id, subscribed_to_id)  -- Can't follow twice
);
```

---

### Lines 42-44: Timestamp

```java
42 @CreationTimestamp
43 @Column(updatable = false, name = "created_at")
44 private Date createdAt;
```
**Lines 42-44**: When user followed
- Records when the follow happened
- Immutable (can't change)

---

## Usage Examples

```java
// John follows Jane
Subscription sub = Subscription.builder()
    .subscriber(john)      // Who is following
    .subscribedTo(jane)    // Who is being followed
    .build();
subscriptionRepository.save(sub);

// Get all users John follows
List<Subscription> johnFollows = subscriptionRepository.findBySubscriber(john);
List<User> following = johnFollows.stream()
    .map(Subscription::getSubscribedTo)
    .toList();

// Get all of Jane's followers
List<Subscription> janeFollowers = subscriptionRepository.findBySubscribedTo(jane);
List<User> followers = janeFollowers.stream()
    .map(Subscription::getSubscriber)
    .toList();

// Check if John follows Jane
boolean johnFollowsJane = subscriptionRepository
    .existsBySubscriberAndSubscribedTo(john, jane);

// Unfollow
subscriptionRepository.deleteBySubscriberAndSubscribedTo(john, jane);
```

---

## Notification.java - Complete Line-by-Line Explanation

**Location**: `backend/src/main/java/_blog/blog/entity/Notification.java`

Notifications inform users about events (new follower, comment, like, etc.)

---

### Lines 36-38: Notification Recipient

```java
36 @ManyToOne
37 @JoinColumn(name = "user_id", nullable = false)
38 private User user;
```
**Lines 36-38**: Who receives this notification?
- The person seeing the notification
- Required (can't have notification without recipient)

---

### Lines 40-42: Notification Actor

```java
40 @ManyToOne
41 @JoinColumn(name = "actor_id")
42 private User actor;
```
**Lines 40-42**: Who triggered this notification?
- The person who performed the action
- Optional (can be null for system notifications)

**Examples**:
```
"John liked your post"
 ^^^^
 actor = John

"Jane commented on your post"
 ^^^^
 actor = Jane

"Your post was approved by admin"
                          ^^^^^
                          actor = Admin user

"System maintenance scheduled"
(no actor - system notification)
```

---

### Lines 44-46: Notification Type

```java
44 @Enumerated(EnumType.STRING)
45 @Column(nullable = false)
46 private NotificationType type;
```
**Lines 44-46**: What kind of notification?

**NotificationType enum** (in `enums/NotificationType.java`):
```java
public enum NotificationType {
    POST_LIKE,       // Someone liked your post
    POST_COMMENT,    // Someone commented on your post
    COMMENT_REPLY,   // Someone replied to your comment
    NEW_FOLLOWER,    // Someone followed you
    POST_SHARED,     // Someone shared your post
    MENTION          // Someone mentioned you
}
```

**Why enum?**
- Type-safe: Can't have typos
- Easy to add new types
- Frontend can switch on type:
```typescript
switch (notification.type) {
    case 'POST_LIKE':
        icon = '❤️';
        break;
    case 'POST_COMMENT':
        icon = '💬';
        break;
    case 'NEW_FOLLOWER':
        icon = '👤';
        break;
}
```

---

### Lines 48-49: Notification Message

```java
48 @Column(nullable = false, length = 500)
49 private String message;
```
**Lines 48-49**: Human-readable message
- Required
- Max 500 characters
- Pre-formatted text shown to user

**Examples**:
```
"John Doe liked your post 'My First Blog'"
"Jane Smith commented on your post"
"You have a new follower: Bob Johnson"
```

---

### Lines 51-53: Related Post

```java
51 @ManyToOne
52 @JoinColumn(name = "post_id")
53 private Post post;
```
**Lines 51-53**: Which post (if applicable)?
- Optional (nullable)
- Links notification to specific post
- Used for: POST_LIKE, POST_COMMENT

**When it's null**:
- NEW_FOLLOWER notifications (no post involved)
- System notifications

---

### Lines 55-57: Related Comment

```java
55 @ManyToOne
56 @JoinColumn(name = "comment_id")
57 private Comment comment;
```
**Lines 55-57**: Which comment (if applicable)?
- Optional (nullable)
- Links notification to specific comment
- Used for: COMMENT_REPLY

---

### Lines 59-61: Read Status

```java
59 @Builder.Default
60 @Column(nullable = false)
61 private boolean read = false;
```
**Lines 59-61**: Has user seen this notification?
- Default: `false` (unread)
- Updates to `true` when user views it
- Used to show unread count

**UI Flow**:
```java
// Get unread count
long unreadCount = notificationRepository.countByUserAndRead(user, false);
// Show badge: 🔔 (5)

// User clicks notifications
List<Notification> notifications = notificationRepository.findByUser(user);
// Show list

// User views a notification
notification.setRead(true);
notificationRepository.save(notification);
// Badge updates: 🔔 (4)
```

---

### Lines 63-65: Timestamp

```java
63 @CreationTimestamp
64 @Column(updatable = false, name = "created_at")
65 private Date createdAt;
```
**Lines 63-65**: When notification was created
- Sort by this to show newest first

---

## Complete Notification Flow

```java
// 1. John likes Jane's post
post.getLikedBy().add(john);
postRepository.save(post);

// 2. Create notification for Jane
Notification notification = Notification.builder()
    .user(jane)                          // Recipient
    .actor(john)                         // Who did it
    .type(NotificationType.POST_LIKE)    // What happened
    .message("John Doe liked your post '" + post.getTitle() + "'")
    .post(post)                          // Link to post
    .read(false)                         // Unread
    .build();
notificationRepository.save(notification);

// 3. Jane's frontend polls for notifications
@GetMapping("/unread-count")
public int getUnreadCount(Authentication auth) {
    User user = getCurrentUser(auth);
    return notificationRepository.countByUserAndRead(user, false);
}

// 4. Jane opens notifications
@GetMapping("/all")
public List<NotificationResponse> getAll(Authentication auth) {
    User user = getCurrentUser(auth);
    return notificationRepository.findByUserOrderByCreatedAtDesc(user);
}

// 5. Jane clicks notification
@PutMapping("/mark-read/{id}")
public void markRead(@PathVariable Long id) {
    Notification notification = notificationRepository.findById(id)
        .orElseThrow();
    notification.setRead(true);
    notificationRepository.save(notification);
}

// 6. Frontend navigates to the post
router.navigate(['/post', notification.post.id]);
```

---

## Entity Relationships Summary

```
USER
├── Posts (author)          @OneToMany
├── Comments (author)       @OneToMany
├── Subscriptions (subscriber)    @OneToMany
├── Followers (subscribedTo)      @OneToMany
├── LikedPosts              @ManyToMany
└── Notifications (user)    @OneToMany

POST
├── Author                  @ManyToOne → User
├── LikedBy                 @ManyToMany → User
├── Comments                @OneToMany → Comment
└── Notifications (post)    @OneToMany

COMMENT
├── Author                  @ManyToOne → User
├── Post                    @ManyToOne → Post
└── Notifications (comment) @OneToMany

SUBSCRIPTION
├── Subscriber              @ManyToOne → User
└── SubscribedTo            @ManyToOne → User

NOTIFICATION
├── User (recipient)        @ManyToOne → User
├── Actor (performer)       @ManyToOne → User
├── Post                    @ManyToOne → Post
└── Comment                 @ManyToOne → Comment
```

---

## Complete Database Schema

```sql
-- Users table
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    first_name VARCHAR(30) NOT NULL,
    last_name VARCHAR(30) NOT NULL,
    username VARCHAR(30) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    avatar VARCHAR(255),
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    banned BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- Posts table
CREATE TABLE posts (
    id BIGSERIAL PRIMARY KEY,
    author_id BIGINT NOT NULL REFERENCES users(id),
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    media_url VARCHAR(255),
    media_type VARCHAR(255),
    hidden BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- Comments table
CREATE TABLE comments (
    id BIGSERIAL PRIMARY KEY,
    author_id BIGINT NOT NULL REFERENCES users(id),
    post_id BIGINT NOT NULL REFERENCES posts(id),
    content VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

-- Subscriptions table
CREATE TABLE subscriptions (
    id BIGSERIAL PRIMARY KEY,
    subscriber_id BIGINT NOT NULL REFERENCES users(id),
    subscribed_to_id BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL,
    UNIQUE(subscriber_id, subscribed_to_id)
);

-- Notifications table
CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    actor_id BIGINT REFERENCES users(id),
    type VARCHAR(50) NOT NULL,
    message VARCHAR(500) NOT NULL,
    post_id BIGINT REFERENCES posts(id),
    comment_id BIGINT REFERENCES comments(id),
    read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL
);

-- Join table for post likes
CREATE TABLE post_likes (
    post_id BIGINT NOT NULL REFERENCES posts(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    PRIMARY KEY (post_id, user_id)
);

-- Indexes for performance
CREATE INDEX idx_posts_author ON posts(author_id);
CREATE INDEX idx_posts_created ON posts(created_at DESC);
CREATE INDEX idx_comments_post ON comments(post_id);
CREATE INDEX idx_comments_author ON comments(author_id);
CREATE INDEX idx_subscriptions_subscriber ON subscriptions(subscriber_id);
CREATE INDEX idx_subscriptions_subscribed_to ON subscriptions(subscribed_to_id);
CREATE INDEX idx_notifications_user ON notifications(user_id);
CREATE INDEX idx_notifications_read ON notifications(user_id, read);
```

---

## All Entities Complete! ✅

You now understand every single line of every entity class. You know:
- How each field maps to database columns
- How relationships work (OneToMany, ManyToOne, ManyToMany)
- How cascading works
- How timestamps work
- Why we made each design decision

**Next**: [Repository Layer - How database queries work](./10-BACKEND-LINE-BY-LINE-REPOSITORIES.md)

Would you like me to continue with Repositories, Services, or Controllers?

