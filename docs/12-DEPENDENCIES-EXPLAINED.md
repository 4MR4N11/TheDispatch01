# Every Dependency Explained

## What is a Dependency?

A dependency is code written by someone else that we use in our project. Instead of writing everything from scratch, we use tested libraries to save time and avoid bugs.

Think of dependencies like ingredients in a recipe - you don't make flour from scratch, you buy it.

---

## Backend Dependencies (Maven - pom.xml)

Location: `backend/pom.xml`

### Core Spring Boot Dependencies

#### 1. spring-boot-starter-web
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

**What it does**: Provides everything needed to create a web application
**Includes**:
- Embedded Tomcat server (runs our application)
- Spring MVC (handles HTTP requests)
- Jackson (converts Java objects to JSON)
- Validation (checks data is correct)

**Why we need it**: This is the foundation of our REST API. Without it, we can't handle HTTP requests.

**Used in**: Every controller (`PostController`, `UserController`, etc.)

---

#### 2. spring-boot-starter-data-jpa
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
```

**What it does**: Provides database access through Java objects
**Includes**:
- Hibernate (ORM framework)
- Spring Data JPA (repository pattern)
- Transaction management

**Why we need it**: Instead of writing SQL queries, we can work with Java objects.

**Example without JPA**:
```java
// Manual SQL - error-prone
String sql = "SELECT * FROM users WHERE username = ?";
PreparedStatement stmt = connection.prepareStatement(sql);
stmt.setString(1, username);
ResultSet rs = stmt.executeQuery();
// ... manually parse results
```

**With JPA**:
```java
// Automatic - clean and safe
User user = userRepository.findByUsername(username);
```

**Used in**: All repositories (`UserRepository`, `PostRepository`, etc.)

---

#### 3. spring-boot-starter-security
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

**What it does**: Provides authentication and authorization
**Includes**:
- Password encryption (BCrypt)
- Authentication filters
- Authorization rules
- CSRF protection

**Why we need it**: Security is critical. This prevents unauthorized access.

**Used in**:
- `SecurityConfig.java` - Security configuration
- `JwtAuthenticationFilter.java` - JWT token validation
- All protected endpoints

---

#### 4. postgresql
```xml
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
</dependency>
```

**What it does**: JDBC driver for PostgreSQL database
**Why we need it**: Allows Java to communicate with PostgreSQL

Without this, our application can't connect to the database.

**Used in**: Configured in `application.properties`:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/blog
spring.datasource.driver-class-name=org.postgresql.Driver
```

---

#### 5. jjwt (JSON Web Token)
```xml
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.5</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.5</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.5</version>
</dependency>
```

**What it does**: Creates and validates JWT tokens
**Why we need it**: For stateless authentication

**How it works**:
1. User logs in → Server creates JWT token
2. User makes requests → Sends JWT in header
3. Server validates JWT → Allows/denies access

**Used in**:
- `JwtService.java` - Token creation/validation
- `JwtAuthenticationFilter.java` - Token extraction from requests

**Real example**:
```java
// Creating a token
String token = jwtService.generateToken(username);
// token = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJqb2huIiwiaWF0IjoxNzYx..."

// Validating a token
boolean isValid = jwtService.validateToken(token, username);
```

---

#### 6. lombok
```xml
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
</dependency>
```

**What it does**: Generates repetitive code automatically
**Why we need it**: Reduces boilerplate code

**Without Lombok**:
```java
public class User {
    private String username;
    private String email;

    public User() {}

    public User(String username, String email) {
        this.username = username;
        this.email = email;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    @Override
    public boolean equals(Object o) {
        // ... lots of code
    }

    @Override
    public int hashCode() {
        // ... more code
    }

    @Override
    public String toString() {
        return "User{username='" + username + "', email='" + email + "'}";
    }
}
```

**With Lombok**:
```java
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    private String username;
    private String email;
}
```

**Lombok Annotations Explained**:
- `@Data` - Generates getters, setters, equals, hashCode, toString
- `@NoArgsConstructor` - Generates empty constructor
- `@AllArgsConstructor` - Generates constructor with all fields
- `@Builder` - Generates builder pattern
- `@Getter` - Generates only getters
- `@Setter` - Generates only setters
- `@ToString` - Generates toString method
- `@EqualsAndHashCode` - Generates equals and hashCode

**Used in**: Every entity and DTO in our project

---

#### 7. jakarta.validation-api
```xml
<dependency>
    <groupId>jakarta.validation</groupId>
    <artifactId>jakarta.validation-api</artifactId>
</dependency>
```

**What it does**: Validates incoming data
**Why we need it**: Ensures data meets requirements before processing

