package com.nononsenseapps.feeder.model

import android.app.Activity
import android.graphics.Point
import android.text.Spanned
import android.text.style.ImageSpan
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import com.nononsenseapps.feeder.R
import com.nononsenseapps.feeder.base.CoroutineScopedKodeinAwareViewModel
import com.nononsenseapps.feeder.db.room.FeedItemDao
import com.nononsenseapps.feeder.db.room.FeedItemWithFeed
import com.nononsenseapps.feeder.ui.text.*
import com.nononsenseapps.feeder.util.Prefs
import com.nononsenseapps.feeder.util.TabletUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.kodein.di.Kodein
import org.kodein.di.generic.instance
import kotlin.math.roundToInt

class FeedItemViewModel(kodein: Kodein) : CoroutineScopedKodeinAwareViewModel(kodein) {
    private val dao: FeedItemDao by instance()
    private val prefs: Prefs by instance()

    fun getLiveItem(id: Long): LiveData<FeedItemWithFeed> = dao.loadLiveFeedItem(id)

    fun getLiveImageText(id: Long, maxImageSize: Point, urlClickListener: UrlClickListener?): MediatorLiveData<Spanned> {
        val liveImageText: MediatorLiveData<Spanned> = MediatorLiveData()
        val liveItem = getLiveItem(id)
        var currentHash = 0

        liveImageText.addSource(liveItem) {
            it?.let {
                val updatedHash: Int = it.description.hashCode()
                if (liveImageText.value == null) {
                    // Only set no image version if value is null (e.g. no load has been done yet)
                    // This avoid flickering when syncs happen
                    liveImageText.value = toSpannedWithNoImages(kodein, it.description, it.feedUrl, maxImageSize, urlClickListener = urlClickListener).also {
                        if (it.getAllImageSpans().isEmpty()) {
                            // If no images in the text, then we are done for now
                            currentHash = updatedHash
                        }
                    }
                }

                // Only load into view if the text is different
                if (currentHash != updatedHash) {
                    currentHash = updatedHash
                    launch(Dispatchers.Default) {
                        val allowDownload = prefs.shouldLoadImages()
                        val spanned = toSpannedWithImages(kodein, it.description, it.feedUrl, maxImageSize, allowDownload, urlClickListener = urlClickListener)
                        liveImageText.postValue(spanned)
                    }
                }
            }
        }

        return liveImageText
    }

    fun getLiveMoo(id: Long, urlClickListener: UrlClickListener2): MediatorLiveData<List<Moo>> {
        val liveMoo = MediatorLiveData<List<Moo>>()
        liveMoo.addSource(getLiveItem(id)) { feedItem ->
            liveMoo.value = if (feedItem == null) {
                emptyList()
            } else {
                HtmlToMoo(feedItem.description, feedItem.feedUrl, urlClickListener, kodein)
            }
        }

        return liveMoo
    }
}

internal fun Activity.maxImageSize(): Point {
    val size = Point()
    windowManager?.defaultDisplay?.getSize(size)
    if (TabletUtils.isTablet(this)) {
        // Using twice window height since we do scroll vertically
        size.set(resources.getDimension(R.dimen.reader_tablet_width).roundToInt(), 2 * size.y)
    } else {
        // Base it on window size
        size.set(size.x - 2 * resources.getDimension(R.dimen.keyline_1).roundToInt(), 2 * size.y)
    }

    return size
}

private fun Spanned.getAllImageSpans(): Array<out ImageSpan> =
        getSpans(0, length, ImageSpan::class.java) ?: emptyArray()
