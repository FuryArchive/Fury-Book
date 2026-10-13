package com.furybook.dubl.acquisition

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext

/** Per invocation, never the platform's mutable active UI model. Not a persistence journal. */
class AcquisitionDispatchContext(val scope:AcquisitionScope,val operationId:String):AbstractCoroutineContextElement(Key) {
    companion object Key:CoroutineContext.Key<AcquisitionDispatchContext>
}
suspend fun capturedAcquisitionCommand():AcquisitionDispatchContext =
    checkNotNull(currentCoroutineContext()[AcquisitionDispatchContext]) { "Explicit captured command scope is required" }
