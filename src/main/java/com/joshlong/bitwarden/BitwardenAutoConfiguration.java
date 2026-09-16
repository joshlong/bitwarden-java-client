package com.joshlong.bitwarden;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Contributes a {@link Bitwarden} client when a session token has been configured.
 *
 * @author Josh Long
 */
@AutoConfiguration
@EnableConfigurationProperties(BitwardenProperties.class)
class BitwardenAutoConfiguration {

	/**
	 * The client, built from the configured session token.
	 * @param properties the bound {@code bw.*} configuration
	 * @return a client for the unlocked vault that token belongs to
	 */
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(name = "bw.session", matchIfMissing = false)
	DefaultBitwarden defaultBitwarden(BitwardenProperties properties) {
		return new DefaultBitwarden(properties.session());
	}

}
