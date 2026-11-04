package org.designer.deepseek_sharp.key;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyStoreTest {

    @Test
    void savesAndReadsKey(@TempDir Path tempDir) throws Exception {
        ApiKeyStore store = new ApiKeyStore(tempDir.resolve("config.properties"));

        assertThat(store.read()).isEqualTo(Optional.empty());

        store.save("sk-1234567890abcdef");

        assertThat(store.read()).contains("sk-1234567890abcdef");
    }

    @Test
    void blankOrMissingFileReadsEmpty(@TempDir Path tempDir) {
        ApiKeyStore store = new ApiKeyStore(tempDir.resolve("nope.properties"));

        assertThat(store.read()).isEmpty();
    }

    @Test
    void masksKey() {
        assertThat(ApiKeyResolver.mask("sk-1234567890abcdef")).isEqualTo("sk-123****cdef");
        assertThat(ApiKeyResolver.mask("short")).isEqualTo("****");
        assertThat(ApiKeyResolver.mask(null)).isEqualTo("****");
    }
}
