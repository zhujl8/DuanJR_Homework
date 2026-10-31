package site.duanjr.homework.api

import org.json.JSONArray
import org.json.JSONObject

/** 学生 */
data class Student(
    val id: String,
    val classroom: String,
    val number: Int,
    val name: String,
    val mentee: Boolean = false
)

/** 单项作业（来自 /api/config.assignments） */
data class Assignment(
    val subject: String,
    val label: String,
    val available: Boolean,
    val submitted: Boolean,
    val status: String?,
    val dueAt: Double?,
    val opensAt: Double?,
    val late: Boolean,
    val overdue: Boolean,
    val draft: Boolean,
    val preview: String?
)

/** 照片 */
data class PhotoInfo(
    val id: String,
    val file: String,
    val name: String?,
    val size: Long,
    val sha256: String?,
    val url: String,
    val thumbnail: String,
    val uploadedAt: Double?
) : java.io.Serializable

/** 提交历史条目 */
data class SubmissionHistoryEntry(
    val at: Double?,
    val revision: Int,
    val photoIds: List<String>,
    val lateReason: String?
)

/** 某天某科目的作业状态（/api/mine、/api/homework/... 的 item） */
data class HomeworkItem(
    val date: String,
    val subject: String,
    val status: String,
    val available: Boolean,
    val draft: Boolean,
    val submitted: Boolean,
    val count: Int,
    val revision: Int,
    val dueAt: Double?,
    val opensAt: Double?,
    val late: Boolean,
    val overdue: Boolean,
    val lateReasonRequired: Boolean,
    val lateReason: String?,
    val photosRetired: Boolean,
    val photos: List<PhotoInfo>,
    val submissionHistory: List<SubmissionHistoryEntry>,
    val submittedAt: Double?,
    val lastSubmittedAt: Double?,
    val updatedAt: Double?,
    val quizRequired: Boolean = false
)

/** /api/config 响应 */
data class Config(
    val serverTime: Double? = null,
    val students: List<Student>,
    val assignments: List<Assignment>,
    val dates: List<String>,
    val quizEnabled: Boolean,
    val quizDates: List<String>,
    val archivedSubjects: List<String>,
    val weekDate: String,
    val today: String?,
    val timezone: String?,
    val student: Student? // 非空表示当前 session 已登录
)

/** /api/math-upload/prepare 响应 */
data class PrepareInfo(
    val required: Boolean,
    val waitSeconds: Int,
    val id: String?,
    val first: Boolean
)

/** 上传单张照片后的完整回包（含 duplicate 标记） */
class UploadResponse(
    val item: HomeworkItem,
    val duplicate: Boolean
)

class SubmitOutcome(
    val item: HomeworkItem,
    val quizRequired: Boolean
)

/** 小测一轮答题记录 */
data class QuizRound(
    val number: Int,
    val score: Int,
    val total: Int,
    val timeouts: Int,
    val completed: Boolean,
    val passed: Boolean
)

data class QuizSummary(
    val required: Boolean,
    val passed: Boolean,
    val state: String?,
    val failures: Int,
    val passedAttempt: Int?,
    val rounds: List<QuizRound>
)

data class Review(
    val grade: String?,
    val summary: String?,
    val strengths: List<String>,
    val concerns: List<String>,
    val suggestions: List<String>,
    val nameCheck: String?,
    val signatureCheck: String?,
    val publishedAt: String?,
    val photosUpdated: Boolean
)

data class FeedbackData(
    val date: String?,
    val dates: List<String>,
    val quiz: QuizSummary?,
    val review: Review?,
    val student: Student?
)

object Parsers {
    fun student(o: JSONObject): Student = Student(
        id = o.optString("id"),
        classroom = o.optString("classroom"),
        number = o.optInt("number"),
        name = o.optString("name"),
        mentee = o.optBoolean("mentee", false)
    )

