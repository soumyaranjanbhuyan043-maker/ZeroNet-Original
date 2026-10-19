package com.soumya.zeronet

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private lateinit var connectionsClient: ConnectionsClient
    private val SERVICE_ID = "com.soumya.zeronet.MESH_SERVICE"
    private var myNickname = "ZeroNetUser"

    private val connectedPeers = ConcurrentHashMap.newKeySet<String>()
    private val outgoingRequests = ConcurrentHashMap.newKeySet<String>()
    private val incomingFileChunks = ConcurrentHashMap<String, ConcurrentHashMap<Int, String>>()
    private val pendingMessages = ConcurrentHashMap<String, MutableList<String>>()

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val dataUri = result.data?.data
            val results = if (dataUri != null) arrayOf(dataUri) else null
            fileUploadCallback?.onReceiveValue(results)
        } else {
            fileUploadCallback?.onReceiveValue(null)
        }
        fileUploadCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        connectionsClient = Nearby.getConnectionsClient(this)
        requestNecessaryPermissions()

        webView = WebView(this)
        setContentView(webView)

        setupWebView()
        setupHardwareBackButton()
        loadOfflineHtmlWithDesign()
    }

    private fun loadOfflineHtmlWithDesign() {
        var rawHtml = assets.open("index.html").bufferedReader(StandardCharsets.UTF_8).use { it.readText() }

        if (rawHtml.contains("â€¢") || rawHtml.contains("ðŸ") || rawHtml.contains("âœ") || rawHtml.contains("âš")) {
            try {
                rawHtml = String(rawHtml.toByteArray(Charset.forName("windows-1252")), StandardCharsets.UTF_8)
            } catch (e: Exception) {}
        }

        val offlineTailwindCss = """
            <meta charset="UTF-8" />
            <style id="zeronet-offline-css">
              *, *::before, *::after { box-sizing: border-box; border-width: 0; border-style: solid; border-color: #1e293b; }
              html, body { margin: 0; padding: 0; width: 100%; height: 100%; background-color: #05080f; color: #f1f5f9; font-family: 'Inter', system-ui, -apple-system, sans-serif; -webkit-font-smoothing: antialiased; }
              h1, h2, h3, h4, p { margin: 0; font-size: inherit; font-weight: inherit; }
              button, input { font-family: inherit; font-size: 100%; font-weight: inherit; line-height: inherit; color: inherit; margin: 0; padding: 0; background-color: transparent; border: none; outline: none; }
              button { cursor: pointer; }
              svg { display: block; vertical-align: middle; }
              .hidden { display: none !important; }
              .block { display: block; }
              .flex { display: flex; }
              .inline-flex { display: inline-flex; }
              .grid { display: grid; }
              .grid-cols-2 { grid-template-columns: repeat(2, minmax(0, 1fr)); }
              .grid-cols-3 { grid-template-columns: repeat(3, minmax(0, 1fr)); }
              .sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border-width: 0; }
              .flex-col { flex-direction: column; }
              .flex-1 { flex: 1 1 0%; }
              .shrink-0 { flex-shrink: 0; }
              .items-center { align-items: center; }
              .items-start { align-items: flex-start; }
              .items-baseline { align-items: baseline; }
              .justify-center { justify-content: center; }
              .justify-between { justify-content: space-between; }
              .justify-end { justify-content: flex-end; }
              .justify-start { justify-content: flex-start; }
              .relative { position: relative; }
              .absolute { position: absolute; }
              .inset-0 { top: 0; right: 0; bottom: 0; left: 0; }
              .bottom-20 { bottom: 5rem; }
              .left-1\/2 { left: 50%; }
              .-translate-x-1\/2 { transform: translateX(-50%); }
              .z-20 { z-index: 20; }
              .z-50 { z-index: 50; }
              .overflow-hidden { overflow: hidden; }
              .overflow-y-auto { overflow-y: auto; }
              .box-border { box-sizing: border-box; }
              .w-full { width: 100%; }
              .w-2 { width: 0.5rem; } .h-2 { height: 0.5rem; }
              .w-2\.5 { width: 0.625rem; } .h-2\.5 { height: 0.625rem; }
              .w-4 { width: 1rem; } .h-4 { height: 1rem; }
              .w-5 { width: 1.25rem; } .h-5 { height: 1.25rem; }
              .w-7 { width: 1.75rem; } .h-7 { height: 1.75rem; }
              .w-8 { width: 2rem; } .h-8 { height: 2rem; }
              .w-9 { width: 2.25rem; } .h-9 { height: 2.25rem; }
              .w-10 { width: 2.5rem; } .h-10 { height: 2.5rem; }
              .w-11 { width: 2.75rem; } .h-6 { height: 1.5rem; }
              .w-12 { width: 3rem; } .h-12 { height: 3rem; }
              .w-14 { width: 3.5rem; } .h-14 { height: 3.5rem; }
              .w-\[260px\] { width: 260px; }
              .h-1\.5 { height: 0.375rem; }
              .h-full { height: 100%; }
              .h-screen { height: 100vh; }
              .min-h-screen { min-height: 100vh; }
              .min-h-\[48px\] { min-height: 48px; }
              .min-w-0 { min-width: 0px; }
              .max-w-\[420px\] { max-width: 420px; }
              .max-w-\[150px\] { max-width: 150px; }
              .max-w-full { max-width: 100%; }
              .max-w-\[80\%\] { max-width: 80%; }
              .max-w-\[85\%\] { max-width: 85%; }
              .max-h-\[80\%\] { max-height: 80%; }
              .p-0 { padding: 0px; }
              .p-1 { padding: 0.25rem; }
              .p-1\.5 { padding: 0.375rem; }
              .p-2 { padding: 0.5rem; }
              .p-2\.5 { padding: 0.625rem; }
              .p-3 { padding: 0.75rem; }
              .p-3\.5 { padding: 0.875rem; }
              .p-4 { padding: 1rem; }
              .p-5 { padding: 1.25rem; }
              .p-8 { padding: 2rem; }
              .px-1\.5 { padding-left: 0.375rem; padding-right: 0.375rem; }
              .px-2 { padding-left: 0.5rem; padding-right: 0.5rem; }
              .px-3 { padding-left: 0.75rem; padding-right: 0.75rem; }
              .px-4 { padding-left: 1rem; padding-right: 1rem; }
              .py-0\.5 { padding-top: 0.125rem; padding-bottom: 0.125rem; }
              .py-1 { padding-top: 0.25rem; padding-bottom: 0.25rem; }
              .py-1\.5 { padding-top: 0.375rem; padding-bottom: 0.375rem; }
              .py-2 { padding-top: 0.5rem; padding-bottom: 0.5rem; }
              .py-2\.5 { padding-top: 0.625rem; padding-bottom: 0.625rem; }
              .py-3 { padding-top: 0.75rem; padding-bottom: 0.75rem; }
              .py-3\.5 { padding-top: 0.875rem; padding-bottom: 0.875rem; }
              .py-4 { padding-top: 1rem; padding-bottom: 1rem; }
              .pt-1 { padding-top: 0.25rem; }
              .pr-2 { padding-right: 0.5rem; }
              .pr-0\.5 { padding-right: 0.125rem; }
              .mx-auto { margin-left: auto; margin-right: auto; }
              .my-auto { margin-top: auto; margin-bottom: auto; }
              .mb-2 { margin-bottom: 0.5rem; }
              .mb-1\.5 { margin-bottom: 0.375rem; }
              .mt-0\.5 { margin-top: 0.125rem; }
              .mt-1 { margin-top: 0.25rem; }
              .gap-1 { gap: 0.25rem; }
              .gap-1\.5 { gap: 0.375rem; }
              .gap-2 { gap: 0.5rem; }
              .gap-2\.5 { gap: 0.625rem; }
              .gap-3 { gap: 0.75rem; }
              .gap-3\.5 { gap: 0.875rem; }
              .space-y-1\.5 > :not([hidden]) ~ :not([hidden]) { margin-top: 0.375rem; }
              .space-y-2 > :not([hidden]) ~ :not([hidden]) { margin-top: 0.5rem; }
              .space-y-2\.5 > :not([hidden]) ~ :not([hidden]) { margin-top: 0.625rem; }
              .space-y-3 > :not([hidden]) ~ :not([hidden]) { margin-top: 0.75rem; }
              .space-y-3\.5 > :not([hidden]) ~ :not([hidden]) { margin-top: 0.875rem; }
              .space-y-4 > :not([hidden]) ~ :not([hidden]) { margin-top: 1rem; }
              .divide-y > :not([hidden]) ~ :not([hidden]) { border-top-width: 1px; border-top-style: solid; border-color: rgba(30, 41, 59, 0.6); }
              .text-\[10px\] { font-size: 10px; line-height: 14px; }
              .text-\[11px\] { font-size: 11px; line-height: 15px; }
              .text-xs { font-size: 0.75rem; line-height: 1rem; }
              .text-sm { font-size: 0.875rem; line-height: 1.25rem; }
              .text-base { font-size: 1rem; line-height: 1.5rem; }
              .text-xl { font-size: 1.25rem; line-height: 1.75rem; }
              .text-2xl { font-size: 1.5rem; line-height: 2rem; }
              .font-medium { font-weight: 500; }
              .font-semibold { font-weight: 600; }
              .font-bold { font-weight: 700; }
              .uppercase { text-transform: uppercase; }
              .tracking-tight { letter-spacing: -0.025em; }
              .tracking-wide { letter-spacing: 0.025em; }
              .tracking-wider { letter-spacing: 0.05em; }
              .leading-tight { line-height: 1.25; }
              .text-center { text-align: center; }
              .text-left { text-align: left; }
              .truncate { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
              .whitespace-nowrap { white-space: nowrap; }
              .text-white { color: #ffffff; }
              .text-slate-200 { color: #e2e8f0; }
              .text-slate-300 { color: #cbd5e1; }
              .text-slate-400 { color: #94a3b8; }
              .text-\[\#00FF9D\] { color: #00FF9D; }
              .text-\[\#00D2FF\] { color: #00D2FF; }
              .text-\[\#05080f\] { color: #05080f; }
              .text-rose-200 { color: #fecdd3; }
              .text-rose-300 { color: #fda4af; }
              .text-rose-400 { color: #fb7185; }
              .text-amber-300 { color: #fcd34d; }
              .bg-\[\#0B0F19\] { background-color: #0B0F19; }
              .bg-\[\#0E1422\] { background-color: #0E1422; }
              .bg-\[\#111827\] { background-color: #111827; }
              .bg-\[\#131B2E\] { background-color: #131B2E; }
              .bg-\[\#131B2E\]\/60 { background-color: rgba(19, 27, 46, 0.6); }
              .bg-\[\#131B2E\]\/70 { background-color: rgba(19, 27, 46, 0.7); }
              .bg-\[\#131B2E\]\/90 { background-color: rgba(19, 27, 46, 0.9); }
              .bg-\[\#00FF9D\] { background-color: #00FF9D; }
              .bg-\[\#00FF9D\]\/10 { background-color: rgba(0, 255, 157, 0.1); }
              .bg-\[\#00FF9D\]\/15 { background-color: rgba(0, 255, 157, 0.15); }
              .bg-\[\#00FF9D\]\/20 { background-color: rgba(0, 255, 157, 0.2); }
              .bg-\[\#00D2FF\] { background-color: #00D2FF; }
              .bg-\[\#00D2FF\]\/15 { background-color: rgba(0, 210, 255, 0.15); }
              .bg-\[\#00D2FF\]\/20 { background-color: rgba(0, 210, 255, 0.2); }
              .bg-slate-700 { background-color: #334155; }
              .bg-slate-800 { background-color: #1e293b; }
              .bg-slate-800\/70 { background-color: rgba(30, 41, 59, 0.7); }
              .bg-emerald-500\/15 { background-color: rgba(16, 185, 129, 0.15); }
              .bg-sky-500\/15 { background-color: rgba(14, 165, 233, 0.15); }
              .bg-rose-500 { background-color: #f43f5e; }
              .bg-rose-500\/10 { background-color: rgba(244, 63, 94, 0.1); }
              .bg-rose-500\/15 { background-color: rgba(244, 63, 94, 0.15); }
              .bg-amber-500\/15 { background-color: rgba(245, 158, 11, 0.15); }
              .bg-black\/75 { background-color: rgba(0, 0, 0, 0.75); }
              .bg-black\/80 { background-color: rgba(0, 0, 0, 0.8); }
              .bg-gradient-to-b { background-image: linear-gradient(to bottom, #0B0F19, #0E1525, #0B0F19); }
              .border { border-width: 1px; border-style: solid; }
              .border-b { border-bottom-width: 1px; border-bottom-style: solid; }
              .border-t { border-top-width: 1px; border-top-style: solid; }
              .border-transparent { border-color: transparent; }
              .border-slate-700 { border-color: #334155; }
              .border-slate-700\/80 { border-color: rgba(51, 65, 85, 0.8); }
              .border-slate-800 { border-color: #1e293b; }
              .border-slate-800\/80 { border-color: rgba(30, 41, 59, 0.8); }
              .border-\[\#00FF9D\]\/30 { border-color: rgba(0, 255, 157, 0.3); }
              .border-\[\#00FF9D\]\/40 { border-color: rgba(0, 255, 157, 0.4); }
              .border-\[\#00FF9D\]\/50 { border-color: rgba(0, 255, 157, 0.5); }
              .border-\[\#00D2FF\]\/40 { border-color: rgba(0, 210, 255, 0.4); }
              .border-\[\#00D2FF\]\/50 { border-color: rgba(0, 210, 255, 0.5); }
              .border-rose-500\/30 { border-color: rgba(244, 63, 94, 0.3); }
              .border-amber-500\/30 { border-color: rgba(245, 158, 11, 0.3); }
              .rounded { border-radius: 0.25rem; }
              .rounded-xl { border-radius: 0.75rem; }
              .rounded-2xl { border-radius: 1rem; }
              .rounded-3xl { border-radius: 1.5rem; }
              .rounded-full { border-radius: 9999px; }
              .shadow-lg { box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.5); }
              .shadow-2xl { box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.7); }
              .backdrop-blur-sm { backdrop-filter: blur(4px); -webkit-backdrop-filter: blur(4px); }
              .outline-none { outline: none; }
              .cursor-pointer { cursor: pointer; }
              .accent-\[\#00FF9D\] { accent-color: #00FF9D; width: 14px; height: 14px; }
              .focus\:border-\[\#00FF9D\]:focus { border-color: #00FF9D; }
              .transition, .transition-all { transition: all 0.2s ease; }
              .duration-150 { transition-duration: 150ms; }
              .duration-200 { transition-duration: 200ms; }
              .active\:scale-90:active { transform: scale(0.90); }
              .active\:scale-95:active { transform: scale(0.95); }
              .active\:scale-\[0\.98\]:active { transform: scale(0.98); }
              .peer + div { position: relative; width: 2.75rem; height: 1.5rem; background-color: #334155; border-radius: 9999px; transition: all 0.2s; }
              .peer + div::after { content: ''; position: absolute; top: 2px; left: 2px; background-color: #ffffff; border-radius: 9999px; height: 1.25rem; width: 1.25rem; transition: all 0.2s; }
              .peer:checked + div { background-color: #00D2FF; }
              .peer:checked + div::after { transform: translateX(100%); }
              @keyframes ping { 75%, 100% { transform: scale(2); opacity: 0; } }
              .animate-ping { animation: ping 1.5s cubic-bezier(0, 0, 0.2, 1) infinite; }
            </style>
        """.trimIndent()

        val finalHtml = if (rawHtml.contains("</head>", ignoreCase = true)) {
            rawHtml.replace("</head>", "$offlineTailwindCss</head>", ignoreCase = true)
        } else {
            "$offlineTailwindCss$rawHtml"
        }

        webView.loadDataWithBaseURL(
            "https://zeronet.local/",
            finalHtml,
            "text/html; charset=utf-8",
            "UTF-8",
            null
        )
    }

    private fun setupWebView() {
        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.defaultTextEncodingName = "utf-8"
        settings.mediaPlaybackRequiresUserGesture = false

        webView.addJavascriptInterface(ZeroNetNativeBridge(), "AndroidBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectNativeBridgeHooks()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = filePathCallback

                val intent = try {
                    fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    }
                } catch (e: Exception) {
                    Intent(Intent.ACTION_GET_CONTENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    }
                }
                filePickerLauncher.launch(intent)
                return true
            }
        }
    }

    private fun setupHardwareBackButton() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                webView.evaluateJavascript("(typeof navStack !== 'undefined' && navStack.length > 0) ? 'HANDLED' : 'EXIT'") { result ->
                    if (result != null && result.contains("HANDLED")) {
                        webView.evaluateJavascript("silentBackCount = 0; window.dispatchEvent(new PopStateEvent('popstate'));", null)
                    } else {
                        finish()
                    }
                }
            }
        })
    }

    private fun injectNativeBridgeHooks() {
        val jsHook = """
            (function() {
                if (window.nativeHooked) return;
                window.nativeHooked = true;

                window.incomingQueue = [];

                // 1. Delete ONLY the 4 hardcoded demo peers. NEVER touch real saved chats or trusted status!
                const demoKeys = ['rahul', 'priya', 'vikram', 'amit'];
                if (typeof defaultPeers !== 'undefined') {
                    demoKeys.forEach(k => delete defaultPeers[k]);
                }
                if (typeof peerData !== 'undefined') {
                    let removedDemo = false;
                    demoKeys.forEach(k => {
                        if (peerData[k]) {
                            delete peerData[k];
                            removedDemo = true;
                        }
                    });
                    if (removedDemo) saveState(true);
                    refreshAllLists();
                }

                // 2. Wire up the top 'Req' notification button for real incoming requests
                window.updateReqBadge = function() {
                    const btns = document.querySelectorAll('button');
                    btns.forEach(btn => {
                        const txt = btn.innerText || '';
                        const onclickAttr = btn.getAttribute('onclick') || '';
                        if (txt.includes('Req') || onclickAttr.includes('Vikram_X9')) {
                            btn.removeAttribute('onclick');
                            if (window.incomingQueue.length > 0) {
                                btn.innerHTML = '🔔 Req (' + window.incomingQueue.length + ')';
                                btn.style.borderColor = '#00FF9D';
                                btn.style.color = '#00FF9D';
                            } else {
                                btn.innerHTML = '🔔 Req';
                                btn.style.borderColor = '';
                                btn.style.color = '';
                            }
                            btn.onclick = function() {
                                if (window.incomingQueue.length > 0) {
                                    const req = window.incomingQueue[0];
                                    window.openRequestModal(req.name, 'Direct Wi-Fi P2P', '10m', req.key, true);
                                } else {
                                    showToast('🔔 No pending connection requests');
                                }
                            };
                        }
                    });
                };
                window.updateReqBadge();

                // 3. Hardware Back Stack sync
                window.pushBackState = function(layerName) {
                    if (navStack[navStack.length - 1] !== layerName) {
                        navStack.push(layerName);
                    }
                };
                window.popBackState = function(layerName) {
                    const idx = navStack.lastIndexOf(layerName);
                    if (idx !== -1) {
                        navStack.splice(idx, navStack.length - idx);
                    }
                };

                // 4. CRITICAL FIX: Intercept openRequestModal!
                // When user clicks 'Connect' on their own phone, index.html calls openRequestModal(name, mode, dist, key) without 5th arg.
                // We block the popup from opening on the sender's phone and send the P2P request to the OTHER phone instead!
                const origOpenRequestModal = window.openRequestModal;
                window.openRequestModal = function(name, mode, dist, key, isRealIncomingFromOtherPhone) {
                    let targetKey = key;
                    if (!targetKey || !peerData[targetKey]) {
                        targetKey = Object.keys(peerData).find(k => peerData[k] && peerData[k].name === name);
                    }

                    if (!isRealIncomingFromOtherPhone) {
                        if (targetKey && peerData[targetKey]) {
                            showToast('📨 Request sent to ' + peerData[targetKey].name + '... Waiting for them to Accept');
                            if (window.AndroidBridge) {
                                window.AndroidBridge.connectToPeer(targetKey);
                            }
                        }
                        return;
                    }

                    if (origOpenRequestModal) {
                        origOpenRequestModal(name, mode, dist, targetKey || key);
                    }
                };

                // 5. Also handle handlePeerAction if called by Chat/Connect button
                window.handlePeerAction = function(key) {
                    const p = peerData[key];
                    if (!p || p.blocked) return;
                    if (!p.trusted) {
                        showToast('📨 Request sent to ' + p.name + '... Waiting for them to Accept');
                        if (window.AndroidBridge) {
                            window.AndroidBridge.connectToPeer(key);
                        }
                    } else {
                        openChat(key);
                    }
                };

                // 6. Start real P2P engine on login
                const origStartApp = window.startApp;
                window.startApp = function() {
                    if (origStartApp) origStartApp();
                    demoKeys.forEach(k => delete peerData[k]);
                    saveState(true);
                    refreshAllLists();
                    if (window.AndroidBridge && typeof currentUser !== 'undefined' && currentUser) {
                        window.AndroidBridge.startMeshEngine(currentUser);
                    }
                };

                // 7. Real Scan button (keeps all saved chats intact)
                window.rescanPeers = function() {
                    demoKeys.forEach(k => delete peerData[k]);
                    refreshAllLists();
                    showToast('📡 Scanning nearby devices...');
                    if (window.AndroidBridge && typeof currentUser !== 'undefined' && currentUser) {
                        window.AndroidBridge.rescanNearby(currentUser);
                    }
                };

                // 8. When opening a saved chat, ensure background P2P connection is active
                const origOpenChat = window.openChat;
                window.openChat = function(key) {
                    const p = peerData[key];
                    if (!p) return;
                    if (!p.trusted) {
                        showToast('📨 Request sent to ' + p.name + '... Waiting for them to Accept');
                        if (window.AndroidBridge) window.AndroidBridge.connectToPeer(key);
                        return;
                    }
                    if (window.AndroidBridge && !window.AndroidBridge.isPeerConnected(key)) {
                        window.AndroidBridge.connectToPeer(key);
                    }
                    if (origOpenChat) origOpenChat(key);
                };

                // 9. Real P2P Message Send (saves permanently & sends or queues if reconnecting)
                window.sendMessage = function() {
                    const input = document.getElementById('messageInput');
                    const text = input ? input.value.trim() : '';
                    if (!text || typeof activePeer === 'undefined' || !activePeer || !peerData[activePeer]) return;

                    const nowTime = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
                    peerData[activePeer].messages.push({
                        sender: 'me',
                        from: 'me',
                        mine: true,
                        type: 'text',
                        text: text,
                        time: nowTime
                    });
                    input.value = '';
                    saveState(true);
                    renderMessages();
                    refreshAllLists();

                    if (window.AndroidBridge) {
                        window.AndroidBridge.sendP2PMessage(activePeer, text);
                    }
                };

                // 10. File / Photo / PDF selection chunked transfer
                document.addEventListener('change', function(e) {
                    if (e.target && e.target.type === 'file' && e.target.files && e.target.files[0]) {
                        const file = e.target.files[0];
                        if (typeof activePeer !== 'undefined' && activePeer && window.AndroidBridge) {
                            const sizeMB = (file.size / (1024 * 1024)).toFixed(1) + ' MB';
                            const reader = new FileReader();
                            reader.onload = function(evt) {
                                const dataUrl = evt.target.result;
                                window.AndroidBridge.sendP2PFile(activePeer, file.name, sizeMB, 'File', dataUrl);
                            };
                            reader.readAsDataURL(file);
                        }
                    }
                }, true);

                // 11. Accept / Reject / Block real incoming connection request
                const origCloseReq = window.closeRequestModal;
                window.closeRequestModal = function(action, fromBack) {
                    const targetKey = typeof pendingReqKey !== 'undefined' ? pendingReqKey : null;
                    if (targetKey) {
                        window.incomingQueue = window.incomingQueue.filter(r => r.key !== targetKey);
                        window.updateReqBadge();
                    }
                    if (origCloseReq) origCloseReq(action, fromBack);
                    if (window.AndroidBridge && targetKey) {
                        if (action === 'accepted') {
                            window.AndroidBridge.respondToPeer(targetKey, true);
                        } else if (action === 'rejected' || action === 'blocked') {
                            window.AndroidBridge.respondToPeer(targetKey, false);
                        }
                    }
                };

                // 12. Helper to render incoming file progress on receiver phone
                window.onReceiveFileProgress = function(peerKey, transferId, fileName, fileSize, pct, savedPath) {
                    const p = peerData[peerKey];
                    if (!p) return;
                    let msg = p.messages.find(m => m.transferId === transferId);
                    const statusText = pct >= 100
                        ? ('📎 ' + fileName + ' (' + fileSize + ') • ✓ Saved to Downloads')
                        : ('📥 Receiving ' + fileName + ' (' + fileSize + ') • ' + pct + '%');
                    if (!msg) {
                        msg = {
                            transferId: transferId,
                            sender: 'them',
                            from: 'them',
                            mine: false,
                            type: 'text',
                            text: statusText,
                            time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
                        };
                        p.messages.push(msg);
                    } else {
                        msg.text = statusText;
                    }
                    saveState(true);
                    refreshAllLists();
                    if (typeof activePeer !== 'undefined' && activePeer === peerKey) {
                        renderMessages();
                    }
                };

                if (typeof currentUser !== 'undefined' && currentUser.length > 0 && window.AndroidBridge) {
                    window.AndroidBridge.startMeshEngine(currentUser);
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(jsHook, null)
    }

    inner class ZeroNetNativeBridge {
        @JavascriptInterface
        fun startMeshEngine(nickname: String) {
            myNickname = nickname.ifBlank { "ZeroNetPeer" }
            runOnUiThread {
                connectionsClient.stopAllEndpoints()
                connectionsClient.stopDiscovery()
                connectionsClient.stopAdvertising()
                connectedPeers.clear()
                outgoingRequests.clear()
                startAdvertising()
                startDiscovery()
            }
        }

        @JavascriptInterface
        fun rescanNearby(nickname: String) {
            myNickname = nickname.ifBlank { "ZeroNetPeer" }
            runOnUiThread {
                connectionsClient.stopDiscovery()
                connectionsClient.stopAdvertising()
                startAdvertising()
                startDiscovery()
            }
        }

        @JavascriptInterface
        fun isPeerConnected(endpointId: String): Boolean {
            return connectedPeers.contains(endpointId)
        }

        @JavascriptInterface
        fun connectToPeer(endpointId: String) {
            runOnUiThread {
                if (connectedPeers.contains(endpointId)) return@runOnUiThread
                outgoingRequests.add(endpointId)
                connectionsClient.requestConnection(myNickname, endpointId, connectionLifecycleCallback)
                    .addOnFailureListener { e ->
                        if (e.message?.contains("8003") == true || e.message?.contains("ALREADY_CONNECTED") == true) {
                            connectedPeers.add(endpointId)
                            outgoingRequests.remove(endpointId)
                        } else {
                            // Retry once after short delay if radio was busy
                            Handler(Looper.getMainLooper()).postDelayed({
                                if (!connectedPeers.contains(endpointId)) {
                                    connectionsClient.requestConnection(myNickname, endpointId, connectionLifecycleCallback)
                                }
                            }, 600)
                        }
                    }
            }
        }

        @JavascriptInterface
        fun sendP2PMessage(peerKey: String, message: String) {
            val json = JSONObject().apply {
                put("type", "chat")
                put("senderName", myNickname)
                put("text", message)
            }
            val rawJson = json.toString()
            if (connectedPeers.contains(peerKey)) {
                val bytes = rawJson.toByteArray(StandardCharsets.UTF_8)
                connectionsClient.sendPayload(peerKey, Payload.fromBytes(bytes))
            } else {
                // Queue message and auto-connect so message is delivered as soon as link is up
                val list = pendingMessages.getOrPut(peerKey) { mutableListOf() }
                list.add(rawJson)
                connectToPeer(peerKey)
            }
        }

        @JavascriptInterface
        fun sendP2PFile(peerKey: String, fileName: String, fileSize: String, kind: String, dataUrl: String) {
            if (!connectedPeers.contains(peerKey)) {
                runOnUiThread {
                    webView.evaluateJavascript("showToast('🔄 Reconnecting to peer, please try sending in 2 seconds...');", null)
                }
                connectToPeer(peerKey)
                return
            }
            Thread {
                try {
                    val transferId = "f_" + System.currentTimeMillis()
                    val chunkSize = 14000
                    val totalChunks = ((dataUrl.length + chunkSize - 1) / chunkSize).coerceAtLeast(1)

                    for (i in 0 until totalChunks) {
                        val start = i * chunkSize
                        val end = (start + chunkSize).coerceAtMost(dataUrl.length)
                        val chunkData = dataUrl.substring(start, end)

                        val json = JSONObject().apply {
                            put("type", "file_chunk")
                            put("transferId", transferId)
                            put("fileName", fileName)
                            put("fileSize", fileSize)
                            put("kind", kind)
                            put("index", i)
                            put("total", totalChunks)
                            put("data", chunkData)
                        }
                        val bytes = json.toString().toByteArray(StandardCharsets.UTF_8)
                        connectionsClient.sendPayload(peerKey, Payload.fromBytes(bytes))
                        Thread.sleep(35)
                    }
                    runOnUiThread {
                        webView.evaluateJavascript("showToast('✅ File sent to peer: $fileName');", null)
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        webView.evaluateJavascript("showToast('❌ File send error');", null)
                    }
                }
            }.start()
        }

        @JavascriptInterface
        fun respondToPeer(endpointId: String, accept: Boolean) {
            runOnUiThread {
                if (accept) {
                    connectionsClient.acceptConnection(endpointId, payloadCallback)
                } else {
                    connectionsClient.rejectConnection(endpointId)
                }
            }
        }
    }

    private fun startAdvertising() {
        val options = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        connectionsClient.startAdvertising(
            myNickname,
            SERVICE_ID,
            connectionLifecycleCallback,
            options
        )
    }

    private fun startDiscovery() {
        val options = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        connectionsClient.startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            options
        )
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val safeName = info.endpointName.replace("'", "")
            val initials = safeName.take(2).uppercase()

            // Preserve any existing chat messages & trusted status if peer was saved earlier!
            val js = """
                (function() {
                    if (typeof peerData === 'undefined') return 'false';
                    let savedMessages = [];
                    let wasTrusted = false;
                    let wasBlocked = false;

                    Object.keys(peerData).forEach(oldKey => {
                        if (peerData[oldKey] && peerData[oldKey].name === '$safeName') {
                            if (peerData[oldKey].messages && peerData[oldKey].messages.length > 0) {
                                savedMessages = peerData[oldKey].messages;
                            }
                            if (peerData[oldKey].trusted) wasTrusted = true;
                            if (peerData[oldKey].blocked) wasBlocked = true;
                            if (oldKey !== '$endpointId') {
                                if (typeof activePeer !== 'undefined' && activePeer === oldKey) {
                                    activePeer = '$endpointId';
                                }
                                delete peerData[oldKey];
                            }
                        }
                    });

                    peerData['$endpointId'] = {
                        key: '$endpointId',
                        initials: '$initials',
                        name: '$safeName',
                        mode: 'direct',
                        distanceMeters: 12,
                        note: wasTrusted ? 'Direct Wi-Fi' : 'Nearby • Tap Connect',
                        trusted: wasTrusted,
                        blocked: wasBlocked,
                        messages: savedMessages
                    };
                    saveState(true);
                    refreshAllLists();
                    showToast('📡 Found nearby: $safeName');
                    return wasTrusted ? 'TRUSTED' : 'UNTRUSTED';
                })();
            """.trimIndent()

            runOnUiThread {
                webView.evaluateJavascript(js) { res ->
                    // If we already trusted & chatted with this friend before, auto-reconnect silently!
                    if (res != null && res.contains("TRUSTED") && !connectedPeers.contains(endpointId)) {
                        outgoingRequests.add(endpointId)
                        connectionsClient.requestConnection(myNickname, endpointId, connectionLifecycleCallback)
                    }
                }
            }
        }

        override fun onEndpointLost(endpointId: String) {
            val js = """
                if (typeof peerData !== 'undefined' && peerData['$endpointId']) {
                    peerData['$endpointId'].note = 'Out of Range';
                    refreshAllLists();
                }
            """.trimIndent()
            runOnUiThread { webView.evaluateJavascript(js, null) }
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            val safeName = info.endpointName.replace("'", "")
            val initials = safeName.take(2).uppercase()
            val isIncoming = info.isIncomingConnection

            val syncAndCheckTrustedJs = """
                (function() {
                    if (typeof peerData === 'undefined') return 'UNTRUSTED';
                    let savedMessages = [];
                    let wasTrusted = false;
                    let wasBlocked = false;

                    Object.keys(peerData).forEach(oldKey => {
                        if (peerData[oldKey] && peerData[oldKey].name === '$safeName') {
                            if (peerData[oldKey].messages && peerData[oldKey].messages.length > 0) {
                                savedMessages = peerData[oldKey].messages;
                            }
                            if (peerData[oldKey].trusted) wasTrusted = true;
                            if (peerData[oldKey].blocked) wasBlocked = true;
                            if (oldKey !== '$endpointId') {
                                if (typeof activePeer !== 'undefined' && activePeer === oldKey) {
                                    activePeer = '$endpointId';
                                }
                                delete peerData[oldKey];
                            }
                        }
                    });

                    peerData['$endpointId'] = {
                        key: '$endpointId',
                        initials: '$initials',
                        name: '$safeName',
                        mode: 'direct',
                        distanceMeters: 10,
                        note: 'Direct Wi-Fi P2P',
                        trusted: wasTrusted,
                        blocked: wasBlocked,
                        messages: savedMessages
                    };
                    saveState(true);
                    refreshAllLists();
                    if (wasBlocked) return 'BLOCKED';
                    return wasTrusted ? 'TRUSTED' : 'UNTRUSTED';
                })();
            """.trimIndent()

            runOnUiThread {
                webView.evaluateJavascript(syncAndCheckTrustedJs) { status ->
                    if (status != null && status.contains("BLOCKED")) {
                        connectionsClient.rejectConnection(endpointId)
                        return@evaluateJavascript
                    }

                    val alreadyTrusted = status != null && status.contains("TRUSTED")

                    // Phone A (Sender who tapped Connect) OR already trusted friend: auto-accept our side!
                    if (outgoingRequests.contains(endpointId) || !isIncoming || alreadyTrusted) {
                        connectionsClient.acceptConnection(endpointId, payloadCallback)
                        return@evaluateJavascript
                    }

                    // Phone B (Receiver of a brand new connection request): Open Accept/Reject popup ONLY here!
                    val receiverPopupJs = """
                        if (typeof window.incomingQueue !== 'undefined') {
                            window.incomingQueue = window.incomingQueue.filter(r => r.key !== '$endpointId');
                            window.incomingQueue.push({ key: '$endpointId', name: '$safeName' });
                            if (window.updateReqBadge) window.updateReqBadge();
                        }
                        showToast('🔔 Connection Request from $safeName!');
                        window.openRequestModal('$safeName', 'Direct Wi-Fi P2P', '10m', '$endpointId', true);
                    """.trimIndent()
                    webView.evaluateJavascript(receiverPopupJs, null)
                }
            }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            val wasManualRequest = outgoingRequests.remove(endpointId)
            if (result.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                connectedPeers.add(endpointId)

                // Flush any queued messages sent while reconnecting
                val queued = pendingMessages.remove(endpointId)
                queued?.forEach { rawJson ->
                    connectionsClient.sendPayload(endpointId, Payload.fromBytes(rawJson.toByteArray(StandardCharsets.UTF_8)))
                }

                val js = """
                    if (typeof window.incomingQueue !== 'undefined') {
                        window.incomingQueue = window.incomingQueue.filter(r => r.key !== '$endpointId');
                        if (window.updateReqBadge) window.updateReqBadge();
                    }
                    if (typeof peerData !== 'undefined' && peerData['$endpointId']) {
                        peerData['$endpointId'].trusted = true;
                        peerData['$endpointId'].mode = 'direct';
                        peerData['$endpointId'].note = 'Live P2P Connected';
                        saveState(true);
                        refreshAllLists();
                        showToast('✓ Connected with ' + peerData['$endpointId'].name);
                        if ($wasManualRequest) {
                            openChat('$endpointId');
                        }
                    }
                """.trimIndent()
                runOnUiThread { webView.evaluateJavascript(js, null) }
            } else {
                connectedPeers.remove(endpointId)
                val js = "showToast('❌ Connection rejected or failed');"
                runOnUiThread { webView.evaluateJavascript(js, null) }
            }
        }

        override fun onDisconnected(endpointId: String) {
            connectedPeers.remove(endpointId)
            // Keep trusted = true so the chat NEVER disappears from the Chats tab!
            val js = """
                if (typeof peerData !== 'undefined' && peerData['$endpointId']) {
                    peerData['$endpointId'].note = 'Offline';
                    saveState(true);
                    refreshAllLists();
                }
            """.trimIndent()
            runOnUiThread { webView.evaluateJavascript(js, null) }
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val raw = String(payload.asBytes() ?: return, StandardCharsets.UTF_8)
                try {
                    val obj = JSONObject(raw)
                    val msgType = obj.optString("type", "chat")

                    if (msgType == "chat") {
                        val text = obj.optString("text", "")
                            .replace("\\", "\\\\")
                            .replace("'", "\\'")
                            .replace("\n", "\\n")
                        val js = """
                            if (typeof peerData !== 'undefined' && peerData['$endpointId']) {
                                peerData['$endpointId'].trusted = true;
                                const nowTime = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
                                peerData['$endpointId'].messages.push({
                                    sender: 'them',
                                    from: 'them',
                                    mine: false,
                                    type: 'text',
                                    text: '$text',
                                    time: nowTime
                                });
                                saveState(true);
                                refreshAllLists();
                                if (typeof activePeer !== 'undefined' && activePeer === '$endpointId') {
                                    renderMessages();
                                } else {
                                    showToast('💬 ' + peerData['$endpointId'].name + ': $text');
                                }
                            }
                        """.trimIndent()
                        runOnUiThread { webView.evaluateJavascript(js, null) }

                    } else if (msgType == "file_chunk") {
                        val transferId = obj.optString("transferId", "f0")
                        val fileName = obj.optString("fileName", "received_file").replace("'", "")
                        val fileSize = obj.optString("fileSize", "0 MB")
                        val index = obj.optInt("index", 0)
                        val total = obj.optInt("total", 1)
                        val chunkData = obj.optString("data", "")

                        val chunkMap = incomingFileChunks.getOrPut(transferId) { ConcurrentHashMap() }
                        chunkMap[index] = chunkData

                        val receivedCount = chunkMap.size
                        val pct = ((receivedCount * 100) / total).coerceAtMost(99)

                        if (receivedCount < total) {
                            val progJs = "if(window.onReceiveFileProgress) window.onReceiveFileProgress('$endpointId', '$transferId', '$fileName', '$fileSize', $pct, '');"
                            runOnUiThread { webView.evaluateJavascript(progJs, null) }
                        } else {
                            Thread {
                                val sb = StringBuilder()
                                for (i in 0 until total) {
                                    sb.append(chunkMap[i] ?: "")
                                }
                                incomingFileChunks.remove(transferId)
                                val fullDataUrl = sb.toString()
                                val savedPath = saveBase64FileToDownloads(fileName, fullDataUrl)

                                runOnUiThread {
                                    val doneJs = """
                                        if (window.onReceiveFileProgress) {
                                            window.onReceiveFileProgress('$endpointId', '$transferId', '$fileName', '$fileSize', 100, '$savedPath');
                                        }
                                        showToast('📥 Saved to Downloads: $fileName');
                                    """.trimIndent()
                                    webView.evaluateJavascript(doneJs, null)
                                }
                            }.start()
                        }
                    }
                } catch (e: Exception) {}
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    private fun saveBase64FileToDownloads(fileName: String, dataUrl: String): String {
        return try {
            val base64Part = if (dataUrl.contains(",")) dataUrl.substringAfter(",") else dataUrl
            val bytes = Base64.decode(base64Part, Base64.DEFAULT)
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val outFile = File(downloadsDir, "ZeroNet_$fileName")
            FileOutputStream(outFile).use { it.write(bytes) }
            outFile.absolutePath
        } catch (e: Exception) {
            ""
        }
    }

    private fun requestNecessaryPermissions() {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_SCAN)
            perms.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            perms.add(Manifest.permission.READ_MEDIA_IMAGES)
            perms.add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 101)
        }
    }
}
