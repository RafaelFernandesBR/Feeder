package com.nononsenseapps.feeder.model

import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.Context
import android.content.OperationApplicationException
import android.net.Uri
import android.os.RemoteException
import com.nononsenseapps.feeder.db.AUTHORITY
import com.nononsenseapps.feeder.db.COL_FEED
import com.nononsenseapps.feeder.db.COL_FEEDTITLE
import com.nononsenseapps.feeder.db.COL_FEEDURL
import com.nononsenseapps.feeder.db.COL_TAG
import com.nononsenseapps.feeder.db.COL_TITLE
import com.nononsenseapps.feeder.db.COL_URL
import com.nononsenseapps.feeder.db.Cleanup
import com.nononsenseapps.feeder.db.FEED_FIELDS
import com.nononsenseapps.feeder.db.FeedSQL
import com.nononsenseapps.feeder.db.URI_FEEDITEMS
import com.nononsenseapps.feeder.db.URI_FEEDS
import com.nononsenseapps.feeder.db.Util.LongsToStringArray
import com.nononsenseapps.feeder.db.Util.ToStringArray
import com.nononsenseapps.feeder.db.Util.WHEREIDIS
import com.nononsenseapps.feeder.db.Util.WhereIs
import com.nononsenseapps.feeder.util.FileLog
import com.nononsenseapps.feeder.util.Optional
import com.nononsenseapps.feeder.util.getFeeds
import com.nononsenseapps.feeder.util.getIdForFeedItem
import com.nononsenseapps.feeder.util.intoContentProviderOperation
import com.nononsenseapps.feeder.util.notifyAllUris
import com.nononsenseapps.jsonfeed.Feed
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import org.joda.time.DateTime
import org.joda.time.Duration
import java.io.File
import java.util.ArrayList
import java.util.Arrays

object RssLocalSync {

    /**
     * @param feedId if less than '1' then all feeds are synchronized
     * @param tag    of feeds to sync, only used if feedId is less than 1. If empty, all feeds are synced.
     */
    fun syncFeeds(context: Context, feedId: Long, tag: String) {
        val log = FileLog.getInstance(context)
        try {
            val start = DateTime.now()

            // Get all stored feeds
            val feeds: List<FeedSQL> = if (feedId > 0) {
                listFeed(context, feedId)
            } else if (!tag.isEmpty()) {
                listFeeds(context, tag)
            } else {
                listFeeds(context)
            }
            log.d(String.format("Syncing %d feeds: %s", feeds.size, start.toString()))

            val cacheDir = context.externalCacheDir

            val ops: ArrayList<ContentProviderOperation> =
                    Observable.fromIterable(feeds.toMutableList()).subscribeOn(Schedulers.io()).map {
                        // Need the feed later in the computation
                        syncFeed(it, cacheDir) to it
                    }.filter {
                        it.first.isPresent
                    }.map {
                        convertResultToOperations(it.first.get(), it.second, context.contentResolver)
                    }.reduce(ArrayList<ContentProviderOperation>(), {acc, next ->
                        acc.addAll(next)
                        acc
                    }).blockingGet()

            try {
                storeSyncResults(context, ops)
                // Notify that we've updated
                context.contentResolver.notifyAllUris()
            } catch (e: RemoteException) {
                log.d("Error during sync: ${e.message}")
                e.printStackTrace()
            } catch (e: OperationApplicationException) {
                log.d("Error during sync: ${e.message}")
                e.printStackTrace()
            }
/*
            for (f in feeds) {
                syncFeed(f, cacheDir).ifPresent { sf ->
                    val ops = convertResultToOperations(sf, f, context.contentResolver)
                    try {
                        storeSyncResults(context, ops)
                        // Notify that we've updated
                        context.contentResolver.notifyAllUris()
                    } catch (e: RemoteException) {
                        log.d("Error during sync: ${e.message}")
                        e.printStackTrace()
                    } catch (e: OperationApplicationException) {
                        log.d("Error during sync: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }*/

            // Finally, prune excessive items
            try {
                Cleanup.prune(context)
            } catch (e: RemoteException) {
                log.d("Error during cleanup: ${e.message}")
                e.printStackTrace()
            } catch (e: OperationApplicationException) {
                log.d("Error during cleanup: ${e.message}")
                e.printStackTrace()
            }

            val end = DateTime.now()

            val duration = Duration.millis(end.millis - start.millis)
            log.d(String.format("Finished sync after %s", duration.toString()))

            // Notify that we've updated
            context.contentResolver.notifyAllUris()
            // Send notifications for configured feeds
            notify(context)
        } catch (e: Throwable) {
            log.d("Some fatal error during sync: " + e.message)
            e.printStackTrace()
        }

    }

