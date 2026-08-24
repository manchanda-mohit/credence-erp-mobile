package com.credence.mobile.data

object ApiConfig {
    /**
     * REQUIRED — set this to your deployed Apps Script web app's exec
     * URL before building. It's the same URL the PWA install flow uses
     * (Deploy → Manage deployments → Web app URL in the Apps Script
     * editor), and looks like:
     *   https://script.google.com/macros/s/AKfycb.../exec
     * Do not add a trailing slash, and do not append any query string
     * here — ApiClient.kt adds ?api=1&action=... itself.
     */
    const val BASE_URL: String = "https://script.google.com/macros/s/REPLACE_WITH_YOUR_DEPLOYMENT_ID/exec"
}
