package org.designer.deepseek_sharp.key;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Optional;
import java.util.Properties;

/**
 * 把 API Key 存在用户目录，而不是项目里，避免被 git 提交：
 * {@code ~/.deepseek-sharp/config.properties}。
 */
public final class ApiKeyStore {

    private static final String PROPERTY = "deepseek.api-key";

    private final Path file;

    public ApiKeyStore() {
        this(defaultFile());
    }

    public ApiKeyStore(Path file) {
        this.file = file;
    }

    public static Path defaultFile() {
        return Path.of(System.getProperty("user.home"), ".deepseek-sharp", "config.properties");
    }

    public Path file() {
        return file;
    }

    public Optional<String> read() {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException | IllegalArgumentException ex) {
            return Optional.empty();
        }
        String value = properties.getProperty(PROPERTY);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
    }

    public Path save(String apiKey) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Properties properties = new Properties();
        properties.setProperty(PROPERTY, apiKey);
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            properties.store(writer, "DeepSeek Harness - local API key, keep it out of git");
        }
        tightenPermissions();
        return file;
    }

    private void tightenPermissions() {
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows 上没有 POSIX 权限，忽略即可
        }
    }
}
