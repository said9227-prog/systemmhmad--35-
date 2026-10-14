package com.example.util

object ArabicSearchUtils {
    /**
     * تطبيع وتجريد النصوص العربية من التشكيل والهمزات
     */
    fun normalize(text: String): String {
        return text
            .replace("[أإآا]".toRegex(), "ا")
            .replace("ة", "ه")
            .replace("ى", "ي")
            .replace("[ً-ٟ]".toRegex(), "") // إزالة علامات التشكيل
            .trim()
            .lowercase()
    }

    /**
     * توليد صيغة بديلة للبحث للتعامل مع الفروق الإملائية الشائعة
     * مثل (أحمد / احمد)، (صيدلية / صيدليه)، (مستشفى / مستشفي)
     */
    fun getAlternativeQuery(query: String): String {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return ""

        val norm = normalize(trimmed)
        if (norm != trimmed.lowercase()) return norm

        return if (trimmed.startsWith("ا")) {
            "أ" + trimmed.substring(1)
        } else if (trimmed.startsWith("أ") || trimmed.startsWith("إ")) {
            "ا" + trimmed.substring(1)
        } else if (trimmed.endsWith("ه")) {
            trimmed.dropLast(1) + "ة"
        } else if (trimmed.endsWith("ة")) {
            trimmed.dropLast(1) + "ه"
        } else if (trimmed.endsWith("ي")) {
            trimmed.dropLast(1) + "ى"
        } else if (trimmed.endsWith("ى")) {
            trimmed.dropLast(1) + "ي"
        } else {
            ""
        }
    }
}
