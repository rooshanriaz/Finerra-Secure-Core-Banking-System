# Production Runbook

## 1) Secrets and Environment

- Copy `.env.production.template` to `.env`.
- Replace every `CHANGE_ME_*` value with strong secrets.
- Keep `FABRIC_ENABLED=true` only when Fabric network and wallet are mounted.

## 2) Start Standard Production-Like Stack

```bash
docker compose up -d --build
```

## 3) Start Strict TLS/mTLS Mode

Use strict overlay only after keystores/truststore exist under `certs/`.

```bash
docker compose -f docker-compose.yml -f docker-compose.secure.yml up -d --build
```

## 4) Verify Runtime Health

```bash
docker compose ps
powershell -ExecutionPolicy Bypass -File .\scripts\production-smoke-test.ps1
```

Expected:
- Gateway, Auth, CBC, KYC, Transaction, Audit, Fraud all `Up`.
- OIDC discovery and JWKS endpoints return 200.
- Compliance export returns 200.

## 5) Fabric Verification

- Ensure `fabric-network` is up and chaincodes are deployed.
- Run existing Fabric test flow from `New_Testing_Guide.md` Phase 11.

## 6) Rollback

```bash
docker compose down
docker compose up -d
```

For strict-mode rollback, remove secure override:

```bash
docker compose -f docker-compose.yml up -d
```
