package com.contentfilter.user.dag

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DagCaptchaPolicyTest {
    @Test
    fun `only closed HTTPS captcha provider routes are accepted`() {
        assertTrue(isDagCaptchaProviderUrl("https://www.google.com/recaptcha/api2/anchor"))
        assertTrue(isDagCaptchaProviderUrl("https://www.recaptcha.net/recaptcha/enterprise/anchor"))
        assertTrue(
            isDagCaptchaProviderUrl(
                "https://challenges.cloudflare.com/cdn-cgi/challenge-platform/h/g/turnstile/if/ov2/av0",
            ),
        )
        assertTrue(isDagCaptchaProviderUrl("https://newassets.hcaptcha.com/captcha/v1/challenge"))

        assertFalse(isDagCaptchaProviderUrl("http://www.google.com/recaptcha/api2/anchor"))
        assertFalse(isDagCaptchaProviderUrl("https://www.google.com/maps"))
        assertFalse(isDagCaptchaProviderUrl("https://www.google.com.evil.example/recaptcha/api2/anchor"))
        assertFalse(isDagCaptchaProviderUrl("https://example.com/recaptcha/api2/anchor"))
    }
}
