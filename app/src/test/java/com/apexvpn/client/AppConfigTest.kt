package com.apexvpn.client

import com.apexvpn.client.data.AppConfig
import com.apexvpn.client.data.AppConfigJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Настройки из пульта (docs/config/app.json): разбор, техработы по расписанию, перезапуск VPN. */
class AppConfigTest {
    private val hour = 3_600_000L
    private val now = 1_800_000_000_000L

    @Test fun realFileFromRepoParses() {
        // Тот же файл, что сохраняет пульт — битый файл ломал бы все баннеры и пометки
        val f = listOf(File("../docs/config/app.json"), File("docs/config/app.json")).firstOrNull { it.exists() } ?: return
        assertNotNull("docs/config/app.json не разбирается", AppConfigJson.parse(f.readText()))
    }

    @Test fun brokenOrEmptyFileIsIgnored() {
        assertNull(AppConfigJson.parse(""))
        assertNull(AppConfigJson.parse("{ это не json"))
        assertEquals(AppConfig.Config(), AppConfigJson.parse("{}"))
    }

    @Test fun bannersLinksAndUpdate() {
        val c = AppConfigJson.parse("""
            {"banners":[{"text":"  Привет ","url":"http://bad","devices":["tv"],"countries":["ru"]},{"text":""}],
             "links":{"support":"https://t.me/x","channel":"javascript:alert(1)","site":"https://dubstep2004.github.io"},
             "update":{"required":110,"revoked":[100,101]},"features":{"lte":false}}
        """)!!
        assertEquals(1, c.banners.size)
        assertEquals("Привет", c.banners[0].text)
        assertEquals("", c.banners[0].url) // только https
        assertEquals(setOf("RU"), c.banners[0].countries)
        assertEquals("https://t.me/x", c.support)
        assertEquals("", c.channel)
        assertEquals(110, c.requiredCode)
        assertEquals(setOf(100, 101), c.revoked)
        assertFalse(c.on("lte")); assertTrue(c.on("hagezi"))
    }

    private fun cfg(from: Long, until: Long, state: String = "maintenance") = AppConfigJson.parse(
        """{"servers":{"status":{"Германия":{"state":"$state","note":"т","from":$from,"until":$until}}}}"""
    )!!

    @Test fun scheduledMaintenance() {
        val c = cfg(now + hour, now + 3 * hour)
        assertNull("ещё не началось", c.maintenanceFor("🇩🇪 Германия 1", within = 0, now = now))
        assertNotNull("начнётся через час — предупредить заранее", c.maintenanceFor("🇩🇪 Германия 1", within = 2 * hour, now = now))
        assertNotNull("идёт сейчас", c.maintenanceFor("Германия", within = 0, now = now + 2 * hour))
        assertNull("закончилось", c.maintenanceFor("Германия", within = 0, now = now + 4 * hour))
        assertNull("другой сервер", c.maintenanceFor("Нидерланды", within = 9 * hour, now = now))
        assertNull("«медленно» — не техработы", cfg(0, 0, "slow").maintenanceFor("Германия", 0, now))
    }

    @Test fun activeKeyChangesWhenMaintenanceStartsAndEnds() {
        val c = cfg(now + hour, now + 2 * hour)
        val before = c.activeKey(now); val during = c.activeKey(now + 90 * 60_000); val after = c.activeKey(now + 3 * hour)
        assertNotEquals(before, during)
        assertEquals(before, after)
    }

    @Test fun bannerTextDoesNotRestartVpn() {
        // 3.18.3: баннер и ссылки не перезапускают VPN, пометки серверов — перезапускают
        val a = AppConfigJson.parse("""{"banners":[{"text":"раз"}]}""")!!
        val b = AppConfigJson.parse("""{"banners":[{"text":"два"}],"links":{"support":"https://t.me/y"}}""")!!
        assertEquals(AppConfigJson.coreKey(a), AppConfigJson.coreKey(b))
        assertNotEquals(AppConfigJson.coreKey(a), AppConfigJson.coreKey(cfg(0, 0)))
    }

    @Test fun hiddenAndRecommendedMatchByPartOfName() {
        val c = AppConfigJson.parse("""{"servers":{"hidden":["Обход №1"],"recommended":"Финляндия"}}""")!!
        assertTrue(c.isHidden("📶 Обход №1 LTE")); assertFalse(c.isHidden("Обход №2"))
        assertTrue(c.isRecommended("🇫🇮 Финляндия"))
        assertTrue(c.blocked("Обход №1"))
    }
}
