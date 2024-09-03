package models

import pages.click.PagesClick.ClickPagesEvent
import pages.scroll.PagesScroll.ScrollPagesEvent
import pages.view.PagesView.ViewPagesEvent

data class ActivityEnvelope(
    val clickPagesEvent: ClickPagesEvent? = null,
    val scrollPagesEvent: ScrollPagesEvent? = null,
    val viewPagesEvent: ViewPagesEvent? = null,
) {
    init {
        if (clickPagesEvent != null && scrollPagesEvent != null && viewPagesEvent != null) {
            throw IllegalStateException("The envelope must contain at least one event")
        }
    }

    fun hasViewPagesEvent(): Boolean = viewPagesEvent != null
}
