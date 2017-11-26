package com.nononsenseapps.feeder.db

import android.database.Cursor
import android.os.Bundle
import com.nononsenseapps.feeder.ui.ARG_AUTHOR
import com.nononsenseapps.feeder.ui.ARG_DATE
import com.nononsenseapps.feeder.ui.ARG_ENCLOSURE
import com.nononsenseapps.feeder.ui.ARG_FEEDTITLE
import com.nononsenseapps.feeder.ui.ARG_FEED_URL
import com.nononsenseapps.feeder.ui.ARG_ID
import com.nononsenseapps.feeder.ui.ARG_IMAGEURL
import com.nononsenseapps.feeder.ui.ARG_LINK
import com.nononsenseapps.feeder.ui.ARG_TITLE
import com.nononsenseapps.feeder.util.bundle
import com.nononsenseapps.feeder.util.contentValues
import com.nononsenseapps.feeder.util.getInt
import com.nononsenseapps.feeder.util.getLong
import com.nononsenseapps.feeder.util.getString
import com.nononsenseapps.feeder.util.setInt
import com.nononsenseapps.feeder.util.setLong
import com.nononsenseapps.feeder.util.setString
import com.nononsenseapps.feeder.util.setStringMaybe
import com.nononsenseapps.feeder.util.sloppyLinkToStrictURL
import com.nononsenseapps.feeder.util.sloppyLinkToStrictURLNoThrows
import org.joda.time.DateTime
import java.net.URI
import java.net.URL


// SQL convention says Table name should be "singular"
const val FEED_ITEM_TABLE_NAME = "FeedItem"


// These fields can be anything you want.
const val COL_GUID = "guid"
const val COL_DESCRIPTION = "description"
const val COL_PLAINTITLE = "plaintitle"
const val COL_PLAINSNIPPET = "plainsnippet"
const val COL_IMAGEURL = "imageurl"
const val COL_ENCLOSURELINK = "enclosurelink"
const val COL_LINK = "link"
const val COL_AUTHOR = "author"
const val COL_PUBDATE = "pubdate"
const val COL_UNREAD = "unread"
const val COL_NOTIFIED = "notified"
// These fields corresponds to columns in Feed table
const val COL_FEED = "feed"
const val COL_FEEDTITLE = "feedtitle"
const val COL_FEEDURL = "feedurl"

// For database projection so order is consistent
@JvmField
val FEED_ITEM_FIELDS = arrayOf(COL_ID, COL_TITLE, COL_DESCRIPTION, COL_PLAINTITLE, COL_PLAINSNIPPET,
        COL_IMAGEURL, COL_LINK, COL_AUTHOR, COL_PUBDATE, COL_UNREAD, COL_FEED, COL_TAG,
        COL_ENCLOSURELINK, COL_FEEDTITLE, COL_NOTIFIED, COL_GUID, COL_FEEDURL)

// In list avoid loading potentially big description field
@JvmField
val FEED_ITEM_FIELDS_FOR_LIST =
        arrayOf(COL_ID, COL_PLAINTITLE, COL_PLAINSNIPPET, COL_IMAGEURL, COL_LINK, COL_AUTHOR,
                COL_PUBDATE, COL_UNREAD, COL_FEED, COL_TAG, COL_ENCLOSURELINK, COL_FEEDTITLE,
                COL_NOTIFIED, COL_GUID, COL_FEEDURL)

val CREATE_FEED_ITEM_TABLE = """
    CREATE TABLE $FEED_ITEM_TABLE_NAME (
      $COL_ID INTEGER PRIMARY KEY,
      $COL_GUID TEXT NOT NULL,
      $COL_TITLE TEXT NOT NULL,
      $COL_DESCRIPTION TEXT NOT NULL,
      $COL_PLAINTITLE TEXT NOT NULL,
      $COL_PLAINSNIPPET TEXT NOT NULL,
      $COL_IMAGEURL TEXT,
      $COL_LINK TEXT,
      $COL_ENCLOSURELINK TEXT,
      $COL_AUTHOR TEXT,
      $COL_PUBDATE TEXT,
      $COL_UNREAD INTEGER NOT NULL DEFAULT 1,
      $COL_NOTIFIED INTEGER NOT NULL DEFAULT 0,
      $COL_FEED INTEGER NOT NULL,
      $COL_FEEDTITLE TEXT NOT NULL,
      $COL_FEEDURL TEXT NOT NULL,
      $COL_TAG TEXT NOT NULL,
      FOREIGN KEY($COL_FEED)
        REFERENCES $FEED_TABLE_NAME($COL_ID)
        ON DELETE CASCADE,
      UNIQUE($COL_GUID,$COL_FEED)
        ON CONFLICT IGNORE
    )"""

