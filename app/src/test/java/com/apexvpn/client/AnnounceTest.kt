package com.apexvpn.client

import com.apexvpn.client.data.AnnounceParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Объявление из Remnawave: картинка, кнопка, «Истекает: никогда». */
class AnnounceTest {
    @Test fun imageAndButton() {
        val r = AnnounceParser.parse("Скидка!\n![](https://x.ru/b.jpg)\n[Продлить](https://x.ru/pay)")
        assertEquals("Скидка!", r.text)
        assertEquals("https://x.ru/b.jpg", r.image)
        assertEquals("Продлить", r.button)
        assertEquals("https://x.ru/pay", r.url)
    }

    @Test fun unsafeLinksIgnored() {
        assertFalse(AnnounceParser.safeLink("javascript:alert(1)"))
        assertEquals("", AnnounceParser.parse("[Жми](http://x.ru)").url)
    }

    @Test fun endlessSubscription() {
        assertEquals("Истекает: никогда", AnnounceParser.tidy("Истекает: 26538", expireAt = 0, total = 0))
        assertEquals("Осталось: безлимит", AnnounceParser.tidy("Осталось ГБ: 0", expireAt = 1, total = 0))
        assertEquals("Истекает: 12.12.2026", AnnounceParser.tidy("Истекает: 12.12.2026", expireAt = 1, total = 1))
    }
}
