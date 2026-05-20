# Cloudflare Persistent Link (cf-p-link)

A self-healing, native-compiled Java toolchain that automates the deployment of persistent Cloudflare Tunnels and reverse-proxying Cloudflare Workers for multiple local services. It provides a seamless, non-interactive setup experience by automatically fetching your Cloudflare Account ID using an API Token and ensures the software runs as an efficient native binary with minimal configuration requirements.

## Components

The project consists of two binaries compiled as native executables (using GraalVM):
1. **`setup-cli-native`**: A CLI tool to configure the initial setup, Cloudflare API tokens, and local service mappings.
2. **`tunnel-daemon-native`**: A long-running daemon that spawns and monitors `cloudflared` tunnels and automatically updates the Cloudflare Worker when tunnels change or reconnect.

## Building

To build the native binaries, you will need Maven and GraalVM with native-image capabilities installed:

```bash
mvn clean package -Pnative
```

The resulting executables will be available in the `target/` directory:
- `target/setup-cli-native`
- `target/tunnel-daemon-native`

## System-Wide Installation & Setup (Recommended)

For a persistent and standardized setup on Linux, it is highly recommended to move the binaries to `/usr/local/bin` and store configuration in `/opt/cf-p-link`. This ensures the daemon is not accidentally deleted during development or cleanup.

1. **Copy Binaries to standard execution path:**
   ```bash
   sudo cp target/setup-cli-native /usr/local/bin/
   sudo cp target/tunnel-daemon-native /usr/local/bin/
   ```

2. **Create the configuration directory:**
   ```bash
   sudo mkdir -p /opt/cf-p-link
   ```

3. **Run the setup CLI tool from the configuration directory:**
   ```bash
   cd /opt/cf-p-link
   sudo setup-cli-native
   ```
   Follow the interactive prompts to provide your Cloudflare API Token. Once configured, a `config.json` file will be created in `/opt/cf-p-link`.

4. **Install and enable the Systemd service:**
   A `cf-p-link.service` file is provided in the repository root (already configured to use `/usr/local/bin` and `/opt/cf-p-link`).
   
   ```bash
   sudo cp cf-p-link.service /etc/systemd/system/
   sudo systemctl daemon-reload
   sudo systemctl enable --now cf-p-link.service
   ```

5. **Check the status and logs:**
   ```bash
   sudo systemctl status cf-p-link.service
   sudo journalctl -u cf-p-link.service -f
   ```

## Manual Execution (Without Systemd)

If you prefer testing before installing as a service, you can run the binaries directly from your project folder:

1. Configure your services first:
   ```bash
   ./target/setup-cli-native
   ```
2. Start the daemon directly:
   ```bash
   ./target/tunnel-daemon-native
   ```

## Configuration File

The `config.json` file stores all necessary information. If you ever need to change the Cloudflare API Token, Account ID, or service mappings, you can manually edit `config.json` (located at `/opt/cf-p-link/config.json` if installed system-wide) or re-run the `setup-cli-native` tool in that directory.
