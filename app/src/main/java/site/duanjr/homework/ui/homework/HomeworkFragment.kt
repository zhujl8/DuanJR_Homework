package site.duanjr.homework.ui.homework

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import site.duanjr.homework.R
import site.duanjr.homework.api.Api
import site.duanjr.homework.api.HomeworkItem
import site.duanjr.homework.databinding.DialogLateReasonBinding
import site.duanjr.homework.databinding.DialogUploadConfirmBinding
import site.duanjr.homework.databinding.FragmentHomeworkBinding
import site.duanjr.homework.ui.MainActivity
import site.duanjr.homework.ui.PhotoViewerActivity
import site.duanjr.homework.ui.Repo
import site.duanjr.homework.util.Fmt
import site.duanjr.homework.util.ImagePrepare
import java.io.File

/** 本周作业：选择科目 → 拍照/相册 → 排序/替换/删除 → 提交 */
class HomeworkFragment : Fragment() {

    private var _binding: FragmentHomeworkBinding? = null
    private val binding get() = _binding!!

    private var busy = false
    private var subject: String = ""
    private var replaceTargetId: String? = null
    private var cameraFile: File? = null

    private val adapter = PhotoGridAdapter(
        onClick = { idx -> openViewer(idx) },
        onOverflow = { view, idx -> showPhotoMenu(view, idx) }
    )

