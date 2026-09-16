package com.example.demo;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

class MogulEnvironment {

    private final Executor executor = Executors.newVirtualThreadPerTaskExecutor();

    private final Bitwarden bitwarden;

    MogulEnvironment(Bitwarden bitwarden) {
        this.bitwarden = bitwarden;
    }

    Map<String, String> build() {
        var env = new ConcurrentHashMap<String, String>();
        var contributors = List.<Runnable>of( //
                () -> this.contributeConstants(env), //
                () -> this.contributeWordpress(env), //
                () -> this.contributeElasticsearch(env), //
                () -> this.contributePodbean(env), //
                () -> this.contributeAbly(env), //
                () -> this.contributeOpenAi(env), //
                () -> this.contributeAws(env), //
                () -> this.contributeAuth0(env) //
        );
        var futures = contributors.stream() //
                .map(contributor -> CompletableFuture.runAsync(contributor, this.executor)) //
                .toArray(CompletableFuture[]::new);
        try {
            CompletableFuture.allOf(futures).join();
        } //
        catch (CompletionException e) {
            throw e.getCause() instanceof RuntimeException cause ? cause : e;
        }
        // everything above is independent; this one reads what they wrote, so it waits
        this.contributeDerived(env);
        return Map.copyOf(env);
    }

    private void contributeConstants(Map<String, String> env) {
        env.put("PODCAST_ASSETS_S3_BUCKET", "podcast-assets-bucket-dev");
        env.put("PODCAST_ASSETS_S3_BUCKET_FOLDER", "062019");
        env.put("PODCAST_INPUT_S3_BUCKET", "podcast-input-bucket-dev");
        env.put("PODCAST_OUTPUT_S3_BUCKET", "podcast-output-bucket-dev");
        env.put("PODCASTS_PROCESSOR_RMQ_REQUESTS", "podcast-processor-requests");
        env.put("PODCASTS_PROCESSOR_RMQ_REPLIES", "podcast-processor-replies");
        env.put("DB_USERNAME", "mogul");
        env.put("DB_PASSWORD", "mogul");
        env.put("DB_HOST", "localhost");
        env.put("DB_SCHEMA", "mogul");
        env.put("RMQ_HOST", "127.0.0.1");
        env.put("RMQ_USERNAME", "mogul");
        env.put("RMQ_PASSWORD", "mogul");
        env.put("RMQ_VIRTUAL_HOST", "/");
    }

    private void contributeWordpress(Map<String, String> env) {
        var item = "mogul-wordpress-client--production";
        env.put("WP_CLIENT_ID", this.field(item, "client-id"));
        env.put("WP_CLIENT_SECRET", this.field(item, "client-secret"));
    }

    private void contributeElasticsearch(Map<String, String> env) {
        var item = "elasticsearch-development";
        env.put("ELASTICSEARCH_API_KEY", this.field(item, "api-key"));
        env.put("ELASTICSEARCH_API_HOST", this.field(item, "api-host"));
        env.put("ELASTICSEARCH_OTEL_HOST", this.field(item, "otel-host"));
        env.put("ELASTICSEARCH_OTEL_HEADER", this.field(item, "otel-header"));
    }

    private void contributePodbean(Map<String, String> env) {
        var item = "development-podbean";
        env.put("PODBEAN_CLIENT_ID", this.field(item, "client-id"));
        env.put("PODBEAN_CLIENT_SECRET", this.field(item, "client-secret"));
    }

    private void contributeAbly(Map<String, String> env) {
        env.put("ABLY_KEY", this.password("mogul-ably-api-key-dev"));
    }

    private void contributeOpenAi(Map<String, String> env) {
        env.put("OPENAI_KEY", this.password("mogul-openai-key"));
    }

    private void contributeAws(Map<String, String> env) {
        var item = "aws-s3-credentials--production";
        env.put("AWS_REGION", this.field(item, "region"));
        env.put("AWS_ACCESS_KEY_ID", this.field(item, "access-key"));
        env.put("AWS_ACCESS_KEY_SECRET", this.field(item, "access-key-secret"));
    }

    private void contributeAuth0(Map<String, String> env) {
        var item = "mogul-auth0-client--production";
        env.put("AUTH0_CLIENT_ID", this.field(item, "client-id"));
        env.put("AUTH0_CLIENT_SECRET", this.field(item, "client-secret"));
        env.put("AUTH0_DOMAIN", this.field(item, "domain"));
    }

    private void contributeDerived(Map<String, String> env) {
        env.put("RMQ_ADDRESS", "rmq://%s:%s@%s/%s".formatted(env.get("RMQ_USERNAME"), env.get("RMQ_PASSWORD"),
                env.get("RMQ_HOST"), env.get("RMQ_VIRTUAL_HOST")));
        env.put("SPRING_RABBITMQ_HOST", env.get("RMQ_HOST"));
        env.put("SPRING_RABBITMQ_USERNAME", env.get("RMQ_USERNAME"));
        env.put("SPRING_RABBITMQ_PASSWORD", env.get("RMQ_PASSWORD"));
        env.put("SPRING_RABBITMQ_VIRTUAL_HOST", env.get("RMQ_VIRTUAL_HOST"));
        env.put("SPRING_DATASOURCE_URL",
                "jdbc:postgresql://%s/%s".formatted(env.get("DB_HOST"), env.get("DB_SCHEMA")));
        env.put("SPRING_DATASOURCE_USERNAME", env.get("DB_USERNAME"));
        env.put("SPRING_DATASOURCE_PASSWORD", env.get("DB_PASSWORD"));
    }

    /**
     * {@code jq -r '.fields[] | select(.name == "<name>") | .value'}
     */
    private String field(String itemId, String name) {
        return this.bitwarden.itemAsString(itemId, "$.fields[?(@.name == '%s')].value".formatted(name));
    }

    /**
     * {@code bw get password <itemId>}, which is just the login password of the item.
     */
    private String password(String itemId) {
        return this.bitwarden.itemAsString(itemId, "$.login.password");
    }

}
