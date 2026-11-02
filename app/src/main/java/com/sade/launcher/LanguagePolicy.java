package com.sade.launcher;

public final class LanguagePolicy {
    private LanguagePolicy() {}
    public static String choose(String primary, String[] supported) {
        java.util.Locale locale = java.util.Locale.forLanguageTag(primary);
        String language = switch (locale.getLanguage()) {
            case "tl" -> "fil";
            case "in" -> "id";
            case "jw" -> "jv";
            case "no" -> "nb";
            default -> locale.getLanguage();
        };
        String script = locale.getScript();
        String region = locale.getCountry();
        if (language.equals("zh") && (script.equals("Hant") ||
                (script.isEmpty() && (region.equals("TW") || region.equals("HK") || region.equals("MO"))))) {
            for (String code : supported) if (code.equals("zh-Hant")) return code;
        }
        for (String code : supported) if (code.equalsIgnoreCase(locale.toLanguageTag())) return code;
        for (String code : supported) if (code.equals(language)) return code;
        return "en";
    }
}
