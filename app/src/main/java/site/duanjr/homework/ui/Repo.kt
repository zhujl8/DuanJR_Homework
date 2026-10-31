package site.duanjr.homework.ui

import site.duanjr.homework.api.Config
import site.duanjr.homework.api.HomeworkItem
import site.duanjr.homework.api.Student

/** 会话内共享的数据缓存（MainActivity 负责首次加载，各页面按需刷新） */
object Repo {
    var config: Config? = null
    var items: List<HomeworkItem> = emptyList()
    var serverTime: Double? = null

    val student: Student? get() = config?.student

    /** 本周各项作业状态（date == week_date） */
    fun weekItems(): List<HomeworkItem> {
        val wd = config?.weekDate ?: return emptyList()
        return items.filter { it.date == wd }
    }

    fun itemFor(subject: String, date: String? = null): HomeworkItem? {
        val d = date ?: config?.weekDate
        return items.firstOrNull { it.subject == subject && (d == null || it.date == d) }
    }

    fun clear() {
        config = null
        items = emptyList()
    }
}
