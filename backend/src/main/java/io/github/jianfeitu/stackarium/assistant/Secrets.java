package io.github.jianfeitu.stackarium.assistant;

import java.util.regex.Pattern;

final class Secrets {
    private static final Pattern KEY = Pattern.compile("(?i)(password|secret|token|api[_-]?key|credential|private[_-]?key)");
    private static final Pattern ASSIGNMENT = Pattern.compile("(?i)\\b([A-Z_]*(?:PASSWORD|SECRET|TOKEN|API_KEY|CREDENTIAL)[A-Z_]*)\\s*[:=]\\s*([^\\s,;]+)");
    private static final Pattern BEARER = Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9._~+/-]+");
    private static final Pattern OPENAI_KEY = Pattern.compile("\\bsk-[A-Za-z0-9_-]{12,}");

    private Secrets() {}

    static boolean sensitiveKey(String key) { return key != null && KEY.matcher(key).find(); }

    static String redact(String value) {
        if (value == null) return "";
        String result = ASSIGNMENT.matcher(value).replaceAll("$1=[已脱敏]");
        result = BEARER.matcher(result).replaceAll("Bearer [已脱敏]");
        return OPENAI_KEY.matcher(result).replaceAll("[已脱敏]");
    }
}
