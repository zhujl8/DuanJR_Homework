package site.duanjr.homework.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import coil.load
import site.duanjr.homework.R
import site.duanjr.homework.api.Api
import site.duanjr.homework.api.PhotoInfo
import site.duanjr.homework.databinding.ActivityPhotoViewerBinding
import site.duanjr.homework.databinding.ItemViewerPageBinding
import site.duanjr.homework.ui.viewer.ZoomImageView

/** 全屏照片查看（原图，需会话 Cookie） */
class PhotoViewerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PHOTOS = "photos_json"
        const val EXTRA_INDEX = "index"
        const val EXTRA_TITLE = "title"
    }

    private lateinit var binding: ActivityPhotoViewerBinding
    private lateinit var photos: List<PhotoInfo>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        photos = Api.photosFromJson(intent.getStringExtra(EXTRA_PHOTOS) ?: "[]")
        val index = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, (photos.size - 1).coerceAtLeast(0))
        binding.toolbar.title = intent.getStringExtra(EXTRA_TITLE) ?: "作业照片"
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.pager.adapter = PagesAdapter(photos)
        binding.pager.orientation = ViewPager2.ORIENTATION_HORIZONTAL
        binding.pager.setCurrentItem(index, false)
        binding.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                binding.toolbar.subtitle = "第 ${position + 1} / ${photos.size} 张"
            }
        })
        binding.toolbar.subtitle = "第 ${index + 1} / ${photos.size} 张"
    }

    private inner class PagesAdapter(private val items: List<PhotoInfo>) :
        RecyclerView.Adapter<PagesAdapter.Holder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val binding = ItemViewerPageBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return Holder(binding)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.bind(items[position])
        }

        inner class Holder(private val binding: ItemViewerPageBinding) :
            RecyclerView.ViewHolder(binding.root) {

            fun bind(photo: PhotoInfo) {
                binding.image.load(Api.ORIGIN + photo.url) {
                    crossfade(true)
                    placeholder(R.drawable.ic_photo_placeholder)
                }
            }
        }
    }
}
