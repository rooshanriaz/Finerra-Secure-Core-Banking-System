#!/bin/bash
# =============================================================================
# Certificate Generation Script for mTLS
# =============================================================================
# This script generates self-signed certificates for mutual TLS between
# the API Gateway and Auth Service.
#
# Requirements:
# - OpenSSL installed
# - Run from the certs/ directory
#
# For Windows, use Git Bash or WSL to run this script.
# =============================================================================

set -e

# Configuration
CA_DAYS=365
CERT_DAYS=365
KEY_SIZE=2048
COUNTRY="PK"
STATE="Punjab"
LOCALITY="Topi"
ORG="FYP Banking"
OU="Security"

echo "=========================================="
echo "Generating mTLS Certificates"
echo "=========================================="

# =============================================================================
# Step 1: Generate Certificate Authority (CA)
# =============================================================================
echo "Step 1: Generating CA certificate..."

openssl genrsa -out ca.key $KEY_SIZE

openssl req -new -x509 -days $CA_DAYS -key ca.key -out ca.crt \
    -subj "/C=$COUNTRY/ST=$STATE/L=$LOCALITY/O=$ORG/OU=$OU/CN=FYP Banking CA"

echo "CA certificate generated: ca.crt"

# =============================================================================
# Step 2: Generate Auth Service Certificate
# =============================================================================
echo "Step 2: Generating auth-service certificate..."

# Generate private key
openssl genrsa -out auth-service.key $KEY_SIZE

# Generate CSR
openssl req -new -key auth-service.key -out auth-service.csr \
    -subj "/C=$COUNTRY/ST=$STATE/L=$LOCALITY/O=$ORG/OU=$OU/CN=auth-service"

# Sign with CA
openssl x509 -req -days $CERT_DAYS -in auth-service.csr \
    -CA ca.crt -CAkey ca.key -CAcreateserial -out auth-service.crt

# Create PKCS12 keystore
openssl pkcs12 -export -in auth-service.crt -inkey auth-service.key \
    -out auth-service.p12 -name auth-service -password pass:changeit

# Create truststore with CA
keytool -importcert -alias ca -file ca.crt -keystore auth-service-truststore.p12 \
    -storetype PKCS12 -storepass changeit -noprompt 2>/dev/null || \
    openssl pkcs12 -export -nokeys -in ca.crt -out auth-service-truststore.p12 \
        -password pass:changeit -name ca

echo "Auth service certificates generated"

# =============================================================================
# Step 3: Generate API Gateway Certificate
# =============================================================================
echo "Step 3: Generating api-gateway certificate..."

# Generate private key
openssl genrsa -out gateway.key $KEY_SIZE

# Generate CSR
openssl req -new -key gateway.key -out gateway.csr \
    -subj "/C=$COUNTRY/ST=$STATE/L=$LOCALITY/O=$ORG/OU=$OU/CN=api-gateway"

# Sign with CA
openssl x509 -req -days $CERT_DAYS -in gateway.csr \
    -CA ca.crt -CAkey ca.key -CAcreateserial -out gateway.crt

# Create PKCS12 keystore
openssl pkcs12 -export -in gateway.crt -inkey gateway.key \
    -out gateway.p12 -name gateway -password pass:changeit

echo "Gateway certificates generated"

# =============================================================================
# Step 4: Cleanup
# =============================================================================
echo "Step 4: Cleaning up CSR files..."
rm -f *.csr
rm -f ca.srl

echo ""
echo "=========================================="
echo "Certificate Generation Complete!"
echo "=========================================="
echo ""
echo "Generated files:"
echo "  - ca.crt, ca.key          : Certificate Authority"
echo "  - auth-service.p12        : Auth Service keystore"
echo "  - auth-service-truststore.p12 : Auth Service truststore"
echo "  - gateway.p12             : Gateway keystore"
echo ""
echo "Default password for all keystores: changeit"
echo ""
echo "To use in Spring Boot, add to application.yml:"
echo ""
echo "  server:"
echo "    ssl:"
echo "      enabled: true"
echo "      key-store: classpath:certs/auth-service.p12"
echo "      key-store-password: changeit"
echo "      key-store-type: PKCS12"
echo "      trust-store: classpath:certs/auth-service-truststore.p12"
echo "      trust-store-password: changeit"
echo "      client-auth: need"
echo ""
