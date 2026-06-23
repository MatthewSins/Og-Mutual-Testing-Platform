package com.example.utilityapp

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.companionWebView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = WebViewClient()

        // 1. Injects the communication bridge so your Vercel site can call native hardware tracking APIs
        webView.addJavascriptInterface(WebAppInterface(this, webView), "AndroidBridge")

        // 2. Load your live Vercel Deployment Link
        // REPLACE THIS URL with your actual live Vercel URL!
        webView.loadUrl("https://og-mutual-testing-platform.vercel.app")

        checkUsageStatsPermission()
    }

    override fun onResume() {
        super.onResume()
        checkUsageStatsPermission()
    }

    private fun checkUsageStatsPermission() {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            packageName
        )
        if (mode != AppOpsManager.MODE_ALLOWED) {
            Toast.makeText(this, "OG Tracker requires Usage Access to verify tests.", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }
}

// The JavaScript Interface class connecting your Vercel HTML to Android hardware APIs
class WebAppInterface(private val context: Context, private val webView: WebView) {

    @JavascriptInterface
    fun verifyAppUsage(targetPackage: String) {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        
        // Window range calculations: Midnight tonight until current time
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val usageStatsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, startTime, endTime
        )

        var foundTimeMs = 0L
        if (usageStatsList != null) {
            for (usageStats in usageStatsList) {
                if (usageStats.packageName == targetPackage) {
                    foundTimeMs = usageStats.totalTimeInForeground
                    break
                }
            }
        }

        val durationSeconds = foundTimeMs / 1000

        // Send validation results back to your Vercel frontend JavaScript variables
        webView.post {
            if (durationSeconds >= 60) {
                // If the app checks out, pass success tokens to your web code logic
                webView.evaluateJavascript("javascript:onValidationSuccess('$targetPackage', $durationSeconds);", null)
            } else {
                val remaining = 60 - durationSeconds
                webView.evaluateJavascript("javascript:onValidationFailed($remaining);", null)
            }
        }
    }
}
