import java.io.*;
import java.time.LocalDate;

public class workerDeployer {

  private static final String API_FILE = "cf_api_token.txt";
  private static final String WORKER_FILE = "worker_name.txt";
  private static final String TUNNEL_FILE = "latest_tunnel.txt";

  public static void main(String[] args) throws IOException, InterruptedException {
    String apiToken = readFirstLine(API_FILE);
    String workerName = readFirstLine(WORKER_FILE);
    String quickTunnelUrl = readFirstLine(TUNNEL_FILE);

    if (apiToken == null || workerName == null || quickTunnelUrl == null) {
      System.err.println("!!! Missing required files or values.");
      System.exit(1);
    }

    File projectDir = new File(workerName);
    if (!projectDir.exists())
      projectDir.mkdirs();

    // Create index.js
    File jsFile = new File(projectDir, "index.js");
    try (PrintWriter out = new PrintWriter(jsFile)) {
      out.println("// Auto-generated Cloudflare Worker");
      out.println("const quickTunnelUrl = \"" + quickTunnelUrl + "\";");
      out.println("addEventListener('fetch', event => event.respondWith(Response.redirect(quickTunnelUrl, 302)));");
    }

    // Create wrangler.toml
    File tomlFile = new File(projectDir, "wrangler.toml");
    try (PrintWriter out = new PrintWriter(tomlFile)) {
      out.println("name = \"" + workerName + "\"");
      out.println("main = \"index.js\"");
      out.println("compatibility_date = \"" + LocalDate.now() + "\"");
    }

    // Deploy worker
    ProcessBuilder deployPb = new ProcessBuilder("wrangler", "deploy");
    deployPb.directory(projectDir);
    deployPb.environment().put("CLOUDFLARE_API_TOKEN", apiToken);
    deployPb.redirectErrorStream(true);
    deployPb.inheritIO();

    Process deploy = deployPb.start();
    int exitCode = deploy.waitFor();
    System.out.println("LOG: Wrangler deploy exited with code " + exitCode);
  }

  private static String readFirstLine(String path) {
    try (BufferedReader br = new BufferedReader(new FileReader(path))) {
      String line = br.readLine();
      return line != null ? line.trim() : null;
    } catch (IOException e) {
      return null;
    }
  }
}
