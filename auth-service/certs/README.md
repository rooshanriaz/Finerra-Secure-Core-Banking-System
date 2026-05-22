# mTLS Certificates

This directory contains certificates for mutual TLS (mTLS) between services.

## Quick Start (Windows PowerShell)

If you have OpenSSL installed (e.g., via Git for Windows):

```powershell
# Navigate to this directory
cd c:\Users\rooshan\Documents\GIKI\FYP\3-Implementation\auth-service\certs

# Generate CA
openssl genrsa -out ca.key 2048
openssl req -new -x509 -days 365 -key ca.key -out ca.crt -subj "/CN=FYP Banking CA"

# Generate Auth Service certificate
openssl genrsa -out auth-service.key 2048
openssl req -new -key auth-service.key -out auth-service.csr -subj "/CN=auth-service"
openssl x509 -req -days 365 -in auth-service.csr -CA ca.crt -CAkey ca.key -CAcreateserial -out auth-service.crt
openssl pkcs12 -export -in auth-service.crt -inkey auth-service.key -out auth-service.p12 -name auth-service -password pass:changeit

# Generate Gateway certificate
openssl genrsa -out gateway.key 2048
openssl req -new -key gateway.key -out gateway.csr -subj "/CN=api-gateway"
openssl x509 -req -days 365 -in gateway.csr -CA ca.crt -CAkey ca.key -CAcreateserial -out gateway.crt
openssl pkcs12 -export -in gateway.crt -inkey gateway.key -out gateway.p12 -name gateway -password pass:changeit
```

## Files

| File | Description |
|------|-------------|
| `ca.crt` | Certificate Authority public certificate |
| `ca.key` | Certificate Authority private key (keep secret!) |
| `auth-service.p12` | Auth Service PKCS12 keystore |
| `gateway.p12` | API Gateway PKCS12 keystore |

## Usage in Spring Boot

### Auth Service (application-prod.yml)

```yaml
server:
  ssl:
    enabled: true
    key-store: classpath:certs/auth-service.p12
    key-store-password: changeit
    key-store-type: PKCS12
    trust-store: classpath:certs/ca.crt
    trust-store-password: changeit
    client-auth: need  # Require client certificate
```

### API Gateway (application-prod.yml)

```yaml
spring:
  cloud:
    gateway:
      httpclient:
        ssl:
          key-store: classpath:certs/gateway.p12
          key-store-password: changeit
          trust-store: classpath:certs/ca.crt
```

## Security Notes

1. **Never commit private keys to Git** - Add `*.key` and `*.p12` to `.gitignore`
2. **Change default password** - Replace `changeit` with a secure password in production
3. **Rotate certificates** - Regenerate before expiration (365 days default)
4. **Use proper CA** - In production, use certificates from a trusted CA
