package site.duanjr.homework.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** 时间与文案格式化（所有时间按北京时间显示） */
object Fmt {

    private val tz: TimeZone = TimeZone.getTimeZone("Asia/Shanghai")

    private fun fmt(pattern: String): SimpleDateFormat =
        SimpleDateFormat(pattern, Locale.CHINA).apply { timeZone = tz }

    /** 1793451600 -> "10月31日 23:00" */
    fun dayTime(epoch: Double?): String {
        if (epoch == null || epoch.isNaN()) return "—"
        return fmt("M月d日 HH:mm").format(Date((epoch * 1000).toLong()))
    }

    /** "2026-10-31" + HH:mm -> "10月31日 23:00" */
    fun dateTime(epoch: Double?): String = dayTime(epoch)

    /** "2026-10-31" -> "10月31日" */
    fun dateOnly(date: String): String {
        val m = Regex("""(\d{4})-(\d{2})-(\d{2})""").find(date) ?: return date
        val (y, mo, d) = m.destructured
        return "${mo.toInt()}月${d.toInt()}日"
    }

    fun fullDate(date: String): String = date

    /** 相对当前时间的友好描述（用于上传/提交时间） */
    fun relative(epoch: Double?, serverNow: Double? = null): String {
        if (epoch == null || epoch.isNaN()) return "—"
        val now = (serverNow ?: System.currentTimeMillis() / 1000.0)
        val diff = now - epoch
        return when {
            diff < 60 -> "刚刚"
            diff < 3600 -> "${(diff / 60).toInt()} 分钟前"
            diff < 86400 -> "${(diff / 3600).toInt()} 小时前"
            diff < 86400 * 30 -> "${(diff / 86400).toInt()} 天前"
            else -> dayTime(epoch)
        }
    }

    /** 截止时间倒计时，如 "距截止约 5 小时" */
    fun countdown(epoch: Double?, serverNow: Double? = null): String? {
        if (epoch == null || epoch.isNaN()) return null
        val now = serverNow ?: System.currentTimeMillis() / 1000.0
        val left = epoch - now
        if (left <= 0) return "已过截止时间"
        return when {
            left < 3600 -> "距截止还有 ${((left / 60).toInt())} 分钟"
            left < 86400 -> {
                val h = (left / 3600).toInt()
                val m = ((left % 3600) / 60).toInt()
                if (m > 0) "距截止还有 $h 小时 $m 分" else "距截止还有 $h 小时"
            }
            else -> "距截止还有 ${(left / 86400).toInt()} 天"
        }
    }

    /** 服务器 today 是否为周一等，不需要；仅提供当前日期（北京时间） */
    fun todayInShanghai(): String {
        return fmt("yyyy-MM-dd").format(Date())
    }

    /** photo size -> "6.5MB" */
    fun size(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1fMB", bytes / 1024.0 / 1024.0)
        bytes >= 1024 -> String.format(Locale.US, "%dKB", bytes / 1024)
        else -> "${bytes}B"
    }

    /** 星期几：epoch -> "周六" */
    fun weekdayOf(date: String): String {
        val m = Regex("""(\d{4})-(\d{2})-(\d{2})""").find(date) ?: return ""
        val (y, mo, d) = m.destructured
        val cal = Calendar.getInstance(tz).apply {
            set(y.toInt(), mo.toInt() - 1, d.toInt(), 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val names = arrayOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")
        return names[cal.get(Calendar.DAY_OF_WEEK) - 1]
    }
}
