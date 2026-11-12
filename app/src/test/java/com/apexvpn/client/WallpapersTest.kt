package com.apexvpn.client

import com.apexvpn.client.data.Wallpapers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Каталог живых обоев (пульт → docs/config/wallpapers.json) и ответ Wikimedia Commons. */
class WallpapersTest {
    @Test fun realCatalogFromRepo() {
        val f = listOf(File("../docs/config/wallpapers.json"), File("docs/config/wallpapers.json")).firstOrNull { it.exists() } ?: return
        val list = Wallpapers.parseCatalog(f.readText())
        assertTrue("каталог обоев пустой или битый", list.isNotEmpty())
        list.forEach { w ->
            if (!w.url.startsWith("https://")) {
                val file = listOf(File("../docs/" + w.url), File("docs/" + w.url)).firstOrNull { it.exists() }
                assertTrue("нет файла docs/${w.url}", file != null)
            }
        }
    }

    @Test fun catalogFiltersBadItems() {
        val list = Wallpapers.parseCatalog("""{"live":[{"id":"ok","title":"Да","url":"wallpapers/a.mp4"},{"id":"Bad id","url":"x"},
            {"id":"nourl"},{"id":"hid","url":"x.mp4","hidden":true},{"id":"ok","url":"dup.mp4"}]}""")
        assertEquals(listOf("ok"), list.map { it.id })
        assertEquals(emptyList<Wallpapers.Live>(), Wallpapers.parseCatalog("не json"))
    }

    @Test fun sourcesAreSafe() {
        assertEquals(listOf("https://x.ru/a.mp4"), Wallpapers.sources("https://x.ru/a.mp4"))
        assertTrue(Wallpapers.sources("http://x.ru/a.mp4").isEmpty())
        assertTrue(Wallpapers.sources("../../etc").isEmpty())
        assertTrue(Wallpapers.sources("wallpapers/a.mp4").all { it.startsWith("https://") && it.endsWith("/wallpapers/a.mp4") })
        assertEquals("video:net:mesh", Wallpapers.key("mesh")); assertEquals("mesh", Wallpapers.idOf("video:net:mesh"))
    }

    @Test fun commonsAnswer() {
        val json = """{"query":{"pages":{"5":{"index":2,"title":"File:Alps.jpg","imageinfo":[{"mime":"image/jpeg","url":"https://upload.wikimedia.org/a/Alps.jpg",
            "thumburl":"https://upload.wikimedia.org/thumb/a/Alps.jpg/360px-Alps.jpg","extmetadata":{"Artist":{"value":"<a href='x'>Анна</a>"},"LicenseShortName":{"value":"CC BY-SA 4.0"}}}]},
            "6":{"index":1,"title":"File:Map.svg","imageinfo":[{"mime":"image/svg+xml","thumburl":"https://x/360px-Map.png"}]}}}}"""
        val p = Wallpapers.parsePhotos(json)
        assertEquals(1, p.size)
        assertEquals("Alps", p[0].title)
        assertEquals("https://upload.wikimedia.org/thumb/a/Alps.jpg/1600px-Alps.jpg", p[0].full)
        assertEquals("Анна · CC BY-SA 4.0", p[0].author)
    }

    @Test fun wallhavenOnlySafe() {
        val json = """{"data":[{"purity":"sfw","path":"https://w.wallhaven.cc/full/ab/wallhaven-ab.jpg","resolution":"1080x1920","thumbs":{"original":"https://th.wallhaven.cc/orig/ab/ab.jpg"}},
            {"purity":"sketchy","path":"https://w.wallhaven.cc/full/cd/x.jpg"},{"purity":"sfw","path":"http://bad"}]}"""
        val p = Wallpapers.parseWallhaven(json)
        assertEquals(1, p.size)
        assertEquals("https://th.wallhaven.cc/orig/ab/ab.jpg", p[0].thumb)
        assertTrue(Wallpapers.photoTopics.any { it.title == "Аниме" } && Wallpapers.photoTopics.any { it.title == "Игры" })
    }

    @Test fun wallpaperOfTheDay() {
        assertEquals(null, Wallpapers.pickForDay(emptyList<String>(), 5))
        assertEquals("b", Wallpapers.pickForDay(listOf("a", "b", "c"), 4))
        assertEquals("c", Wallpapers.pickForDay(listOf("a", "b", "c"), -1))
        assertEquals(Wallpapers.photoTopics.size, Wallpapers.photoTopics.map { it.id }.toSet().size)
    }

    @Test fun favoritesRoundTrip() {
        val list = listOf(
            Wallpapers.Fav("live", "neoncity", "Неон-город", "wallpapers/live/neoncity.jpg", "wallpapers/live/neoncity.mp4"),
            Wallpapers.Fav("pic", "https://w.wallhaven.cc/full/ab/x.jpg", "1080x1920", "https://th/x.jpg", "https://w.wallhaven.cc/full/ab/x.jpg"),
        )
        assertEquals(list, Wallpapers.decodeFavs(Wallpapers.encodeFavs(list)))
        assertEquals(emptyList<Wallpapers.Fav>(), Wallpapers.decodeFavs("битое"))
        assertEquals(1, Wallpapers.decodeFavs("""[{"type":"live","id":"a"},{"type":"live","id":"a"},{"type":"x","id":"b"}]""").size)
    }

