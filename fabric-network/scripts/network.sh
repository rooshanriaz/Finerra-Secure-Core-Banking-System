#!/bin/bash
# =============================================================================
# Fabric Network Management Script
# FYP Banking System - Full Network with Audit + DID Chaincode
# =============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NETWORK_DIR="$(dirname "$SCRIPT_DIR")"
CHANNEL_NAME="banking-channel"

# Prevent Git Bash (MSYS) from rewriting Linux-style paths passed to docker exec
# (e.g. /opt/gopath/... -> C:/Program Files/Git/opt/gopath/...).
if [[ "${OSTYPE:-}" == msys* || "${OSTYPE:-}" == cygwin* || "${OSTYPE:-}" == win32* ]]; then
    export MSYS_NO_PATHCONV=1
    export MSYS2_ARG_CONV_EXCL="*"
fi

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Log to stderr so stdout stays clean for $(command) captures (e.g. query_next_cc_sequence).
log_info() { echo -e "${GREEN}[INFO]${NC} $1" >&2; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1" >&2; }
log_error() { echo -e "${RED}[ERROR]${NC} $1" >&2; }
log_step() { echo -e "${BLUE}[STEP]${NC} $1" >&2; }

normalize_msp_config_paths() {
    # cryptogen on Windows may write backslashes in MSP config.yaml paths (e.g. cacerts\ca....pem),
    # but Fabric containers expect POSIX-style separators.
    local root="${NETWORK_DIR}/organizations"
    if [ ! -d "$root" ]; then
        return
    fi

    while IFS= read -r -d '' cfg; do
        # Replace backslash with forward slash in place.
        sed -i 's#\\#/#g' "$cfg"
    done < <(find "$root" -type f -name "config.yaml" -print0 2>/dev/null || true)
}

check_prerequisites() {
    log_step "Checking prerequisites..."
    local missing=0

    if ! command -v docker &> /dev/null; then
        log_error "Docker is not installed"
        missing=1
    fi

    if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
        log_error "Docker Compose is not installed"
        missing=1
    fi

    if [ $missing -eq 1 ]; then
        exit 1
    fi

    log_info "Prerequisites satisfied"
}

generate_crypto() {
    log_step "Generating crypto materials..."

    mkdir -p "${NETWORK_DIR}/organizations/ordererOrganizations"
    mkdir -p "${NETWORK_DIR}/organizations/peerOrganizations"
    mkdir -p "${NETWORK_DIR}/channel-artifacts"

    # Reuse previously downloaded Fabric binaries first (prevents re-downloading and config overwrite).
    if [ -x "${NETWORK_DIR}/bin/cryptogen" ]; then
        export PATH="${NETWORK_DIR}/bin:${PATH}"
    fi

    local crypto_cfg="${NETWORK_DIR}/config/crypto-config.yaml"
    local org_out="${NETWORK_DIR}/organizations"
    # Windows .exe binaries need Windows-style paths under Git Bash.
    if [[ "${OSTYPE:-}" == msys* || "${OSTYPE:-}" == cygwin* || "${OSTYPE:-}" == win32* ]]; then
        if command -v cygpath >/dev/null 2>&1; then
            crypto_cfg="$(cygpath -w "${crypto_cfg}")"
            org_out="$(cygpath -w "${org_out}")"
        fi
    fi

    if command -v cryptogen &> /dev/null; then
        cryptogen generate --config="${crypto_cfg}" \
            --output="${org_out}"
        normalize_msp_config_paths
        log_info "Crypto materials generated with cryptogen"
    else
        log_warn "cryptogen not found — downloading Fabric binaries..."
        cd "${NETWORK_DIR}"
        cp "${NETWORK_DIR}/config/configtx.yaml" "${NETWORK_DIR}/config/configtx.yaml.fyp.bak"
        curl -sSLO https://raw.githubusercontent.com/hyperledger/fabric/main/scripts/install-fabric.sh
        chmod +x install-fabric.sh
        ./install-fabric.sh binary 2.5.0
        if [ -f "${NETWORK_DIR}/config/configtx.yaml.fyp.bak" ]; then
            mv -f "${NETWORK_DIR}/config/configtx.yaml.fyp.bak" "${NETWORK_DIR}/config/configtx.yaml"
        fi
        export PATH="${NETWORK_DIR}/bin:$PATH"
        cryptogen generate --config="${crypto_cfg}" \
            --output="${org_out}"
        normalize_msp_config_paths
        log_info "Crypto materials generated"
    fi
}

generate_channel_artifacts() {
    log_step "Generating channel artifacts..."

    local cfg_path="${NETWORK_DIR}/config"
    if [[ "${OSTYPE:-}" == msys* || "${OSTYPE:-}" == cygwin* || "${OSTYPE:-}" == win32* ]]; then
        if command -v cygpath >/dev/null 2>&1; then
            cfg_path="$(cygpath -w "${cfg_path}")"
        fi
    fi
    export FABRIC_CFG_PATH="${cfg_path}"
    # Prefer Fabric binaries unpacked into fabric-network/bin (install-fabric.sh)
    if [ -d "${NETWORK_DIR}/bin" ]; then
        export PATH="${NETWORK_DIR}/bin:${PATH}"
    fi

    local genesis_out="${NETWORK_DIR}/channel-artifacts/genesis.block"
    if [[ "${OSTYPE:-}" == msys* || "${OSTYPE:-}" == cygwin* || "${OSTYPE:-}" == win32* ]]; then
        if command -v cygpath >/dev/null 2>&1; then
            genesis_out="$(cygpath -w "${genesis_out}")"
        fi
    fi

    if command -v configtxgen &> /dev/null; then
        configtxgen -profile TwoOrgsApplicationGenesis \
            -outputBlock "${genesis_out}" \
            -channelID "${CHANNEL_NAME}"
        log_info "Genesis block generated for channel: ${CHANNEL_NAME}"
    else
        log_error "configtxgen not found. Cannot generate channel artifacts."
        exit 1
    fi
}

network_up() {
    log_step "Starting Fabric network..."
    check_prerequisites

    cd "${NETWORK_DIR}"

    if [ ! -d "${NETWORK_DIR}/organizations/peerOrganizations" ] || \
       [ -z "$(ls -A ${NETWORK_DIR}/organizations/peerOrganizations 2>/dev/null)" ]; then
        generate_crypto
        generate_channel_artifacts
    fi

    # Ensure existing crypto generated on Windows has POSIX separators in MSP config files.
    normalize_msp_config_paths

    # If cryptogen succeeded earlier but configtxgen failed, artifacts may be missing — regenerate.
    if [ ! -f "${NETWORK_DIR}/channel-artifacts/genesis.block" ]; then
        mkdir -p "${NETWORK_DIR}/channel-artifacts"
        generate_channel_artifacts
    fi

    docker-compose up -d

    log_info "Waiting for network to stabilize (10s)..."
    sleep 10

    log_info "Joining orderer to channel via osnadmin: ${CHANNEL_NAME}"
    docker exec fabric-cli osnadmin channel join \
        --channelID ${CHANNEL_NAME} \
        --config-block /opt/gopath/src/github.com/hyperledger/fabric/peer/channel-artifacts/genesis.block \
        -o orderer.example.com:7053 \
        --ca-file /opt/gopath/src/github.com/hyperledger/fabric/peer/organizations/ordererOrganizations/example.com/orderers/orderer.example.com/msp/tlscacerts/tlsca.example.com-cert.pem \
        --client-cert /opt/gopath/src/github.com/hyperledger/fabric/peer/organizations/ordererOrganizations/example.com/users/Admin@example.com/tls/client.crt \
        --client-key /opt/gopath/src/github.com/hyperledger/fabric/peer/organizations/ordererOrganizations/example.com/users/Admin@example.com/tls/client.key \
        2>/dev/null || log_warn "Orderer may already be joined to channel"

    log_info "Fetching channel block: ${CHANNEL_NAME}"
    docker exec fabric-cli peer channel fetch 0 ${CHANNEL_NAME}.block \
        -o orderer.example.com:7050 \
        -c ${CHANNEL_NAME} \
        --tls --cafile /opt/gopath/src/github.com/hyperledger/fabric/peer/organizations/ordererOrganizations/example.com/orderers/orderer.example.com/msp/tlscacerts/tlsca.example.com-cert.pem \
        2>/dev/null || log_warn "Channel block may already exist"

    log_info "Joining Bank peer to channel..."
    docker exec fabric-cli peer channel join \
        -b ${CHANNEL_NAME}.block \
        2>/dev/null || log_warn "Bank peer may already be joined"

    log_info "Joining Regulator peer to channel..."
    docker exec -e CORE_PEER_ADDRESS=peer0.regulator.example.com:9051 \
        -e CORE_PEER_LOCALMSPID=RegulatorMSP \
        -e CORE_PEER_TLS_ROOTCERT_FILE=/opt/gopath/src/github.com/hyperledger/fabric/peer/organizations/peerOrganizations/regulator.example.com/peers/peer0.regulator.example.com/tls/ca.crt \
        -e CORE_PEER_MSPCONFIGPATH=/opt/gopath/src/github.com/hyperledger/fabric/peer/organizations/peerOrganizations/regulator.example.com/users/Admin@regulator.example.com/msp \
        fabric-cli peer channel join -b ${CHANNEL_NAME}.block \
        2>/dev/null || log_warn "Regulator peer may already be joined"

    log_info "Fabric network is up!"
    docker-compose ps
}

# Orderer TLS CA (inside fabric-cli container paths)
ORDERER_TLS_CA="/opt/gopath/src/github.com/hyperledger/fabric/peer/organizations/ordererOrganizations/example.com/orderers/orderer.example.com/msp/tlscacerts/tlsca.example.com-cert.pem"

# Next lifecycle sequence for this chaincode on the channel (1 if never committed).
query_next_cc_sequence() {
    local cc_name=$1
    local out
    out=$(docker exec fabric-cli peer lifecycle chaincode querycommitted \
        -o orderer.example.com:7050 \
        --channelID "${CHANNEL_NAME}" \
        --name "${cc_name}" \
        --tls --cafile "${ORDERER_TLS_CA}" \
        2>/dev/null) || true

    if echo "${out}" | grep -qE 'Sequence:[[:space:]]*[0-9]+'; then
        local cur
        cur=$(echo "${out}" | grep -oE 'Sequence:[[:space:]]*[0-9]+' | head -1 | grep -oE '[0-9]+' | tail -1)
        log_info "Committed definition for ${cc_name} has sequence ${cur}; using sequence $((cur + 1)) for this upgrade"
        echo $((cur + 1))
    else
        log_info "No committed definition for ${cc_name} on ${CHANNEL_NAME}; using sequence 1"
        echo 1
    fi
}

deploy_chaincode() {
    local CC_NAME=$1
    local CC_PATH=$2
    local CC_VERSION=${3:-1.0}
    # 4th arg: explicit sequence number, or "auto" (default) to query channel and use next sequence
    local CC_SEQ_ARG=${4:-auto}
    local CC_SEQUENCE
    if [ "${CC_SEQ_ARG}" = "auto" ] || [ -z "${CC_SEQ_ARG}" ]; then
        CC_SEQUENCE=$(query_next_cc_sequence "${CC_NAME}")
    else
        CC_SEQUENCE="${CC_SEQ_ARG}"
    fi
    local CC_LABEL="${CC_NAME}_${CC_VERSION}"
    local ENDORSEMENT_POLICY="OR('BankMSP.member')"

    log_step "Deploying chaincode: ${CC_NAME} v${CC_VERSION} (sequence ${CC_SEQUENCE})"

    log_info "Building chaincode JAR..."
    cd "${NETWORK_DIR}/chaincode/${CC_NAME}"
    if command -v gradle &> /dev/null; then
        gradle shadowJar
    elif [ -f gradlew ]; then
        chmod +x gradlew && ./gradlew shadowJar
    else
        log_error "Gradle not found and no wrapper present"
        return 1
    fi
    cd "${NETWORK_DIR}"

    log_info "Packaging chaincode..."
    docker exec fabric-cli peer lifecycle chaincode package ${CC_NAME}.tar.gz \
        --path /opt/gopath/src/github.com/hyperledger/fabric/peer/chaincode/${CC_NAME} \
        --lang java \
        --label ${CC_LABEL}

    # Capture package ID from THIS install — do not use queryinstalled + grep (stale duplicates
    # from prior runs produce a wrong hash and approveformyorg fails with ENDORSEMENT_POLICY_FAILURE).
    log_info "Installing on Bank peer..."
    INSTALL_BANK_OUT=$(docker exec fabric-cli peer lifecycle chaincode install ${CC_NAME}.tar.gz 2>&1)
    echo "${INSTALL_BANK_OUT}"
    PACKAGE_ID=$(echo "${INSTALL_BANK_OUT}" | grep 'Chaincode code package identifier:' | sed 's/.*Chaincode code package identifier:[[:space:]]*//' | tr -d '\r' | awk '{print $1}' | tail -1)
    if [ -z "${PACKAGE_ID}" ]; then
        log_error "Could not parse package ID from install output. Expected line: Chaincode code package identifier: ..."
        return 1
    fi
    log_info "Package ID (from Bank install): ${PACKAGE_ID}"

    log_info "Installing on Regulator peer..."
    docker exec -e CORE_PEER_ADDRESS=peer0.regulator.example.com:9051 \
        -e CORE_PEER_LOCALMSPID=RegulatorMSP \
        -e CORE_PEER_TLS_ROOTCERT_FILE=/opt/gopath/src/github.com/hyperledger/fabric/peer/organizations/peerOrganizations/regulator.example.com/peers/peer0.regulator.example.com/tls/ca.crt \
        -e CORE_PEER_MSPCONFIGPATH=/opt/gopath/src/github.com/hyperledger/fabric/peer/organizations/peerOrganizations/regulator.example.com/users/Admin@regulator.example.com/msp \
        fabric-cli peer lifecycle chaincode install ${CC_NAME}.tar.gz

    log_info "Approving for Bank..."
    docker exec fabric-cli peer lifecycle chaincode approveformyorg \
        -o orderer.example.com:7050 \
        --channelID ${CHANNEL_NAME} \
        --name ${CC_NAME} \
        --version ${CC_VERSION} \
        --package-id ${PACKAGE_ID} \
        --sequence ${CC_SEQUENCE} \
        --signature-policy "${ENDORSEMENT_POLICY}" \
        --tls --cafile "${ORDERER_TLS_CA}"

    log_info "Approving for Regulator..."
    docker exec -e CORE_PEER_ADDRESS=peer0.regulator.example.com:9051 \
        -e CORE_PEER_LOCALMSPID=RegulatorMSP \
        -e CORE_PEER_TLS_ROOTCERT_FILE=/opt/gopath/src/github.com/hyperledger/fabric/peer/organizations/peerOrganizations/regulator.example.com/peers/peer0.regulator.example.com/tls/ca.crt \
        -e CORE_PEER_MSPCONFIGPATH=/opt/gopath/src/github.com/hyperledger/fabric/peer/organizations/peerOrganizations/regulator.example.com/users/Admin@regulator.example.com/msp \
        fabric-cli peer lifecycle chaincode approveformyorg \
        -o orderer.example.com:7050 \
        --channelID ${CHANNEL_NAME} \
        --name ${CC_NAME} \
        --version ${CC_VERSION} \
        --package-id ${PACKAGE_ID} \
        --sequence ${CC_SEQUENCE} \
        --signature-policy "${ENDORSEMENT_POLICY}" \
        --tls --cafile "${ORDERER_TLS_CA}"

    log_info "Committing chaincode definition..."
    docker exec fabric-cli peer lifecycle chaincode commit \
        -o orderer.example.com:7050 \
        --channelID ${CHANNEL_NAME} \
        --name ${CC_NAME} \
        --version ${CC_VERSION} \
        --sequence ${CC_SEQUENCE} \
        --signature-policy "${ENDORSEMENT_POLICY}" \
        --tls --cafile "${ORDERER_TLS_CA}" \
        --peerAddresses peer0.bank.example.com:7051 \
        --tlsRootCertFiles /opt/gopath/src/github.com/hyperledger/fabric/peer/organizations/peerOrganizations/bank.example.com/peers/peer0.bank.example.com/tls/ca.crt \
        --peerAddresses peer0.regulator.example.com:9051 \
        --tlsRootCertFiles /opt/gopath/src/github.com/hyperledger/fabric/peer/organizations/peerOrganizations/regulator.example.com/peers/peer0.regulator.example.com/tls/ca.crt

    log_info "Initializing ledger (InitLedger)..."
    if docker exec fabric-cli peer chaincode invoke \
        -o orderer.example.com:7050 \
        --channelID ${CHANNEL_NAME} \
        -n ${CC_NAME} \
        -c '{"function":"InitLedger","Args":[]}' \
        --tls --cafile "${ORDERER_TLS_CA}" \
        --peerAddresses peer0.bank.example.com:7051 \
        --tlsRootCertFiles /opt/gopath/src/github.com/hyperledger/fabric/peer/organizations/peerOrganizations/bank.example.com/peers/peer0.bank.example.com/tls/ca.crt
    then
        log_info "InitLedger completed for ${CC_NAME}"
    else
        log_warn "InitLedger failed or was skipped (common on sequence>1 if state already exists); verify chaincode manually if needed"
    fi

    log_info "Chaincode ${CC_NAME} deployed (sequence ${CC_SEQUENCE})!"
}

deploy_all() {
    log_step "Deploying all chaincode contracts..."
    # Omit sequence → "auto": query channel and use next sequence (fixes redeploy after partial commit).
    deploy_chaincode "audit-contract" "chaincode/audit-contract" "1.0"
    deploy_chaincode "did-contract" "chaincode/did-contract" "1.0"
    log_info "All chaincode contracts deployed!"
}

network_down() {
    log_step "Stopping Fabric network..."
    cd "${NETWORK_DIR}"
    docker-compose down -v --remove-orphans
    log_info "Fabric network is down"
}

network_clean() {
    log_step "Cleaning up network artifacts..."
    network_down 2>/dev/null || true

    rm -rf "${NETWORK_DIR}/organizations/ordererOrganizations"
    rm -rf "${NETWORK_DIR}/organizations/peerOrganizations"
    rm -rf "${NETWORK_DIR}/channel-artifacts"
    rm -f "${NETWORK_DIR}/install-fabric.sh"

    docker images -q "dev-peer*" 2>/dev/null | xargs -r docker rmi -f
    log_info "Clean up complete"
}

network_status() {
    log_step "Network Status:"
    cd "${NETWORK_DIR}"
    docker-compose ps
}

case "$1" in
    up)
        network_up
        ;;
    down)
        network_down
        ;;
    clean)
        network_clean
        ;;
    deploy-all)
        deploy_all
        ;;
    deploy)
        if [ -z "$2" ]; then
            echo "Usage: $0 deploy <chaincode-name>"
            exit 1
        fi
        deploy_chaincode "$2" "chaincode/$2"
        ;;
    status)
        network_status
        ;;
    restart)
        network_down
        network_up
        ;;
    *)
        echo "Fabric Network Management"
        echo ""
        echo "Usage: $0 {up|down|clean|deploy-all|deploy <name>|status|restart}"
        echo ""
        echo "Commands:"
        echo "  up          Start the Fabric network + create/join channel"
        echo "  down        Stop the Fabric network"
        echo "  clean       Stop and remove all artifacts"
        echo "  deploy-all  Deploy both audit-contract and did-contract (auto lifecycle sequence)"
        echo "  deploy <n>  Deploy one chaincode (v1.0, auto sequence from channel state)"
        echo "  status      Show network status"
        echo "  restart     Stop and restart the network"
        exit 1
        ;;
esac
