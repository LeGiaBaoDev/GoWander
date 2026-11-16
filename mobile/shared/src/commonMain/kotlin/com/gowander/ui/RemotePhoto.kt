package com.gowander.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gowander.api.ApiError
import com.gowander.api.GoWanderApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap

/** Downloads GoWander's photos through the API client (its host checks) and keeps the last few decoded. */
class PhotoLoader(private val api: GoWanderApi, private val keep: Int = 40) {
    private val cache = LinkedHashMap<String, ImageBitmap>()

    suspend fun load(url: String): ImageBitmap? {
        cache[url]?.let { return it }
        val bitmap = try {
            val bytes = api.imageBytes(url)
            withContext(Dispatchers.Default) { bytes.decodeToImageBitmap() }
        } catch (e: ApiError) {
            return null
        } catch (e: IllegalArgumentException) { // not a picture
            return null
        }
        cache[url] = bitmap
        if (cache.size > keep) cache.remove(cache.keys.first())
        return bitmap
    }
}

val LocalPhotoLoader = compositionLocalOf<PhotoLoader?> { null }

/**
 * A real photo when [url] is set and loads; until then, or when it fails, the [MockPhoto] tile. Decorative like
 * MockPhoto unless [description] is given (the alt text the editor wrote).
 */
@Composable
fun RemotePhoto(url: String?, seed: String, modifier: Modifier, icon: ImageVector = KitIcons.camera, radius: Dp = 14.dp, iconSize: Dp = 24.dp, description: String? = null) {
    val loader = LocalPhotoLoader.current
    val bitmap by produceState<ImageBitmap?>(null, url, loader) { value = if (url == null || loader == null) null else loader.load(url) }
    val b = bitmap
    if (b == null) MockPhoto(seed, modifier, icon, radius, iconSize)
    else Box(modifier.clip(RoundedCornerShape(radius))) {
        Image(b, description, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
    }
}
