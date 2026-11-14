package com.apacheTika.dto;

import java.util.Map;

public record DocumentParseResponse(
        String fileName,
        String detectedType,
        String text,
        Map<String, String> metadata
) {
}