const val TRIGGER_NAME: String = "trigger_tag_updater"
val CREATE_TAG_TRIGGER = """
    CREATE TEMP TRIGGER IF NOT EXISTS $TRIGGER_NAME
      AFTER UPDATE OF $COL_TAG,$COL_TITLE
      ON $FEED_TABLE_NAME
      WHEN
        new.$COL_TAG IS NOT old.$COL_TAG
      OR
        new.$COL_TITLE IS NOT old.$COL_TITLE
      BEGIN
        UPDATE $FEED_ITEM_TABLE_NAME
          SET $COL_TAG = new.$COL_TAG,
              $COL_FEEDTITLE = new.$COL_TITLE
          WHERE $COL_FEED IS old.$COL_ID;
      END
"""

data class FeedItemSQL(val id: Long = -1,
                       val guid: String = "",
                       val title: String = "",
                       val description: String = "",
                       val plaintitle: String = "",
                       val plainsnippet: String = "",
                       val imageurl: String? = null,
                       val enclosurelink: String? = null,
                       val author: String? = null,
                       val pubDate: DateTime? = null,
                       val link: String? = null,
                       val tag: String = "",
                       val feedtitle: String = "",
                       val feedUrl: URL = sloppyLinkToStrictURL(""),
                       val notified: Boolean = false,
        // Variable to stop UI flickering
                       var unread: Boolean = false,
                       val feedid: Long = -1) {

    val pubDateString: String?
        get() = pubDate?.toString()

    val enclosureFilename: String?
        get() {
            if (enclosurelink != null) {
                var fname: String? = null
                try {
                    fname = URI(enclosurelink).path.split("/").last()
                } catch (e: Exception) {
                }
                if (fname == null || fname.isEmpty()) {
                    return null
                } else {
                    return fname
                }
            }
            return null
        }

    val domain: String?
        get() {
            val l: String? = enclosurelink ?: link
            if (l != null) {
                try {
                    return URL(l).host.replace("www.", "")
                } catch (e: Throwable) {
                }
            }
            return null
        }

    fun asContentValues() =
            contentValues {
                setString(COL_TITLE to title)
                setString(COL_DESCRIPTION to description)
                setString(COL_PLAINTITLE to plaintitle)
                setString(COL_PLAINSNIPPET to plainsnippet)
                setLong(COL_FEED to feedid)
                setInt(COL_UNREAD to if (unread) 1 else 0)
                setString(COL_FEEDTITLE to feedtitle)
                setString(COL_FEEDURL to feedUrl.toString())
                setInt(COL_NOTIFIED to if (notified) 1 else 0)
                setString(COL_GUID to guid)
                setString(COL_TAG to tag)

                setStringMaybe(COL_IMAGEURL to imageurl)
                setStringMaybe(COL_LINK to link)
                setStringMaybe(COL_AUTHOR to author)
                setStringMaybe(COL_PUBDATE to pubDateString)
                setStringMaybe(COL_ENCLOSURELINK to enclosurelink)
            }

    fun asBundle() =
            bundle {
                storeFeedItem()
            }

    fun storeInBundle(bundle: Bundle): Bundle {
        bundle.storeFeedItem()
        return bundle
    }

    private fun Bundle.storeFeedItem() {
        setLong(ARG_ID to id)
        setString(ARG_TITLE to title)
        setString(ARG_LINK to link)
        setString(ARG_ENCLOSURE to enclosurelink)
        setString(ARG_IMAGEURL to imageurl)
        setString(ARG_FEEDTITLE to feedtitle)
        setString(ARG_AUTHOR to author)
        setString(ARG_DATE to pubDateString)
        setString(ARG_FEED_URL to feedUrl.toString())
    }
}

fun Cursor.asFeedItem(): FeedItemSQL {
    return FeedItemSQL(id = getLong(COL_ID) ?: -1,
            guid = getString(COL_GUID) ?: "",
            title = getString(COL_TITLE) ?: "",
            description = getString(COL_DESCRIPTION) ?: "",
            plaintitle = getString(COL_PLAINTITLE) ?: "",
            plainsnippet = getString(COL_PLAINSNIPPET) ?: "",
            tag = getString(COL_TAG) ?: "",
            feedid = getLong(COL_FEED) ?: -1,
            feedtitle = getString(COL_FEEDTITLE) ?: "",
            feedUrl = sloppyLinkToStrictURLNoThrows(getString(COL_FEEDURL) ?: ""),
            notified = when (getInt(COL_NOTIFIED) ?: 0) {
                1 -> true
                else -> false
            },
            unread = when (getInt(COL_UNREAD) ?: 1) {
                1 -> true
                else -> false
            },
            imageurl = getString(COL_IMAGEURL),
            link = getString(COL_LINK),
            author = getString(COL_AUTHOR),
            pubDate = when (getString(COL_PUBDATE)) {
                null -> null
                else -> {
                    var dt: DateTime? = null
                    try {
                        dt = DateTime.parse(getString(COL_PUBDATE))
                    } catch(t: Throwable) {
                    }
                    dt
                }
            },
            enclosurelink = getString(COL_ENCLOSURELINK))
}

