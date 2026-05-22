# Core Banking Connector

A Spring Boot application that acts as a wrapper/connector for Apache Fineract APIs, providing a unified interface for banking operations in the FYP Banking System with Blockchain Integration.

## Overview

This connector is part of the Integration Layer in the system architecture, providing:

- **Authentication APIs** - User authentication and token management
- **Client APIs** - Customer onboarding operations
- **Savings Account APIs** - Account management operations
- **Loan APIs** - Loan applications, approvals, and disbursements
- **Transaction APIs** - Deposits, withdrawals, and loan repayments
- **Role & Permission APIs** - RBAC management for insider threat mitigation
- **Audit & Report APIs** - Compliance monitoring and reporting

## Technology Stack

- **Java 17**
- **Spring Boot 3.2.2**
- **Spring WebFlux** (WebClient for non-blocking HTTP calls)
- **Spring Security** (HTTP Basic authentication)
- **Resilience4j** (Circuit breaker and retry patterns)
- **Lombok** (Boilerplate code reduction)
- **OpenAPI/Swagger** (API documentation)

## Project Structure

```
core-banking-connector/
├── src/main/java/com/fyp/cbc/
│   ├── client/                 # Fineract API clients
│   ├── config/                 # Configuration classes
│   ├── controller/             # REST controllers
│   ├── dto/
│   │   ├── request/            # Request DTOs
│   │   └── response/           # Response DTOs
│   ├── exception/              # Custom exceptions
│   ├── service/                # Business logic layer
│   └── CoreBankingConnectorApplication.java
├── src/main/resources/
│   ├── application.yml         # Main configuration
│   ├── application-dev.yml     # Development profile
│   └── application-prod.yml    # Production profile
└── src/test/java/              # Unit and integration tests
```

## Getting Started

### Prerequisites

- Java 17 or higher
- Maven 3.8+
- Apache Fineract running (default: https://localhost:8443/fineract-provider/api)

### Configuration

Edit `application.yml` or use environment variables:

```yaml
fineract:
  base-url: ${FINERACT_BASE_URL:https://localhost:8443/fineract-provider/api}
  username: ${FINERACT_USERNAME:mifos}
  password: ${FINERACT_PASSWORD:password}
  tenant-id: ${FINERACT_TENANT_ID:default}
```

### Build and Run

```bash
# Build the project
mvn clean package

# Run with development profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Or run the JAR
java -jar target/core-banking-connector-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev
```

### Running Tests

```bash
# Run all tests
mvn test

# Run with coverage report
mvn test jacoco:report
```

## API Documentation

Once the application is running, access the Swagger UI:

- **Swagger UI**: http://localhost:8080/api/swagger-ui.html
- **OpenAPI Spec**: http://localhost:8080/api/api-docs

## API Endpoints

### Authentication
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/v1/authentication` | Authenticate user |
| POST | `/v1/authentication/self` | Authenticate with roles & permissions |

### Clients
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/v1/clients` | Create new client |
| GET | `/v1/clients/{id}` | Get client details |
| PUT | `/v1/clients/{id}` | Update client |
| GET | `/v1/clients` | Search clients |
| POST | `/v1/clients/{id}/identifiers` | Add identifier (CNIC) |
| POST | `/v1/clients/{id}/images` | Upload client image |

### Savings Accounts
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/v1/savingsaccounts` | Create account |
| GET | `/v1/savingsaccounts/{id}` | Get account details |
| PUT | `/v1/savingsaccounts/{id}` | Update account |
| POST | `/v1/savingsaccounts/{id}?command=approve` | Approve account |
| POST | `/v1/savingsaccounts/{id}?command=activate` | Activate account |

### Loans
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/v1/loans` | Create loan application |
| GET | `/v1/loans/{id}` | Get loan details |
| POST | `/v1/loans/{id}?command=approve` | Approve loan |
| POST | `/v1/loans/{id}?command=disburse` | Disburse loan |
| POST | `/v1/loans/{id}?command=reject` | Reject loan |

### Transactions
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/v1/savingsaccounts/{id}/transactions?command=deposit` | Deposit |
| POST | `/v1/savingsaccounts/{id}/transactions?command=withdrawal` | Withdraw |
| POST | `/v1/loans/{id}/transactions?command=repayment` | Loan repayment |
| GET | `/v1/journalentries` | Get journal entries |

### Roles & Permissions
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/v1/roles` | Create role |
| GET | `/v1/roles` | List roles |
| PUT | `/v1/roles/{id}/permissions` | Update role permissions |
| GET | `/v1/permissions` | List all permissions |

### Audits & Reports
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/v1/audits` | Search audit logs |
| GET | `/v1/audits/{id}` | Get audit entry |
| GET | `/v1/reports` | List available reports |
| GET | `/v1/reports/{name}` | Run report |

## Security

- HTTP Basic authentication is required for all endpoints except health checks and Swagger
- Default users for development:
  - `admin:admin123` (ADMIN, USER roles)
  - `user:user123` (USER role)
  - `system:system123` (SYSTEM, ADMIN, USER roles)

## Circuit Breaker

Resilience4j circuit breaker is configured for all Fineract API calls:

- **Sliding window size**: 10 calls
- **Failure rate threshold**: 50%
- **Wait duration in open state**: 10 seconds
- **Automatic transition**: Enabled

Monitor circuit breaker status: `GET /actuator/circuitbreakers`

## Health Checks

- **Health endpoint**: `GET /actuator/health`
- **Metrics**: `GET /actuator/metrics`
- **Info**: `GET /actuator/info`

## Development

### Code Style
- Follow standard Java conventions
- Use Lombok annotations for boilerplate
- Document public APIs with Javadoc

### Adding New Endpoints
1. Create DTO in `dto/request/` and `dto/response/`
2. Add client method in appropriate `*Client.java`
3. Add service method in appropriate `*Service.java`
4. Add controller endpoint in appropriate `*Controller.java`
5. Write unit tests

## License

MIT License - See LICENSE file for details.

## Support

For issues or questions, contact the FYP team at fyp@giki.edu.pk
