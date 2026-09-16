package com.example.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @Bean
    ApplicationRunner runner(Bitwarden bitwarden) {
        return new ApplicationRunner() {
            @Override
            public void run(ApplicationArguments args) throws Exception {
                JsonNode item = bitwarden.getItem("mogul-auth0-client--production");
            }
        };
    }

    @Bean
    Bitwarden bitwarden(@Value("${BW_SESSION}") String bwSessionId, JsonMapper jsonMapper) {
        return new Bitwarden(bwSessionId, jsonMapper);
    }
}


class Bitwarden {

    private final ObjectMapper json;
    private final String bwSessionId;

    Bitwarden(String bwSessionId) {
        this(bwSessionId, JsonMapper.builder().build());
    }

    Bitwarden(String bwSessionId, ObjectMapper json) {
        this.json = json;
        this.bwSessionId = bwSessionId;
    }

    JsonNode getItem(String itemId) {
        return this.bwGetItem(this.bwSessionId, itemId);
    }

    private JsonNode bwGetItem(String bwSessionId, String itemId) {
        try {
            var builder = new ProcessBuilder("bw", "get", "item", itemId, "--raw");
            builder.environment().put("BW_SESSION", bwSessionId);
            var process = builder.start();
            var stderr = new StringBuilder();
            var drain = Thread.ofVirtual() //
                    .start(() -> {
                        try (var err = process.getErrorStream()) {
                            stderr.append(new String(err.readAllBytes(), StandardCharsets.UTF_8));
                        } //
                        catch (IOException ignored) {
                            // dont care
                        }
                    });
            var stdout = (String) null;
            try (var out = process.getInputStream()) {
                stdout = new String(out.readAllBytes(), StandardCharsets.UTF_8);
            }
            var exitCode = process.waitFor();
            drain.join();
            if (exitCode != 0) {
                throw new IllegalStateException("`bw get item %s --raw` exited with %d: %s".formatted(itemId, exitCode,
                        stderr.toString().strip()));
            }
            return this.json.readTree(stdout);
        } catch (IOException e) {
            throw new UncheckedIOException("could not run the bw CLI for item " + itemId, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while reading item " + itemId, e);
        }
    }
}