    fun assignment(o: JSONObject): Assignment = Assignment(
        subject = o.optString("subject"),
        label = o.optString("label"),
        available = o.optBoolean("available", false),
        submitted = o.optBoolean("submitted", false),
        status = if (o.isNull("status")) null else o.optString("status"),
        dueAt = if (o.isNull("due_at")) null else o.optDouble("due_at"),
        opensAt = if (o.isNull("opens_at")) null else o.optDouble("opens_at"),
        late = o.optBoolean("late", false),
        overdue = o.optBoolean("overdue", false),
        draft = o.optBoolean("draft", false),
        preview = if (o.isNull("preview")) null else o.optString("preview")
    )

    fun photo(o: JSONObject): PhotoInfo = PhotoInfo(
        id = o.optString("id"),
        file = o.optString("file"),
        name = if (o.isNull("name")) null else o.optString("name"),
        size = o.optLong("size"),
        sha256 = if (o.isNull("sha256")) null else o.optString("sha256"),
        url = o.optString("url"),
        thumbnail = o.optString("thumbnail"),
        uploadedAt = if (o.isNull("uploaded_at")) null else o.optDouble("uploaded_at")
    )

    fun historyEntry(o: JSONObject): SubmissionHistoryEntry = SubmissionHistoryEntry(
        at = if (o.isNull("at")) null else o.optDouble("at"),
        revision = o.optInt("revision"),
        photoIds = o.optJSONArray("photo_ids")?.let { ja ->
            (0 until ja.length()).map { ja.optString(it) }
        } ?: emptyList(),
        lateReason = if (o.isNull("late_reason")) null else o.optString("late_reason")
    )

    fun homeworkItem(o: JSONObject): HomeworkItem = HomeworkItem(
        date = o.optString("date"),
        subject = o.optString("subject"),
        status = o.optString("status", ""),
        available = o.optBoolean("available", false),
        draft = o.optBoolean("draft", false),
        submitted = o.optBoolean("submitted", false),
        count = o.optInt("count", 0),
        revision = o.optInt("revision", 0),
        dueAt = if (o.isNull("due_at")) null else o.optDouble("due_at"),
        opensAt = if (o.isNull("opens_at")) null else o.optDouble("opens_at"),
        late = o.optBoolean("late", false),
        overdue = o.optBoolean("overdue", false),
        lateReasonRequired = o.optBoolean("late_reason_required", false),
        lateReason = if (o.isNull("late_reason")) null else o.optString("late_reason"),
        photosRetired = o.optBoolean("photos_retired", false),
        photos = o.optJSONArray("photos")?.let { ja ->
            (0 until ja.length()).map { photo(ja.getJSONObject(it)) }
        } ?: emptyList(),
        submissionHistory = o.optJSONArray("submission_history")?.let { ja ->
            (0 until ja.length()).map { historyEntry(ja.getJSONObject(it)) }
        } ?: emptyList(),
        submittedAt = if (o.isNull("submitted_at")) null else o.optDouble("submitted_at"),
        lastSubmittedAt = if (o.isNull("last_submitted_at")) null else o.optDouble("last_submitted_at"),
        updatedAt = if (o.isNull("updated_at")) null else o.optDouble("updated_at"),
        quizRequired = o.optBoolean("quiz_required", false)
    )

