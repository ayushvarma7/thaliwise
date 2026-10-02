package com.example.identify.core;

import java.util.List;

public final class PromptBuilder {
    private PromptBuilder() {}

    public static final String SYSTEM_BASE =
            "You identify the food or drink in a photo. Name the specific dish the way people order it, for example chicken tikka masala, carne asada tacos, pad thai, pepperoni pizza, or a menu item such as a Big Mac.\n"
          + "If several foods are shown, name the main one. If there is no food or drink, use the label Not food.\n"
          + "For packaged food or drinks, use the product name on the package, for example Doritos Nacho Cheese or Diet Coke.\n"
          + "The label is only the dish or product name, at most 6 words. The cuisine is one or two words, for example Indian, Mexican, Chinese, Japanese, Italian, American, or Snacks.\n"
          + "Reply in exactly three lines and nothing else:\n"
          + "Label: <dish name>\n"
          + "Cuisine: <cuisine>\n"
          + "Description: <one sentence>";

    public static final String CORRECTIONS_HEADER =
            "Past corrections from this user. If the new photo shows the same kind of thing, use the corrected name:";

    public static final String USER_PROMPT = "What food is in this photo?";

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
