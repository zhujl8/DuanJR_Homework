package site.duanjr.homework.ui.feedback

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import site.duanjr.homework.R
import site.duanjr.homework.api.Api
import site.duanjr.homework.api.FeedbackData
import site.duanjr.homework.api.QuizRound
import site.duanjr.homework.databinding.FragmentFeedbackBinding
import site.duanjr.homework.ui.MainActivity
import site.duanjr.homework.util.Fmt

/** 历史评语 + 小测记录（按日期查看） */
class FeedbackFragment : Fragment() {

    private var _binding: FragmentFeedbackBinding? = null
    private val binding get() = _binding!!

    private var data: FeedbackData? = null
    private var loading = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFeedbackBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.swipe.setOnRefreshListener { data?.dates?.let { load(selectedDate) } ?: load(null) }
        if (data == null) load(null)
    }

    override fun onResume() {
        super.onResume()
        if (data == null && !loading) load(null)
    }

    private val selectedDate: String?
        get() = data?.date

    private fun load(date: String?) {
        loading = true
        binding.swipe.isRefreshing = true
        binding.contentView.visibility = View.GONE
        binding.emptyView.visibility = View.GONE
        lifecycleScope.launch {
            try {
                val fb = Api.feedback(date)
                data = fb
                render(fb)
            } catch (e: Exception) {
                (activity as? MainActivity)?.handleFailure(e)
            } finally {
                loading = false
                binding.swipe.isRefreshing = false
            }
        }
    }

    // ---------------------------------------------------------------- 渲染

    private fun render(fb: FeedbackData) {
        renderDateChips(fb)

        val review = fb.review
        if (review == null || (review.summary.isNullOrBlank() && review.grade == null)) {
            binding.contentView.visibility = View.GONE
            binding.emptyView.visibility = View.VISIBLE
            binding.emptyView.text = "${Fmt.dateOnly(fb.date)} 暂无教师评价"
            return
        }
        binding.contentView.visibility = View.VISIBLE
        binding.emptyView.visibility = View.GONE

        binding.gradeChip.text = review.grade ?: "已评价"
        binding.gradeChip.setChipBackgroundColorResource(
            when {
                review.grade?.contains("优秀") == true -> R.color.chipOkBg
                review.grade?.contains("努力") == true || review.grade?.contains("需") == true -> R.color.chipPendingBg
                else -> R.color.chipNoneBg
            }
        )
        binding.publishedText.text = review.publishedAt?.let {
            "评价发布于 " + it.replace("T", " ").take(16)
        } ?: "评价已发布"

        binding.summaryText.text = review.summary ?: ""

        fillList(binding.strengthsList, review.strengths, R.color.statusOk, "亮点")
        fillList(binding.concernsList, review.concerns, R.color.statusOverdue, "关注")
        fillList(binding.suggestionsList, review.suggestions, R.color.secondaryVariant, "建议")
        binding.strengthsCard.visibility =
            if (review.strengths.isEmpty()) View.GONE else View.VISIBLE
        binding.concernsCard.visibility =
            if (review.concerns.isEmpty()) View.GONE else View.VISIBLE
        binding.suggestionsCard.visibility =
            if (review.suggestions.isEmpty()) View.GONE else View.VISIBLE

        binding.nameCheckText.text = "姓名栏：${review.nameCheck ?: "—"}"
        binding.nameCheckDot.backgroundTintList = ContextCompat.getColorStateList(
            requireContext(), checkColor(review.nameCheck)
        )
        binding.signatureCheckText.text = "家长签名：${review.signatureCheck ?: "—"}"
        binding.signatureCheckDot.backgroundTintList = ContextCompat.getColorStateList(
            requireContext(), checkColor(review.signatureCheck)
        )
        binding.checksCard.visibility =
            if (review.nameCheck.isNullOrBlank() && review.signatureCheck.isNullOrBlank()) View.GONE else View.VISIBLE

        if (review.photosUpdated) {
            binding.photosUpdatedText.visibility = View.VISIBLE
        } else {
            binding.photosUpdatedText.visibility = View.GONE
        }

        renderQuiz(fb.quiz)
    }

    /** "未见填写" 之类视为待改进（红），其余描述视为已落实（绿） */
    private fun checkColor(v: String?): Int = when {
        v.isNullOrBlank() -> R.color.statusNone
        v.contains("未见") || v.contains("未填") || v.contains("缺") -> R.color.statusOverdue
        else -> R.color.statusOk
    }

    private fun fillList(container: LinearLayout, texts: List<String>, colorRes: Int, label: String) {
        container.removeAllViews()
        texts.forEach { text ->
            val row = layoutInflater.inflate(R.layout.item_feedback_bullet, container, false)
            val dot = row.findViewById<View>(R.id.dot)
            dot.backgroundTintList = ContextCompat.getColorStateList(requireContext(), colorRes)
            row.findViewById<TextView>(R.id.bulletText).text = text
            container.addView(row)
        }
    }

    private fun renderDateChips(fb: FeedbackData) {
        binding.dateChips.removeAllViews()
        fb.dates.reversed().forEach { date ->
            val chip = layoutInflater.inflate(
                R.layout.item_date_chip, binding.dateChips, false
            ) as Chip
            chip.text = Fmt.dateOnly(date)
            chip.id = View.generateViewId()
            chip.isChecked = date == fb.date
            chip.setOnCheckedChangeListener { _, checked ->
                if (checked && date != selectedDate) load(date)
            }
            binding.dateChips.addView(chip)
        }
    }

    private fun renderQuiz(quiz: site.duanjr.homework.api.QuizSummary?) {
        if (quiz == null || !quiz.required) {
            binding.quizCard.visibility = View.GONE
            return
        }
        binding.quizCard.visibility = View.VISIBLE
        val passed = quiz.passed
        binding.quizStateChip.text = when (quiz.state) {
            "passed" -> "小测已通过"
            "failed" -> "小测未通过"
            else -> "小测未完成"
        }
        binding.quizStateChip.setChipBackgroundColorResource(
            if (passed) R.color.statusOk else R.color.statusOverdue
        )
        binding.quizHeaderInfo.text = buildString {
            if (passed) append("第 ${quiz.passedAttempt} 次尝试通过")
            else append("尚未通过")
            if (quiz.failures > 0) append(" · 失败 ${quiz.failures} 次")
        }
        binding.quizRounds.removeAllViews()
        quiz.rounds.forEach { round ->
            val row = layoutInflater.inflate(R.layout.item_quiz_round, binding.quizRounds, false)
            row.findViewById<TextView>(R.id.roundTitle).text = "第 ${round.number} 次检测"
            row.findViewById<TextView>(R.id.roundResult).text = buildString {
                append("得分 ${round.score} / ${round.total}")
                if (round.timeouts > 0) append(" · 超时 ${round.timeouts} 题")
                append(if (round.passed) " · 通过" else if (round.completed) " · 未通过" else " · 未完成")
            }
            val color = if (round.passed) R.color.statusOk else R.color.statusOverdue
            row.findViewById<View>(R.id.roundDot).backgroundTintList =
                ContextCompat.getColorStateList(requireContext(), color)
            binding.quizRounds.addView(row)
        }
        if (quiz.rounds.isEmpty()) {
            binding.quizRounds.addView(
                TextView(requireContext()).apply {
                    text = "暂无检测记录"
                    setPadding(0, 8, 0, 8)
                }
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
