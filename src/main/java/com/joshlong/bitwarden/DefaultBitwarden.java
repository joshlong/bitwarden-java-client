package com.joshlong.bitwarden;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.Jackson3JsonNodeJsonProvider;
import org.jspecify.annotations.NonNull;
import org.springframework.util.Assert;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.MissingNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

class DefaultBitwarden implements Bitwarden {

    private final Configuration jsonPath;
    private final ObjectMapper json;
    private final String bwSessionId;

    DefaultBitwarden(String bwSessionId) {
        var jsonMapper = JsonMapper.builder().build();
        var configuration = jsonPathConfiguration(jsonMapper);
        this(bwSessionId, jsonMapper, configuration);
    }

    DefaultBitwarden(String bwSessionId, JsonMapper objectMapper) {
        this(bwSessionId, objectMapper, jsonPathConfiguration(objectMapper));
    }

    DefaultBitwarden(String bwSessionId, ObjectMapper json, Configuration configuration) {
        this.json = json;
        this.bwSessionId = bwSessionId;
        this.jsonPath = configuration;
        Assert.notNull(this.json, () -> "the " + ObjectMapper.class.getName() + " is null");
        Assert.notNull(this.jsonPath, () -> "the " + Configuration.class.getName() + " is null");
        Assert.hasText(this.bwSessionId, "the Bitwarden session is null");
    }

    private static Configuration jsonPathConfiguration(JsonMapper jsonMapper) {
        return Configuration.builder() //
                .options(Option.SUPPRESS_EXCEPTIONS) //
                .jsonProvider(new Jackson3JsonNodeJsonProvider(jsonMapper))
                .build();
    }

    @Override
    public JsonNode item(String itemId) {
        return this.json.readTree(this.getItemAsStringUnchecked(itemId));
    }

    @Override
    public JsonNode select(JsonNode item, String jsonPath) {
        var match = JsonPath.using(this.jsonPath).parse((Object) item).read(jsonPath);
        return switch (match) {
            case null -> MissingNode.getInstance();
            case JsonNode node -> node; // Jackson3JsonNodeJsonProvider hands back a tree already
            default -> this.json.valueToTree(match);
        };
    }

    @Override
    public String selectString(JsonNode item, String jsonPath) {
        var node = this.select(item, jsonPath);
        // filters and wildcards are "indefinite" and always come back as an array,
        // even when exactly one value matched
        if (node.isArray()) {
            if (node.size() != 1) {
                throw new IllegalStateException("`%s` matched %d values in %s, expected exactly 1"
                        .formatted(jsonPath, node.size(), describe(item)));
            }
            node = node.get(0);
        }
        if (node.isMissingNode() || node.isNull()) {
            throw new IllegalStateException("`%s` matched nothing in %s".formatted(jsonPath, describe(item)));
        }
        return node.asString();
    }

    private static String describe(JsonNode item) {
        var name = item.path("name");
        return name.isString() ? "item " + name.asString() : "the item";
    }

    private String getItemAsStringUnchecked(String itemId) {
        try {
            return this.getItemAsString(itemId);
        }//
        catch (IOException e) {
            throw new UncheckedIOException("could not run the bw CLI for item " + itemId, e);
        }//
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while reading item " + itemId, e);
        }
    }

    @NonNull
    private String getItemAsString(String itemId) throws IOException, InterruptedException {
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
        return stdout;
    }
}
