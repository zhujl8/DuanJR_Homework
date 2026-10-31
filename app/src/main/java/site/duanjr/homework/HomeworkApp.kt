package site.duanjr.homework

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import site.duanjr.homework.api.Api

class HomeworkApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        Api.init(this)
    }

    /** Coil 全局图片加载器：使用带 Cookie 的 OkHttp（图片端点要求会话） */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient { Api.imageClient }
            .crossfade(true)
            .build()
}
