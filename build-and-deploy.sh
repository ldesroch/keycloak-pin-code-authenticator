#!/bin/bash

# PIN Code Authenticator - Build and Deploy Script
# Copyright 2026 Pin Code Authenticator Contributors
# Licensed under the Apache License, Version 2.0

set -e

echo "================================================"
echo "PIN Code Authenticator - Build & Deploy"
echo "================================================"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo -e "${RED}Error: Maven is not installed${NC}"
    exit 1
fi

# Build the project
echo -e "\n${YELLOW}Building project...${NC}"
mvn clean package -DskipTests

if [ $? -eq 0 ]; then
    echo -e "${GREEN}Build successful!${NC}"
else
    echo -e "${RED}Build failed!${NC}"
    exit 1
fi

# Check if JAR exists
JAR_FILE="target/keycloak-pin-authenticator-1.0.0-SNAPSHOT.jar"
if [ ! -f "$JAR_FILE" ]; then
    echo -e "${RED}Error: JAR file not found at $JAR_FILE${NC}"
    exit 1
fi

echo -e "${GREEN}JAR file created: $JAR_FILE${NC}"

# Stop existing containers
echo -e "\n${YELLOW}Stopping existing containers...${NC}"
docker-compose down

# Start services
echo -e "\n${YELLOW}Starting Keycloak and PostgreSQL...${NC}"
docker-compose up -d

# Wait for Keycloak to be ready
echo -e "\n${YELLOW}Waiting for Keycloak to start...${NC}"
echo "This may take a minute..."

TIMEOUT=120
ELAPSED=0
while [ $ELAPSED -lt $TIMEOUT ]; do
    if docker exec keycloak-pin-server /opt/keycloak/bin/kcadm.sh config credentials --server http://localhost:8080 --realm master --user admin --password admin 2>/dev/null; then
        echo -e "${GREEN}Keycloak is ready!${NC}"
        break
    fi
    sleep 2
    ELAPSED=$((ELAPSED + 2))
    echo -n "."
done

if [ $ELAPSED -ge $TIMEOUT ]; then
    echo -e "\n${RED}Timeout waiting for Keycloak to start${NC}"
    echo "Check logs with: docker-compose logs keycloak"
    exit 1
fi

echo ""
echo -e "${GREEN}================================================${NC}"
echo -e "${GREEN}Deployment Complete!${NC}"
echo -e "${GREEN}================================================${NC}"
echo ""
echo "Keycloak Admin Console: http://localhost:8080"
echo "  Username: admin"
echo "  Password: admin"
echo ""
echo "Test Realm: pin-test"
echo "  Test User: testuser"
echo "  Password: password123"
echo ""
echo "To view logs:"
echo "  docker-compose logs -f keycloak"
echo ""
echo "To stop:"
echo "  docker-compose down"
echo ""
