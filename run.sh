#!/bin/bash

echo "Starting The Dispatch..."

if [ ! -f .env ]; then
    echo "Generating JWT secret..."
    echo "JWT_SECRET_KEY=$(openssl rand -base64 32)" > .env
    echo "JWT secret created in .env file"
fi

echo "Starting Docker containers..."
docker compose up -d

echo ""
echo "Application started!"
echo ""
echo "Frontend: http://localhost:4200"
echo "Backend:  http://localhost:8080"
echo "Database: localhost:5432"
echo ""
echo "Run 'docker compose logs -f' to view logs"
echo "Run 'docker compose down' to stop"
