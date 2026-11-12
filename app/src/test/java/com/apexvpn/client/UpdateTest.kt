package com.apexvpn.client

import com.apexvpn.client.update.UpdateManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Сравнение версий при обновлении: ошибка здесь = «обновление» на старую версию или его отсутствие. */
class UpdateTest {
    @Test fun versionNumbers() {
        assertEquals(3_019_006, UpdateManager.versionNumber("3.19.6"))
        assertEquals(3_019_006, UpdateManager.versionNumber("v3.19.6"))
        assertEquals(3_010_000, UpdateManager.versionNumber("3.10"))
    }

    @Test fun order() {
        val names = listOf("3.9.9", "3.19.0-beta1", "3.19.0-beta2", "3.19.0", "3.19.6", "3.20.0")
        names.zipWithNext().forEach { (a, b) -> assertTrue("$a должна быть раньше $b", UpdateManager.rank(a) < UpdateManager.rank(b)) }
        assertTrue(UpdateManager.isBeta("3.19.0-Beta1"))
    }

    @Test fun notesWithoutMarkup() {
        assertEquals("• Новое\n• Исправлено", UpdateManager.cleanNotes("- **Новое**\r\n- `Исправлено`\n---\nфайлы"))
    }
}
