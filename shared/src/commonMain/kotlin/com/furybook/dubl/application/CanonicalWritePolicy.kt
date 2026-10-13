package com.furybook.dubl.application

/** A protected display is never a writable legacy session, even with disabled capabilities. */
enum class CanonicalWritePolicy { LegacyIsolated, Protected }
class CanonicalWriteRequired : IllegalStateException("Используйте подтверждённую каноническую операцию; старое редактирование недоступно")
internal fun CanonicalWritePolicy.beforeLegacyWrite() { if (this == CanonicalWritePolicy.Protected) throw CanonicalWriteRequired() }
