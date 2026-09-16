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

/**
 * The default {@link Bitwarden}, which forks the {@code bw} binary off the {@code PATH}
 * and reads its standard output.
 * <p>
 * The session token is handed to each invocation through the {@code BW_SESSION}
 * environment variable, so the vault must already be unlocked; nothing here will prompt
 * for a master password. Instances are stateless beyond that token and safe to share
 * between threads.
 *
 * @author Josh Long
 */
public class DefaultBitwarden implements Bitwarden {

	private final Configuration jsonPath;

	private final ObjectMapper json;

	private final String bwSessionId;

	/**
	 * Creates a client with a Jackson {@link JsonMapper} and JsonPath
	 * {@link Configuration} of its own.
	 * @param bwSessionId the {@code BW_SESSION} token of an unlocked vault
	 */
	DefaultBitwarden(String bwSessionId) {
		var jsonMapper = JsonMapper.builder().build();
		var configuration = jsonPathConfiguration(jsonMapper);
		this(bwSessionId, jsonMapper, configuration);
	}

	/**
	 * Creates a client that parses with the given mapper, deriving a matching JsonPath
	 * {@link Configuration} from it.
	 * @param bwSessionId the {@code BW_SESSION} token of an unlocked vault
	 * @param objectMapper the mapper used to parse {@code bw} output
	 */
	DefaultBitwarden(String bwSessionId, JsonMapper objectMapper) {
		this(bwSessionId, objectMapper, jsonPathConfiguration(objectMapper));
	}

	/**
	 * Creates a client from fully specified collaborators.
	 * @param bwSessionId the {@code BW_SESSION} token of an unlocked vault
	 * @param json the mapper used to parse {@code bw} output
	 * @param configuration the JsonPath configuration used to evaluate expressions; it
	 * should carry a Jackson 3 node provider so that matches come back as
	 * {@link JsonNode}s
	 */
	DefaultBitwarden(String bwSessionId, ObjectMapper json, Configuration configuration) {
		this.json = json;
		this.bwSessionId = bwSessionId;
		this.jsonPath = configuration;
		Assert.notNull(this.json, () -> "the " + ObjectMapper.class.getName() + " is null");
		Assert.notNull(this.jsonPath, () -> "the " + Configuration.class.getName() + " is null");
		Assert.hasText(this.bwSessionId, "the Bitwarden session is null");
	}

	/**
	 * Builds the JsonPath configuration this client expects: Jackson 3 nodes in, Jackson
	 * 3 nodes out, and no exception when an expression matches nothing.
	 * @param jsonMapper the mapper backing the node provider
	 * @return the configuration
	 */
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
			case JsonNode node -> node; // Jackson3JsonNodeJsonProvider hands back a tree
										// already
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
				throw new IllegalStateException("`%s` matched %d values in %s, expected exactly 1".formatted(jsonPath,
						node.size(), describe(item)));
			}
			node = node.get(0);
		}
		if (node.isMissingNode() || node.isNull()) {
			throw new IllegalStateException("`%s` matched nothing in %s".formatted(jsonPath, describe(item)));
		}
		return node.asString();
	}

	/**
	 * Names an entry for an error message, falling back to something generic when the
	 * entry has no {@code name}.
	 * @param item the vault entry
	 * @return a short human-readable description of the entry
	 */
	private static String describe(JsonNode item) {
		var name = item.path("name");
		return name.isString() ? "item " + name.asString() : "the item";
	}

	/**
	 * Runs {@code bw} for an entry, turning the checked failures of
	 * {@link #getItemAsString(String)} into unchecked ones.
	 * @param itemId the vault entry to read, given as either its identifier or its name
	 * @return the raw JSON printed by {@code bw}
	 * @throws UncheckedIOException if the process could not be run or read
	 * @throws IllegalStateException if the calling thread was interrupted while waiting
	 * for {@code bw}
	 */
	private String getItemAsStringUnchecked(String itemId) {
		try {
			return this.getItemAsString(itemId);
		} //
		catch (IOException e) {
			throw new UncheckedIOException("could not run the bw CLI for item " + itemId, e);
		} //
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("interrupted while reading item " + itemId, e);
		}
	}

	/**
	 * Runs {@code bw get item <itemId> --raw} with the session token in the environment,
	 * draining standard error on a virtual thread so that a chatty failure cannot fill
	 * the pipe buffer and wedge the process.
	 * @param itemId the vault entry to read, given as either its identifier or its name
	 * @return the raw JSON printed by {@code bw}
	 * @throws IOException if the process could not be started or its output could not be
	 * read
	 * @throws InterruptedException if the calling thread was interrupted while waiting
	 * for the process to exit
	 * @throws IllegalStateException if {@code bw} exited with a non-zero status, e.g.
	 * because the vault is locked or no such entry exists
	 */
	@NonNull private String getItemAsString(String itemId) throws IOException, InterruptedException {
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
			throw new IllegalStateException(
					"`bw get item %s --raw` exited with %d: %s".formatted(itemId, exitCode, stderr.toString().strip()));
		}
		return stdout;
	}

}
