package app.sunflower

import app.sunflower.data.db.MessageEntity
import app.sunflower.ui.chat.versionsOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class VersionsTest {
    private var clock = 0L

    private fun msg(
        id: String,
        role: String,
        turnId: String?,
        variant: Int = 0,
        active: Boolean = true,
    ) = MessageEntity(id = id, conversationId = "c", role = role, content = id, createdAt = clock++, turnId = turnId, variant = variant, active = active)

    @Test
    fun singleVersionShowsNoSwitcher() {
        val (visible, versions) = versionsOf(listOf(msg("u1", "user", "u1"), msg("a1", "assistant", "u1")))
        assertEquals(listOf("u1", "a1"), visible.map { it.id })
        assertNull(versions)
    }

    @Test
    fun hiddenVersionsCountButStayOffScreen() {
        val all =
            listOf(
                msg("u0", "user", "u0"),
                msg("a0", "assistant", "u0"),
                msg("u1", "user", "u1", 0, active = false),
                msg("a1", "assistant", "u1", 0, active = false),
                msg("u1b", "user", "u1", 1),
                msg("a1b", "assistant", "u1", 1),
                msg("u1c", "user", "u1", 2, active = false),
                msg("a1c", "assistant", "u1", 2, active = false),
            )
        val (visible, versions) = versionsOf(all)
        assertEquals(listOf("u0", "a0", "u1b", "a1b"), visible.map { it.id })
        val v = assertNotNull(versions)
        assertEquals("u1", v.turnId)
        assertEquals(3, v.count)
        assertEquals(1, v.index)
    }

    @Test
    fun olderChatsWithoutTurnsShowNoSwitcher() {
        val (visible, versions) = versionsOf(listOf(msg("u", "user", null), msg("a", "assistant", null)))
        assertEquals(2, visible.size)
        assertNull(versions)
    }
}
