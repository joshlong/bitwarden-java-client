package com.joshlong.bitwarden;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties (prefix = "bw")
public record BitwardenProperties (String session) {
}

