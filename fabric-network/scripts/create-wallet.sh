#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NETWORK_DIR="$(dirname "$SCRIPT_DIR")"

USER_MSP_DIR="${NETWORK_DIR}/organizations/peerOrganizations/bank.example.com/users/Admin@bank.example.com/msp"
CERT_FILE="$(ls "${USER_MSP_DIR}/signcerts/"*.pem 2>/dev/null | head -n 1)"
KEY_FILE="$(ls "${USER_MSP_DIR}/keystore/"* 2>/dev/null | head -n 1)"
WALLET_DIR="${NETWORK_DIR}/wallet"
WALLET_FILE="${WALLET_DIR}/admin.id"

if [[ -z "${CERT_FILE:-}" || -z "${KEY_FILE:-}" ]]; then
  echo "[ERROR] Could not find admin cert/key. Run fabric-network/scripts/network.sh up first."
  exit 1
fi

mkdir -p "${WALLET_DIR}"

CERT_ESCAPED="$(awk '{printf "%s\\n", $0}' "${CERT_FILE}" | sed 's/"/\\"/g')"
KEY_ESCAPED="$(awk '{printf "%s\\n", $0}' "${KEY_FILE}" | sed 's/"/\\"/g')"

cat > "${WALLET_FILE}" <<EOF
{
  "version": 1,
  "mspId": "BankMSP",
  "type": "X.509",
  "credentials": {
    "certificate": "${CERT_ESCAPED}",
    "privateKey": "${KEY_ESCAPED}"
  }
}
EOF

echo "[INFO] Wallet identity created at: ${WALLET_FILE}"
