# Fintech API for Core Banking Security

Production-oriented banking security platform inspired by ecosystem patterns seen in initiatives like Open Bank Project and Apache Fineract: composable microservices, auditable transaction workflows, and standards-aligned identity and authorization.

## Overview

This repository delivers a secure core-banking extension stack with:

- API-driven banking operations through a Core Banking Connector integrated with Apache Fineract.
- Centralized identity, OAuth2/OIDC token validation, and role-based controls.
- KYC/AML orchestration with encrypted PII and DID issuance.
- Transaction processing with fraud scoring and maker-checker controls.
- Immutable audit anchoring to Hyperledger Fabric (with CouchDB world state).
- Operational dashboards for Super Admin, Compliance, Manager, and Loan Officer personas.

## Architecture

Primary modules:

- `api-gateway` - edge routing, policy enforcement, request mediation.
- `auth-service` - identity lifecycle, MFA, and authorization support.
- `core-banking-connector` - integration facade over Apache Fineract APIs.
- `transaction-service` - transaction orchestration, validations, approvals.
- `fraud-detection-service` - anomaly scoring, alerting, threshold management.
- `audit-service` - event integrity, report export, blockchain anchoring.
- `kyc-aml-service` - onboarding, KYC status, AML checks, DID workflows.
- `frontend` - React operational console and role-aware dashboards.
- `fabric-network` - Hyperledger Fabric network and chaincode assets.
- `ml-fraud-model` - model-serving component for fraud-risk predictions.

## Technology Stack

- Java 17 / Spring Boot microservices
- React + Vite frontend
- MySQL + Redis
- Keycloak (OIDC / OAuth2)
- Apache Fineract integration
- Hyperledger Fabric + CouchDB
- Docker Compose for local orchestration

## Getting Started

### Prerequisites

- Docker Desktop with Docker Compose
- Java 17+
- Maven 3.9+
- Node.js 18+ (for standalone frontend workflows)
- Hyperledger Fabric binaries (optional for local Fabric scripts): see `fabric-network/bin/README.md`

### Local Deployment

1. Create local environment file:
   - Copy `.env.example` to `.env`
   - Replace all `CHANGE_ME_*` placeholders
2. Start the platform:
   - `docker compose up -d --build`
3. Access endpoints:
   - Frontend: `http://localhost:3001`
   - API Gateway: `http://localhost:8080`
   - Keycloak: `http://localhost:8090`

## Security and Configuration

- Commit only templates; keep runtime secrets in local `.env`.
- `.gitignore` excludes environment secrets, keys, certificates, and local-only documentation assets.
- mTLS, TLS, and Fabric configuration values are centrally documented in `.env.example`.

## API and Operations

- Application-level API catalog is available in the frontend at `/api-docs`.
- Core operational domains:
  - Customer onboarding and KYC/AML
  - Deposits, withdrawals, transfers, and loan actions
  - Fraud alerts, investigation, and threshold administration
  - Audit exports and blockchain integrity verification

## Repository Conventions

- Use feature branches and pull requests for all production changes.
- Configuration templates are versioned; environment-specific secrets are not.
- Domain services are independently buildable and deployable.
