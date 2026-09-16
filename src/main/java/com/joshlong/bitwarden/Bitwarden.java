package com.joshlong.bitwarden;

import tools.jackson.databind.JsonNode;

/**
 * Reads secrets out of an already-unlocked Bitwarden CLI.
 * <p>
 * There is no caching here. {@link #item(String)} is one {@code bw} invocation every time
 * it is called, so pulling several values out of one vault entry means fetching it once
 * and passing the node to the {@code select} overloads:
 * {@snippet :
 * var item = bitwarden.item("elasticsearch-development"); // one bw call
 * var key = bitwarden.selectString(item, "$.fields[?(@.name == 'api-key')].value");
 * var host = bitwarden.selectString(item, "$.fields[?(@.name == 'api-host')].value");
 * }
 * The {@code String itemId} overloads are conveniences for reading a single value; as
 * their default implementations show, each one fetches the entry again.
 */
public interface Bitwarden {

	/**
	 * The whole vault entry, parsed. One {@code bw get item <itemId> --raw} per call.
	 */
	JsonNode item(String itemId);

	/**
	 * Evaluates a JsonPath expression against an entry you already hold, e.g.
	 * {@code $.login.password} or {@code $.fields[?(@.name == 'client-id')].value}.
	 * @return the match, an array node if the expression is indefinite (wildcard, filter,
	 * slice), or a missing node if nothing matched
	 */
	JsonNode select(JsonNode item, String jsonPath);

	/**
	 * The equivalent of {@code jq -r}: like {@link #select(JsonNode, String)} but unwraps
	 * the single result to a bare string.
	 * @throws IllegalStateException if the expression matched no values or more than one
	 */
	String selectString(JsonNode item, String jsonPath);

	default JsonNode select(String itemId, String jsonPath) {
		return this.select(this.item(itemId), jsonPath);
	}

	default String selectString(String itemId, String jsonPath) {
		return this.selectString(this.item(itemId), jsonPath);
	}

}
