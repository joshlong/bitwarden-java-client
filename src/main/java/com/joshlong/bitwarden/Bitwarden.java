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
 *
 * @author Josh Long
 */
public interface Bitwarden {

	/**
	 * The whole vault entry, parsed. One {@code bw get item <itemId> --raw} per call.
	 * @param itemId the vault entry to read, given as either its identifier or its name
	 * @return the entry as a JSON tree
	 */
	JsonNode item(String itemId);

	/**
	 * Evaluates a JsonPath expression against an entry you already hold, e.g.
	 * {@code $.login.password} or {@code $.fields[?(@.name == 'client-id')].value}.
	 * @param item the vault entry to read, as returned by {@link #item(String)}
	 * @param jsonPath the JsonPath expression to evaluate
	 * @return the match, an array node if the expression is indefinite (wildcard, filter,
	 * slice), or a missing node if nothing matched
	 */
	JsonNode select(JsonNode item, String jsonPath);

	/**
	 * The equivalent of {@code jq -r}: like {@link #select(JsonNode, String)} but unwraps
	 * the single result to a bare string.
	 * @param item the vault entry to read, as returned by {@link #item(String)}
	 * @param jsonPath the JsonPath expression to evaluate
	 * @return the matched value as a string
	 * @throws IllegalStateException if the expression matched no values or more than one
	 */
	String selectString(JsonNode item, String jsonPath);

	/**
	 * Fetches an entry and evaluates a JsonPath expression against it in one go. Fetching
	 * is the expensive half, so prefer {@link #select(JsonNode, String)} when you want
	 * more than one value out of the same entry.
	 * @param itemId the vault entry to read, given as either its identifier or its name
	 * @param jsonPath the JsonPath expression to evaluate
	 * @return the match, an array node if the expression is indefinite (wildcard, filter,
	 * slice), or a missing node if nothing matched
	 */
	default JsonNode select(String itemId, String jsonPath) {
		return this.select(this.item(itemId), jsonPath);
	}

	/**
	 * Fetches an entry and reads a single value out of it in one go. Fetching is the
	 * expensive half, so prefer {@link #selectString(JsonNode, String)} when you want
	 * more than one value out of the same entry.
	 * @param itemId the vault entry to read, given as either its identifier or its name
	 * @param jsonPath the JsonPath expression to evaluate
	 * @return the matched value as a string
	 * @throws IllegalStateException if the expression matched no values or more than one
	 */
	default String selectString(String itemId, String jsonPath) {
		return this.selectString(this.item(itemId), jsonPath);
	}

}
