# Security Deployment Guide

## ✅ Production Deployment with Security Headers

This document explains how to deploy The Dispatch with all security features enabled, including frontend security headers.

---

## Development vs Production

### Development Mode (Current - `docker-compose.yml`)
- Uses Angular dev server (no security headers)
- HTTP cookies (not secure)
- Detailed error logging
- **Use for**: Local development and testing

### Production Mode (`docker-compose.prod.yml`)
- Uses nginx with security headers ✅
- HTTPS-only cookies ✅
- Minimal error logging
- **Use for**: Production deployment

---

## Starting Production Mode

```bash
# Stop development containers
docker compose down

# Start production containers with security headers
docker compose -f docker-compose.prod.yml up -d --build
```

The frontend will be available at `http://localhost` (port 80).

---

## Frontend Security Headers Implemented

The production nginx configuration (`frontend/nginx.conf`) includes:

### 1. **X-Frame-Options: DENY**
- Prevents clickjacking attacks
- Blocks the app from being embedded in iframes

### 2. **X-Content-Type-Options: nosniff**
- Prevents MIME type sniffing
- Forces browsers to respect declared content types

### 3. **Content-Security-Policy**
- Restricts resource loading (scripts, styles, images)
- Prevents XSS attacks by controlling what can execute
- Current policy:
  ```
  default-src 'self';
  script-src 'self' 'unsafe-inline' 'unsafe-eval';
  style-src 'self' 'unsafe-inline';
  img-src 'self' data: http://localhost:8080;
  font-src 'self' data:;
  connect-src 'self' http://localhost:8080;
  ```

### 4. **X-XSS-Protection: 1; mode=block**
- Enables browser's XSS filter
- Blocks page if XSS attack is detected

### 5. **X-Permitted-Cross-Domain-Policies: none**
- Prevents Adobe Flash/PDF from loading cross-domain content

### 6. **Referrer-Policy: strict-origin-when-cross-origin**
- Controls referrer information sent to other sites
- Protects user privacy

### 7. **Permissions-Policy**
- Disables geolocation, microphone, and camera
- Prevents unauthorized feature access

### 8. **Strict-Transport-Security (HSTS)**
- Forces HTTPS connections (commented out for HTTP development)
- Uncomment in production with HTTPS:
  ```nginx
  add_header Strict-Transport-Security "max-age=31536000; includeSubDomains; preload" always;
  ```

---

## Testing Security Headers

Once production mode is running, verify headers:

```bash
curl -I http://localhost

# Expected output includes:
# X-Frame-Options: DENY
# X-Content-Type-Options: nosniff
# Content-Security-Policy: default-src 'self'; ...
# X-XSS-Protection: 1; mode=block
```

---

## Backend Security Features (Already Implemented)

The backend (`backend/src/main/java/_blog/blog/config/SecurityConfig.java`) includes:

1. ✅ Security headers (X-Frame-Options, CSP, HSTS)
2. ✅ BCrypt password hashing
3. ✅ JWT authentication with HttpOnly cookies
4. ✅ Strong password validation
5. ✅ Rate limiting (5 req/min per IP)
6. ✅ XSS protection (HTML sanitization)
7. ✅ File upload validation (ImageIO parsing)
8. ✅ Input validation (@Valid annotations)

---

## HTTPS Setup (Production Only)

For production with HTTPS:

1. **Obtain SSL certificate** (Let's Encrypt recommended):
   ```bash
   certbot certonly --standalone -d yourdomain.com
   ```

2. **Update nginx.conf** to use HTTPS:
   ```nginx
   server {
       listen 443 ssl http2;
       ssl_certificate /etc/letsencrypt/live/yourdomain.com/fullchain.pem;
       ssl_certificate_key /etc/letsencrypt/live/yourdomain.com/privkey.pem;

       # Enable HSTS
       add_header Strict-Transport-Security "max-age=31536000; includeSubDomains; preload" always;

       # ... rest of config
   }
   ```

3. **Update backend** environment variable:
   ```yaml
   COOKIE_SECURE: "true"  # Already set in docker-compose.prod.yml
   ```

---

## Security Checklist

Before deploying to production:

- [ ] Use `docker-compose.prod.yml`
- [ ] Set strong `JWT_SECRET_KEY` environment variable
- [ ] Enable HTTPS and uncomment HSTS header
- [ ] Set `COOKIE_SECURE: "true"` (already done)
- [ ] Review Content-Security-Policy for your domain
- [ ] Set up database backups
- [ ] Configure firewall rules
- [ ] Monitor logs for security events

---

## Files Created

1. `frontend/nginx.conf` - nginx configuration with security headers
2. `frontend/Dockerfile.prod` - Production Dockerfile using nginx
3. `docker-compose.prod.yml` - Production docker-compose configuration
4. This file - `SECURITY-DEPLOYMENT.md`

---

## Support

For questions or issues:
- Review `COMPREHENSIVE-SECURITY-AUDIT-REPORT.md` for vulnerability details
- Check `SECURITY-FIXES-REPORT.md` for implemented fixes