**Validation Annotations**:
```java
public class CommentRequest {
    @NotBlank(message = "Comment cannot be empty")
    @Size(min = 1, max = 5000, message = "Comment must be 1-5000 characters")
    private String content;
}
```

**Without validation**:
```java
// User could send empty comment
// User could send 1 million character comment
// Database might crash
// Application breaks
```

**With validation**:
```java
// Spring automatically checks
// Rejects invalid data
// Returns error message to user
```

**Common Annotations**:
- `@NotNull` - Field cannot be null
- `@NotBlank` - String cannot be empty or whitespace
- `@NotEmpty` - Collection cannot be empty
- `@Size(min, max)` - String/collection size limits
- `@Min` / `@Max` - Number range
- `@Email` - Valid email format
- `@Pattern` - Regex pattern matching

**Used in**: All `*Request.java` DTOs

---

## Frontend Dependencies (npm - package.json)

Location: `frontend/package.json`

### Core Angular Dependencies

#### 1. @angular/core
```json
"@angular/core": "^20.3.7"
```

**What it does**: Core Angular framework
**Why we need it**: This IS Angular - the foundation of our frontend

**Provides**:
- Component system
- Dependency injection
- Change detection
- Lifecycle hooks

**Used in**: Every component, service, and module

---

#### 2. @angular/common
```json
"@angular/common": "^20.3.7"
```

**What it does**: Common Angular utilities
**Provides**:
- `HttpClient` - Make HTTP requests
- `DatePipe`, `CurrencyPipe` - Format data
- `NgIf`, `NgFor` - Template directives
- `Location`, `PathLocationStrategy` - Routing

**Example**:
```typescript
// Without HttpClient - manual fetch
fetch('http://localhost:8080/posts/all')
  .then(response => response.json())
  .then(data => console.log(data));

// With HttpClient - clean and powerful
this.http.get<Post[]>(`${this.apiUrl}/posts/all`)
  .subscribe(posts => console.log(posts));
```

**Used in**: Every service that makes HTTP calls

---

#### 3. @angular/router
```json
"@angular/router": "^20.3.7"
```

**What it does**: Navigation between pages
**Why we need it**: Single Page Application (SPA) routing

**How it works**:
```typescript
// Define routes
const routes = [
  { path: '', component: HomeComponent },
  { path: 'login', component: LoginComponent },
  { path: 'profile', component: ProfileComponent }
];

// Navigate in code
this.router.navigate(['/profile']);

// Navigate in template
<a routerLink="/profile">My Profile</a>
```

**Without router**: Page refreshes on every click (slow, bad UX)
**With router**: Instant navigation (fast, smooth UX)

**Used in**: `app.routes.ts` and navigation components

---

#### 4. @angular/forms
```json
"@angular/forms": "^20.3.7"
```

**What it does**: Form handling and validation
**Provides**:
- `FormsModule` - Template-driven forms
- `ReactiveFormsModule` - Reactive forms
- Form validation
- Form controls

**Example**:
```typescript
// Two-way binding
<input [(ngModel)]="username">

// Reactive form
this.loginForm = new FormGroup({
  username: new FormControl('', [Validators.required]),
  password: new FormControl('', [Validators.required, Validators.minLength(8)])
});
```

**Used in**: Login, register, create post, edit profile forms

---

#### 5. rxjs
```json
"rxjs": "~7.8.0"
```

**What it does**: Reactive programming with Observables
**Why we need it**: Handle async data streams

**What are Observables?**
Think of Netflix:
- You subscribe to a show
- Episodes arrive over time
- You watch as they come
- You can unsubscribe anytime

```typescript
// Observable = stream of data over time
this.postService.getAllPosts().subscribe({
  next: (posts) => {
    // Data arrived!
    this.posts = posts;
  },
  error: (err) => {
    // Something went wrong
    console.error(err);
  },
  complete: () => {
    // Done receiving data
    console.log('Complete');
  }
});
```

**Common RxJS operators**:
```typescript
// map - transform data
.pipe(map(user => user.username))

// filter - only emit matching items
.pipe(filter(post => post.author === 'john'))

// debounceTime - wait before emitting
.pipe(debounceTime(300))  // Search as user types

// switchMap - switch to new observable
.pipe(switchMap(id => this.getPost(id)))
```

**Used in**: Every HTTP call, form validation, search features

---

#### 6. typescript
```json
"typescript": "~5.7.3"
```

**What it does**: Adds types to JavaScript
**Why we need it**: Catch errors before running code

**JavaScript (no types)**:
```javascript
function greet(name) {
  return "Hello " + name.toUppercase();  // typo! will crash at runtime
}

greet(123);  // wrong type! will crash
```

