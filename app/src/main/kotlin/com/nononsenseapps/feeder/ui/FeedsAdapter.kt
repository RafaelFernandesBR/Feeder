package com.nononsenseapps.feeder.ui

import android.support.v4.util.ArraySet
import android.support.v7.util.DiffUtil
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.nononsenseapps.feeder.R

internal class FeedsAdapter(private val activity: BaseActivity) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val expandedTags: MutableSet<String> = ArraySet(setOf(""))
    private var allItems: List<FeedWrapper> = emptyList()
    private var visibleItems: List<FeedWrapper> = emptyList()

    init {
        setHasStableIds(true)
    }


    override fun getItemCount(): Int = visibleItems.size

    override fun getItemId(position: Int): Long {
        val item = visibleItems[position]
        return if (item.item != null) {
            item.item.id
        } else {
            item.hashCode().toLong()
        }
    }

    override fun getItemViewType(position: Int): Int {
        val item = visibleItems[position]
        return when {
            item.isTop -> VIEWTYPE_TOP
            item.isTag -> VIEWTYPE_TAG
            item.tag.isEmpty() -> VIEWTYPE_FEED
            else -> VIEWTYPE_FEED_CHILD
        }
    }

    fun updateData(items: List<FeedWrapper>) {
        allItems = items
        updateView()
    }

    private fun updateView() {
        val oldVisibleItems = visibleItems
        visibleItems = allItems.filter {
            when {
                it.isTop -> true
                it.isTag -> expandedTags.contains("")
                else -> expandedTags.contains(it.tag)
            }
        }

        val diffResult = DiffUtil.calculateDiff(object: DiffUtil.Callback() {
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldItem: FeedWrapper = oldVisibleItems[oldItemPosition]
                val newItem: FeedWrapper = visibleItems[newItemPosition]

                return when {
                    oldItem.isTag && newItem.isTag -> oldItem.tag == newItem.tag
                    !oldItem.isTag && !newItem.isTag -> oldItem.item?.id == newItem.item?.id
                    else -> false
                }
            }

            override fun getOldListSize(): Int = oldVisibleItems.size

            override fun getNewListSize(): Int = visibleItems.size

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldItem: FeedWrapper = oldVisibleItems[oldItemPosition]
                val newItem: FeedWrapper = visibleItems[newItemPosition]

                return when {
                    oldItem.isTag && newItem.isTag -> {
                                oldItem.unreadCount == newItem.unreadCount
                    }
                    !oldItem.isTag && !newItem.isTag -> {
                        oldItem.item?.displayTitle == newItem.item?.displayTitle &&
                                oldItem.unreadCount == newItem.unreadCount
                    }
                    else -> false
                }
            }
        })

        diffResult.dispatchUpdatesTo(this)
    }

    /**
     * @param tag
     * @return true if tag is expanded after this call, false otherwise
     */
    fun toggleExpansion(item: FeedWrapper): Boolean {
        val result = if (expandedTags.contains(item.tag)) {
            expandedTags.remove(item.tag)
            false
        } else {
            expandedTags.add(item.tag)
            true
        }
        updateView()
        return result
    }

    private fun isExpanded(item: FeedWrapper): Boolean = expandedTags.contains(item.tag)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder? = when (viewType) {
        VIEWTYPE_FEED -> FeedHolder(activity, LayoutInflater.from(activity).inflate(R.layout.view_feed, parent, false))
        VIEWTYPE_FEED_CHILD -> FeedHolder(activity, LayoutInflater.from(activity).inflate(R.layout.view_feed_child, parent, false))
        VIEWTYPE_TAG -> TagHolder(activity, LayoutInflater.from(activity).inflate(R.layout.view_feed_tag, parent, false))
        VIEWTYPE_TOP -> TopHolder(activity, LayoutInflater.from(activity).inflate(R.layout.view_feed, parent, false))
        else -> null
    }


    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val wrap = visibleItems[position]
        when (getItemViewType(position)) {
            VIEWTYPE_FEED, VIEWTYPE_FEED_CHILD -> {
                val fh = holder as FeedHolder
                fh.item = wrap.item
                fh.title.text = fh.item!!.displayTitle
                fh.unreadCount.text = "${wrap.unreadCount}"
                fh.unreadCount.visibility = if (wrap.unreadCount > 0) View.VISIBLE else View.INVISIBLE

                if (fh.item!!.icon != null) {
                    // Take up width
                    holder.icon.visibility = View.VISIBLE
                    // Load image when item has been measured
                    holder.itemView.viewTreeObserver.addOnPreDrawListener(fh)
                } else {
                    holder.icon.visibility = View.GONE
                }
            }
            VIEWTYPE_TAG -> {
                val th = holder as TagHolder
                th.wrap = wrap
                th.title.text = wrap.tag
                if (isExpanded(wrap)) {
                    th.expander.setImageResource(R.drawable.tinted_expand_less)
                } else {
                    th.expander.setImageResource(R.drawable.tinted_expand_more)
                }
                th.unreadCount.text = "${wrap.unreadCount}"
                th.unreadCount.visibility = if (wrap.unreadCount > 0) View.VISIBLE else View.INVISIBLE
            }
            VIEWTYPE_TOP -> {
                val tp = holder as TopHolder
                tp.unreadCount.text = "${wrap.unreadCount}"
                tp.unreadCount.visibility = if (wrap.unreadCount > 0) View.VISIBLE else View.INVISIBLE
            }
        }
    }

    companion object {


        private val VIEWTYPE_TOP = 0
        private val VIEWTYPE_TAG = 1
        private val VIEWTYPE_FEED = 2
        private val VIEWTYPE_FEED_CHILD = 3
    }
}
