package com.nononsenseapps.feeder.db.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nononsenseapps.feeder.db.COL_CUSTOM_TITLE
import com.nononsenseapps.feeder.db.COL_EXTRACTFULLTEXT
import com.nononsenseapps.feeder.db.COL_ID
import com.nononsenseapps.feeder.db.COL_IMAGEURL
import com.nononsenseapps.feeder.db.COL_LASTSYNC
import com.nononsenseapps.feeder.db.COL_NOTIFY
import com.nononsenseapps.feeder.db.COL_RESPONSEHASH
import com.nononsenseapps.feeder.db.COL_TAG
import com.nononsenseapps.feeder.db.COL_TITLE
import com.nononsenseapps.feeder.db.COL_URL
import com.nononsenseapps.feeder.db.FEEDS_TABLE_NAME
import com.nononsenseapps.feeder.util.sloppyLinkToStrictURL
import com.nononsenseapps.feeder.util.sloppyLinkToStrictURLNoThrows
import com.nononsenseapps.jsonfeed.Feed
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import java.net.URL

@Entity(tableName = FEEDS_TABLE_NAME,
        indices = [Index(value = [COL_URL], unique = true),
            Index(value = [COL_ID, COL_URL, COL_TITLE], unique = true)])
data class Feed @Ignore constructor(
        @PrimaryKey(autoGenerate = true)
        @ColumnInfo(name = COL_ID) var id: Long = ID_UNSET,
        @ColumnInfo(name = COL_TITLE) var title: String = "",
        @ColumnInfo(name = COL_CUSTOM_TITLE) var customTitle: String = "",
        @ColumnInfo(name = COL_URL) var url: URL = sloppyLinkToStrictURL(""),
        @ColumnInfo(name = COL_TAG) var tag: String = "",
        @ColumnInfo(name = COL_NOTIFY) var notify: Boolean = false,
        @ColumnInfo(name = COL_IMAGEURL) var imageUrl: URL? = null,
        @ColumnInfo(name = COL_LASTSYNC, typeAffinity = ColumnInfo.INTEGER) var lastSync: DateTime = DateTime(0L, DateTimeZone.UTC),
        @ColumnInfo(name = COL_RESPONSEHASH) var responseHash: Int = 0,
        @ColumnInfo(name = COL_EXTRACTFULLTEXT) var extractFullText: Boolean = false
) {
    constructor() : this(id = ID_UNSET)

    val displayTitle: String
        get() = (if (customTitle.isBlank()) title else customTitle)

}
