# The Dispatch - Complete Project Documentation

A full-stack blog platform with Spring Boot & Angular, designed for learning modern web development.

---

## 📚 Documentation Structure

This project includes comprehensive learning materials in the [`docs/`](./docs/) folder:

### **➡️ [START HERE - Complete Learning Roadmap](./docs/START-HERE.md)** ⭐

### 1. **[LEARNING-GUIDE.md](./docs/LEARNING-GUIDE.md)** - Start Here!
Your complete introduction to Spring Boot and Angular:
- Spring Boot fundamentals (annotations, DI, JPA)
- Angular basics (components, services, RxJS, signals)
- Complete flow examples
- How everything connects

**Start with this if you're new to Spring Boot or Angular.**

### 2. **[ADVANCED-CONCEPTS.md](./docs/ADVANCED-CONCEPTS.md)** - Level Up
Deep dives into professional patterns:
- DTOs and why they matter
- Mappers for data transformation
- Validation strategies
- Error handling patterns
- Testing basics (unit & integration)

**Read this after understanding the basics.**

### 3. **[PRACTICAL-PATTERNS.md](./docs/PRACTICAL-PATTERNS.md)** - Real Implementation Examples
Real patterns from the codebase explained in detail:
- Notification system (complete backend + frontend flow)
- File upload with security (magic bytes, validation)
- Pagination pattern (Spring Data + Angular)
- Builder pattern in practice
- Transaction management
- Frontend signals
- Security patterns (JWT, CORS, password hashing)

**Read this to see how features are actually built.**

### 4. **[HOW-TO-ADD-FEATURES.md](./docs/HOW-TO-ADD-FEATURES.md)** - Build Features
Step-by-step guide to adding new features:
- Complete example: Bookmark feature (database → frontend)
- Common patterns (one-to-many, many-to-many, soft delete)
- Feature checklist
- Testing new features

**Use this as a reference when building.**

### 5. **[DEBUGGING-AND-COMMON-MISTAKES.md](./docs/DEBUGGING-AND-COMMON-MISTAKES.md)** - Troubleshooting Guide
Essential debugging techniques and common pitfalls:
- Backend and frontend debugging techniques
- Common Spring Boot and Angular mistakes
- Database and authentication issues
- CORS errors explained
- Performance troubleshooting
- Debugging tools and techniques

**Read this when you're stuck or getting errors.**

---

## 🚀 Quick Start

### Prerequisites

- **Backend**: Java 17+, PostgreSQL 18+, Maven
- **Frontend**: Node.js 18+, npm

### Setup

#### 1. Database
```bash
createdb blog
createuser blog -P  # password: blog
```

#### 2. Backend
```bash
cd backend

# Set environment variables
export JWT_SECRET_KEY=$(openssl rand -hex 32)
export DB_URL=jdbc:postgresql://localhost:5432/blog
export DB_USERNAME=blog
export DB_PASSWORD=blog

# Run
./mvnw spring-boot:run
```

Backend runs on **http://localhost:8080**

#### 3. Frontend
```bash
cd frontend
npm install
npm start
```

Frontend runs on **http://localhost:4200**

---

## 🏗️ Project Architecture

```
The Dispatch
│
├── backend (Spring Boot 3.5.6)
│   ├── entity/          - Database models
│   ├── repository/      - Data access layer
│   ├── service/         - Business logic
│   ├── controller/      - REST API endpoints
│   ├── dto/             - Data Transfer Objects
│   ├── mapper/          - Entity ↔ DTO conversion
│   ├── config/          - Spring configuration
│   └── filter/          - JWT authentication
│
└── frontend (Angular 20.3)
    ├── core/            - Services, guards, interceptors
    ├── features/        - Page components
    ├── shared/          - Reusable components, models
    └── app.routes.ts    - Routing configuration
```

---

## ✨ Features

### Implemented
- ✅ User authentication (JWT)
- ✅ Blog posts (create, read, update, delete)
- ✅ Rich text editor (Editor.js + Quill)
- ✅ Comments system
- ✅ Like/unlike posts
- ✅ Follow/unfollow users
- ✅ Personalized feed
- ✅ Notifications (5 types)
- ✅ Report system (posts & users)
- ✅ Admin dashboard
- ✅ User moderation (ban/unban)
- ✅ Media uploads (images/videos)
- ✅ Avatar management
- ✅ Profile editing

