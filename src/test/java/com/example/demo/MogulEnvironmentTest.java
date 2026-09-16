package com.example.demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Drives {@link MogulEnvironment} against a stub {@code bw} that serves every item
 * {@code env.sh} reads and appends a line to a log each time it runs, so "one fetch per
 * item, no matter how many fields" is measured rather than asserted by inspection.
 */
class MogulEnvironmentTest {

    private static final Path CALL_LOG = Path.of("target", "test-bin", "bw-calls.log");

    /**
     * item id -> its raw JSON, mirroring the vault entries env.sh expects.
     */
    private static final Map<String, String> VAULT = Map.of( //
            "mogul-wordpress-client--production", fields("client-id", "wp-id", "client-secret", "wp-secret"), //
            "elasticsearch-development", fields("api-key", "es-key", "api-host", "es-host", "otel-host", "es-otel-host",
                    "otel-header", "es-otel-header"), //
            "development-podbean", fields("client-id", "pb-id", "client-secret", "pb-secret"), //
            "aws-s3-credentials--production",
            fields("region", "us-east-1", "access-key", "aws-key", "access-key-secret", "aws-secret"), //
            "mogul-auth0-client--production",
            fields("client-id", "auth0-id", "client-secret", "auth0-secret", "domain", "mogul.auth0.com"), //
            "mogul-ably-api-key-dev", login("ably-password"), //
            "mogul-openai-key", login("openai-password") //
    );

    private static String fields(String... namesAndValues) {
        var entries = new StringBuilder();
        for (var i = 0; i < namesAndValues.length; i += 2) {
            entries.append(entries.isEmpty() ? "" : ",") //
                    .append("{\"name\":\"%s\",\"value\":\"%s\"}".formatted(namesAndValues[i], namesAndValues[i + 1]));
        }
        return "{\"object\":\"item\",\"fields\":[%s]}".formatted(entries);
    }

    private static String login(String password) {
        return "{\"object\":\"item\",\"login\":{\"password\":\"%s\"}}".formatted(password);
    }

    @BeforeEach
    void installStubBitwardenCli() throws IOException {
        assumeTrue(!System.getProperty("os.name").toLowerCase().startsWith("win"), "stub bw is a shell script");
        var bin = CALL_LOG.getParent();
        Files.createDirectories(bin);
        Files.deleteIfExists(CALL_LOG);
        var cases = new StringBuilder();
        VAULT.forEach((itemId, json) -> cases
                .append("    %s) cat <<'__J__'\n%s\n__J__\n    ;;\n".formatted(itemId, json)));
        var bw = bin.resolve("bw");
        Files.writeString(bw, """
                #!/bin/sh
                echo "$3" >> "__CALL_LOG__"
                case "$3" in
                __CASES__
                    *) echo "Not found." >&2; exit 1 ;;
                esac
                """ //
                .replace("__CALL_LOG__", CALL_LOG.toAbsolutePath().toString()) //
                .replace("__CASES__", cases.toString().stripTrailing()));
        assertThat(bw.toFile().setExecutable(true)).isTrue();
    }

    private List<String> recordedCalls() throws IOException {
        return Files.exists(CALL_LOG) ? Files.readAllLines(CALL_LOG) : List.of();
    }

    private Map<String, String> build() {
        return new MogulEnvironment(new DefaultBitwarden("fake-session", JsonMapper.builder().build())).build();
    }

    @Test
    void secretsComeFromTheVault() {
        assertThat(this.build()) //
                .containsEntry("WP_CLIENT_ID", "wp-id") //
                .containsEntry("WP_CLIENT_SECRET", "wp-secret") //
                .containsEntry("ELASTICSEARCH_API_KEY", "es-key") //
                .containsEntry("ELASTICSEARCH_OTEL_HEADER", "es-otel-header") //
                .containsEntry("PODBEAN_CLIENT_SECRET", "pb-secret") //
                .containsEntry("AWS_REGION", "us-east-1") //
                .containsEntry("AWS_ACCESS_KEY_SECRET", "aws-secret") //
                .containsEntry("AUTH0_DOMAIN", "mogul.auth0.com");
    }

