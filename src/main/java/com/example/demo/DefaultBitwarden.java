package com.example.demo;

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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

class DefaultBitwarden implements Bitwarden {

    private static final Executor VIRTUAL_THREADS = runnable -> Thread.ofVirtual().start(runnable);

    private final Configuration jsonPath;
    private final ObjectMapper json;
    private final String bwSessionId;

    // one `bw get item` per id, however many expressions get evaluated against it, and
    // however many threads ask at once: the first caller installs the future, the rest join it
    private final ConcurrentHashMap<String, CompletableFuture<JsonNode>> items = new ConcurrentHashMap<>();

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
    public JsonNode itemAsJsonNode(String itemId, String jsonPath) {
        return this.selectFrom(this.item(itemId), jsonPath);
    }

    /**
     * The whole item, parsed once and shared. The {@code bw} invocation happens on a
     * virtual thread so that callers already on one aren't the ones blocking on the pipe.
     */
    private JsonNode item(String itemId) {
        var future = this.items.computeIfAbsent(itemId, //
                id -> CompletableFuture.supplyAsync(() -> this.json.readTree(this.getItemAsStringUnchecked(id)),
                        VIRTUAL_THREADS));
        try {
            return future.join();
        } //
        catch (CompletionException e) {
            // don't let one failed unlock/typo poison the cache for the rest of the JVM
            this.items.remove(itemId, future);
            throw e.getCause() instanceof RuntimeException cause ? cause : e;
        }
    }

    @Override
    public String itemAsString(String itemId, String jsonPath) {
        var node = this.itemAsJsonNode(itemId, jsonPath);
        if (node.isArray()) {
            if (node.size() != 1) {
                throw new IllegalStateException("`%s` matched %d values in item %s, expected exactly 1"
                        .formatted(jsonPath, node.size(), itemId));
            }
            node = node.get(0);
        }
        if (node.isMissingNode() || node.isNull()) {
            throw new IllegalStateException("`%s` matched nothing in item %s".formatted(jsonPath, itemId));
        }
        return node.asString();
    }

    private JsonNode selectFrom(JsonNode root, String jsonPath) {
        var match = JsonPath.using(this.jsonPath).parse((Object) root).read(jsonPath);
        return switch (match) {
            case null -> MissingNode.getInstance();
            case JsonNode node -> node; // Jackson3JsonNodeJsonProvider hands back a tree already
            default -> this.json.valueToTree(match);
        };
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
