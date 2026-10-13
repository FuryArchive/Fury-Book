package com.furybook.web

/** View discovery only, never financial authority. Initial uncertainty cannot enable a legacy writer. */
enum class WebCanonicalReadPhase { InitialLoading, Refreshing, Ready, Unavailable }
data class WebCanonicalReadState(val phase: WebCanonicalReadPhase, val classifiedProtected: Boolean?, val error: String? = null) {
    val blocksLegacy: Boolean get() = classifiedProtected != false || phase != WebCanonicalReadPhase.Ready
    // Routine protected refresh/failure must not unmount navigation, search or retained command state.
    val navigationAvailable: Boolean get() = classifiedProtected == true ||
        (classifiedProtected == false && phase == WebCanonicalReadPhase.Ready)
    fun begin() = copy(phase = if (classifiedProtected == null) WebCanonicalReadPhase.InitialLoading else WebCanonicalReadPhase.Refreshing, error = null)
    // Losing acknowledged heads cannot authorize legacy writes within this owner/root.
    fun loaded(protected: Boolean) = WebCanonicalReadState(WebCanonicalReadPhase.Ready, classifiedProtected == true || protected)
    fun failed(message: String) = copy(phase = WebCanonicalReadPhase.Unavailable, error = message)
    companion object {
        fun initial(hasStore: Boolean, hasVerifiedHeads: Boolean) = when {
            hasVerifiedHeads -> WebCanonicalReadState(WebCanonicalReadPhase.Ready, true)
            hasStore -> WebCanonicalReadState(WebCanonicalReadPhase.InitialLoading, null)
            else -> WebCanonicalReadState(WebCanonicalReadPhase.Ready, false)
        }
    }
}
