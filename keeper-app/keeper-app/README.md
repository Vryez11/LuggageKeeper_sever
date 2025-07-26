# Keeper App

A Spring Boot application for luggage keeping service that serves both store and customer applications.

## Overview

This application provides a backend service for two different types of users:
- **Store Users**: Users from the store application who manage luggage storage
- **Customer Users**: Users from the customer application who want to store their luggage

## Authentication

The application distinguishes between store and customer users during login. When a user logs in, the response includes the user type (STORE or CUSTOMER), which can be used by the client applications to determine which UI to display.

### Login Endpoint

```
POST /api/auth/login
```

#### Request Body

```json
{
  "username": "string",
  "password": "string"
}
```

#### Response Body

```json
{
  "username": "string",
  "name": "string",
  "userType": "STORE | CUSTOMER",
  "success": true,
  "message": "Login successful"
}
```

### Test Users

For testing purposes, the application initializes two test users:

1. **Store User**
   - Username: `store`
   - Password: `password`
   - User Type: `STORE`

2. **Customer User**
   - Username: `customer`
   - Password: `password`
   - User Type: `CUSTOMER`

## Development

### Prerequisites

- Java 17 or higher
- Gradle

### Running the Application

```bash
./gradlew bootRun
```

The application will start on port 8080.

### H2 Console

The H2 database console is available at:

```
http://localhost:8080/h2-console
```

Connection details:
- JDBC URL: `jdbc:h2:mem:testdb`
- Username: `sa`
- Password: (empty)

## Implementation Details

The application uses:
- Spring Boot 3.5.3
- Spring Security for authentication
- H2 in-memory database for development
- JPA for database operations

The user type distinction is implemented using an enum `UserType` with values `STORE` and `CUSTOMER`. This is stored in the database and returned in the login response.