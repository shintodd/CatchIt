package my.feeder.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.core.content.ContextCompat
import android.graphics.drawable.Icon
import androidx.core.graphics.drawable.IconCompat
import my.feeder.app.R

/**
 * The CatchIt brand mark, rendered for places where a notification cannot be
 * left to the system's own app-icon lookup.
 *
 * Android's notification header shows the application icon when a notification
 * has no large icon of its own. That fallback goes through SystemUI's cached
 * copy of the app icon, which can be **stale** — it has been known to keep
 * showing the previous mark after the icon changed, because the cache is keyed
 * by package rather than by icon version.
 *
 * Rendering the adaptive icon ourselves, straight from the app's own
 * resources, means the notification always shows the logo that is actually
 * installed. It also means the notification logo and the launcher logo can
 * never drift apart.
 */
object BrandIcon {

    private const val SIZE = 192

    /**
     * The brand mark as a circular bitmap, ready for
     * [android.app.Notification.Builder.setLargeIcon].
     */
    fun notificationIcon(context: Context): Icon? {
        val adaptive = runCatching {
            ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
        }.getOrNull() as? AdaptiveIconDrawable ?: return null

        val square = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(square)
        adaptive.setBounds(0, 0, SIZE, SIZE)
        adaptive.draw(canvas)

        // The launcher masks adaptive icons to a circle; do the same here so
        // the notification looks identical to the launcher.
        val circle = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val out = Canvas(circle)
        out.clipPath(
            Path().apply {
                addCircle(SIZE / 2f, SIZE / 2f, SIZE / 2f, Path.Direction.CW)
            }
        )
        out.drawBitmap(square, 0f, 0f, null)

        return IconCompat.createWithBitmap(circle).toIcon()
    }
}