### You Can Add (Learning Exercises)
- 🔖 Bookmarks (see HOW-TO-ADD-FEATURES.md)
- 💬 Direct messaging
- 🔍 Search functionality
- 🏷️ Tags/categories
- 📊 Analytics dashboard
- 🌙 Dark mode
- 📱 Mobile responsive improvements
- 🔔 Real-time notifications (WebSocket)
- 📄 Pagination
- ♻️ Infinite scroll

---

## 🎓 Learning Path

Follow this progression:

### Week 1-2: Fundamentals
1. Read **[docs/LEARNING-GUIDE.md](./docs/LEARNING-GUIDE.md)** sections 1-9 (Spring Boot)
2. Experiment with existing endpoints using Postman
3. Read the Entity classes and understand relationships
4. Try modifying a simple endpoint

### Week 3-4: Frontend Basics
1. Read **[docs/LEARNING-GUIDE.md](./docs/LEARNING-GUIDE.md)** sections 10-18 (Angular)
2. Explore the HomeComponent
3. Understand how signals work
4. Modify the UI (change colors, add fields)

### Week 5-6: Advanced Concepts
1. Read **[docs/ADVANCED-CONCEPTS.md](./docs/ADVANCED-CONCEPTS.md)**
2. Study the DTO pattern in the codebase
3. Add validation to an existing endpoint
4. Write a unit test

### Week 7-8: Practical Patterns
1. Read **[docs/PRACTICAL-PATTERNS.md](./docs/PRACTICAL-PATTERNS.md)**
2. Study the notification system flow
3. Examine file upload security patterns
4. Understand transaction management in practice

### Week 9-10: Build Features
1. Follow **[docs/HOW-TO-ADD-FEATURES.md](./docs/HOW-TO-ADD-FEATURES.md)** to add Bookmarks
2. Add a different feature (e.g., tags)
3. Write tests for your new feature
4. Document your changes

---

## 🔑 Key Concepts

### Backend (Spring Boot)

#### Dependency Injection
```java
@RestController
public class PostController {
    private final PostService postService;  // Injected by Spring

    public PostController(PostService postService) {
        this.postService = postService;
    }
}
```

#### JPA Relationships
```java
@ManyToOne  // Many posts → One user
@JoinColumn(name = "author_id")
private User author;

@OneToMany(mappedBy = "author")  // One user → Many posts
private List<Post> posts;

@ManyToMany  // Users ↔ Posts (likes)
private Set<User> likedBy;
```

#### REST Endpoints
```java
@PostMapping("/posts/create")
public ResponseEntity<String> createPost(
    @Valid @RequestBody PostRequest request,  // DTO from JSON
    Authentication auth                        // Current user
) {
    // Business logic
    return ResponseEntity.ok("Success");
}
```

### Frontend (Angular)

#### Components
```typescript
@Component({
  selector: 'app-home',
  standalone: true,
  templateUrl: './home.html'
})
export class HomeComponent {
  posts = signal<PostResponse[]>([]);  // Reactive state
}
```

#### Services
```typescript
@Injectable({ providedIn: 'root' })
export class ApiService {
  getPosts(): Observable<PostResponse[]> {
    return this.http.get<PostResponse[]>('/posts/all');
  }
}
```

#### Template Syntax
```html
@if (loading()) {
  <div>Loading...</div>
} @else {
  @for (post of posts(); track post.id) {
    <article (click)="viewPost(post.id)">
      {{ post.title }}
    </article>
  }
}
```

---

## 🧪 Testing

### Backend Tests
```bash
cd backend
./mvnw test
```

### Frontend Tests
```bash
cd frontend
npm test
```

