package com.notsonata.comixium.web

import com.notsonata.comixium.web.GoogleSignIn.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleSignInTest {
    // As comix redirects today (seen live): starting on comix.ws, Google returns to comix.to.
    private val authorize = "https://accounts.google.com/o/oauth2/v2/auth?client_id=542375206191-x.apps.googleusercontent.com" +
        "&redirect_uri=https%3A%2F%2Fcomix.to%2Fapi%2Fv1%2Fauth%2Fgoogle%2Fcallback&response_type=code" +
        "&scope=openid+email+profile&access_type=online&prompt=select_account&state=d323493444cb099e24c64e65e879913c"

    @Test fun recognisesTheFlowsUrls() {
        assertTrue(GoogleSignIn.isStart("https://comix.ws/api/v1/auth/google/redirect"))
        assertTrue(GoogleSignIn.isStart("https://comix.to/api/v1/auth/google/redirect/"))
        assertFalse(GoogleSignIn.isStart("https://evil.com/api/v1/auth/google/redirect"))
        assertTrue(GoogleSignIn.isCallback("https://comix.to/api/v1/auth/google/callback?code=abc&state=def"))
        assertFalse(GoogleSignIn.isCallback("https://comix.to/api/v1/auth/google/callbackx"))
        assertFalse(GoogleSignIn.isCallback("https://evil.com/api/v1/auth/google/callback?code=abc"))
        assertTrue(GoogleSignIn.isGoogleAuthorize(authorize))
        assertFalse(GoogleSignIn.isGoogleAuthorize("https://accounts.google.com/v3/signin/identifier?x=1"))
        assertFalse(GoogleSignIn.isGoogleAuthorize("https://accounts.google.com.evil.com/o/oauth2/v2/auth"))
    }

    @Test fun returnHostComesFromRedirectUri() {
        assertEquals("comix.to", GoogleSignIn.returnHost(authorize))
        // Only a comix domain, over https, on the callback path counts.
        assertNull(GoogleSignIn.returnHost(authorize.replace("comix.to", "evil.com")))
        assertNull(GoogleSignIn.returnHost(authorize.replace("https%3A%2F%2Fcomix.to", "http%3A%2F%2Fcomix.to")))
        assertNull(GoogleSignIn.returnHost(authorize.replace("callback", "elsewhere")))
        assertNull(GoogleSignIn.returnHost("https://accounts.google.com/o/oauth2/v2/auth?client_id=x"))
    }

    @Test fun restartsOnTheReturnDomainOnceThenOpensTheBrowser() {
        assertEquals(Step.Restart("comix.to"), GoogleSignIn.step(authorize, startedOn = "comix.ws", restarted = false))
        assertEquals(Step.OpenInBrowser(authorize, "comix.to"), GoogleSignIn.step(authorize, startedOn = "comix.to", restarted = true))
        assertEquals(Step.OpenInBrowser(authorize, "comix.to"), GoogleSignIn.step(authorize, startedOn = "comix.to", restarted = false))
        // A site that keeps returning elsewhere doesn't loop: the second time, it goes to the browser.
        assertEquals(Step.OpenInBrowser(authorize, "comix.to"), GoogleSignIn.step(authorize, startedOn = "comix.ws", restarted = true))
        assertEquals(Step.Pass, GoogleSignIn.step("https://comix.ws/browse", startedOn = null, restarted = false))
        assertEquals(Step.Pass, GoogleSignIn.step(authorize.replace("comix.to", "evil.com"), startedOn = "comix.ws", restarted = false))
    }
}
