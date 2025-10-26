#!/bin/bash

# The Dispatch - Application Startup Script
# This script sets up and runs the entire application with Docker

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Functions
print_header() {
    echo -e "${BLUE}"
    echo "================================================"
    echo "   The Dispatch - Application Startup"
    echo "================================================"
    echo -e "${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

print_info() {
    echo -e "${BLUE}ℹ $1${NC}"
}

check_docker() {
    if ! command -v docker &> /dev/null; then
        print_error "Docker is not installed"
        echo "Please install Docker from: https://docs.docker.com/get-docker/"
        exit 1
    fi
    print_success "Docker is installed"
}

check_docker_compose() {
    if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
        print_error "Docker Compose is not installed"
        echo "Please install Docker Compose from: https://docs.docker.com/compose/install/"
        exit 1
    fi
    print_success "Docker Compose is installed"
}

generate_jwt_secret() {
    if [ -z "$JWT_SECRET_KEY" ]; then
        print_warning "JWT_SECRET_KEY not set, generating secure key..."

        # Generate a secure 32-byte (256-bit) random key and base64 encode it
        if command -v openssl &> /dev/null; then
            export JWT_SECRET_KEY=$(openssl rand -base64 32)
            print_success "JWT secret generated with openssl"
        elif command -v head &> /dev/null && [ -f /dev/urandom ]; then
            export JWT_SECRET_KEY=$(head -c 32 /dev/urandom | base64)
            print_success "JWT secret generated with /dev/urandom"
        else
            print_error "Cannot generate JWT secret. Please install openssl or set JWT_SECRET_KEY manually"
            exit 1
        fi

        # Save to .env file for future runs
        echo "JWT_SECRET_KEY=$JWT_SECRET_KEY" > .env
        print_success "JWT secret saved to .env file"
    else
        print_success "JWT_SECRET_KEY already set"
    fi
}

check_ports() {
    print_info "Checking if required ports are available..."

    local ports=(5432 8080 4200)
    local port_names=("PostgreSQL" "Backend" "Frontend")
    local all_free=true

    for i in "${!ports[@]}"; do
        if lsof -Pi :${ports[$i]} -sTCP:LISTEN -t >/dev/null 2>&1; then
            print_warning "Port ${ports[$i]} (${port_names[$i]}) is already in use"
            all_free=false
        fi
    done

    if [ "$all_free" = false ]; then
        print_warning "Some ports are in use. Do you want to continue anyway? (y/N)"
        read -r response
        if [[ ! "$response" =~ ^[Yy]$ ]]; then
            print_info "Exiting. Please free up the ports and try again."
            exit 1
        fi
    else
        print_success "All required ports are available"
    fi
}

clean_old_containers() {
    print_info "Cleaning up old containers..."
    docker-compose down -v 2>/dev/null || docker compose down -v 2>/dev/null || true
    print_success "Old containers cleaned up"
}

build_containers() {
    print_info "Building Docker containers (this may take a few minutes)..."
    if docker compose version &> /dev/null; then
        docker compose build --no-cache
    else
        docker-compose build --no-cache
    fi
    print_success "Containers built successfully"
}

start_containers() {
    print_info "Starting containers..."
    if docker compose version &> /dev/null; then
        docker compose up -d
    else
        docker-compose up -d
    fi
    print_success "Containers started"
}

wait_for_backend() {
    print_info "Waiting for backend to be ready..."
    local max_attempts=60
    local attempt=0

    while [ $attempt -lt $max_attempts ]; do
        if curl -s http://localhost:8080/actuator/health &> /dev/null; then
            print_success "Backend is ready!"
            return 0
        fi

        attempt=$((attempt + 1))
        echo -n "."
        sleep 2
    done

    print_error "Backend failed to start within 2 minutes"
    return 1
}

wait_for_frontend() {
    print_info "Waiting for frontend to be ready..."
    local max_attempts=60
    local attempt=0

    while [ $attempt -lt $max_attempts ]; do
        if curl -s http://localhost:4200 &> /dev/null; then
            print_success "Frontend is ready!"
            return 0
        fi

        attempt=$((attempt + 1))
        echo -n "."
        sleep 2
    done

    print_error "Frontend failed to start within 2 minutes"
    return 1
}

show_logs() {
    print_info "Showing application logs (Ctrl+C to exit)..."
    if docker compose version &> /dev/null; then
        docker compose logs -f
    else
        docker-compose logs -f
    fi
}

print_final_info() {
    echo ""
    echo -e "${GREEN}================================================"
    echo "   🚀 Application is Running!"
    echo "================================================${NC}"
    echo ""
    echo -e "${BLUE}Access Points:${NC}"
    echo "  📱 Frontend:  http://localhost:4200"
    echo "  🔧 Backend:   http://localhost:8080"
    echo "  🗄️  Database:  localhost:5432"
    echo ""
    echo -e "${BLUE}Credentials:${NC}"
    echo "  Database User: blog"
    echo "  Database Pass: blog"
    echo "  Database Name: blog"
    echo ""
    echo -e "${BLUE}Useful Commands:${NC}"
    echo "  View logs:        docker-compose logs -f"
    echo "  Stop:            docker-compose down"
    echo "  Restart:         docker-compose restart"
    echo "  Remove all:      docker-compose down -v"
    echo ""
    echo -e "${YELLOW}Security Note:${NC}"
    echo "  JWT Secret stored in .env file"
    echo "  Never commit .env to version control!"
    echo ""
    echo -e "${GREEN}Ready to use! 🎉${NC}"
    echo ""
}

# Main execution
main() {
    print_header

    # Pre-flight checks
    check_docker
    check_docker_compose
    generate_jwt_secret
    check_ports

    # Setup
    clean_old_containers
    build_containers
    start_containers

    # Wait for services
    wait_for_backend
    wait_for_frontend

    # Done
    print_final_info

    # Ask if user wants to see logs
    print_info "Do you want to view application logs? (y/N)"
    read -r response
    if [[ "$response" =~ ^[Yy]$ ]]; then
        show_logs
    else
        print_success "Application is running in the background"
        echo "Run 'docker-compose logs -f' to view logs anytime"
    fi
}

# Run main function
main
