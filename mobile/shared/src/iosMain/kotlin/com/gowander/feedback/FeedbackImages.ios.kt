package com.gowander.feedback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy

/** iOS: the app window drawn into an image (never another app), and the system photo picker (one image). */
@Composable
actual fun rememberFeedbackImages(): FeedbackImages = remember { IosFeedbackImages() }

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class IosFeedbackImages : FeedbackImages {
    private var delegate: PickerDelegate? = null // the picker keeps only a weak reference

    @Suppress("DEPRECATION")
    private fun window(): UIWindow? = UIApplication.sharedApplication.keyWindow

    override fun capture(onResult: (Result<ByteArray?>) -> Unit) {
        val w = window() ?: return onResult(Result.failure(IllegalStateException("Chưa chụp được màn hình. Hãy thử lại.")))
        val result = runCatching {
            val format = UIGraphicsImageRendererFormat.defaultFormat().apply { setScale(2.0) }
            val canvas = w.bounds.useContents { CGSizeMake(size.width, size.height) }
            val image = UIGraphicsImageRenderer(canvas, format).imageWithActions { _ ->
                w.bounds.useContents { w.drawViewHierarchyInRect(CGRectMake(0.0, 0.0, size.width, size.height), afterScreenUpdates = false) }
            }
            jpeg(image)
        }
        onResult(result)
    }

    override fun pick(onResult: (Result<ByteArray?>) -> Unit) {
        val top = topController() ?: return onResult(Result.failure(IllegalStateException("Không mở được thư viện ảnh.")))
        val config = PHPickerConfiguration().apply {
            setFilter(PHPickerFilter.imagesFilter)
            setSelectionLimit(1)
        }
        val picker = PHPickerViewController(config)
        val d = PickerDelegate { result ->
            delegate = null
            onResult(result)
        }
        delegate = d
        picker.delegate = d
        top.presentViewController(picker, animated = true, completion = null)
    }

    private fun topController(): UIViewController? {
        var c = window()?.rootViewController
        while (c?.presentedViewController != null) c = c.presentedViewController
        return c
    }
}

@OptIn(ExperimentalForeignApi::class)
private class PickerDelegate(private val done: (Result<ByteArray?>) -> Unit) : NSObject(), PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val first = didFinishPicking.firstOrNull() as? PHPickerResult ?: return done(Result.success(null))
        first.itemProvider.loadDataRepresentationForTypeIdentifier("public.image") { data, _ ->
            val result = runCatching {
                val image = data?.let { UIImage(data = it) } ?: error("Không đọc được ảnh đã chọn.")
                jpeg(image)
            }
            dispatch_async(dispatch_get_main_queue()) { done(result) }
        }
    }
}

/** At most 2000 px on the long side, JPEG, under the 5 MB limit. */
@OptIn(ExperimentalForeignApi::class)
private fun jpeg(image: UIImage): ByteArray {
    val (w, h) = image.size.useContents { width to height }
    require(w > 0 && h > 0) { "Ảnh không hợp lệ." }
    val ratio = minOf(1.0, 2000.0 / maxOf(w, h))
    val scaled = if (ratio >= 1.0) image else {
        val format = UIGraphicsImageRendererFormat.defaultFormat().apply { setScale(1.0) }
        UIGraphicsImageRenderer(CGSizeMake(w * ratio, h * ratio), format).imageWithActions { _ ->
            image.drawInRect(CGRectMake(0.0, 0.0, w * ratio, h * ratio))
        }
    }
    val data = UIImageJPEGRepresentation(scaled, 0.85) ?: error("Không xử lý được ảnh.")
    return data.toByteArray().also { require(it.size <= FEEDBACK_IMAGE_MAX_BYTES) { "Ảnh phải nhỏ hơn 5 MB." } }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val out = ByteArray(length.toInt())
    if (out.isNotEmpty()) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}
