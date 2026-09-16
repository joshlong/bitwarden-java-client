package com.example.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @Bean
    ApplicationRunner runner(MogulEnvironment environment) {
        return args -> {
            var env = environment.build();
            // env.sh's `export K=V`, so this is still `eval`-able from a shell
            env.entrySet().stream() //
                    .sorted(Map.Entry.comparingByKey()) //
                    .forEach(entry -> IO.println("export %s='%s'".formatted(entry.getKey(), entry.getValue())));
        };
    }

    @Bean
    MogulEnvironment mogulEnvironment(Bitwarden bitwarden) {
        return new MogulEnvironment(bitwarden);
    }

    @Bean
    Bitwarden bitwarden(@Value("${BW_SESSION}") String bwSessionId, JsonMapper jsonMapper) {
        return new DefaultBitwarden(bwSessionId, jsonMapper);
    }
}