    @Test fun pexelsPicksPortraitHd() {
        val json = """{"videos":[{"id":42,"image":"https://images.pexels.com/42.jpg","user":{"name":"Ann"},"video_files":[
            {"file_type":"video/mp4","width":1920,"height":1080,"link":"https://videos.pexels.com/land.mp4"},
            {"file_type":"video/mp4","width":540,"height":960,"link":"https://videos.pexels.com/sd.mp4"},
            {"file_type":"video/mp4","width":1080,"height":1920,"link":"https://videos.pexels.com/hd.mp4"},
            {"file_type":"video/mp4","width":2160,"height":3840,"link":"https://videos.pexels.com/4k.mp4"}]},
            {"id":7,"video_files":[{"file_type":"video/mp4","width":1920,"height":1080,"link":"https://x/land.mp4"}]}]}"""
        val v = Wallpapers.parsePexels(json)
        assertEquals(1, v.size)
        assertEquals("px42", v[0].id)
        assertEquals("https://videos.pexels.com/hd.mp4", v[0].url)
        assertEquals("Видео: Ann · Pexels", v[0].credit)
        assertEquals("abcDEF1234567890abcdef12", Wallpapers.parseKey("""{"pexelsKey":"abcDEF1234567890abcdef12","live":[]}"""))
        assertEquals("", Wallpapers.parseKey("""{"pexelsKey":"bad key!"}"""))
        assertTrue(Wallpapers.liveTopics.any { it.title == "Аниме" })
    }

    @Test fun pixabayPortraitFirst() {
        val json = """{"hits":[
            {"id":1,"user":"Bob","videos":{"large":{"url":"https://cdn.pixabay.com/1-large.mp4","width":1920,"height":1080,"thumbnail":"https://cdn.pixabay.com/1.jpg"}}},
            {"id":2,"user":"Eva","videos":{"large":{"url":"https://cdn.pixabay.com/2-large.mp4","width":3840,"height":2160},"medium":{"url":"https://cdn.pixabay.com/2-medium.mp4","width":1080,"height":1920,"thumbnail":"https://cdn.pixabay.com/2.jpg"}}}]}"""
        val v = Wallpapers.parsePixabay(json)
        assertEquals(listOf("pb2", "pb1"), v.map { it.id })
        assertEquals("https://cdn.pixabay.com/2-medium.mp4", v[0].url)
        assertEquals("https://cdn.pixabay.com/2.jpg", v[0].thumb)
        assertEquals("12345678-0123456789abcdef0123", Wallpapers.parsePixabayKey("""{"pixabayKey":"12345678-0123456789abcdef0123"}"""))
    }

    @Test fun commonsVideosShortWebm() {
        val json = """{"query":{"pages":{"1":{"pageid":11,"index":1,"title":"File:Aurora timelapse.webm","videoinfo":[{"duration":30.5,"thumburl":"https://upload.wikimedia.org/a.jpg",
            "derivatives":[{"src":"https://upload.wikimedia.org/a.webm.360p.webm","type":"video/webm; codecs=\"vp9\"","width":640,"height":360},
                           {"src":"https://upload.wikimedia.org/a.webm.1080p.vp9.webm","type":"video/webm; codecs=\"vp9\"","width":1920,"height":1080},
                           {"src":"https://upload.wikimedia.org/a.webm.2160p.webm","type":"video/webm","width":3840,"height":2160}]}]},
            "2":{"pageid":12,"index":2,"title":"File:Long.webm","videoinfo":[{"duration":900,"derivatives":[]}]}}}}"""
        val v = Wallpapers.parseCommonsVideos(json)
        assertEquals(1, v.size)
        assertEquals("wc11", v[0].id)
        assertEquals("https://upload.wikimedia.org/a.webm.1080p.vp9.webm", v[0].url)
        assertEquals("Aurora timelapse", v[0].title)
    }

    @Test fun giphyOnlyRatedG() {
        val json = """{"data":[
            {"id":"abc123","title":"Anime rain","rating":"g","images":{"original_mp4":{"mp4":"https://media.giphy.com/a.mp4","width":"720","height":"1280"},"fixed_width_still":{"url":"https://media.giphy.com/a_s.gif"}}},
            {"id":"bad","rating":"r","images":{"original_mp4":{"mp4":"https://media.giphy.com/b.mp4","width":"480","height":"854"}}},
            {"id":"tiny","rating":"g","images":{"original_mp4":{"mp4":"https://media.giphy.com/c.mp4","width":"480","height":"270"}}}]}"""
        val v = Wallpapers.parseGiphy(json)
        assertEquals(listOf("gpabc123"), v.map { it.id })
        assertEquals("https://media.giphy.com/a.mp4", v[0].url)
        assertEquals("abcdefghijklmnopqrstuvwxyz012345", Wallpapers.parseGiphyKey("""{"giphyKey":"abcdefghijklmnopqrstuvwxyz012345"}"""))
    }

    @Test fun sourceOrderFromPult() {
        assertEquals(listOf("commons", "giphy", "pixabay", "pexels"), Wallpapers.parseSources("""{"sources":["commons","giphy","bad","giphy"]}"""))
        assertEquals("giphy", Wallpapers.defaultSources.last())
        assertEquals(Wallpapers.defaultSources, Wallpapers.parseSources("""{"live":[]}"""))
    }
}