    fun config(o: JSONObject): Config = Config(
        students = o.optJSONArray("students")?.let { ja ->
            (0 until ja.length()).map { student(ja.getJSONObject(it)) }
        } ?: emptyList(),
        assignments = o.optJSONArray("assignments")?.let { ja ->
            (0 until ja.length()).map { assignment(ja.getJSONObject(it)) }
        } ?: emptyList(),
        dates = o.optJSONArray("dates")?.let { ja ->
            (0 until ja.length()).map { ja.optString(it) }
        } ?: emptyList(),
        quizEnabled = o.optBoolean("quiz_enabled", false),
        quizDates = o.optJSONArray("quiz_dates")?.let { ja ->
            (0 until ja.length()).map { ja.optString(it) }
        } ?: emptyList(),
        archivedSubjects = o.optJSONArray("archived_subjects")?.let { ja ->
            (0 until ja.length()).map { ja.optString(it) }
        } ?: emptyList(),
        weekDate = o.optString("week_date"),
        serverTime = if (o.isNull("server_time")) null else o.optDouble("server_time"),
        today = if (o.isNull("today")) null else o.optString("today"),
        timezone = if (o.isNull("timezone")) null else o.optString("timezone"),
        student = if (o.isNull("student") || o.optJSONObject("student") == null) null
        else student(o.optJSONObject("student"))
    )

    fun quizSummary(o: JSONObject?): QuizSummary? = if (o == null) null else QuizSummary(
        required = o.optBoolean("required", false),
        passed = o.optBoolean("passed", false),
        state = if (o.isNull("state")) null else o.optString("state"),
        failures = o.optInt("failures", 0),
        passedAttempt = if (o.isNull("passed_attempt")) null else o.optInt("passed_attempt"),
        rounds = o.optJSONArray("rounds")?.let { ja: JSONArray ->
            (0 until ja.length()).map { i ->
                val r = ja.getJSONObject(i)
                QuizRound(
                    number = r.optInt("number", i + 1),
                    score = r.optInt("score", 0),
                    total = r.optInt("total", 0),
                    timeouts = r.optInt("timeouts", 0),
                    completed = r.optBoolean("completed", false),
                    passed = r.optBoolean("passed", false)
                )
            }
        } ?: emptyList()
    )

    fun review(o: JSONObject?): Review? = if (o == null) null else Review(
        grade = if (o.isNull("grade")) null else o.optString("grade"),
        summary = if (o.isNull("summary")) null else o.optString("summary"),
        strengths = o.optJSONArray("strengths")?.let { ja ->
            (0 until ja.length()).map { ja.optString(it) }
        } ?: emptyList(),
        concerns = o.optJSONArray("concerns")?.let { ja ->
            (0 until ja.length()).map { ja.optString(it) }
        } ?: emptyList(),
        suggestions = o.optJSONArray("suggestions")?.let { ja ->
            (0 until ja.length()).map { ja.optString(it) }
        } ?: emptyList(),
        nameCheck = if (o.isNull("name_check")) null else o.optString("name_check"),
        signatureCheck = if (o.isNull("signature_check")) null else o.optString("signature_check"),
        publishedAt = if (o.isNull("published_at")) null else o.optString("published_at"),
        photosUpdated = o.optBoolean("photos_updated", false)
    )

    fun feedback(o: JSONObject): FeedbackData = FeedbackData(
        date = if (o.isNull("date")) null else o.optString("date"),
        dates = o.optJSONArray("dates")?.let { ja ->
            (0 until ja.length()).map { ja.optString(it) }
        } ?: emptyList(),
        quiz = quizSummary(if (o.isNull("quiz")) null else o.optJSONObject("quiz")),
        review = review(if (o.isNull("review")) null else o.optJSONObject("review")),
        student = if (o.isNull("student")) null else student(o.optJSONObject("student"))
    )

    fun photosJson(photos: List<PhotoInfo>): String {
        val ja = JSONArray()
        photos.forEach { p ->
            ja.put(JSONObject().apply {
                put("id", p.id)
                put("file", p.file)
                put("name", p.name ?: JSONObject.NULL)
                put("size", p.size)
                put("url", p.url)
                put("thumbnail", p.thumbnail)
                put("uploaded_at", p.uploadedAt ?: JSONObject.NULL)
            })
        }
        return ja.toString()
    }

    fun photosFromJson(s: String): List<PhotoInfo> {
        val ja = JSONArray(s)
        return (0 until ja.length()).map { photo(ja.getJSONObject(it)) }
    }
}
