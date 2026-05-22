# Fabric binaries (not in Git)

Windows/Linux Fabric CLI binaries are large (~300 MB) and are excluded from this repository.

After cloning, install them once from the `fabric-network` directory:

```bash
curl -sSLO https://raw.githubusercontent.com/hyperledger/fabric/main/scripts/install-fabric.sh
chmod +x install-fabric.sh
./install-fabric.sh binary 2.5.0
```

Or run `./scripts/network.sh up` — it downloads binaries automatically when `cryptogen` is missing.
