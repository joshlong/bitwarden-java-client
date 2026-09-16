package com.joshlong.bitwarden;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the Bitwarden client, bound from the {@code bw} property prefix.
 *
 * @param session the {@code BW_SESSION} token of an already-unlocked vault, as printed by
 * {@code bw unlock --raw}. Nothing is autoconfigured until this is set.
 * @author Josh Long
 */
@ConfigurationProperties(prefix = "bw")
public record BitwardenProperties(String session) {
}
