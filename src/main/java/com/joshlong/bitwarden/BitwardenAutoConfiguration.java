package com.joshlong.bitwarden;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(BitwardenProperties.class)
class BitwardenAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(name = "bw.session", matchIfMissing = false)
	DefaultBitwarden defaultBitwarden(BitwardenProperties properties) {
		return new DefaultBitwarden(properties.session());
	}

}
