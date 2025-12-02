# Programming Basics - From Zero to Understanding

## What is Programming?

Programming is giving instructions to a computer. Just like you follow a recipe to cook, a computer follows code to perform tasks.

### The Recipe Analogy

```
Recipe (Human Instructions):
1. Heat oven to 350°F
2. Mix flour and sugar
3. Bake for 30 minutes

Code (Computer Instructions):
1. Set temperature = 350
2. Mix(flour, sugar)
3. Wait(30, "minutes")
```

Both are step-by-step instructions, just for different audiences.

---

## Core Programming Concepts

### 1. Variables (Storage Boxes)

Think of variables as labeled boxes that store information.

```java
// Java (Backend)
String username = "john";
int age = 25;
boolean isLoggedIn = true;
```

```typescript
// TypeScript (Frontend)
let username: string = "john";
let age: number = 25;
let isLoggedIn: boolean = true;
```

**Real example from our code**:
```java
// backend/src/main/java/_blog/blog/entity/User.java
private String username;  // Box labeled "username" storing text
private String email;     // Box labeled "email" storing text
private Role role;        // Box labeled "role" storing USER or ADMIN
```

💡 **Why it matters**: Variables let us remember information and use it later.

---

### 2. Functions/Methods (Reusable Instructions)

Functions are like recipes you can call by name instead of writing steps again.

```java
// Without function (repetitive):
System.out.println("Hello, John");
System.out.println("Hello, Jane");
System.out.println("Hello, Bob");

// With function (reusable):
void greet(String name) {
    System.out.println("Hello, " + name);
}

greet("John");  // Outputs: Hello, John
greet("Jane");  // Outputs: Hello, Jane
greet("Bob");   // Outputs: Hello, Bob
```

**Real example from our code**:
```java
// backend/src/main/java/_blog/blog/service/UserService.java
public User getUserByUsername(String username) {
    // This function finds and returns a user
    return userRepository.findByUsername(username)
        .orElseThrow(() -> new RuntimeException("User not found"));
}
```

💡 **Why it matters**: Functions prevent code duplication and make code readable.

---

### 3. Objects and Classes (Blueprints)

A **class** is a blueprint. An **object** is something built from that blueprint.

```java
// Class = Blueprint for a car
class Car {
    String color;
    int speed;

    void accelerate() {
        speed = speed + 10;
    }
}

// Objects = Actual cars built from blueprint
Car myCar = new Car();
myCar.color = "red";
myCar.speed = 0;
myCar.accelerate();  // Now speed is 10
```

**Real example from our code**:
```java
// backend/src/main/java/_blog/blog/entity/Post.java
// This is a blueprint for blog posts
@Entity
public class Post {
    private Long id;
    private String title;
    private String content;
    private User author;
    private LocalDateTime createdAt;
}

// Creating actual post objects:
Post firstPost = new Post();
firstPost.setTitle("My First Post");
firstPost.setContent("Hello World!");
```

💡 **Why it matters**: Objects let us model real-world things in code.

---

### 4. Conditionals (Decision Making)

Conditionals let programs make decisions.

```java
if (age >= 18) {
    System.out.println("Adult");
} else {
    System.out.println("Minor");
}
```

**Real example from our code**:
```java
// backend/src/main/java/_blog/blog/controller/PostController.java
if (user.getRole() == Role.ADMIN) {
    // Admins can delete any post
    postService.deletePost(postId);
} else if (post.getAuthor().getId().equals(user.getId())) {
    // Users can only delete their own posts
    postService.deletePost(postId);
} else {
    // Nobody else can delete
    throw new UnauthorizedException("Cannot delete this post");
}
```

💡 **Why it matters**: Programs need to make decisions based on conditions.

---

### 5. Loops (Repetition)

Loops repeat actions multiple times.

```java
// Print numbers 1 to 5
for (int i = 1; i <= 5; i++) {
    System.out.println(i);
}
// Output: 1 2 3 4 5
```