**TypeScript (with types)**:
```typescript
function greet(name: string): string {
  return "Hello " + name.toUppercase();  // ERROR: toUppercase doesn't exist
  // TypeScript catches typo immediately!
}

greet(123);  // ERROR: number is not assignable to string
// Caught before running!
```

**Used in**: Every `.ts` file in the project

---

#### 7. zone.js
```json
"zone.js": "~0.15.0"
```

**What it does**: Change detection magic
**Why we need it**: Automatically updates UI when data changes

**How it works**:
```typescript
// When you change data
this.username = "New Name";

// Zone.js detects the change
// Automatically updates all places showing username
// No manual refresh needed!
```

Without zone.js, you'd have to manually tell Angular to update the UI.

**Used in**: Runs automatically in background

---

### Dev Dependencies (Used Only During Development)

#### 1. @angular/cli
```json
"@angular/cli": "^20.3.7"
```

**What it does**: Command-line tool for Angular
**Commands**:
```bash
ng serve              # Run development server
ng build              # Build for production
ng generate component # Create new component
ng test               # Run tests
ng update            # Update dependencies
```

---

#### 2. @angular/build
```json
"@angular/build": "^20.3.7"
```

**What it does**: Builds the application for production
**Process**:
1. Compiles TypeScript → JavaScript
2. Bundles all files together
3. Minifies code (removes spaces, shortens names)
4. Optimizes for browser

**Before build** (development):
```typescript
// Multiple files, readable code
user.component.ts (1000 lines)
post.component.ts (800 lines)
...
Total: 50 files, 5MB
```

**After build** (production):
```javascript
// Single file, minified code
main.f7a8b3c2.js (250KB)
Total: 3 files, 300KB
```

---

## Dependency Version Numbers Explained

```json
"@angular/core": "^20.3.7"
                  ┬ ┬ ┬ ┬
                  │ │ │ └─ Patch (bug fixes)
                  │ │ └─── Minor (new features, backwards compatible)
                  │ └───── Major (breaking changes)
                  └─────── Caret (^) allows minor and patch updates
```

**Version symbols**:
- `^20.3.7` - Allow updates to 20.x.x (not 21.0.0)
- `~20.3.7` - Allow updates to 20.3.x only
- `20.3.7` - Exact version only

---

## How Dependencies Are Downloaded

### Backend (Maven)
```bash
mvn clean install
```

**What happens**:
1. Maven reads `pom.xml`
2. Downloads dependencies from Maven Central
3. Stores in `~/.m2/repository/`
4. Adds to project classpath

### Frontend (npm)
```bash
npm install
```

**What happens**:
1. npm reads `package.json`
2. Downloads from npm registry
3. Stores in `node_modules/`
4. Creates `package-lock.json` (exact versions used)

---

## Transitive Dependencies

Dependencies have their own dependencies!

```
Our Project
├── spring-boot-starter-web
│   ├── spring-web
│   ├── spring-webmvc
│   ├── tomcat-embed-core
│   └── jackson-databind
│       ├── jackson-core
│       └── jackson-annotations
└── ... hundreds more
```

**Backend**: ~200 total dependencies
**Frontend**: ~600 total dependencies

This is normal and expected!

---

## Security Vulnerabilities

Run these regularly:

```bash
# Backend
mvn dependency:tree      # See all dependencies
mvn versions:display-dependency-updates  # Check for updates

# Frontend
npm audit               # Check for vulnerabilities
npm audit fix          # Fix automatically
npm outdated           # Check for updates
```

---

## Adding New Dependencies

### Backend (Maven)
1. Find dependency on [mvnrepository.com](https://mvnrepository.com)
2. Add to `pom.xml`:
```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>library-name</artifactId>
    <version>1.0.0</version>
</dependency>
```
3. Reload Maven project

### Frontend (npm)
```bash
npm install library-name
npm install --save-dev library-name  # Dev dependency only
```

---

## Summary: Why We Use Each Dependency

| Dependency | Purpose | Without It |
|------------|---------|------------|
| Spring Boot Web | Handle HTTP | Can't create API |
| Spring Data JPA | Database access | Manual SQL everywhere |
| Spring Security | Authentication | No login system |
| PostgreSQL Driver | Connect to database | Can't store data |
| JWT | Token authentication | Stateful sessions |
| Lombok | Reduce boilerplate | 3x more code |
| Validation | Check data | Bad data crashes app |
| Angular Core | Frontend framework | No component system |
| HttpClient | API calls | Manual fetch calls |
| Router | Navigation | Page refreshes |
| RxJS | Async data | Callback hell |
| TypeScript | Type safety | Runtime errors |

---

**Next**: [10-BACKEND-LINE-BY-LINE.md](./10-BACKEND-LINE-BY-LINE.md) - Every line of backend code explained
