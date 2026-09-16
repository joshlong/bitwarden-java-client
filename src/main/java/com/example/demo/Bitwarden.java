package com.example.demo;

import tools.jackson.databind.JsonNode;

interface Bitwarden {

    JsonNode itemAsJsonNode(String itemId, String jsonPath);

    String itemAsString(String itemId, String jsonPath);
}