    @Test
    void bwGetPasswordBecomesALoginPasswordLookup() {
        assertThat(this.build()) //
                .containsEntry("ABLY_KEY", "ably-password") //
                .containsEntry("OPENAI_KEY", "openai-password");
    }

    @Test
    void constantsCarryOver() {
        assertThat(this.build()) //
                .containsEntry("PODCAST_ASSETS_S3_BUCKET_FOLDER", "062019") //
                .containsEntry("DB_SCHEMA", "mogul") //
                .containsEntry("RMQ_VIRTUAL_HOST", "/");
    }

    @Test
    void derivedValuesInterpolateTheirInputs() {
        assertThat(this.build()) //
                .containsEntry("RMQ_ADDRESS", "rmq://mogul:mogul@127.0.0.1//") //
                .containsEntry("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost/mogul") //
                .containsEntry("SPRING_RABBITMQ_HOST", "127.0.0.1") //
                .containsEntry("SPRING_DATASOURCE_PASSWORD", "mogul");
    }

    @Test
    void everyVariableEnvShExportedIsPresent() {
        assertThat(this.build()).containsOnlyKeys("PODCAST_ASSETS_S3_BUCKET", "PODCAST_ASSETS_S3_BUCKET_FOLDER",
                "PODCAST_INPUT_S3_BUCKET", "PODCAST_OUTPUT_S3_BUCKET", "PODCASTS_PROCESSOR_RMQ_REQUESTS",
                "PODCASTS_PROCESSOR_RMQ_REPLIES", "DB_USERNAME", "DB_PASSWORD", "DB_HOST", "DB_SCHEMA", "RMQ_HOST",
                "RMQ_USERNAME", "RMQ_PASSWORD", "RMQ_VIRTUAL_HOST", "RMQ_ADDRESS", "SPRING_RABBITMQ_HOST",
                "SPRING_RABBITMQ_USERNAME", "SPRING_RABBITMQ_PASSWORD", "SPRING_RABBITMQ_VIRTUAL_HOST",
                "SPRING_DATASOURCE_URL", "SPRING_DATASOURCE_USERNAME", "SPRING_DATASOURCE_PASSWORD", "WP_CLIENT_ID",
                "WP_CLIENT_SECRET", "ELASTICSEARCH_API_KEY", "ELASTICSEARCH_API_HOST", "ELASTICSEARCH_OTEL_HOST",
                "ELASTICSEARCH_OTEL_HEADER", "PODBEAN_CLIENT_ID", "PODBEAN_CLIENT_SECRET", "ABLY_KEY", "OPENAI_KEY",
                "AWS_REGION", "AWS_ACCESS_KEY_ID", "AWS_ACCESS_KEY_SECRET", "AUTH0_CLIENT_ID", "AUTH0_CLIENT_SECRET",
                "AUTH0_DOMAIN");
    }

    @Test
    void eachItemIsFetchedExactlyOnceNoMatterHowManyFieldsItFeeds() throws IOException {
        this.build();
        // elasticsearch alone feeds 4 variables and aws 3; env.sh forked cat|jq per field
        assertThat(this.recordedCalls()).hasSize(VAULT.size()).containsExactlyInAnyOrderElementsOf(VAULT.keySet());
    }

    @Test
    void repeatedLookupsOnOneClientDoNotReFetch() throws IOException {
        var bitwarden = new DefaultBitwarden("fake-session", JsonMapper.builder().build());
        for (var i = 0; i < 5; i++) {
            bitwarden.itemAsString("mogul-auth0-client--production", "$.fields[?(@.name == 'domain')].value");
        }
        assertThat(this.recordedCalls()).containsExactly("mogul-auth0-client--production");
    }

    @Test
    void concurrentCallersForOneItemShareASingleFetch() throws Exception {
        var bitwarden = new DefaultBitwarden("fake-session", JsonMapper.builder().build());
        var threads = java.util.stream.IntStream.range(0, 16) //
                .mapToObj(i -> Thread.ofVirtual().start(() -> bitwarden.itemAsString("elasticsearch-development",
                        "$.fields[?(@.name == 'api-key')].value"))) //
                .toList();
        for (var thread : threads) {
            thread.join();
        }
        assertThat(this.recordedCalls()).containsExactly("elasticsearch-development");
    }

}
