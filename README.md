# The Dispatch

A full-stack blog platform with a vintage newspaper design. Built with Spring Boot and Angular.

---

## Table of Contents

- [Overview](#overview)
- [Technologies Used](#technologies-used)
- [Project Structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Manual Setup](#manual-setup)
  - [Backend Setup](#backend-setup)
  - [Frontend Setup](#frontend-setup)
- [Docker Deployment](#docker-deployment)
- [Environment Variables](#environment-variables)
- [API Endpoints](#api-endpoints)
- [Features](#features)
- [Security](#security)

---

## Overview

The Dispatch is a modern blog platform that combines a clean, vintage newspaper aesthetic with powerful content management features. Users can create, publish, and manage blog posts, interact through comments and likes, subscribe to their favorite authors, and receive real-time notifications.

---

## Technologies Used

### Backend

| Technology | Version | Purpose |
|------------|---------|---------|
| Java | 17 | Runtime environment |
| Spring Boot | 3.5.6 | REST API framework |
| Spring Security | 6.4.6 | Authentication & authorization |
| Spring Data JPA | 3.5.6 | Database ORM |
| Hibernate | 6.6.4 | Object-relational mapping |
| PostgreSQL | 16 | Relational database |
| JWT (JJWT) | 0.12.5 | Token-based authentication |
| Bucket4j | 8.1.0 | Rate limiting |
| Spring Boot Validation | 3.5.6 | Input validation |
| Spring Boot Actuator | 3.5.6 | Production monitoring |
| Lombok | 1.18.38 | Boilerplate code reduction |
| Maven | 3.9 | Build tool |

### Frontend

| Technology | Version | Purpose |
|------------|---------|---------|
| Angular | 21.0.1 | SPA framework |
| TypeScript | 5.9.2 | Type-safe JavaScript |
| Angular Material | 20.2.5 | UI component library |
| Angular CDK | 20.2.5 | Component development kit |
| RxJS | 7.8.0 | Reactive programming |
| Karma | 6.4.0 | Test runner |
| Jasmine | 5.9.0 | Testing framework |

### DevOps & Infrastructure

| Technology | Version | Purpose |
|------------|---------|---------|
| Docker | - | Containerization |
| Docker Compose | 3.8 | Multi-container orchestration |
| Nginx | Alpine | Production reverse proxy |

---

## Project Structure

```
TheDispatch01/
├── backend/                    # Spring Boot REST API
│   ├── src/
│   │   ├── main/java/_blog/blog/
│   │   │   ├── config/         # Security, WebMVC configuration
│   │   │   ├── controller/     # REST endpoints
│   │   │   ├── service/        # Business logic
│   │   │   ├── repository/     # Database access (JPA)
│   │   │   ├── entity/         # Database models
│   │   │   ├── dto/            # Data transfer objects
│   │   │   ├── filter/         # JWT authentication filter
│   │   │   ├── exception/      # Custom exceptions
│   │   │   ├── mapper/         # Entity mappers
│   │   │   ├── utils/          # Utility classes
│   │   │   ├── validation/     # Input validation
│   │   │   └── enums/          # Enumerations
│   │   └── resources/
│   │       └── application.properties
│   ├── uploads/                # File storage directory
│   ├── Dockerfile
│   ├── pom.xml
│   └── mvnw                    # Maven wrapper
│
├── frontend/                   # Angular SPA
│   ├── src/
│   │   ├── app/
│   │   │   ├── core/           # Auth, services, guards
│   │   │   ├── features/       # Feature modules
│   │   │   │   ├── admin/
│   │   │   │   ├── auth/
│   │   │   │   ├── edit-profile/
│   │   │   │   ├── home/
│   │   │   │   ├── my-blog/
│   │   │   │   ├── not-found/
│   │   │   │   ├── notifications/
│   │   │   │   ├── posts/
│   │   │   │   ├── reports/
│   │   │   │   └── users/
│   │   │   └── shared/         # Reusable components
│   │   └── environments/
│   ├── public/
│   ├── Dockerfile
│   ├── Dockerfile.prod
│   ├── nginx.conf
│   ├── angular.json
│   └── package.json
│
├── docker-compose.yml          # Development setup
├── docker-compose.prod.yml     # Production setup
├── run.sh                      # Automated startup script
├── .env.example
└── README.md
```

---

## Prerequisites

- **Docker** and **Docker Compose** (recommended)
- Or for manual setup:
  - Java 17+
  - Node.js 20+
  - PostgreSQL 16+
  - Maven 3.9+

---

## Quick Start

The easiest way to run the project is using the provided startup script:

```bash
# Make the script executable
chmod +x run.sh

# Run the application
./run.sh
```

The script will:
1. Check Docker installation
2. Generate a JWT secret key
3. Build and start all containers
4. Wait for services to be healthy
5. Display access URLs

Once started, access the application at:
- **Frontend:** http://localhost:4200
- **Backend API:** http://localhost:8080
- **Database:** localhost:5432

---

## Manual Setup

### Backend Setup

1. **Navigate to the backend directory:**
   ```bash
   cd backend
   ```

2. **Set up environment variables:**
   ```bash
   cp .env.example .env
   ```

3. **Edit `.env` with your configuration:**
   ```env
   DB_URL=jdbc:postgresql://localhost:5432/blog
   DB_USERNAME=blog
   DB_PASSWORD=blog
   JWT_SECRET_KEY=your-secret-key-min-32-characters
   JWT_EXPIRATION=3600000
   ```

4. **Create the database:**
   ```bash
   createdb blog
   ```

5. **Build and run:**
   ```bash
   # Using Maven wrapper
   ./mvnw clean install
   ./mvnw spring-boot:run

   # Or using installed Maven
   mvn clean install
   mvn spring-boot:run
   ```

The backend will start on http://localhost:8080

### Frontend Setup

1. **Navigate to the frontend directory:**
   ```bash
   cd frontend
   ```

2. **Install dependencies:**
   ```bash
   npm install
   ```

3. **Start the development server:**
   ```bash
   npm start
   # or
   ng serve
   ```

The frontend will start on http://localhost:4200

---

## Docker Deployment

### Development

```bash
# Start all services
docker compose up

# Start in detached mode
docker compose up -d

# View logs
docker compose logs -f

# Stop services
docker compose down

# Stop and remove volumes
docker compose down -v
```

### Production

```bash
# Start production stack
docker compose -f docker-compose.prod.yml up -d

# View logs
docker compose -f docker-compose.prod.yml logs -f

# Stop services
docker compose -f docker-compose.prod.yml down
```

### Docker Services

| Service | Port | Description |
|---------|------|-------------|
| postgres | 5432 | PostgreSQL database |
| backend | 8080 | Spring Boot API |
| frontend | 4200 (dev) / 80 (prod) | Angular application |

---

## Environment Variables

### Root `.env`

| Variable | Default | Description |
|----------|---------|-------------|
| `JWT_SECRET_KEY` | - | Secret key for JWT signing (min 32 chars) |
| `POSTGRES_DB` | blog | Database name |
| `POSTGRES_USER` | blog | Database username |
| `POSTGRES_PASSWORD` | blog | Database password |
| `COOKIE_SECURE` | false | Set to true in production (HTTPS) |
| `COOKIE_SAME_SITE` | Lax | Cookie SameSite policy |
| `LOG_LEVEL_WEB` | INFO | Spring web logging level |
| `LOG_LEVEL_SECURITY` | INFO | Spring security logging level |
| `LOG_LEVEL_APP` | INFO | Application logging level |
| `SHOW_SQL` | true | Show SQL queries in logs |
| `FORMAT_SQL` | true | Format SQL queries in logs |
| `FRONTEND_URL` | http://localhost:4200 | Frontend URL for CORS |

### Backend `.env`

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_URL` | jdbc:postgresql://localhost:5432/blog | Database JDBC URL |
| `DB_USERNAME` | blog | Database username |
| `DB_PASSWORD` | blog | Database password |
| `JWT_SECRET_KEY` | - | JWT signing secret |
| `JWT_EXPIRATION` | 3600000 | JWT expiration (ms) - 1 hour |

---

## API Endpoints

### Authentication

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/auth/register` | Register new user | No |
| POST | `/auth/login` | User login | No |
| POST | `/auth/logout` | User logout | Yes |

### Posts

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/posts/all` | Get all posts | No |
| GET | `/posts/feed` | Get paginated feed | Yes |
| GET | `/posts/post/{id}` | Get post by ID | No |
| GET | `/posts/{username}` | Get user's posts | No |
| GET | `/posts/my-posts` | Get current user's posts | Yes |
| POST | `/posts/create` | Create new post | Yes |
| PUT | `/posts/{id}` | Update post | Yes (owner/admin) |
| DELETE | `/posts/{id}` | Delete post | Yes (owner/admin) |
| PUT | `/posts/hide/{id}` | Hide post | Yes (admin) |
| PUT | `/posts/unhide/{id}` | Unhide post | Yes (admin) |

### Users

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/users` | Get all users | No |
| GET | `/users/me` | Get current user | Yes |
| GET | `/users/{id}` | Get user by ID | No |
| GET | `/users/username/{username}` | Get user by username | No |
| GET | `/users/search?q={keyword}` | Search users | Yes |
| PUT | `/users/update-profile` | Update profile | Yes |
| POST | `/users/me/delete` | Delete own account | Yes |
| POST | `/users/delete/{id}` | Delete user | Yes (admin) |
| POST | `/users/ban/{id}` | Ban user | Yes (admin) |
| POST | `/users/unban/{id}` | Unban user | Yes (admin) |
| POST | `/users/promote/{id}` | Promote to admin | Yes (admin) |

### Comments

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/comments` | Get all comments | Yes |
| GET | `/comments/post/{postId}` | Get post comments | No |
| POST | `/comments/create/{postId}` | Add comment | Yes |
| PUT | `/comments/{id}` | Update comment | Yes (owner) |
| DELETE | `/comments/{id}` | Delete comment | Yes (owner/admin) |

### Likes

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/likes/post/{postId}` | Like post | Yes |
| DELETE | `/likes/post/{postId}` | Unlike post | Yes |
| GET | `/likes/post/{postId}/check` | Check if liked | Yes |

### Notifications

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/notifications` | Get user notifications | Yes |
| GET | `/notifications/unread/count` | Get unread count | Yes |
| PUT | `/notifications/{id}/read` | Mark as read | Yes |

### Subscriptions

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/subscriptions/subscribe/{userId}` | Subscribe to user | Yes |
| POST | `/subscriptions/unsubscribe/{userId}` | Unsubscribe | Yes |
| GET | `/subscriptions/check/{userId}` | Check if subscribed | Yes |

### Reports

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/reports` | Get all reports | Yes (admin) |
| POST | `/reports/create` | Create report | Yes |
| PUT | `/reports/{id}/status` | Update report status | Yes (admin) |

### File Uploads

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/uploads/image` | Upload image | Yes |
| POST | `/uploads/video` | Upload video | Yes |
| POST | `/uploads/avatar` | Upload avatar | Yes |
| GET | `/uploads/**` | Serve uploaded files | No |

---

## Features

### User Features
- User registration and authentication
- Profile management with avatar upload
- Subscribe to favorite authors
- Real-time notifications for new posts from subscriptions
- Edit and delete own posts
- Dark/Light theme toggle

### Content Features
- Create and publish blog posts with media (images/videos)
- Comment system with edit and delete functionality
- Like/unlike posts
- View user profiles and their posts
- Paginated feed showing posts from subscribed authors
- Personal blog page showing own posts

### Admin Features
- Admin dashboard with three sections:
  - User management (view, delete, ban/unban, promote)
  - Post management (view all, hide/unhide, delete)
  - Report management (view, update status)
- Content moderation (hide/unhide posts)
- User management (ban/unban accounts)
- Report reviewing and status updates
- View all hidden posts
- Delete any post or comment

### Security Features
- JWT-based authentication with HttpOnly cookies
- BCrypt password hashing
- Role-based authorization (USER/ADMIN)
- Rate limiting on authentication endpoints
- Input validation on all endpoints
- Banned users automatically logged out
- Hidden posts return 404 (not 403) to prevent information leakage
- Banned users return 404 to prevent enumeration

### Design
- Vintage newspaper aesthetic
- Dark/Light theme toggle
- Responsive design (mobile, tablet, desktop)
- Mobile-friendly navigation
- Clean error notifications (no console spam)

---

## Security

### Authentication
- JWT-based stateless authentication
- HttpOnly, Secure, SameSite cookies
- BCrypt password hashing with salt
- Rate limiting on auth endpoints (Bucket4j)
- Automatic logout for banned users

### Authorization
- Role-based access control (RBAC)
- Admin-only endpoints protected with `@PreAuthorize`
- Owner-only operations (edit/delete own posts/comments)
- Admin can view hidden posts and banned users

### Input Security
- Input validation on all endpoints (`@Valid`, `@NotNull`, `@NotBlank`)
- Content trimming to prevent whitespace attacks
- Path traversal protection for file uploads
- SQL injection prevention via JPA/Hibernate parameterized queries

### Information Security
- Hidden posts return 404 (not 403) to prevent information disclosure
- Banned users return 404 to prevent enumeration
- Admin-only visibility for sensitive operations
- Clean error messages without technical details

### Transport Security
- CORS configuration for cross-origin requests
- Security headers (HSTS, CSP, X-Frame-Options)
- CSRF protection disabled (stateless JWT)
- Configurable cookie security (Secure, SameSite)

### File Upload Security
- File size limits (100MB max)
- File type validation (images and videos)
- Isolated upload directory
- Sanitized file paths

---

## License

This project is part of Zone01Oujda's Curriculum.

---

## Author

Developed by Khalid El Amrani - [GitHub](https://github.com/4mr4n11)
