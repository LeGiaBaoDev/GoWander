package com.gowander.feedback

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private class PendingImage {
    var callback: ((Result<ByteArray?>) -> Unit)? = null
    fun finish(result: Result<ByteArray?>) { val done = callback; callback = null; done?.invoke(result) }
}

@Composable
actual fun rememberFeedbackImages(): FeedbackImages {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pending = remember { PendingImage() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) pending.finish(Result.success(null))
        else scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                        val out = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            require(out.size() + n <= FEEDBACK_IMAGE_MAX_BYTES) { "Ảnh phải nhỏ hơn 5 MB." }
                            out.write(buffer, 0, n)
                        }
                        out.toByteArray()
                    } ?: error("Không đọc được ảnh đã chọn.")
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    require(bounds.outWidth in 1..12000 && bounds.outHeight in 1..12000 && bounds.outWidth.toLong() * bounds.outHeight <= 40_000_000) { "Ảnh quá lớn hoặc không hợp lệ." }
                    var sample = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2000) sample *= 2
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                        ?: error("Không đọc được định dạng ảnh này.")
                    try { encode(bitmap) } finally { bitmap.recycle() }
                }
            }
            pending.finish(result)
        }
    }
    return remember(context, launcher) {
        object : FeedbackImages {
            override fun pick(onResult: (Result<ByteArray?>) -> Unit) {
                if (pending.callback != null) { onResult(Result.failure(IllegalStateException("Đang chọn ảnh."))); return }
                pending.callback = onResult
                try { launcher.launch("image/*") } catch (e: Exception) { pending.finish(Result.failure(IllegalStateException("Không mở được thư viện ảnh.", e))) }
            }

            override fun capture(onResult: (Result<ByteArray?>) -> Unit) {
                val activity = context.activity()
                val view = activity?.window?.decorView
                if (view == null || view.width <= 0 || view.height <= 0) {
                    onResult(Result.failure(IllegalStateException("Chưa chụp được màn hình. Hãy thử lại."))); return
                }
                val ratio = minOf(1f, 2000f / maxOf(view.width, view.height))
                val bitmap = Bitmap.createBitmap((view.width * ratio).toInt().coerceAtLeast(1), (view.height * ratio).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                fun finish(result: Result<Unit>) {
                    scope.launch {
                        val encoded = withContext(Dispatchers.Default) { result.mapCatching { encode(bitmap) }.also { bitmap.recycle() } }
                        onResult(encoded)
                    }
                }
                try {
                    if (Build.VERSION.SDK_INT >= 26) {
                        PixelCopy.request(activity.window, bitmap, { code ->
                            finish(if (code == PixelCopy.SUCCESS) Result.success(Unit) else Result.failure(IllegalStateException("Không chụp được màn hình. Hãy chọn ảnh từ thư viện.")))
                        }, Handler(Looper.getMainLooper()))
                    } else {
                        val canvas = Canvas(bitmap)
                        canvas.scale(ratio, ratio)
                        view.draw(canvas)
                        finish(Result.success(Unit))
                    }
                } catch (e: Exception) { finish(Result.failure(IllegalStateException("Không chụp được màn hình.", e))) }
            }
        }
    }
}

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

private fun encode(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().use {
    check(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it)) { "Không xử lý được ảnh." }
    it.toByteArray().also { bytes -> require(bytes.size <= FEEDBACK_IMAGE_MAX_BYTES) { "Ảnh phải nhỏ hơn 5 MB." } }
}