**Real example from our code**:
```typescript
// frontend/src/app/features/home/home.component.ts
posts.forEach(post => {
    // Do something with each post
    console.log(post.title);
});
```

💡 **Why it matters**: Loops save us from writing the same code many times.

---

### 6. Arrays and Lists (Collections)

Arrays/Lists store multiple items in one variable.

```java
// Array of usernames
String[] usernames = {"john", "jane", "bob"};

// List of posts (can grow/shrink)
List<Post> posts = new ArrayList<>();
posts.add(firstPost);
posts.add(secondPost);
```

**Real example from our code**:
```java
// backend/src/main/java/_blog/blog/controller/PostController.java
List<Post> allPosts = postService.getAllPosts();
// allPosts now contains every post in the database
```

💡 **Why it matters**: Real apps deal with multiple items, not just one.

---

## How Web Applications Work

### The Restaurant Analogy

```
Traditional Restaurant:
Customer → Waiter → Kitchen → Waiter → Customer

Web Application:
Browser → API → Server → Database → Server → API → Browser
(Frontend)      (Backend)            (Backend)      (Frontend)
```

### Our Application Flow

```
1. USER TYPES: "I want to see all posts"
   ↓
2. BROWSER (Frontend): "Hey server, GET /posts/all"
   ↓
3. SERVER (Backend): "Let me check the database..."
   ↓
4. DATABASE: "Here are all the posts"
   ↓
5. SERVER: "Here's the data in JSON format"
   ↓
6. BROWSER: "I'll display these nicely for the user"
   ↓
7. USER SEES: Beautiful list of blog posts
```

### Real Example from Our Code

```typescript
// STEP 1: Frontend makes request
// frontend/src/app/services/post.service.ts
getAllPosts() {
  return this.http.get<Post[]>(`${this.apiUrl}/posts/all`);
}
```

```java
// STEP 2: Backend receives request
// backend/src/main/java/_blog/blog/controller/PostController.java
@GetMapping("/all")
public ResponseEntity<List<PostResponse>> getAllPosts() {
    // Get posts from database
    List<Post> posts = postService.getAllPosts();

    // Convert to response format
    List<PostResponse> response = posts.stream()
        .map(this::convertToPostResponse)
        .toList();

    // Send back to frontend
    return ResponseEntity.ok(response);
}
```

```java
// STEP 3: Service layer queries database
// backend/src/main/java/_blog/blog/service/PostService.java
public List<Post> getAllPosts() {
    return postRepository.findAll();  // Gets all posts from database
}
```

---

## Key Terminology

### Frontend vs Backend

| Term | What it means | In our project |
|------|---------------|----------------|
| **Frontend** | What the user sees and interacts with | Angular app in `frontend/` folder |
| **Backend** | The server that processes requests | Spring Boot app in `backend/` folder |
| **Database** | Where data is permanently stored | PostgreSQL database |
| **API** | How frontend and backend talk | REST endpoints like `/posts/all` |

### HTTP Methods (Verbs)

Think of these as action words:

| Method | Meaning | Example |
|--------|---------|---------|
| **GET** | Retrieve/Read data | Get all posts |
| **POST** | Create new data | Create a new post |
| **PUT** | Update existing data | Update a post |
| **DELETE** | Remove data | Delete a post |

**Real examples from our code**:
```java
@GetMapping("/all")          // READ: Get all posts
@PostMapping("/create")      // CREATE: Make new post
@PutMapping("/update/{id}")  // UPDATE: Edit post
@DeleteMapping("/delete/{id}")  // DELETE: Remove post
```

---

## Data Flow Example: Creating a Post

Let's follow what happens when a user creates a blog post:

### Step 1: User fills out form (Frontend)
```html
<!-- frontend/src/app/features/create-post/create-post.component.html -->
<input [(ngModel)]="title" placeholder="Post title">
<textarea [(ngModel)]="content" placeholder="Write your post..."></textarea>
<button (click)="createPost()">Publish</button>
```

