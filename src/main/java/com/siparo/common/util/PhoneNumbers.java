package com.siparo.common.util;

/** Türkiye cep telefonu biçimini tek forma getirir: "0532 123 45 67", "+905321234567" → "05321234567". */
public final class PhoneNumbers {
    private PhoneNumbers() {}

    public static String normalize(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("\\D", "");
        if (digits.isEmpty()) return raw.trim();
        if (digits.length() == 12 && digits.startsWith("90")) return "0" + digits.substring(2);
        if (digits.length() == 10 && digits.startsWith("5")) return "0" + digits;
        return digits;
    }
}
