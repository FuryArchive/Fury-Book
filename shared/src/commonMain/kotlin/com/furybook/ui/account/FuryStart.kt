package com.furybook.ui.account

/** How the player last entered the app: not yet chosen, as a guest, or with an account. */
enum class FuryStartMode(val key: String) {
    NONE("none"),
    GUEST("guest"),
    SIGNED_IN("signed-in");

    companion object {
        fun fromKey(key: String?): FuryStartMode = entries.firstOrNull { it.key == key?.trim() } ?: NONE
    }
}

/** Where the start mode lives on this device. */
interface FuryStartStore {
    fun load(): FuryStartMode
    fun save(mode: FuryStartMode)
}

class InMemoryFuryStartStore(private var mode: FuryStartMode = FuryStartMode.NONE) : FuryStartStore {
    override fun load(): FuryStartMode = mode
    override fun save(mode: FuryStartMode) { this.mode = mode }
}

/**
 * The login screen comes first unless the player is signed in or chose «Играть без входа».
 * Characters already on the device (an update from before login existed) still see it once;
 * [hasLocalCharacters] only changes what the guest button promises.
 */
@Suppress("UNUSED_PARAMETER")
fun furyShowsLogin(mode: FuryStartMode, signedIn: Boolean, hasLocalCharacters: Boolean): Boolean = when {
    signedIn -> false
    mode == FuryStartMode.GUEST -> false
    else -> true
}