    private fun syncFeed(feedSQL: FeedSQL, cacheDir: File?): Optional<Feed> {
        try {
            return Optional.of(FeedParser.parseFeed(feedSQL.url, cacheDir))
        } catch (error: Throwable) {
            System.err.println("Error when syncing " + feedSQL.url)
            error.printStackTrace()
        }

        return Optional.empty()
    }

    @Synchronized
    @Throws(RemoteException::class, OperationApplicationException::class)
    private fun storeSyncResults(context: Context,
                                 operations: List<ContentProviderOperation>) {
        if (!operations.isEmpty()) {
            context.contentResolver
                    .applyBatch(AUTHORITY, ArrayList(operations))
        }
    }

    /**
     * @param parsedFeed
     * @param feedSQL
     * @return A list of Operations containing no back references
     */
    private fun convertResultToOperations(parsedFeed: Feed,
                                          feedSQL: FeedSQL,
                                          resolver: ContentResolver): ArrayList<ContentProviderOperation> {
        val operations = ArrayList<ContentProviderOperation>()

        val feedOp = ContentProviderOperation.newUpdate(Uri.withAppendedPath(URI_FEEDS,
                java.lang.Long.toString(feedSQL.id)))

        // This can be null, in that case do not override existing value
        val selfLink = parsedFeed.feed_url ?: feedSQL.url

        // Populate with values
        feedOp.withValue(COL_TITLE, parsedFeed.title)
                .withValue(COL_TAG, feedSQL.tag)
                .withValue(COL_URL, selfLink)

        // Add to list of operations
        operations.add(feedOp.build())

        parsedFeed.items?.forEach { entry ->
            if (entry.id != null) {
                val itemId = resolver.getIdForFeedItem(entry.id!!, feedSQL.id)
                val itemOp: ContentProviderOperation.Builder = if (itemId < 1) {
                    ContentProviderOperation.newInsert(URI_FEEDITEMS)
                } else {
                    ContentProviderOperation.newUpdate(Uri.withAppendedPath(URI_FEEDITEMS,
                            itemId.toString()))
                }

                // Use the actual id, because update operation will not return id
                itemOp.withValue(COL_FEED, feedSQL.id)
                        .withValue(COL_FEEDTITLE, feedSQL.displayTitle)
                        .withValue(COL_FEEDURL, selfLink)
                        .withValue(COL_TAG, feedSQL.tag)

                entry.intoContentProviderOperation(parsedFeed, itemOp)

                // Add to list of operations
                operations.add(itemOp.build())
            }
        }

        return operations
    }

    private fun listFeed(context: Context, id: Long): List<FeedSQL> {
        return context.contentResolver.getFeeds(
                FEED_FIELDS.asList(), WHEREIDIS, Arrays.asList(*LongsToStringArray(id)), null)
    }

    private fun listFeeds(context: Context, tag: String): List<FeedSQL> {
        return context.contentResolver.getFeeds(
                FEED_FIELDS.asList(), WhereIs(COL_TAG), Arrays.asList(*ToStringArray(tag)), null)
    }

    private fun listFeeds(context: Context): List<FeedSQL> {
        return context.contentResolver.getFeeds(
                FEED_FIELDS.asList(), null, null, null)
    }
}
