package com.notsonata.comixium.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OfflineServerTest {
    @get:Rule val tmp = TemporaryFolder()

    private val link = ChapterLink("9nej6-blade", "11578620", "185")
    private val server = OfflineServer()
    private val page = "https://comix.ws/title/9nej6-blade/11578620-chapter-185"
    private val api = "https://comix.ws/api/v1/chapters/11578620?v=0&w=0&_=fdj.new-token"
    private val js = "https://comix.to/assets/build/1/dist/main-a.js"
    private val font = "https://fonts.gstatic.com/s/poppins/v24/a.woff2"
    private val lookup = "https://comix.to/api/v1/threads/lookup?page_identifier=manga1_chap185_vol0&page_url=%2Ftitle%2F9nej6-blade%2F11578620-chapter-185"

    private val online = OfflineServer.Mode(online = true)
    private val offline = OfflineServer.Mode(online = false)

    @Before fun saveAChapter() {
        val store = DownloadStore(tmp.root)
        store.writeChapter(DownloadStore.Chapter(link, "Ch.185", pages = 2, complete = true, recorded = true))
        store.newPageFile(link, 1, "webp").writeBytes(byteArrayOf(1))
        store.newPageFile(link, 2, "jpg").writeBytes(byteArrayOf(2))
        store.writeRecording(
            link, "<html></html>",
            listOf(
                DownloadStore.Response("https://comix.to/api/v1/chapters/11578620?v=0&w=0&_=fdj.old", "application/json", "2", "{}".toByteArray()),
                DownloadStore.Response(lookup, "application/json", "", "{}".toByteArray()),
            ),
            listOf("/hi/page-one", "/hi/page-two"), listOf(js, font),
        )
        store.writeAsset(js, "application/javascript", "x".toByteArray())
        store.writeAsset(font, "font/woff2", "f".toByteArray())
        // A chapter still downloading isn't served.
        val other = ChapterLink("9nej6-blade", "2", "186")
        store.writeChapter(DownloadStore.Chapter(other, "Ch.186", complete = false, recorded = true))
        store.writeRecording(other, "<html></html>", emptyList(), listOf("/hi/unfinished"), emptyList())
        server.rebuild(store)
    }

    @Test fun pageImagesComeFromDiskOnAnyHost() {
        val s = server.lookup("https://pq4.anyhost.site/hi/page-two", "GET", false, online)
        assertEquals("002.jpg", s?.file?.name)
        assertEquals("image/jpeg", s?.type)
        assertNull(server.lookup("https://pq4.anyhost.site/hi/unfinished", "GET", false, offline))
        assertNull(server.lookup("https://pq4.anyhost.site/hi/page-two", "POST", false, offline))
    }

    @Test fun theChapterPageComesFromDiskWhenOfflineOrInTheDownloadedReader() {
        assertNull(server.lookup(page, "GET", true, online))
        assertTrue(server.lookup(page, "GET", true, offline)!!.isPage)
        assertTrue(server.lookup(page, "GET", true, online.copy(preferOffline = true))!!.isPage)
        assertNull(server.lookup("https://comix.ws/title/9nej6-blade/2-chapter-186", "GET", true, offline))
        assertNull(server.lookup("https://evil.example/title/9nej6-blade/11578620-chapter-185", "GET", true, offline))
    }

    @Test fun chapterDataMatchesThePageItCameWith() {
        // A live page gets live data (it must match the live page's scripts).
        assertNull(server.lookup(api, "GET", false, online))
        val s = server.lookup(api, "GET", false, online.copy(replaying = true))
        assertNotNull(s)
        assertEquals("2", s!!.headers["x-enc"])
        assertNotNull(server.lookup(api, "GET", false, offline))
    }

    @Test fun sharedFilesOnAnyDomainAndCommentsOffline() {
        assertEquals("x", server.lookup("https://comix.ws/assets/build/1/dist/main-a.js", "GET", false, online)?.file?.readText())
        assertEquals("font/woff2", server.lookup(font, "GET", false, online)?.type)
        assertEquals("*", server.lookup(font, "GET", false, online)?.headers?.get("Access-Control-Allow-Origin"))
        assertNull(server.lookup(lookup, "GET", false, online))
        assertNotNull(server.lookup(lookup.replace("comix.to", "comix.ws") + "&_=123", "GET", false, offline))
        assertNull(server.lookup("https://comix.ws/api/v1/user", "GET", false, offline))
    }

    @Test fun answerKeysIgnoreVolatileParametersAndOrder() {
        assertEquals(
            OfflineServer.answerKey("https://comix.to/api/v1/threads/4/comments?sort=best&_=1"),
            OfflineServer.answerKey("https://comix.ws/api/v1/threads/4/comments?v=0&sort=best"),
        )
        assertNull(OfflineServer.answerKey("https://comix.to/api/v1/manga/x/chapters?page=1"))
        assertEquals("11578620", OfflineServer.chapterIdOfApi(api))
        assertNull(OfflineServer.chapterIdOfApi("https://example.com/api/v1/chapters/1"))
    }
}
