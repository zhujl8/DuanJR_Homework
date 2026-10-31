package site.duanjr.homework.ui.records

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import site.duanjr.homework.R
import site.duanjr.homework.api.Api
import site.duanjr.homework.api.HomeworkItem
import site.duanjr.homework.databinding.FragmentRecordsBinding
import site.duanjr.homework.databinding.ItemRecordBinding
import site.duanjr.homework.ui.MainActivity
import site.duanjr.homework.ui.PhotoViewerActivity
import site.duanjr.homework.ui.Repo
import site.duanjr.homework.util.Fmt

/** 我的提交记录（全部日期） */
class RecordsFragment : Fragment() {

    private var _binding: FragmentRecordsBinding? = null
    private val binding get() = _binding!!

    private var loading = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecordsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.adapter = RecordAdapter(emptyList()) { item -> openItem(item) }
        binding.swipe.setOnRefreshListener { load() }
        if (Repo.items.isEmpty()) load()
    }

    override fun onResume() {
        super.onResume()
        if (!loading && Repo.items.isEmpty()) load()
        else render()
    }

    private fun load() {
        loading = true
        binding.swipe.isRefreshing = true
        lifecycleScope.launch {
            try {
                val (_, items) = Api.mine()
                Repo.items = items
                render()
                (activity as? MainActivity)?.updateBadge()
            } catch (e: Exception) {
                (activity as? MainActivity)?.handleFailure(e)
            } finally {
                loading = false
                binding.swipe.isRefreshing = false
            }
        }
    }

    private fun render() {
        val weekDate = Repo.config?.weekDate
        val items = Repo.items.sortedWith(
            compareByDescending<HomeworkItem> { it.date }.thenBy { it.subject }
        )
        (binding.list.adapter as RecordAdapter).submit(items, weekDate)
        binding.emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openItem(item: HomeworkItem) {
        if (item.photos.isEmpty()) {
            Snackbar.make(binding.root, "该项暂无照片", Snackbar.LENGTH_SHORT).show()
            return
        }
        startActivity(
            Intent(requireContext(), PhotoViewerActivity::class.java)
                .putExtra(PhotoViewerActivity.EXTRA_PHOTOS, Api.photosJson(item.photos))
                .putExtra(PhotoViewerActivity.EXTRA_INDEX, 0)
                .putExtra(
                    PhotoViewerActivity.EXTRA_TITLE,
                    "${item.subject} · ${Fmt.dateOnly(item.date)}"
                )
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ------------------------------------------------------------------ 适配器

    inner class RecordAdapter(
        private var items: List<HomeworkItem>,
        private val onClick: (HomeworkItem) -> Unit
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<RecordAdapter.Holder>() {

        private var weekDate: String? = null

        fun submit(list: List<HomeworkItem>, weekDate: String?) {
            items = list
            this.weekDate = weekDate
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val binding = ItemRecordBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return Holder(binding)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.bind(items[position])
        }

        inner class Holder(private val binding: ItemRecordBinding) :
            androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {

            fun bind(item: HomeworkItem) {
                binding.titleText.text = "${item.subject} · ${Fmt.dateOnly(item.date)}"
                binding.weekTag.text = Fmt.weekdayOf(item.date) +
                    if (item.date == weekDate) " · 本周" else ""

                binding.statusChip.text = when {
                    item.submitted -> item.status ?: "已提交"
                    item.count > 0 -> item.status ?: "照片已保存"
                    else -> item.status ?: "未提交"
                }
                val bg = when {
                    item.submitted -> R.color.statusOk
                    item.count > 0 -> R.color.statusPending
                    else -> R.color.statusNone
                }
                binding.statusChip.setChipBackgroundColorResource(bg)

                binding.infoText.text = buildString {
                    append("${item.count} 张照片")
                    if (item.submittedAt != null) {
                        append(" · 提交于 ")
                        append(Fmt.relative(item.submittedAt, Repo.serverTime))
                    } else if (item.updatedAt != null) {
                        append(" · 更新于 ")
                        append(Fmt.relative(item.updatedAt, Repo.serverTime))
                    }
                    if (item.revision > 0) append(" · 第 ${item.revision} 版")
                }
                if (item.photosRetired) {
                    binding.infoText.text = "历史照片已清理，提交与评价记录保留"
                }
                binding.root.setOnClickListener { onClick(item) }
            }
        }
    }
}
