package site.duanjr.homework.ui.viewer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView

/** 支持双指缩放、拖动、双击缩放的 ImageView */
class ZoomImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    private val matrix = Matrix()
    private val minScale = 1f
    private val maxScale = 5f
    private var scale = 1f

    private var last = PointF()
    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val newScale = (scale * detector.scaleFactor).coerceIn(minScale, maxScale)
                matrix.postScale(newScale / scale, newScale / scale, detector.focusX, detector.focusY)
                scale = newScale
                applyMatrix()
                return true
            }
        }
    )

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                last.set(e.x, e.y)
                return true
            }

            override fun onScroll(
                e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float
            ): Boolean {
                if (scale <= 1.02f) return false
                matrix.postTranslate(-dx, -dy)
                applyMatrix()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (scale > 1.05f) {
                    reset()
                } else {
                    scale = 2.5f
                    matrix.setScale(scale, scale, e.x, e.y)
                    applyMatrix()
                }
                return true
            }
        }
    )

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        reset()
    }

    private fun reset() {
        scale = 1f
        matrix.reset()
        applyMatrix()
    }

    private fun applyMatrix() {
        if (drawable == null || width == 0 || height == 0) {
            imageMatrix = matrix
            return
        }
        imageMatrix = matrix
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            if (scale < 1.02f) reset()
        }
        return true
    }
}
