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

## Setup

Before running the daemon, you need to configure your Cloudflare credentials and map your services.

1. Run the setup CLI tool:
   ```bash
   ./target/setup-cli-native
   ```
2. Follow the interactive prompts to provide your Cloudflare API Token. The tool will automatically fetch your Account ID and configure the routes.
3. Once configured, a `config.json` file will be created in the current directory.

## Running the Daemon

You can run the daemon directly:

```bash
./target/tunnel-daemon-native
```

However, for a persistent setup, it is highly recommended to run it as a systemd service so it automatically starts on boot and restarts on failure.

## Systemd Service Installation

A `cf-p-link.service` file has been provided in the root directory (or can be generated automatically by running `./target/tunnel-daemon-native --install-systemd`).

To install and enable the systemd service, follow these steps:

1. Copy the service file to the systemd directory:
   ```bash
   sudo cp cf-p-link.service /etc/systemd/system/
   ```

2. Reload the systemd daemon to recognize the new service:
   ```bash
   sudo systemctl daemon-reload
   ```

3. Enable the service to start automatically on boot and start it immediately:
   ```bash
   sudo systemctl enable --now cf-p-link.service
   ```

4. Check the status of the service to ensure it is running correctly:
   ```bash
   sudo systemctl status cf-p-link.service
   ```

5. To view the logs, use `journalctl`:
   ```bash
   sudo journalctl -u cf-p-link.service -f
   ```

## Configuration File

The `config.json` file stores all necessary information. If you ever need to change the Cloudflare API Token, Account ID, or service mappings, you can manually edit `config.json` or re-run the `setup-cli-native` tool.
