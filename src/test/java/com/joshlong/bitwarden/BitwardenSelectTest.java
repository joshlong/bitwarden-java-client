package com.joshlong.bitwarden;

import com.jayway.jsonpath.InvalidPathException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Everything here goes through {@link Bitwarden#select(String, String)}, the real entry
 * point, so the process launch and the JsonPath evaluation are both under test. The cost
 * is the stub {@code bw} below; surefire puts its directory first on the {@code PATH}.
 */
class BitwardenSelectTest {

	private static final String ITEM_ID = "mogul-auth0-client--production";

	private static final String ITEM = """
			{
			  "object": "item",
			  "id": "3a1f...",
			  "name": "mogul-auth0-client--production",
			  "login": {
			    "username": "admin@example.com",
			    "password": "s3cr3t",
			    "uris": [
			      { "uri": "https://example.auth0.com" },
			      { "uri": "https://example.com/callback" }
			    ]
			  },
			  "fields": [
			    { "name": "client-id", "value": "abc123", "type": 0 },
			    { "name": "client-secret", "value": "shhh", "type": 1 },
			    { "name": "audience", "value": "https://api.example.com", "type": 0 }
			  ]
			}
			""";

	private final Bitwarden bitwarden = new DefaultBitwarden("fake-session", JsonMapper.builder().build());

	@BeforeAll
	static void installStubBitwardenCli() throws IOException {
		assumeTrue(!System.getProperty("os.name").toLowerCase().startsWith("win"), "stub bw is a shell script");
		var bin = Path.of("target", "test-bin");
		Files.createDirectories(bin);
		var bw = bin.resolve("bw");
		Files.writeString(bw, """
				#!/bin/sh
				# Stands in for the Bitwarden CLI: asserts it was called the way the real
				# one would be, then replays a fixture.
				if [ "$1" != "get" ] || [ "$2" != "item" ] || [ "$4" != "--raw" ]; then
				  echo "unexpected argv: $*" >&2
				  exit 64
				fi
				if [ -z "$BW_SESSION" ]; then
				  echo "You are not logged in." >&2
				  exit 1
				fi
				if [ "$3" != "__ITEM_ID__" ]; then
				  echo "Not found." >&2
				  exit 1
				fi
				cat <<'__FIXTURE__'
				__JSON__
				__FIXTURE__
				""".replace("__ITEM_ID__", ITEM_ID).replace("__JSON__", ITEM.strip()));
		assertThat(bw.toFile().setExecutable(true)).isTrue();
	}

	private String select(String jsonPath) {
		return this.bitwarden.select(ITEM_ID, jsonPath).toString();
	}

	@Test
	void jqFieldSelectBecomesAFilter() {
		// jq: .fields[] | select(.name == "client-id") | .value
		assertThat(this.select("$.fields[?(@.name == 'client-id')].value")).isEqualTo("[\"abc123\"]");
	}

	@Test
	void doubleQuotedLiteralsAlsoWorkInFilters() {
		assertThat(this.select("$.fields[?(@.name == \"client-secret\")].value")).isEqualTo("[\"shhh\"]");
	}

	@Test
	void jqPlainPathKeepsItsShapeButNeedsARoot() {
		// jq: .login.password
		assertThat(this.select("$.login.password")).isEqualTo("\"s3cr3t\"");
	}

	@Test
	void jqIterateBecomesAWildcard() {
		// jq: .login.uris[].uri
		assertThat(this.select("$.login.uris[*].uri"))
			.isEqualTo("[\"https://example.auth0.com\",\"https://example.com/callback\"]");
	}

	@Test
	void jqRecursiveDescentIsTheSame() {
		// jq: .. | .name?
		assertThat(this.select("$..name")).contains("client-id", "client-secret", "audience");
	}

	@Test
	void numericPredicate() {
		assertThat(this.select("$.fields[?(@.type == 1)].name")).isEqualTo("[\"client-secret\"]");
	}

	@Test
	void filterOfOneStillReturnsAnArray() {
		assertThat(this.bitwarden.select(ITEM_ID, "$.fields[?(@.name == 'client-id')].value").isArray()).isTrue();
	}

	@Test
	void missingDefinitePathIsAMissingNode() {
		assertThat(this.bitwarden.select(ITEM_ID, "$.nope.nothing").isMissingNode()).isTrue();
	}

	@Test
	void filterThatMatchesNothingIsAnEmptyArray() {
		assertThat(this.bitwarden.select(ITEM_ID, "$.fields[?(@.name == 'nope')].value").isEmpty()).isTrue();
	}

	@Test
	void jqSyntaxIsRejectedRatherThanSilentlyEmpty() {
		assertThatExceptionOfType(InvalidPathException.class)
			.isThrownBy(() -> this.bitwarden.select(ITEM_ID, ".fields[] | select(.name == \"client-id\")"));
	}

	@Test
	void rootSelectorReturnsTheWholeItemSoJsonPointerStillWorks() {
		assertThat(this.bitwarden.select(ITEM_ID, "$").at("/login/username").asString()).isEqualTo("admin@example.com");
	}

	@Test
	void selectStringUnwrapsASingleMatch() {
		assertThat(this.bitwarden.selectString(ITEM_ID, "$.fields[?(@.name == 'client-id')].value"))
			.isEqualTo("abc123");
	}

	@Test
	void selectStringRejectsAMultiValuedMatch() {
		assertThatIllegalStateException().isThrownBy(() -> this.bitwarden.selectString(ITEM_ID, "$..name"))
			.withMessageContaining("expected exactly 1");
	}

	@Test
	void selectStringRejectsAMatchOfNothing() {
		assertThatIllegalStateException().isThrownBy(() -> this.bitwarden.selectString(ITEM_ID, "$.login.nope"))
			.withMessageContaining("matched nothing");
	}

	@Test
	void anUnknownItemSurfacesTheCliStderr() {
		assertThatIllegalStateException().isThrownBy(() -> this.bitwarden.select("no-such-item", "$"))
			.withMessageContaining("Not found.");
	}

}
