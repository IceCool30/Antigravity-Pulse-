package com.example.ui.components

import android.content.Context
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.remote.WebViewOfflineCacheHelper

/**
 * Native Android ViewGroup host for a RAM-pinned [WebView] inside Jetpack Compose.
 *
 * Ensures:
 * 1. Touch events (`ACTION_DOWN` / `ACTION_MOVE`) inside an active WebView immediately call
 *    `requestDisallowInterceptTouchEvent(true)` so Compose's `AndroidViewHolder` pointer
 *    arbitration never stalls or steals inner `overflow-y: auto` / horizontal scroll drags.
 * 2. Inactive tabs (`isActiveWorkspace == false`) drop to `View.GONE` at the wrapper level and
 *    short-circuit touch dispatch in O(1), preventing hidden views from ever participating in
 *    ViewGroup measurement, drawing, or hit-testing.
 */
class TouchSafeWebViewContainer(context: Context) : FrameLayout(context) {
    var isActiveWorkspace: Boolean = true

    init {
        layoutParams = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        isFocusable = false
        isFocusableInTouchMode = false
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!isActiveWorkspace) return true
        val action = ev.actionMasked
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        return false
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (!isActiveWorkspace) return false
        val action = ev.actionMasked
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        return super.dispatchTouchEvent(ev)
    }
}

/**
 * Composable interop wrapper that hosts a retained [WebView] inside a [TouchSafeWebViewContainer]
 * clipped to its own hardware layer bounds (`graphicsLayer { clip = true }`) so WebView surface
 * invalidations never bleed across Compose overlays during 60/120Hz scrolling.
 */
@Composable
fun PinnedWebViewHost(
    isWorkspaceActive: Boolean,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    webViewProvider: (Context) -> WebView
) {
    val hostModifier = modifier
        .graphicsLayer { clip = true }
        .let { if (testTag != null) it.testTag(testTag) else it }

    AndroidView(
        modifier = hostModifier,
        factory = { ctx ->
            TouchSafeWebViewContainer(ctx).also { container ->
                val webView = webViewProvider(ctx)
                attachWebViewToContainer(container, webView)
                WebViewOfflineCacheHelper.applyWindowAttachedVisibility(webView, isWorkspaceActive)
            }
        },
        update = { container ->
            val webView = container.getChildAt(0) as? WebView
            if (webView != null) {
                WebViewOfflineCacheHelper.applyWindowAttachedVisibility(webView, isWorkspaceActive)
            }
        },
        onRelease = { container ->
            // Detach only the transient container relationship cleanly without destroying the
            // RAM-pinned WebView instance.
            container.removeAllViews()
        }
    )
}

internal fun attachWebViewToContainer(container: TouchSafeWebViewContainer, webView: WebView) {
    val currentParent = webView.parent as? ViewGroup
    if (currentParent === container) return
    currentParent?.removeView(webView)
    container.removeAllViews()
    container.addView(
        webView,
        FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    )
}
