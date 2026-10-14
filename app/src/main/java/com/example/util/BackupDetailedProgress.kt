package com.example.util

import java.util.Locale

/**
 * نموذج يصف حالة التقدم الدقيقة وتفاصيل كل خطوة وملف لحظة بلحظة أثناء النسخ والمزامنة
 */
data class BackupDetailedProgress(
    val percent: Int,
    val stage: String,
    val currentFileName: String? = null,
    val currentFileIndex: Int = 0,
    val totalFiles: Int = 0,
    val bytesUploaded: Long = 0L,
    val totalBytesToUpload: Long = 0L,
    val uploadSpeedBps: Long = 0L,
    val estimatedRemainingSeconds: Long = -1L
) {
    fun formatRemainingTimeArabic(): String {
        if (estimatedRemainingSeconds <= 0L) {
            return if (percent >= 100) "اكتمل الرفع" else "جاري الحساب..."
        }
        val mins = estimatedRemainingSeconds / 60
        val secs = estimatedRemainingSeconds % 60
        return when {
            mins > 0 && secs > 0 -> "الوقت المتبقي: $mins دقيقة و $secs ثانية"
            mins > 0 -> "الوقت المتبقي: حوالي $mins دقيقة"
            else -> "الوقت المتبقي: $secs ثانية"
        }
    }

    fun formatSpeedArabic(): String {
        if (uploadSpeedBps <= 0L) return ""
        val kbps = uploadSpeedBps / 1024.0
        return if (kbps >= 1024.0) {
            val mbps = kbps / 1024.0
            String.format(Locale.US, "السرعة: %.1f ميجابايت/ث", mbps)
        } else {
            String.format(Locale.US, "السرعة: %.0f كيلوبايت/ث", kbps)
        }
    }

    fun formatUploadedSizeArabic(): String {
        if (totalBytesToUpload <= 0L) return ""
        val uploadedMb = bytesUploaded / (1024.0 * 1024.0)
        val totalMb = totalBytesToUpload / (1024.0 * 1024.0)
        return String.format(Locale.US, "%.1f ميجابايت من أصل %.1f ميجابايت", uploadedMb, totalMb)
    }

    fun buildNotificationSubtext(): String {
        val parts = mutableListOf<String>()
        if (currentFileName != null && currentFileName.isNotBlank()) {
            val filePart = if (totalFiles > 0 && currentFileIndex > 0) {
                "الملف ($currentFileIndex/$totalFiles): $currentFileName"
            } else {
                "الملف: $currentFileName"
            }
            parts.add(filePart)
        }
        if (totalBytesToUpload > 0L && bytesUploaded > 0L) {
            parts.add(formatUploadedSizeArabic())
        }
        val remaining = formatRemainingTimeArabic()
        if (remaining != "جاري الحساب..." && remaining != "اكتمل الرفع") {
            parts.add(remaining)
        }
        val speed = formatSpeedArabic()
        if (speed.isNotBlank()) {
            parts.add(speed)
        }
        return parts.joinToString("\n")
    }
}
