# Blog Backend API

Spring Boot REST API for the blog application.

## Prerequisites

- Java 17 or higher
- PostgreSQL 18+
- Maven (or use the included `mvnw` wrapper)

## Setup

### 1. Database Setup

Create a PostgreSQL database:

```bash
createdb blog
createuser blog -P  # Set password to 'blog' or your preferred password
```

### 2. Environment Variables

Copy the example environment file:

```bash
cp .env.example .env
```

Edit `.env` and update the values:

```properties
# Database Configuration
DB_URL=jdbc:postgresql://localhost:5432/blog
DB_USERNAME=blog
DB_PASSWORD=your-database-password

# JWT Configuration
# IMPORTANT: Generate a new secret key for production
# Example: openssl rand -hex 32
JWT_SECRET_KEY=your-generated-secret-key-here

# Other settings...
```

**IMPORTANT:**
- Never commit the `.env` file to version control
- Always generate a new JWT secret for production using: `openssl rand -hex 32`
- The `.env` file is already in `.gitignore`

### 3. Run the Application

#### Option 1: Using the run script (Recommended)

```bash
./run.sh
```

This script automatically loads environment variables from `.env` and starts the server.

#### Option 2: Manual export

```bash
export JWT_SECRET_KEY=your-secret-key
export DB_URL=jdbc:postgresql://localhost:5432/blog
export DB_USERNAME=blog
export DB_PASSWORD=blog
./mvnw spring-boot:run
```

#### Option 3: Using IDE

In IntelliJ IDEA or Eclipse, configure environment variables in the run configuration.

## API Endpoints

The API will be available at `http://localhost:8080`

### Authentication
- `POST /api/v1/auth/register` - Register new user
- `POST /api/v1/auth/login` - Login

### Posts
- `GET /posts/all` - Get all posts
- `POST /posts/create` - Create post (authenticated)
- `PUT /posts/{id}` - Update post (authenticated, owner only)
- `DELETE /posts/{id}` - Delete post (authenticated, owner only)

### Users
- `GET /users` - Get all users
- `GET /users/me` - Get current user profile
- `PUT /users/update` - Update user profile

See the controllers for complete API documentation.

## Security Features

✅ JWT-based authentication
✅ Password encryption with BCrypt
✅ Role-based authorization (USER, ADMIN)
✅ File upload validation with magic byte verification
✅ Path traversal protection
✅ Input validation on all DTOs
✅ Environment variable configuration for secrets

## Development

### Build

```bash
./mvnw clean install
```

### Run Tests

```bash
./mvnw test
```

### Package

```bash
./mvnw package
```

The JAR file will be created in `target/blog-0.0.1-SNAPSHOT.jar`

## Production Deployment

1. Generate a strong JWT secret: `openssl rand -hex 32`
2. Set environment variables on your production server
3. Update database credentials
4. Set logging levels to INFO or WARN
5. Disable SQL logging: `SHOW_SQL=false`
6. Use HTTPS and enable secure cookies
7. Set up proper CORS origins
8. Consider using a secret management service (AWS Secrets Manager, HashiCorp Vault, etc.)

## Troubleshooting

### Error: Could not resolve placeholder 'JWT_SECRET_KEY'

Make sure you've set the `JWT_SECRET_KEY` environment variable or created a `.env` file.

### Database connection errors

Verify PostgreSQL is running and credentials are correct in your `.env` file.

## License

MIT
