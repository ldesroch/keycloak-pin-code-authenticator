# Docker Testing Environment

This directory contains Docker Compose configuration for local testing of the PIN Code Authenticator.

## Prerequisites

- Docker and Docker Compose installed
- Maven installed (for building the JAR)

## Quick Start

1. **Build and deploy** (recommended):
   ```bash
   chmod +x build-and-deploy.sh
   ./build-and-deploy.sh
   ```

2. **Manual steps**:
   ```bash
   # Build the project
   mvn clean package

   # Start services
   docker-compose up -d

   # View logs
   docker-compose logs -f keycloak
   ```

## Services

### Keycloak (port 8080)
- **Version**: 26.0.5
- **Admin Console**: http://localhost:8080
- **Admin credentials**: admin / admin
- **Pre-configured with**:
  - PIN Code Authenticator extension
  - Test realm "pin-test"
  - Test user "testuser" / "password123"
  - PIN authentication flow

### PostgreSQL (internal)
- **Version**: 16-alpine
- **Database**: keycloak
- **Credentials**: keycloak / keycloak_password
- **Data persistence**: postgres_data volume

## Test Realm

The `pin-test` realm is automatically imported with:

- **Browser Flow**: Modified to include PIN authentication
- **Test User**: testuser@example.com / password123
- **Test Client**: pin-test-client (public client for testing)
- **Required Action**: CONFIGURE_PIN enabled

## Testing the Extension

1. **Access Keycloak**: http://localhost:8080
2. **Login to Test Realm**: Click "Administration Console"
3. **Switch Realm**: Select "pin-test" from dropdown
4. **Test User Login**:
   - Go to http://localhost:8080/realms/pin-test/account
   - Login as: testuser / password123
   - You'll be prompted to configure a PIN
   - Choose format and set your PIN
   - Complete login

5. **Test PIN Authentication**:
   - Logout and login again
   - After password, you'll be prompted for PIN
   - Enter your PIN to complete authentication

## Verify Extension Loaded

Check Keycloak logs to confirm the extension is loaded:

```bash
docker-compose logs keycloak | grep -i "pin"
```

You should see messages about PIN providers being registered.

## Development Workflow

1. **Make code changes**
2. **Rebuild and redeploy**:
   ```bash
   ./build-and-deploy.sh
   ```
3. **Test changes** in browser

## Clean Up

**Stop services** (keeps data):
```bash
docker-compose down
```

**Remove all data**:
```bash
docker-compose down -v
```

## Troubleshooting

### Extension not loading
- Check JAR is built: `ls -lh target/keycloak-pin-authenticator-*.jar`
- Check JAR is mounted: `docker exec keycloak-pin-server ls -lh /opt/keycloak/providers/`
- Check logs: `docker-compose logs keycloak`

### Keycloak won't start
- Check PostgreSQL is healthy: `docker-compose ps`
- Check PostgreSQL logs: `docker-compose logs postgres`
- Increase timeout in healthcheck

### Realm not imported
- Check realm file: `cat docker/realms/pin-test-realm.json`
- Manually import via Admin Console
- Check Keycloak logs for import errors

### Port 8080 already in use
Edit `docker-compose.yml` and change ports:
```yaml
ports:
  - "8081:8080"  # Use 8081 instead
```

## Accessing Database

If you need to access PostgreSQL directly:

```bash
docker exec -it keycloak-pin-postgres psql -U keycloak -d keycloak
```

## Health Checks

Both services have health checks configured:
- **PostgreSQL**: `pg_isready` check
- **Keycloak**: HTTP health endpoint check

View health status:
```bash
docker-compose ps
```

## Production Notes

⚠️ **This setup is for development/testing only**

For production:
- Use proper SSL/TLS (KC_HOSTNAME_STRICT_HTTPS=true)
- Use strong database passwords
- Use production database (not dev mode)
- Configure proper hostname
- Enable additional security features
- Use secrets management
- Configure backup strategies
