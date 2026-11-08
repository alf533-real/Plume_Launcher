package dev.plume

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.SoundEffectConstants
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.HorizontalScrollView

/**
 * A row that scrolls by whole items: when focus moves past an edge, the focused item lands flush with
 * that edge of the padded area, so the row always shows full tiles and never half-cut ones. The items
 * sit on a fixed grid that exactly fills the area, so aligning one edge aligns them all.
 */
class SnapScrollView(context: Context, attrs: AttributeSet?) : HorizontalScrollView(context, attrs) {
    init {
        // Clip to our own bounds. The parent can't do it: it leaves clipping off so other views can lift.
        outlineProvider = ViewOutlineProvider.BOUNDS
        clipToOutline = true
        clipChildren = false // or the row inside clips the first tile's focus lift to its own edge

    }

    /**
     * HorizontalScrollView's arrow-key handling (executeKeyEvent → arrowScroll) moves focus itself and eats the
     * key, so ViewRootImpl never runs its default navigation — and that navigation is the only thing that plays
     * the system's focus sound. The top bar's icons aren't in a scroll view, so their Left/Right reach it and
     * sound while these rows stay silent. When the scroll view really moved the focus, play the same native
     * effect on the newly focused view: it lands in the same AudioManager path, which stays muted when the user
     * turns the system's operation sounds off, and nothing sounds when focus didn't move (row edge, key eaten
     * by a tile).
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val before = findFocus()
        val handled = super.dispatchKeyEvent(event)
        if (handled && event.action == KeyEvent.ACTION_DOWN && before != null &&
            (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)
        ) {
            val focused = findFocus()
            if (focused != null && focused !== before && focused.isSoundEffectsEnabled) {
                val direction = if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) View.FOCUS_LEFT else View.FOCUS_RIGHT
                focused.playSoundEffect(SoundEffectConstants.getConstantForFocusDirection(direction, event.repeatCount > 0))
            }
        }
        return handled
    }

    override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect): Int {
        val left = scrollX + paddingLeft
        val right = scrollX + width - paddingRight
        return when {
            rect.left < left -> rect.left - left
            rect.right > right -> rect.right - right
            else -> 0
        }
    }
}
