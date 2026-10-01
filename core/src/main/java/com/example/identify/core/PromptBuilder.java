package com.example.identify.core;

import java.util.List;

public final class PromptBuilder {
    private PromptBuilder() {}

    public static final String SYSTEM_BASE =
            "You identify the main object in a photo. Be specific: give the breed, species, variety, make, or model when it is visible.\n"
          + "Reply in exactly two lines and nothing else:\n"
          + "Label: <short name, at most 6 words>\n"
          + "Description: <one sentence>";

    public static final String CORRECTIONS_HEADER =
            "Past corrections from this user. If the new photo shows the same kind of thing, use the corrected name:";

    public static final String USER_PROMPT = "Identify the main object in this photo.";

    public static final int MAX_FIELD_CHARS = 80;

    public static String systemPrompt(List<CorrectionExample> examples) {
        if (examples == null || examples.isEmpty()) return SYSTEM_BASE;
        StringBuilder sb = new StringBuilder(SYSTEM_BASE).append("\n\n").append(CORRECTIONS_HEADER);
        for (CorrectionExample e : examples) {
            sb.append("\n- You said \"").append(sanitize(e.modelLabel))
              .append("\". Correct answer: \"").append(sanitize(e.correctLabel)).append("\".");
        }
        return sb.toString();
    }

    public static String sanitize(String s) {
        if (s == null) return "";
        String t = s.replace('\r', ' ').replace('\n', ' ').replace("\"", "")
                    .replaceAll("\\s+", " ").trim();
        return t.length() > MAX_FIELD_CHARS ? t.substring(0, MAX_FIELD_CHARS).trim() : t;
    }
}
