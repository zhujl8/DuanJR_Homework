package site.duanjr.homework.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import site.duanjr.homework.R
import site.duanjr.homework.api.Api
import site.duanjr.homework.api.ApiException
import site.duanjr.homework.databinding.ActivityLoginBinding

/**
 * 学生登录：班级 → 学生 → 家长电话尾号后两位 PIN。
 * 提示语由无鉴权的 /api/hint/{id} 提供（尾号前两位）。
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private var classes: List<String> = emptyList()
    private var students = emptyList<site.duanjr.homework.api.Student>()
    private var selectedStudent: site.duanjr.homework.api.Student? = null
    private var currentHint: String? = null
    private var checking = false

    private val uiPrefs by lazy { getSharedPreferences("ui", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.pinInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                binding.pinLayout.error = null
            }
        })

        binding.loginButton.setOnClickListener { attemptLogin() }

        if (Api.hasSession()) {
            // 已有会话：静默校验，有效则直接进入
            binding.progress.visibility = View.VISIBLE
            lifecycleScope.launch {
                runCatching { Api.config() }.fold(
                    onSuccess = { cfg ->
                        if (cfg.student != null) {
                            goToMain()
                        } else {
                            Api.clearSession()
                            setupForm(cfg)
                        }
                    },
                    onFailure = { e ->
                        setupForm(loadConfigCached())
                        showError(e.message ?: "加载失败，请下拉重试")
                    }
                )
            }
        } else {
            binding.progress.visibility = View.VISIBLE
            lifecycleScope.launch {
                runCatching { Api.config() }.fold(
                    onSuccess = { setupForm(it) },
                    onFailure = { setupForm(loadConfigCached()) }
                )
            }
        }
    }

    private fun loadConfigCached(): site.duanjr.homework.api.Config {
        // 无网络兜底：用最近一次缓存的花名册展示（登录仍需网络）
        val json = uiPrefs.getString("config_cache", null)
        return if (json != null) {
            runCatching {
                org.json.JSONObject(json).let { site.duanjr.homework.api.Parsers.config(it) }
            }.getOrDefault(emptyConfig())
        } else emptyConfig()
    }

    private fun emptyConfig() = site.duanjr.homework.api.Config(
        students = emptyList(), assignments = emptyList(), dates = emptyList(),
        quizEnabled = false, quizDates = emptyList(), archivedSubjects = emptyList(),
        weekDate = "", today = null, timezone = null, student = null
    )

    private fun setupForm(cfg: site.duanjr.homework.api.Config) {
        binding.progress.visibility = View.GONE
        binding.form.visibility = View.VISIBLE
        students = cfg.students
        classes = students.map { it.classroom }.distinct().sorted()

        val classAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, classes)
        binding.classInput.setAdapter(classAdapter)

        val lastId = uiPrefs.getString("last_student", null)
        val last = students.firstOrNull { it.id == lastId }

        if (last != null) {
            binding.classInput.setText(last.classroom, false)
            applyStudentList(last.classroom)
            binding.studentInput.setText("${last.number}. ${last.name}", false)
            selectStudent(last)
        } else if (classes.isNotEmpty()) {
            binding.classInput.setText(classes.first(), false)
            applyStudentList(classes.first())
        }

        binding.classInput.setOnItemClickListener { _, _, position, _ ->
            val cls = classes.getOrNull(position) ?: return@setOnItemClickListener
            applyStudentList(cls)
        }
        binding.studentInput.setOnItemClickListener { _, _, position, _ ->
            binding.studentLayout.error = null
            val s = studentOptions().getOrNull(position) ?: return@setOnItemClickListener
            selectStudent(s)
        }
    }

    private fun studentOptions(): List<site.duanjr.homework.api.Student> =
        students.filter { it.classroom == binding.classInput.text.toString() }
            .sortedBy { it.number }

    private fun applyStudentList(cls: String) {
        val opts = students.filter { it.classroom == cls }.sortedBy { it.number }
        val names = opts.map { "${it.number}. ${it.name}" }
        binding.studentInput.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, names))
        binding.studentInput.setText("", false)
        selectedStudent = null
        currentHint = null
        updateHintText()
    }

    private fun selectStudent(s: site.duanjr.homework.api.Student) {
        selectedStudent = s
        uiPrefs.edit().putString("last_student", s.id).apply()
        currentHint = null
        updateHintText()
        lifecycleScope.launch {
            runCatching { Api.hint(s.id) }.onSuccess { hint ->
                if (selectedStudent?.id == s.id) {
                    currentHint = hint
                    updateHintText()
                }
            }
        }
    }

    private fun updateHintText() {
        val hint = currentHint
        binding.hintText.text = if (hint != null) {
            "家长电话尾号 $hint __，请填写最后两位"
        } else {
            "家长电话尾号（输入学生后自动提示前两位）"
        }
    }

    private fun attemptLogin() {
        if (checking) return
        val student = selectedStudent ?: run {
            binding.studentLayout.error = "请先选择学生"
            return
        }
        val pin = binding.pinInput.text?.toString()?.trim() ?: ""
        if (!Regex("^\\d{2}$").matches(pin)) {
            binding.pinLayout.error = "请输入电话尾号的最后两位数字"
            return
        }
        checking = true
        binding.loginButton.isEnabled = false
        binding.loginProgress.visibility = View.VISIBLE
        lifecycleScope.launch {
            runCatching { Api.login(student.id, pin) }.fold(
                onSuccess = {
                    // 缓存 config 供离线兜底
                    runCatching {
                        val cfg = Api.config()
                        uiPrefs.edit().putString("config_cache", org.json.JSONObject().apply {
                            put("students", org.json.JSONArray().apply {
                                cfg.students.forEach { s ->
                                    put(org.json.JSONObject().apply {
                                        put("id", s.id); put("classroom", s.classroom)
                                        put("number", s.number); put("name", s.name)
                                    })
                                }
                            })
                        }.toString()).apply()
                    }
                    goToMain()
                },
                onFailure = { e ->
                    checking = false
                    binding.loginButton.isEnabled = true
                    binding.loginProgress.visibility = View.GONE
                    val msg = when (e) {
                        is ApiException -> e.message ?: "登录失败"
                        else -> "网络异常，请检查网络后重试"
                    }
                    MaterialAlertDialogBuilder(this@LoginActivity)
                        .setTitle("无法进入")
                        .setMessage(msg)
                        .setPositiveButton("知道了", null)
                        .show()
                }
            )
        }
    }

    private fun showError(msg: String) {
        Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