### Step 2: Button click triggers function (Frontend)
```typescript
// frontend/src/app/features/create-post/create-post.component.ts
createPost() {
  const postData = {
    title: this.title,        // "My First Post"
    content: this.content     // "Hello World!"
  };

  // Send to backend
  this.postService.createPost(postData).subscribe({
    next: () => alert('Post created!'),
    error: (err) => alert('Error: ' + err.message)
  });
}
```

### Step 3: HTTP request sent (Frontend Service)
```typescript
// frontend/src/app/services/post.service.ts
createPost(post: PostRequest) {
  // Sends POST request to backend at http://localhost:8080/posts/create
  return this.http.post(`${this.apiUrl}/posts/create`, post);
}
```

### Step 4: Backend receives request (Controller)
```java
// backend/src/main/java/_blog/blog/controller/PostController.java
@PostMapping("/create")
public ResponseEntity<String> createPost(
    @RequestBody PostRequest request,  // Receives: {title: "...", content: "..."}
    Authentication auth                 // Who is creating this post?
) {
    // Get the logged-in user
    User author = userService.getUserByUsername(auth.getName());

    // Create the post
    postService.createPost(request, author);

    // Send success response
    return ResponseEntity.ok("Post created successfully");
}
```

### Step 5: Service creates post (Business Logic)
```java
// backend/src/main/java/_blog/blog/service/PostService.java
public void createPost(PostRequest request, User author) {
    // Build a new Post object
    Post post = Post.builder()
        .title(request.getTitle())
        .content(request.getContent())
        .author(author)
        .createdAt(LocalDateTime.now())
        .build();

    // Save to database
    postRepository.save(post);
}
```

### Step 6: Database stores the post
```sql
-- Hibernate generates this SQL automatically:
INSERT INTO posts (title, content, author_id, created_at)
VALUES ('My First Post', 'Hello World!', 1, '2025-10-27 00:00:00');
```

### Step 7: Success response travels back
```
Database → Service → Controller → Frontend → User sees "Post created!"
```

---

## Common Patterns You'll See

### Pattern 1: Repository Pattern
```
Controller → Service → Repository → Database

Controller:  Handles HTTP requests
Service:     Contains business logic
Repository:  Talks to database
```

### Pattern 2: DTO (Data Transfer Object)
```java
// Don't send entity directly:
❌ return user;  // Contains password, sensitive data

// Use DTO to control what's sent:
✅ return new UserResponse(user.getId(), user.getUsername());
```

### Pattern 3: Builder Pattern
```java
// Instead of:
Post post = new Post();
post.setTitle("Title");
post.setContent("Content");

// Use builder:
Post post = Post.builder()
    .title("Title")
    .content("Content")
    .build();
```

---

## Practice Exercise

Try to trace what happens when:

1. A user visits `/home` page
2. Clicks on a post to read it
3. Leaves a comment
4. Deletes their comment

For each step, identify:
- Which frontend component handles it?
- Which backend controller receives it?
- Which service processes it?
- What happens in the database?

---

## Next Steps

Now that you understand the basics, you're ready to learn about the technologies we use:

**Next**: [02-TECH-STACK-OVERVIEW.md](./02-TECH-STACK-OVERVIEW.md)

---

## Quick Reference

| Concept | Java Example | TypeScript Example |
|---------|--------------|-------------------|
| Variable | `String name = "John";` | `let name: string = "John";` |
| Function | `void greet() { }` | `greet() { }` |
| Class | `class User { }` | `class User { }` |
| Conditional | `if (x > 5) { }` | `if (x > 5) { }` |
| Loop | `for (int i=0; i<10; i++)` | `for (let i=0; i<10; i++)` |
| List | `List<String> names` | `let names: string[]` |
