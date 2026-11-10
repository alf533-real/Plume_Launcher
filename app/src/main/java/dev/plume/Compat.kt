package dev.plume

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build

/** Pre-API-26 stand-ins for the handful of framework calls mzilo leans on. */
object Compat {
    /** Same Rec.709 formula [Color.luminance] uses (API 26); the float math is trivial. */
    fun luminance(color: Int): Float {
        val r = Color.red(color) / 255.0
        val g = Color.green(color) / 255.0
        val b = Color.blue(color) / 255.0
        return (0.2126 * r + 0.7152 * g + 0.0722 * b).toFloat()
    }

    fun copyBitmap(b: Bitmap): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= 26) b.copy(Bitmap.Config.HARDWARE, false)?.also { b.recycle() } ?: b
        else b.copy(Bitmap.Config.ARGB_8888, false)?.also { b.recycle() } ?: b
    } catch (e: Exception) {
        b
    }

    /** [android.animation.ValueAnimator.areAnimatorsEnabled] is API 26; older systems animate. */
    fun animatorsEnabled() = Build.VERSION.SDK_INT >= 26 && android.animation.ValueAnimator.areAnimatorsEnabled()
}
