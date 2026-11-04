package org.designer.deepseek_sharp.cli;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CliOptionsTest {

    @Test
    void parsesInlineForm() {
        CliOptions options = CliOptions.parse(new String[]{
                "--api-key=sk-abc", "--ask=你好", "--model=deepseek-reasoner", "--no-color"});

        assertThat(options.apiKey()).isEqualTo("sk-abc");
        assertThat(options.ask()).isEqualTo("你好");
        assertThat(options.model()).isEqualTo("deepseek-reasoner");
        assertThat(options.noColor()).isTrue();
    }

    @Test
    void parsesSeparatedForm() {
        CliOptions options = CliOptions.parse(new String[]{
                "-k", "sk-xyz", "-a", "1+1 等于几", "-s", "42"});

        assertThat(options.apiKey()).isEqualTo("sk-xyz");
        assertThat(options.ask()).isEqualTo("1+1 等于几");
        assertThat(options.sessionId()).isEqualTo(42L);
    }

    @Test
    void ignoresSpringArguments() {
        CliOptions options = CliOptions.parse(new String[]{"--spring.profiles.active=dev", "--debug"});

        assertThat(options.apiKey()).isNull();
        assertThat(options.ask()).isNull();
        assertThat(options.help()).isFalse();
    }

    @Test
    void parsesHelpAndVersion() {
        assertThat(CliOptions.parse(new String[]{"-h"}).help()).isTrue();
        assertThat(CliOptions.parse(new String[]{"--version"}).version()).isTrue();
    }

    @Test
    void parsesWindowFlags() {
        CliOptions noWindow = CliOptions.parse(new String[]{"--no-window"});
        assertThat(noWindow.noWindow()).isTrue();
        assertThat(noWindow.inWindow()).isFalse();

        CliOptions inWindow = CliOptions.parse(new String[]{"--in-window"});
        assertThat(inWindow.inWindow()).isTrue();
    }

    @Test
    void toleratesBrokenSessionId() {
        CliOptions options = CliOptions.parse(new String[]{"-s", "abc"});

        assertThat(options.sessionId()).isNull();
    }
}
