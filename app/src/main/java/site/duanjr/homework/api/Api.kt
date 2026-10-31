package site.duanjr.homework.api

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** 会话过期（401） */
class SessionExpiredException(message: String = "登录已过期，请重新验证") : Exception(message)

/** 通用业务错误（服务器返回 {error:...} 或网络异常） */
class ApiException(val httpCode: Int, message: String) : Exception(message)

/**
 * duanjr.site 数学作业上交系统 API 客户端。
 *
 * 会话基于 Cookie（homework_session，HttpOnly，路径 /tools/upload_homework）。
 * 所有 /api/ JSON 请求必须携带自定义头 X-Homework-Request: 1（与网页版一致）。
 * /api/image/... 图片请求不带该头，但同样依赖 Cookie。
 */
object Api {

    const val BASE = "https://duanjr.site/tools/upload_homework"
    const val ORIGIN = "https://duanjr.site"

    private const val PREFS = "homework_session"
    private const val KEY_COOKIE = "cookie_line"

    private lateinit var prefs: SharedPreferences

    /** API 请求客户端（带 X-Homework-Request 头与 Cookie） */
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(headerInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    /** 图片加载客户端（Cookie 必需，但不添加自定义头，与网页 <img> 行为一致） */
    val imageClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /** 统一为 /api/ 请求附加 X-Homework-Request: 1 */
    private val headerInterceptor = Interceptor { chain ->
        val req = chain.request()
        val built = if (req.url.encodedPath.startsWith("/tools/upload_homework/api/")) {
            req.newBuilder().header("X-Homework-Request", "1").build()
        } else req
        chain.proceed(built)
    }

    /** 持久化 CookieJar：仅保存 homework_session，写入 SharedPreferences */
    private val cookieJar = object : CookieJar {
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val saved = prefs.getString(KEY_COOKIE, null) ?: return emptyList()
            val c = Cookie.parse(url, saved) ?: return emptyList()
            // 服务器 cookie 路径为 /tools/upload_homework，图片与 API 均在其下
            return if (c.matches(url)) listOf(c) else emptyList()
        }

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val session = cookies.firstOrNull { it.name == "homework_session" } ?: return
            val line = "${session.name}=${session.value}"
            prefs.edit().putString(KEY_COOKIE, line).apply()
        }
    }

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    /** 当前登录学生 id（由 login/config/mine 响应维护，用于作业路径 {sid}） */
    @Volatile
    var studentId: String? = null

    private fun sid(): String =
        studentId ?: throw SessionExpiredException("会话尚未建立，请重新登录")

    fun clearSession() {
        prefs.edit().remove(KEY_COOKIE).apply()
    }

    fun hasSession(): Boolean = prefs.getString(KEY_COOKIE, null) != null

    // ---------------------------------------------------------------- helpers

    private fun url(path: String): HttpUrl = (BASE + path).toHttpUrl()

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private suspend fun execute(request: Request): JSONObject = withContext(Dispatchers.IO) {
        val resp = try {
            client.newCall(request).execute()
        } catch (e: Exception) {
            throw ApiException(-1, "网络连接失败，请检查网络后重试")
        }
        resp.use { r ->
            val body = r.body?.string() ?: ""
            val json = if (body.isBlank()) JSONObject() else runCatching { JSONObject(body) }
                .getOrElse { JSONObject() }
            if (r.code == 401) {
                clearSession()
                throw SessionExpiredException(json.optString("error", "登录已过期，请重新验证"))
            }
            if (!r.isSuccessful) {
                throw ApiException(r.code, json.optString("error", "请求失败（${r.code}）"))
            }
            json
        }
    }

    private suspend fun getJson(path: String): JSONObject =
        execute(Request.Builder().url(url(path)).get().build())

    private suspend fun postJson(path: String, body: String): JSONObject =
        execute(
            Request.Builder().url(url(path))
                .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
        )

    private suspend fun putJson(path: String, body: String): JSONObject =
        execute(
            Request.Builder().url(url(path))
                .put(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
        )

    private suspend fun delete(path: String): JSONObject =
        execute(Request.Builder().url(url(path)).delete().build())

    // ---------------------------------------------------------------- endpoints

    /** 班级花名册、作业列表、week_date；已登录时含 student */
    suspend fun config(): Config {
        val o = getJson("/api/config")
        val c = Parsers.config(o)
        c.student?.let { this.studentId = it.id }
        return c
    }

    /** 学生登录。成功返回学生信息，失败抛出 ApiException。 */
    suspend fun login(studentId: String, pin: String): Student {
        val o = postJson("/api/login", JSONObject().put("student", studentId).put("pin", pin).toString())
        val s = o.optJSONObject("student") ?: throw ApiException(0, "登录失败，请稍后重试")
        this.studentId = s.optString("id")
        return Parsers.student(s)
    }

    suspend fun logout() {
        runCatching { postJson("/api/logout", "{}") }
        clearSession()
    }

    /** 无鉴权：任意学生家长电话尾号前两位（用于登录提示） */
    suspend fun hint(studentId: String): String {
        val o = getJson("/api/hint/" + enc(studentId))
        return o.optString("hint")
    }

    /** 当前 session 学生的全部提交记录（含所有日期） */
    suspend fun mine(): Pair<Student?, List<HomeworkItem>> {
        val o = getJson("/api/mine")
        val student = if (o.isNull("student") || o.optJSONObject("student") == null) null
        else Parsers.student(o.optJSONObject("student"))
        val items = o.optJSONArray("items")?.let { ja ->
            (0 until ja.length()).map { Parsers.homeworkItem(ja.getJSONObject(it)) }
        } ?: emptyList()
        student?.let { this.studentId = it.id }
        return student to items
    }

    /** 上传前提醒确认（数学在 quiz 日需要二次确认） */
    suspend fun prepareUpload(date: String, subject: String): PrepareInfo {
        val o = postJson(
            "/api/math-upload/prepare",
            JSONObject().put("date", date).put("subject", subject).toString()
        )
        return PrepareInfo(
            required = o.optBoolean("required", false),
            waitSeconds = o.optInt("wait_seconds", 0),
            id = if (o.isNull("id")) null else o.optString("id"),
            first = o.optBoolean("first", false)
        )
    }

    /** 二次确认，返回 quiz_ack token（提交时需要） */
    suspend fun confirmUpload(prepareId: String?): String {
        val body = JSONObject()
        prepareId?.let { body.put("id", it) }
        val o = postJson("/api/math-upload/confirm", body.toString())
        return o.optString("token", "")
    }

    /** 上传一张照片（multipart: photo，可选 replace=<照片id>）。duplicate=true 表示服务器判定重复已跳过。 */
    suspend fun uploadPhoto(
        date: String,
        subject: String,
        file: File,
        displayName: String,
        replace: String? = null
    ): UploadResponse {
        val multipart: RequestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "photo", displayName,
                file.asRequestBody("image/jpeg".toMediaType())
            )
            .apply { replace?.let { addFormDataPart("replace", it) } }
            .build()
        val req = Request.Builder()
            .url(url("/api/homework/${enc(sid())}/${enc(date)}/${enc(subject)}"))
            .post(multipart)
            .build()
        val o = execute(req)
        return UploadResponse(Parsers.homeworkItem(o), o.optBoolean("duplicate", false))
    }

    /** 删除某张照片 */
    suspend fun deletePhoto(date: String, subject: String, photoId: String): HomeworkItem {
        val o = delete("/api/homework/${enc(sid())}/${enc(date)}/${enc(subject)}/${enc(photoId)}")
        return Parsers.homeworkItem(o)
    }

    /** 调整照片顺序（传入完整 id 列表） */
    suspend fun reorder(date: String, subject: String, ids: List<String>): HomeworkItem {
        val o = putJson(
            "/api/homework/${enc(sid())}/${enc(date)}/${enc(subject)}/order",
            JSONObject().put("ids", org.json.JSONArray(ids)).toString()
        )
        return Parsers.homeworkItem(o)
    }

    /** 提交作业（乐观锁 revision；quiz 日需要 quizAck；迟交需 lateReason） */
    suspend fun submit(
        date: String,
        subject: String,
        revision: Int,
        quizAck: String? = null,
        lateReason: String? = null
    ): SubmitOutcome {
        val body = JSONObject().put("revision", revision)
        quizAck?.takeIf { it.isNotBlank() }?.let { body.put("quiz_ack", it) }
        lateReason?.takeIf { it.isNotBlank() }?.let { body.put("late_reason", it) }
        val o = postJson("/api/homework/${enc(sid())}/${enc(date)}/${enc(subject)}/submit", body.toString())
        return SubmitOutcome(Parsers.homeworkItem(o), o.optBoolean("quiz_required", false))
    }

    /** 历史评语与小测记录 */
    suspend fun feedback(date: String? = null): FeedbackData {
        val p = if (date.isNullOrBlank()) "/api/feedback" else "/api/feedback?date=" + enc(date)
        return Parsers.feedback(getJson(p))
    }

    /** 照片列表 <-> JSON（跨页面传递用） */
    fun photosJson(photos: List<PhotoInfo>): String = Parsers.photosJson(photos)

    fun photosFromJson(s: String): List<PhotoInfo> = Parsers.photosFromJson(s)

    /** 单项作业详情（登录学生的） */
    suspend fun homeworkItem(date: String, subject: String): HomeworkItem {
        val o = getJson("/api/homework/${enc(sid())}/${enc(date)}/${enc(subject)}")
        return Parsers.homeworkItem(o)
    }
}