### Manual Testing
Use Postman or curl:
```bash
# Login
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"usernameOrEmail":"john","password":"password"}'

# Get posts (with token)
curl -X GET http://localhost:8080/posts/all \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

---

## 📊 Database Schema

### Core Tables
- **users** - User accounts
- **posts** - Blog posts
- **comments** - Post comments
- **subscriptions** - Follow relationships
- **post_likes** - Like relationships (join table)
- **notifications** - User notifications
- **reports** - Content reports
- **post_reports** - Post-specific reports

### Relationships
```
users (1) ──< (N) posts
users (1) ──< (N) comments
posts (1) ──< (N) comments
users (M) ──< (N) posts  (likes)
users (M) ──< (N) users  (subscriptions)
```

---

## 🛠️ Technology Stack

### Backend
- **Spring Boot 3.5.6** - Application framework
- **Spring Security** - Authentication & authorization
- **Spring Data JPA** - Database ORM
- **PostgreSQL 18+** - Relational database
- **JWT (jjwt 0.11.5)** - Token-based auth
- **Lombok** - Reduce boilerplate code
- **Maven** - Build tool

### Frontend
- **Angular 20.3** - Frontend framework
- **TypeScript 5.9.2** - Type-safe JavaScript
- **RxJS 7.8** - Reactive programming
- **Angular Material 20.2** - UI components
- **Editor.js 2.31** - Rich text editor
- **Quill 2.0.3** - Alternative editor

---

## 📁 Important Files

### Backend Configuration
- `application.properties` - Database, JWT, logging
- `SecurityConfig.java` - Security settings
- `JwtAuthenticationFilter.java` - JWT validation

### Frontend Configuration
- `environment.ts` - API URL configuration
- `app.routes.ts` - Route definitions
- `auth.interceptor.ts` - Auto JWT injection
- `auth-guard.ts` - Route protection

---

## 🐛 Troubleshooting

### Backend Issues

**Error**: "Could not resolve placeholder 'JWT_SECRET_KEY'"
```bash
# Solution: Set environment variable
export JWT_SECRET_KEY=$(openssl rand -hex 32)
```

**Error**: Database connection refused
```bash
# Solution: Check PostgreSQL is running
sudo service postgresql status
sudo service postgresql start
```

**Error**: Port 8080 already in use
```bash
# Solution: Kill process or change port
lsof -i :8080
kill -9 <PID>
```

### Frontend Issues

**Error**: Cannot find module '@angular/core'
```bash
# Solution: Install dependencies
npm install
```

**Error**: API calls fail with CORS error
- Check backend is running on port 8080
- Check SecurityConfig allows localhost:4200

**Error**: 401 Unauthorized
- Token might be expired (refresh login)
- Check JWT is being sent (inspect Network tab)

---

## 🎯 Next Steps

After completing the learning guides, try these challenges:

### Beginner
1. Change the color scheme
2. Add a new field to User profile
3. Modify validation rules
4. Add a new REST endpoint

### Intermediate
1. Implement the Bookmark feature (guide included)
2. Add search functionality
3. Implement pagination
4. Add tags to posts

### Advanced
1. Add WebSocket for real-time notifications
2. Implement direct messaging
3. Add Redis caching
4. Create analytics dashboard
5. Deploy to production (AWS/Heroku/DigitalOcean)

---

## 🤝 Contributing

This is a learning project. Feel free to:
- Add new features
- Improve documentation
- Fix bugs
- Optimize performance
- Add tests

---

## 📝 License

MIT License - Feel free to use for learning!

---

## 💡 Tips for Learning

1. **Read code like a book**: Start from main() and follow the flow
2. **Use debugger**: Set breakpoints and step through code
3. **Break things**: Best way to learn is fixing what you broke
4. **Google is your friend**: Don't memorize, understand concepts
5. **Write tests**: They force you to understand the code
6. **Ask questions**: No question is stupid
7. **Build something**: Apply what you learned to a personal project

---

## 📚 Additional Resources

### Spring Boot
- [Official Documentation](https://spring.io/projects/spring-boot)
- [Baeldung Tutorials](https://www.baeldung.com/)
- [Spring Guides](https://spring.io/guides)

### Angular
- [Official Documentation](https://angular.dev/)
- [RxJS Documentation](https://rxjs.dev/)
- [Angular University](https://blog.angular-university.io/)

### General
- [HTTP Status Codes](https://httpstatuses.com/)
- [REST API Best Practices](https://restfulapi.net/)
- [Git Tutorial](https://www.atlassian.com/git)

---

## 🎊 You've Got This!

Learning full-stack development is challenging but incredibly rewarding. Take it one step at a time, and don't rush. The guides are structured to build on each other.

**Happy coding!** 🚀

---

## 📞 Need Help?

- Check the troubleshooting section above
- Review the learning guides
- Search Stack Overflow
- Read the official docs

**Remember**: Every expert was once a beginner. Keep learning, keep building!
