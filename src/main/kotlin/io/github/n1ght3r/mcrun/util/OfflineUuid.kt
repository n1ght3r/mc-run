package io.github.n1ght3r.mcrun.util

import java.util.UUID

/** The UUID an offline-mode server assigns to a player name. */
object OfflineUuid {
    fun of(username: String): UUID =
        UUID.nameUUIDFromBytes("OfflinePlayer:$username".toByteArray(Charsets.UTF_8))

    /** The launcher passes `${auth_uuid}` without dashes. */
    fun undashed(username: String): String = of(username).toString().replace("-", "")
}
