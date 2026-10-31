package site.duanjr.homework.ui.homework

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import coil.load
import site.duanjr.homework.R
import site.duanjr.homework.api.Api
import site.duanjr.homework.api.PhotoInfo
import site.duanjr.homework.databinding.ItemPhotoBinding
import site.duanjr.homework.util.Fmt

/** 照片网格（2 列）：缩略图 + 序号 + 大小 + 菜单 */
class PhotoGridAdapter(
    private val onClick: (Int) -> Unit,
    private val onOverflow: (View, Int) -> Unit
) : RecyclerView.Adapter<PhotoGridAdapter.Holder>() {

    private val items = mutableListOf<PhotoInfo>()
    var dirty = false
        private set

    val photos: List<PhotoInfo> get() = items

    fun submit(list: List<PhotoInfo>) {
        items.clear()
        items.addAll(list)
        dirty = false
        notifyDataSetChanged()
    }

    fun move(from: Int, to: Int) {
        if (from == to || from !in items.indices || to !in items.indices) return
        items.add(to, items.removeAt(from))
        dirty = true
        notifyItemMoved(from, to)
        notifyItemRangeChanged(minOf(from, to), abs(to - from) + 1)
    }

    fun currentIds(): List<String> = items.map { it.id }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val photo = items[position]
        holder.bind(photo, position)
    }

    inner class Holder(private val binding: ItemPhotoBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(photo: PhotoInfo, position: Int) {
            binding.indexBadge.text = "第 ${position + 1} 张"
            binding.sizeText.text = Fmt.size(photo.size)
            binding.nameText.text = photo.name ?: ""

            binding.photo.transitionName = "photo_${photo.id}"
            binding.photo.load(Api.ORIGIN + photo.thumbnail) {
                crossfade(true)
                placeholder(R.drawable.ic_photo_placeholder)
                error(R.drawable.ic_photo_placeholder)
            }
            binding.root.setOnClickListener { onClick(bindingAdapterPosition) }
            binding.overflow.setOnClickListener { v ->
                onOverflow(v, bindingAdapterPosition)
            }
        }
    }
}
