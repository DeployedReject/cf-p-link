import java.io.*;
import java.util.Scanner;
import java.util.regex.*;

public class subprocessMaster {

  private static final String API_FILE = "cf_api_token.txt";
  private static final String WORKER_FILE = "worker_name.txt";
  private static final String TUNNEL_FILE = "latest_tunnel.txt";

  public static void main(String[] args) throws IOException, InterruptedException {
    Scanner scanner = new Scanner(System.in);

    // --- Step 1: API Token Setup ---
    File apiFile = new File(API_FILE);
    if (!apiFile.exists()) {
      System.out.println("Wrangler not logged in. Follow these steps to get your API token:");
      System.out.println("1. Login to Cloudflare.");
      System.out.println("2. Go to Profile → API Tokens → Create Token (template: 'Edit Cloudflare Workers').");
      System.out.print("Enter your Cloudflare API token: ");
      String apiToken = scanner.nextLine().trim();
      try (PrintWriter pw = new PrintWriter(apiFile)) {
        pw.println(apiToken);
      }
    }

    // --- Step 2: Worker name ---
    File workerFile = new File(WORKER_FILE);
    if (!workerFile.exists()) {
      System.out.print("Enter worker name: ");
      String workerName = scanner.nextLine().trim();
      try (PrintWriter pw = new PrintWriter(workerFile)) {
        pw.println(workerName);
      }
    }
    BufferedReader apiTokenR = new BufferedReader(new FileReader(API_FILE));
    String apiToken = apiTokenR.readLine().trim();
    apiTokenR.close();
    BufferedReader workerNameR = new BufferedReader(new FileReader(WORKER_FILE));
    String workerName = workerNameR.readLine().trim();
    workerNameR.close();

    // --- Step 3: Validate API token ---
    ProcessBuilder verifyPb = new ProcessBuilder("wrangler", "whoami");
    verifyPb.environment().put("CLOUDFLARE_API_TOKEN", apiToken);
    verifyPb.redirectErrorStream(true);
    Process verifyProcess = verifyPb.start();

    try (BufferedReader br = new BufferedReader(new InputStreamReader(verifyProcess.getInputStream()))) {
      String line;
      while ((line = br.readLine()) != null)
        System.out.println(line);
    }
    if (verifyProcess.waitFor() != 0) {
      System.out.println(" Invalid API token. Delete cf_api_token.txt and rerun.");
      return;
    }

    System.out.println(" API token valid.");

    // --- Step 4: Start Tunnel ---
    ProcessBuilder tunnelPb = new ProcessBuilder("cloudflared", "tunnel", "--url", "http://localhost:2283");
    tunnelPb.redirectErrorStream(true);
    Process tunnelProcess = tunnelPb.start();
    BufferedReader tunnelReader = new BufferedReader(new InputStreamReader(tunnelProcess.getInputStream()));
    Pattern urlPattern = Pattern.compile("(https?://[a-zA-Z0-9-]+\\.trycloudflare\\.com)");
    String lastUrl = "";

    // --- Step 5: Watch for tunnels and redeploy ---
    while (true) {
      String line;
      while ((line = tunnelReader.readLine()) != null) {
        Matcher matcher = urlPattern.matcher(line);
        if (matcher.find()) {
          String foundUrl = matcher.group(1);
          if (!foundUrl.equals(lastUrl)) {
            lastUrl = foundUrl;
            System.out.println(" New Quick Tunnel detected: " + foundUrl);

            try (PrintWriter pw = new PrintWriter(TUNNEL_FILE)) {
              pw.println(foundUrl);
            }

            System.out.println(" Deploying Worker...");
            ProcessBuilder deployPb = new ProcessBuilder("java", "-cp", "out", "workerDeployer");
            deployPb.inheritIO();
            Process deploy = deployPb.start();
            deploy.waitFor();

            System.out.println(" Worker deployed/redeployed successfully: " + foundUrl);
          }
        }
      }

      try {
        int exitCode = tunnelProcess.exitValue();
        System.out.println(" Tunnel exited unexpectedly (code " + exitCode + "), restarting...");
        tunnelProcess = tunnelPb.start();
        tunnelReader = new BufferedReader(new InputStreamReader(tunnelProcess.getInputStream()));
      } catch (IllegalThreadStateException e) {
        // Still running
      }

      Thread.sleep(2000);
    }
  }
}
