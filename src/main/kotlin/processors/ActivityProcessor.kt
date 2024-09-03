package processors

import models.ActivityEnvelope
import store.activity.StoreActivity.ActivityStore

data class ActivityProcessor(val viewedPages: MutableSet<String> = mutableSetOf()) {
    fun process(activityEnvelope: ActivityEnvelope): ActivityProcessor {
        if (!activityEnvelope.hasViewPagesEvent())
            return this

        viewedPages.add(activityEnvelope.viewPagesEvent!!.pageId)

        return this
    }

    companion object {
        fun combine(left: ActivityProcessor, right: ActivityProcessor): ActivityProcessor {
            return ActivityProcessor(
                (left.viewedPages + right.viewedPages).toMutableSet()
            )
        }

        fun from(data: ActivityStore): ActivityProcessor {
            return ActivityProcessor(
                viewedPages = data.pageIdsList.toMutableSet()
            )
        }
    }
}