    private val pickMultiple = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(10)
    ) { uris -> onImagesPicked(uris) }

    private val pickSingle = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> onImagesPicked(listOfNotNull(uri)) }

    private val takePicture = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok -> if (ok) cameraFile?.let { onImagesPicked(listOf(Uri.fromFile(it))) } }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeworkBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.swipe.setOnRefreshListener { refreshAll() }
        binding.photosGrid.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.photosGrid.adapter = adapter
        attachDragHelper()

        binding.fab.setOnClickListener { showSourceSheet() }
        binding.submitBtn.setOnClickListener { onSubmitClicked() }

        if (Repo.config == null) {
            refreshAll()
        } else {
            if (subject.isBlank()) subject = Repo.config?.assignments?.firstOrNull()?.subject ?: ""
            render()
            if (Repo.items.isEmpty()) refreshAll()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ------------------------------------------------------------- 数据加载

    private fun refreshAll() {
        if (busy) {
            binding.swipe.isRefreshing = false
            return
        }
        lifecycleScope.launch {
            try {
                val cfg = Api.config()
                Repo.config = cfg
                Repo.serverTime = cfg.serverTime
                val (student, items) = Api.mine()
                Repo.items = items
                if (subject.isBlank()) subject = cfg.assignments.firstOrNull()?.subject ?: ""
                render()
                (activity as? MainActivity)?.updateBadge()
            } catch (e: Exception) {
                (activity as? MainActivity)?.handleFailure(e)
            } finally {
                binding.swipe.isRefreshing = false
            }
        }
    }

    private val current: HomeworkItem?
        get() = Repo.itemFor(subject)

    // ------------------------------------------------------------- 渲染

    @SuppressLint("NotifyDataSetChanged")
    private fun render() {
        val cfg = Repo.config ?: return
        binding.swipe.isRefreshing = false

        // 顶部信息卡
        binding.introDeadline.text = buildString {
            cfg.assignments.firstOrNull()?.let {
                append("提交截止：")
                append(Fmt.dayTime(it.dueAt))
                append("（北京时间）")
            }
        }
        val pending = Repo.weekItems().count { !it.submitted }
        val total = Repo.weekItems().size
        binding.introSummary.text = when {
            total == 0 -> "本周暂无作业"
            pending == 0 -> "本周 $total 项作业均已提交 ✓"
            else -> "本周共 $total 项作业，还有 $pending 项待提交"
        }

        // 科目选择 chips
        if (binding.subjectChips.childCount != cfg.assignments.size) {
            binding.subjectChips.removeAllViews()
            cfg.assignments.forEach { a ->
                val chip = layoutInflater.inflate(
                    R.layout.item_subject_chip, binding.subjectChips, false
                ) as Chip
                chip.text = a.subject
                chip.id = View.generateViewId()
                chip.isChecked = a.subject == subject
                chip.setOnCheckedChangeListener { _, checked ->
                    if (checked && subject != a.subject) {
                        subject = a.subject
                        render()
                    }
                }
                binding.subjectChips.addView(chip)
            }
        } else {
            (0 until binding.subjectChips.childCount).forEach { i ->
                val chip = binding.subjectChips.getChildAt(i) as Chip
                val a = cfg.assignments[i]
                chip.text = a.subject
                chip.isChecked = a.subject == subject
            }
        }

        renderDetail()
        renderPhotos()
    }

    private fun renderDetail() {
        val cfg = Repo.config ?: return
        val assignment = cfg.assignments.firstOrNull { it.subject == subject }
        val item = current
        if (assignment == null) {
            binding.detailCard.visibility = View.GONE
            return
        }
        binding.detailCard.visibility = View.VISIBLE
        binding.detailTitle.text = assignment.label

        val statusText = when {
            item == null || item.count == 0 -> if (assignment.overdue) "未提交（已过截止）" else "未提交"
            item.submitted -> item.status ?: "已提交"
            else -> item.status ?: "照片已保存，待提交"
        }
        binding.statusChip.text = statusText
        val submittedColor = item?.submitted == true
        binding.statusChip.setChipBackgroundColorResource(
            when {
                submittedColor -> R.color.statusOk
                (item?.count ?: 0) > 0 -> R.color.statusPending
                assignment.overdue -> R.color.statusOverdue
                else -> R.color.statusNone
            }
        )

        binding.due.text = "截止时间：${Fmt.dayTime(assignment.dueAt)}"
        val now = Repo.serverTime ?: System.currentTimeMillis() / 1000.0
        binding.countdown.text = if (assignment.dueAt != null && !submittedColor) {
            Fmt.countdown(assignment.dueAt!!, now)
        } else ""

        val count = item?.count ?: 0
        binding.count.text = "已保存 $count / 10 张"
        binding.photoProgress.max = 10
        binding.photoProgress.progress = count

        binding.detailHint.text = when {
            item != null && item.submitted -> "需要补充或修改照片时，修改后请再次提交。"
            else -> "可分多次拍照、选图；每张不超过 10MB，每项最多 10 张。照片保存后还需手动提交。"
        }
        binding.lateReasonText.visibility =
            if (item?.lateReason?.isNotBlank() == true) View.VISIBLE else View.GONE
        binding.lateReasonText.text = "补交说明：${item?.lateReason ?: ""}"
    }

    private fun renderPhotos() {
        val photos = current?.photos ?: emptyList()
        adapter.submit(photos)
        binding.emptyPhotos.visibility = if (photos.isEmpty()) View.VISIBLE else View.GONE
        binding.photosCount.text = "已保存照片（${photos.size}）"
        binding.photosHint.text = if (photos.isEmpty()) "" else "长按照片可拖动调整顺序"

        val item = current
        binding.submitBtn.isEnabled = !busy && (item?.available ?: false) && (item?.count ?: 0) > 0 && !(item?.submitted ?: false)
        binding.submitBtn.text = when {
            item?.submitted == true -> "作业已提交"
            (item?.count ?: 0) == 0 -> "添加照片后才能提交"
            else -> "照片已齐，提交作业"
        }
        binding.fab.isEnabled = !busy
    }

    // ------------------------------------------------------------- 拖动排序

    private fun attachDragHelper() {
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or
                ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0
        ) {
            override fun isLongPressDragEnabled() = !busy

            override fun onMove(
                rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder
            ): Boolean {
                if (busy) return false
                val from = vh.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                adapter.move(from, to)
                return true
            }

            override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {}

            override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
                super.clearView(rv, vh)
                if (adapter.dirty) persistOrder()
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(binding.photosGrid)
    }

    private fun persistOrder() {
        val date = Repo.config?.weekDate ?: return
        val ids = adapter.currentIds()
        if (ids.size < 2) {
            adapter.dirty = false
            return
        }
        lifecycleScope.launch {
            try {
                Api.reorder(date, subject, ids)
                adapter.dirty = false
                renderPhotos()
                Snackbar.make(binding.root, "顺序已保存", Snackbar.LENGTH_SHORT).show()
            } catch (e: Exception) {
                (activity as? MainActivity)?.handleFailure(e)
                refreshAll()
            }
        }
    }

    // ------------------------------------------------------------- 照片操作

    private fun showPhotoMenu(anchor: View, index: Int) {
        if (busy) return
        val photos = adapter.photos
        if (index >= photos.size) return
        val photo = photos[index]
        val popup = PopupMenu(requireContext(), anchor)
        popup.menuInflater.inflate(R.menu.menu_photo, popup.menu)
        popup.menu.findItem(R.id.action_up).isEnabled = index > 0
        popup.menu.findItem(R.id.action_up).title = if (index == 0) "前移（已是最前）" else "前移到第 $index 张"
        popup.setOnMenuItemClickListener { mi ->
            when (mi.itemId) {
                R.id.action_up -> {
                    adapter.move(index, index - 1)
                    persistOrder()
                    true
                }
                R.id.action_replace -> {
                    replaceTargetId = photo.id
                    showSourceSheet(replaceMode = true)
                    true
                }
                R.id.action_delete -> {
                    confirmDelete(photo.id)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun confirmDelete(id: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除这张照片？")
            .setMessage("删除后需要重新上传。若已提交过，删除后请再次提交。")
            .setPositiveButton("删除") { _, _ -> doDelete(id) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun doDelete(id: String) {
        val date = Repo.config?.weekDate ?: return
        setBusy(true, "正在删除照片…")
        lifecycleScope.launch {
            try {
                Api.deletePhoto(date, subject, id)
                refreshFromMine()
            } catch (e: Exception) {
                (activity as? MainActivity)?.handleFailure(e)
            } finally {
                setBusy(false)
            }
        }
    }

    private fun openViewer(index: Int) {
        val photos = adapter.photos
        if (photos.isEmpty()) return
        startActivity(
            Intent(requireContext(), PhotoViewerActivity::class.java)
                .putExtra(PhotoViewerActivity.EXTRA_PHOTOS, Api.photosJson(photos))
                .putExtra(PhotoViewerActivity.EXTRA_INDEX, index)
                .putExtra(PhotoViewerActivity.EXTRA_TITLE, subject)
        )
    }

    // ------------------------------------------------------------- 选择照片

    private fun showSourceSheet(replaceMode: Boolean = false) {
        if (busy) return
        val item = current
        val remaining = 10 - (item?.count ?: 0)
        if (remaining <= 0 && !replaceMode) {
            Snackbar.make(binding.root, "每项作业最多 10 张照片，已达上限", Snackbar.LENGTH_SHORT).show()
            return
        }
        val sheet = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.sheet_pick_source, null)
        sheet.setContentView(view)
        view.findViewById<MaterialButton>(R.id.btnCamera).setOnClickListener {
            sheet.dismiss()
            launchCamera()
        }
        view.findViewById<MaterialButton>(R.id.btnAlbum).setOnClickListener {
            sheet.dismiss()
            launchAlbum(remaining)
        }
        view.findViewById<android.widget.TextView>(R.id.sheetTitle).text =
            if (replaceMode) "替换当前照片" else "添加作业照片"
        view.findViewById<android.widget.TextView>(R.id.sheetHint).text =
            if (replaceMode) "选择一张新照片替换这张照片"
            else "每张不超过 10MB，还可添加 $remaining 张"
        sheet.show()
    }

    private fun launchCamera() {
        try {
            val dir = File(requireContext().cacheDir, "photos").apply { mkdirs() }
            val file = File(dir, "IMG_${System.currentTimeMillis()}.jpg")
            cameraFile = file
            val uri = FileProvider.getUriForFile(
                requireContext(), "${requireContext().packageName}.fileprovider", file
            )
            takePicture.launch(uri)
        } catch (e: Exception) {
            Snackbar.make(binding.root, "无法启动相机：${e.message}", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun launchAlbum(remaining: Int) {
        val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        if (replaceTargetId != null || remaining <= 1) {
            pickSingle.launch(request)
        } else {
            pickMultiple.launch(request)
        }
    }

    private fun onImagesPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val replace = replaceTargetId
        replaceTargetId = null
        if (replace == null) {
            val remaining = 10 - (current?.count ?: 0)
            if (uris.size > remaining) {
                Snackbar.make(
                    binding.root,
                    "最多还能添加 $remaining 张，已自动忽略多余照片",
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
        uploadAll(uris.take(if (replace == null) maxOf(1, 10 - (current?.count ?: 0)) else 1), replace)
    }

    // ------------------------------------------------------------- 上传

    private fun uploadAll(uris: List<Uri>, replace: String?) {
        if (uris.isEmpty() || busy) return
        val date = Repo.config?.weekDate ?: return
        val subject = this.subject
        var ok = 0
        var dup = 0
        var failed = 0
        val details = mutableListOf<String>()

        setBusy(true, "正在准备照片…")
        lifecycleScope.launch {
            try {
                // 数学在 quiz 日需先完成“上传提醒”二次确认
                if (needQuizGate(subject)) {
                    ensureQuizAck(date, subject) // 无需确认时直接返回 null
                }
            } catch (e: Exception) {
                setBusy(false)
                return@launch
            }

            uris.forEachIndexed { i, uri ->
                updateProgress(i + 1, uris.size)
                var name = "photo_${i + 1}.jpg"
                try {
                    val prepared = ImagePrepare.prepare(requireContext(), uri)
                    name = prepared.displayName
                    val result = Api.uploadPhoto(date, subject, prepared.file, prepared.displayName, replace)
                    prepared.file.delete()
                    if (result.duplicate) {
                        dup++
                        details += "$name：已存在，已跳过（无需重复上传）"
                    } else {
                        ok++
                        details += "$name：保存成功"
                    }
                } catch (e: Exception) {
                    failed++
                    val msg = if (e.message?.contains("Failed") == true) "网络连接中断" else (e.message ?: "上传失败")
                    details += "$name：$msg"
                }
            }

            try {
                refreshFromMine()
            } catch (e: Exception) {
                details += "刷新照片列表失败：已成功保存的照片不会丢失，稍后下拉刷新即可。"
            }

            setBusy(false)
            (activity as? MainActivity)?.updateBadge()

            val summary = "本次选择 ${uris.size} 张：保存成功 $ok 张，已存在 $dup 张，失败 $failed 张。"
            if (failed > 0 || dup > 0) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("上传结果")
                    .setMessage(summary + "\n\n" + details.joinToString("\n"))
                    .setPositiveButton("知道了", null)
                    .show()
            } else {
                Snackbar.make(binding.root, "照片已保存，齐全后请提交作业", Snackbar.LENGTH_LONG)
                    .setAction("提交") { onSubmitClicked() }
                    .show()
            }
        }
    }

    private fun updateProgress(i: Int, total: Int) {
        if (_binding == null) return
        binding.uploadPanel.visibility = View.VISIBLE
        binding.uploadStatus.text = "正在保存第 $i / $total 张…"
        binding.uploadProgress.max = total
        binding.uploadProgress.progress = i
    }

    private fun setBusy(value: Boolean, status: String? = null) {
        busy = value
        if (_binding == null) return
        binding.fab.isEnabled = !value
        binding.submitBtn.isEnabled = !value
        if (status != null) {
            binding.uploadPanel.visibility = View.VISIBLE
            binding.uploadStatus.text = status
        } else {
            binding.uploadPanel.visibility = View.GONE
        }
        if (value) binding.swipe.isRefreshing = false
    }

    private fun refreshFromMine() {
        lifecycleScope.launch {
            try {
                val (_, items) = Api.mine()
                Repo.items = items
                render()
            } finally {
            }
        }
    }

    // ------------------------------------------------------------- 提交

    private fun needQuizGate(subject: String): Boolean {
        val cfg = Repo.config ?: return false
        val date = cfg.weekDate
        return cfg.quizEnabled && cfg.quizDates.contains(date) && subject == "数学"
    }

    /**
     * 上传/提交数学（quiz 日）前的“提醒确认”。
     * 返回可用于 quiz_ack 的 token；不需要确认或用户确认后返回。
     */
    private suspend fun ensureQuizAck(date: String, subject: String): String? {
        val info = Api.prepareUpload(date, subject)
        if (!info.required) return null
        return showDialogForConfirm(info)
    }

    private suspend fun showDialogForConfirm(
        info: site.duanjr.homework.api.PrepareInfo
    ): String? {
        val ctx = context ?: return null
        val bindingDlg = DialogUploadConfirmBinding.inflate(layoutInflater)
        bindingDlg.confirmMessage.text = buildString {
            append("请确认已保存的照片完整、清晰，并准备好独立作答。确认提交后会进入检测。\n\n")
            append("每题 60 秒，退出或刷新不会暂停计时。若已完成本日检测，将进入已有结果，不会重新抽题。")
        }
        val dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle("提交数学后将开始检测")
            .setView(bindingDlg.root)
            .setPositiveButton("确认提交", null)
            .setNegativeButton("继续补充照片", null)
            .setOnCancelListener { }
            .create()
        dialog.show()

        val positive = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
        val seconds = info.waitSeconds
        val handler = Handler(Looper.getMainLooper())
        val ack = kotlinx.coroutines.CompletableDeferred<String?>()

        val tick = object : Runnable {
            var left = seconds
            override fun run() {
                if (_binding == null || !dialog.isShowing) {
                    if (!ack.isCompleted) ack.complete(null)
                    return
                }
                if (left > 0) {
                    bindingDlg.confirmCountdown.text = "请先阅读规则，${left} 秒后可确认"
                    positive.isEnabled = false
                    left--
                    handler.postDelayed(this, 1000)
                } else {
                    bindingDlg.confirmCountdown.text = "确认提交后将开始检测。"
                    positive.isEnabled = true
                }
            }
        }
        handler.post(tick)

        positive.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val token = Api.confirmUpload(info.id)
                    ack.complete(token)
                } catch (e: Exception) {
                    if (!ack.isCompleted) ack.complete("__ERROR__:${e.message}")
                }
            }
        }
        dialog.setOnDismissListener {
            handler.removeCallbacksAndMessages(null)
            if (!ack.isCompleted) ack.complete(null)
        }

        val token = ack.await()
        handler.removeCallbacksAndMessages(null)
        if (dialog.isShowing) dialog.dismiss()
        if (token == null || token.startsWith("__ERROR__")) {
            throw RuntimeException(token?.removePrefix("__ERROR__:") ?: "已取消上传")
        }
        return token
    }

    private fun onSubmitClicked() {
        if (busy) return
        val date = Repo.config?.weekDate ?: return
        val subject = this.subject
        if ((current?.count ?: 0) == 0) return

        setBusy(true, "正在提交作业…")
        lifecycleScope.launch {
            try {
                // 1. 刷新 revision（乐观锁）
                val fresh = Api.homeworkItem(date, subject)

                // 2. 迟交说明
                var lateReason: String? = null
                if (fresh.lateReasonRequired) {
                    lateReason = askLateReason() ?: run {
                        setBusy(false)
                        return@launch
                    }
                }

                // 3. 数学 quiz 日：二次确认
                var quizAck: String? = null
                if (needQuizGate(subject)) {
                    val info = Api.prepareUpload(date, subject)
                    if (info.required) {
                        quizAck = showDialogForConfirm(info) ?: run {
                            setBusy(false)
                            return@launch
                        }
                    }
                }

                // 4. 提交
                val outcome = Api.submit(date, subject, fresh.revision, quizAck, lateReason)

                // 5. 刷新
                refreshFromMine()
                Snackbar.make(binding.root, "作业已提交成功", Snackbar.LENGTH_LONG).show()
                (activity as? MainActivity)?.updateBadge()

                if (outcome.quizRequired) {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("请完成今日小测")
                        .setMessage("作业已提交。提交数学后需要完成当日检测（每题 60 秒）。")
                        .setPositiveButton("打开网页版") { _, _ ->
                            openQuiz(date)
                        }
                        .setNegativeButton("稍后", null)
                        .show()
                }
            } catch (e: Exception) {
                (activity as? MainActivity)?.handleFailure(e)
            } finally {
                setBusy(false)
            }
        }
    }

    private suspend fun askLateReason(): String? {
        val ctx = context ?: return null
        val dlg = DialogLateReasonBinding.inflate(layoutInflater)
        var result: String? = null
        val dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle("补交情况说明")
            .setMessage("本次提交晚于截止时间，请填写补交原因（100 字以内）。")
            .setView(dlg.root)
            .setPositiveButton("提交") { _, _ -> result = dlg.reasonInput.text?.toString()?.trim() }
            .setNegativeButton("取消", null)
            .create()
        dialog.show()
        // 阻塞式：用 suspend 挂起等待
        return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            dialog.setOnDismissListener {
                if (!cont.isCompleted) cont.resumeWith(kotlin.Result.success(result))
            }
        }
    }

    private fun openQuiz(date: String) {
        val url = "${Api.BASE}/quiz?date=$date"
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(requireContext(), "未找到浏览器", Toast.LENGTH_SHORT).show()
        }
    }
}
