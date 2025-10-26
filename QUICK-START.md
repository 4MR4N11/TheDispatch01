# 🚀 Quick Start Guide - The Dispatch

Get the application running in **under 5 minutes** with Docker!

---

## Prerequisites

Make sure you have installed:
- **Docker** (version 20.10+)
- **Docker Compose** (version 2.0+)

Check installation:
```bash
docker --version
docker-compose --version
```

If not installed, get Docker from: https://docs.docker.com/get-docker/

---

## 🎯 One-Command Setup

Simply run:

```bash
./run.sh
```

That's it! The script will:
- ✅ Check if Docker is installed
- ✅ Generate a secure JWT secret
- ✅ Build all containers
- ✅ Start PostgreSQL database
- ✅ Start Spring Boot backend
- ✅ Start Angular frontend
- ✅ Wait for everything to be ready

---

## 📱 Access the Application

Once the script completes:

| Service | URL | Description |
|---------|-----|-------------|
| **Frontend** | http://localhost:4200 | Angular UI |
| **Backend API** | http://localhost:8080 | Spring Boot REST API |
| **Database** | localhost:5432 | PostgreSQL |

### Database Credentials
- **Host**: localhost
- **Port**: 5432
- **Database**: blog
- **Username**: blog
- **Password**: blog

---

## 🛠️ Common Commands

### View Logs
```bash
docker-compose logs -f
```

### View specific service logs
```bash
docker-compose logs -f backend
docker-compose logs -f frontend
docker-compose logs -f postgres
```

### Stop the application
```bash
docker-compose down
```

### Stop and remove all data (including database)
```bash
docker-compose down -v
```

### Restart services
```bash
docker-compose restart
```

### Restart specific service
```bash
docker-compose restart backend
```

### Rebuild containers after code changes
```bash
docker-compose down
docker-compose build --no-cache
docker-compose up -d
```

---

## 🔧 Manual Setup (Without run.sh)

If you prefer to run commands manually:

### 1. Generate JWT Secret
```bash
# Generate secure 32-byte base64 key
export JWT_SECRET_KEY=$(openssl rand -base64 32)

# Save to .env file
echo "JWT_SECRET_KEY=$JWT_SECRET_KEY" > .env
```

### 2. Start Containers
```bash
docker-compose up -d
```

### 3. Check Status
```bash
docker-compose ps
```

### 4. Wait for services to be ready
```bash
# Backend health check
curl http://localhost:8080/actuator/health

# Frontend
curl http://localhost:4200
```

---

## 📊 Container Architecture

```
┌─────────────────┐
│   Frontend      │  Angular 20 (Port 4200)
│  (Container)    │
└────────┬────────┘
         │ HTTP
         ↓
┌─────────────────┐
│   Backend       │  Spring Boot 3.5 (Port 8080)
│  (Container)    │
└────────┬────────┘
         │ JDBC
         ↓
┌─────────────────┐
│   PostgreSQL    │  PostgreSQL 16 (Port 5432)
│  (Container)    │
└─────────────────┘
```

---

## 🐛 Troubleshooting

### Port Already in Use

If you see "port is already allocated":

```bash
# Check what's using the port
sudo lsof -i :8080  # For backend
sudo lsof -i :4200  # For frontend
sudo lsof -i :5432  # For database

# Kill the process
kill -9 <PID>

# Or change ports in docker-compose.yml
```

### Backend Won't Start

```bash
# View backend logs
docker-compose logs backend

# Common issues:
# 1. JWT_SECRET_KEY not set → Run: export JWT_SECRET_KEY=$(openssl rand -base64 32)
# 2. Database not ready → Wait 30 seconds and restart: docker-compose restart backend
# 3. Port 8080 in use → Stop other services or change port in docker-compose.yml
```

### Frontend Won't Start

```bash
# View frontend logs
docker-compose logs frontend

# Common issues:
# 1. Node modules not installed → Rebuild: docker-compose build --no-cache frontend
# 2. Port 4200 in use → Change port in docker-compose.yml and update CORS in backend
```

### Database Connection Failed

```bash
# Check if PostgreSQL is running
docker-compose ps postgres

# View PostgreSQL logs
docker-compose logs postgres

# Test connection
docker-compose exec postgres psql -U blog -d blog -c "SELECT 1;"
```

### JWT Secret Validation Error

```bash
# Make sure JWT_SECRET_KEY is set
echo $JWT_SECRET_KEY

# If empty, generate one:
export JWT_SECRET_KEY=$(openssl rand -base64 32)

# Restart backend
docker-compose restart backend
```

---

## 🔒 Security Notes

### JWT Secret

The `run.sh` script automatically generates a secure JWT secret and saves it to `.env`.

**Important**:
- ✅ The `.env` file is git-ignored
- ✅ Never commit `.env` to version control
- ✅ Generate a new secret for production

### Production Deployment

Before deploying to production:

1. **Set environment variables**:
```bash
export COOKIE_SECURE=true
export COOKIE_SAME_SITE=Strict
export JWT_SECRET_KEY=<your-production-secret>
export DB_PASSWORD=<strong-password>
```

2. **Enable HTTPS** (cookies won't work over HTTP in production)

3. **Update CORS origins** in `SecurityConfig.java`:
```java
configuration.setAllowedOrigins(Arrays.asList("https://yourdomain.com"));
```

4. **Review security settings** in `SECURITY-FIXES-REPORT.md`

---

## 📚 Next Steps

1. **Register a user**: http://localhost:4200/auth/register
2. **Create a post**: After login, create your first blog post
3. **Explore features**: Comments, likes, subscriptions, notifications

### Documentation

- **Learning Guide**: [docs/LEARNING-GUIDE.md](docs/LEARNING-GUIDE.md)
- **Security Report**: [SECURITY-FIXES-REPORT.md](SECURITY-FIXES-REPORT.md)
- **API Documentation**: http://localhost:8080/swagger-ui.html (if Swagger is configured)

---

## 🎓 Development Mode

To run in development mode with hot-reload:

### Backend
```bash
cd backend
./mvnw spring-boot:run
```

### Frontend
```bash
cd frontend
npm install
npm start
```

### Database
```bash
# Use the Docker PostgreSQL or install locally
docker-compose up -d postgres
```

---

## 🧹 Clean Up

### Remove containers but keep data
```bash
docker-compose down
```

### Remove everything (including database data)
```bash
docker-compose down -v
```

### Remove images
```bash
docker rmi dispatch-backend dispatch-frontend
```

### Complete cleanup
```bash
docker-compose down -v
docker system prune -a
```

---

## ✅ Verification Checklist

After running `./run.sh`, verify:

- [ ] PostgreSQL is running: `docker-compose ps postgres`
- [ ] Backend health check: `curl http://localhost:8080/actuator/health`
- [ ] Frontend accessible: Open http://localhost:4200 in browser
- [ ] Can register a user
- [ ] Can login
- [ ] Can create a post
- [ ] Can view posts

---

## 🆘 Getting Help

If you encounter issues:

1. **Check logs**: `docker-compose logs -f`
2. **Verify containers**: `docker-compose ps`
3. **Check ports**: `sudo lsof -i :8080,:4200,:5432`
4. **Review security report**: [SECURITY-FIXES-REPORT.md](SECURITY-FIXES-REPORT.md)
5. **Check documentation**: [docs/](docs/)

---

## 🎉 Success!

If everything is running:
- Frontend: http://localhost:4200
- Backend: http://localhost:8080
- Database: localhost:5432

You're ready to start developing! 🚀
