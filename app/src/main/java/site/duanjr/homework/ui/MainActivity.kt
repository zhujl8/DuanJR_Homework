package site.duanjr.homework.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.fragment.app.Fragment
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import site.duanjr.homework.R
import site.duanjr.homework.api.Api
import site.duanjr.homework.api.SessionExpiredException
import site.duanjr.homework.databinding.ActivityMainBinding
import site.duanjr.homework.ui.feedback.FeedbackFragment
import site.duanjr.homework.ui.homework.HomeworkFragment
import site.duanjr.homework.ui.records.RecordsFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var homeworkFragment: HomeworkFragment
    private lateinit var recordsFragment: RecordsFragment
    private lateinit var feedbackFragment: FeedbackFragment
    private var activeTag: String = "homework"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        if (Repo.student == null) {
            // 冷启动（进程被回收等）：先恢复会话数据
            binding.progress.visibility = android.view.View.VISIBLE
            lifecycleScope.launch {
                val cfg = runCatching { Api.config() }.getOrNull()
                val ok = cfg?.student != null
                if (cfg != null) {
                    Repo.config = cfg
                    Repo.serverTime = cfg.serverTime
                }
                if (ok) runCatching { Repo.items = Api.mine().second }
                binding.progress.visibility = android.view.View.GONE
                if (!ok) {
                    sessionExpired()
                    return@launch
                }
                binding.toolbar.subtitle = subtitle()
                bootFragments(savedInstanceState)
            }
        } else {
            binding.toolbar.subtitle = subtitle()
            bootFragments(savedInstanceState)
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_homework -> switchTo(homeworkFragment)
                R.id.nav_records -> switchTo(recordsFragment)
                R.id.nav_feedback -> switchTo(feedbackFragment)
            }
            true
        }
    }

    private fun bootFragments(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .add(R.id.fragmentContainer, FeedbackFragment(), "feedback").hide(supportFragmentManager.findFragmentByTag("feedback")!!)
                .add(R.id.fragmentContainer, RecordsFragment(), "records").hide(supportFragmentManager.findFragmentByTag("records")!!)
                .add(R.id.fragmentContainer, HomeworkFragment(), "homework")
                .commitNow()
        }
        homeworkFragment = supportFragmentManager.findFragmentByTag("homework") as HomeworkFragment
        recordsFragment = supportFragmentManager.findFragmentByTag("records") as RecordsFragment
        feedbackFragment = supportFragmentManager.findFragmentByTag("feedback") as FeedbackFragment
        switchTo(homeworkFragment)
    }

    private fun switchTo(fragment: Fragment) {
        val target = fragmentTag(fragment)
        if (target == activeTag) return
        val fm = supportFragmentManager
        val current = fm.findFragmentByTag(activeTag) ?: homeworkFragment
        fm.beginTransaction().hide(current).show(fragment).commitNowAllowingStateLoss()
        activeTag = target
    }

    private fun fragmentTag(fragment: Fragment): String = when (fragment) {
        is HomeworkFragment -> "homework"
        is RecordsFragment -> "records"
        is FeedbackFragment -> "feedback"
        else -> "homework"
    }

    fun subtitle(): String {
        val s = Repo.student ?: return ""
        return "${s.classroom} 班 · ${s.name}"
    }

    fun refreshBadge() {
        val pending = Repo.weekItems().count { !it.submitted || it.draft }
        val nav: BottomNavigationView = binding.bottomNav
        val itemId = R.id.nav_homework
        if (pending > 0) {
            val badge = nav.getOrCreateBadge(itemId)
            badge.number = pending
            badge.isVisible = true
            badge.badgeGravity = BadgeDrawable.TOP_END
        } else {
            nav.removeBadge(itemId)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_logout -> {
            confirmLogout()
            true
        }
        R.id.action_about -> {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.app_name)
                .setMessage(
                    "数学假期作业上交客户端\n\n" +
                        "对应网页版：duanjr.site/tools/upload_homework\n" +
                        "支持：照片上交与替换、排序、提交、历史记录与教师评语。\n" +
                        "本应用仅封装官方接口，数据与网页版完全同步。"
                )
                .setPositiveButton("知道了", null)
                .show()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun confirmLogout() {
        MaterialAlertDialogBuilder(this)
            .setTitle("切换学生")
            .setMessage("确定要退出当前登录吗？")
            .setPositiveButton("退出") { _, _ ->
                lifecycleScope.launch {
                    runCatching { Api.logout() }
                    Repo.clear()
                    startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                    finish()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 任何页面遇到 401：回登录页 */
    fun sessionExpired() {
        Repo.clear()
        Snackbar.make(binding.root, "登录已过期，请重新验证", Snackbar.LENGTH_SHORT).show()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    fun handleFailure(e: Throwable) {
        if (e is SessionExpiredException) {
            sessionExpired()
        } else {
            Snackbar.make(binding.root, e.message ?: "操作失败，请重试", Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onBackPressed() {
        if (activeTag != "homework") {
            binding.bottomNav.selectedItemId = R.id.nav_homework
        } else {
            super.onBackPressed()
        }
    }
}
