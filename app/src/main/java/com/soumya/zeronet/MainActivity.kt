package com.soumya.zeronet

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private lateinit var connectionsClient: ConnectionsClient
    private val SERVICE_ID = "com.soumya.zeronet.MESH_SERVICE"
    private var myNickname = "ZeroNetUser"
    private val connectedPeers = mutableSetOf<String>()
    private val outgoingRequests = mutableSetOf<String>()

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

                // 1. Remove all 4 prototype demo peers permanently
                const demoKeys = ['rahul', 'priya', 'vikram', 'amit'];
                if (typeof defaultPeers !== 'undefined') {
                    demoKeys.forEach(k => delete defaultPeers[k]);
                }
                if (typeof peerData !== 'undefined') {
                    demoKeys.forEach(k => delete peerData[k]);
                    saveState(true);
                    refreshAllLists();
                }

                // 2. Fix top 'Req' button so it doesn't open fake Vikram_X9 demo modal
                const reqBtns = document.querySelectorAll('button');
                reqBtns.forEach(btn => {
                    const onclickAttr = btn.getAttribute('onclick') || '';
                    if (onclickAttr.includes('Vikram_X9')) {
                        btn.setAttribute('onclick', "showToast('🔔 Waiting for nearby peer requests...')");
                    }
                });

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

                // 4. Start real P2P engine on login
                const origStartApp = window.startApp;
                window.startApp = function() {
                    if (origStartApp) origStartApp();
                    demoKeys.forEach(k => delete peerData[k]);
                    saveState(true);
                    refreshAllLists();
                    if (window.AndroidBridge && currentUser) {
                        window.AndroidBridge.startMeshEngine(currentUser);
                    }
                };

                // 5. Real scan button
                window.rescanPeers = function() {
                    demoKeys.forEach(k => delete peerData[k]);
                    refreshAllLists();
                    showToast('📡 Scanning real nearby devices...');
                    if (window.AndroidBridge && currentUser) {
                        window.AndroidBridge.rescanNearby(currentUser);
                    }
                };

                // 6. Connect to discovered real peer when tapping 'Chat' in Nearby List
                window.handlePeerAction = function(key) {
                    const p = peerData[key];
                    if (!p || p.blocked) return;
                    if (!p.trusted) {
                        if (window.AndroidBridge) {
                            showToast('🔗 Sending connection request to ' + p.name + '...');
                            window.AndroidBridge.connectToPeer(key);
                        }
                    } else {
                        openChat(key);
                    }
                };

                // 7. Real P2P message sending (without fake 900ms demo auto-reply)
                window.sendMessage = function() {
                    const input = document.getElementById('messageInput');
                    const text = input ? input.value.trim() : '';
                    if (!text || !activePeer || !peerData[activePeer]) return;

                    peerData[activePeer].messages.push({ sender: 'me', text: text });
                    input.value = '';
                    saveState(true);
                    renderMessages();
                    refreshAllLists();

                    if (window.AndroidBridge) {
                        window.AndroidBridge.sendP2PMessage(activePeer, text);
                    }
                };

                // 8. Real P2P File & Media sending
                const origHandleFile = window.handleFileSelect;
                window.handleFileSelect = function(input, kind) {
                    const file = input.files && input.files[0];
                    if (file && activePeer && window.AndroidBridge && file.size <= 15 * 1024 * 1024) {
                        const reader = new FileReader();
                        reader.onload = function(evt) {
                            window.AndroidBridge.sendP2PFile(activePeer, file.name, kind, evt.target.result);
                        };
                        reader.readAsDataURL(file);
                    }
                    if (origHandleFile) origHandleFile(input, kind);
                };

                // 9. Accept / Reject / Block real connection request
                const origCloseReq = window.closeRequestModal;
                window.closeRequestModal = function(action, fromBack) {
                    const targetKey = pendingReqKey;
                    if (origCloseReq) origCloseReq(action, fromBack);
                    if (window.AndroidBridge && targetKey) {
                        if (action === 'accepted') window.AndroidBridge.respondToPeer(targetKey, true);
                        else if (action === 'rejected' || action === 'blocked') window.AndroidBridge.respondToPeer(targetKey, false);
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
        fun connectToPeer(endpointId: String) {
            runOnUiThread {
                if (!connectedPeers.contains(endpointId)) {
                    outgoingRequests.add(endpointId)
                    connectionsClient.requestConnection(myNickname, endpointId, connectionLifecycleCallback)
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
            val bytes = json.toString().toByteArray(StandardCharsets.UTF_8)
            connectionsClient.sendPayload(peerKey, Payload.fromBytes(bytes))
        }

        @JavascriptInterface
        fun sendP2PFile(peerKey: String, fileName: String, kind: String, dataUrl: String) {
            val json = JSONObject().apply {
                put("type", "file")
                put("senderName", myNickname)
                put("fileName", fileName)
                put("kind", kind)
                put("dataUrl", dataUrl)
            }
            val bytes = json.toString().toByteArray(StandardCharsets.UTF_8)
            connectionsClient.sendPayload(peerKey, Payload.fromBytes(bytes))
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
            val js = """
                if (typeof peerData !== 'undefined') {
                    if (!peerData['$endpointId']) {
                        peerData['$endpointId'] = {
                            key: '$endpointId',
                            initials: '$initials',
                            name: '$safeName',
                            mode: 'direct',
                            distanceMeters: 12,
                            note: 'Tap Chat to Connect',
                            trusted: false,
                            blocked: false,
                            messages: []
                        };
                    }
                    refreshAllLists();
                    showToast('📡 Found nearby device: $safeName');
                }
            """.trimIndent()
            runOnUiThread { webView.evaluateJavascript(js, null) }
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

            // If we initiated the request from this phone, auto-accept our side and wait for other phone
            if (outgoingRequests.contains(endpointId) || !info.isIncomingConnection) {
                connectionsClient.acceptConnection(endpointId, payloadCallback)
                return
            }

            // If the other phone requested to connect to us, show the Accept/Reject/Block popup!
            val js = """
                if (typeof peerData !== 'undefined') {
                    if (!peerData['$endpointId']) {
                        peerData['$endpointId'] = {
                            key: '$endpointId',
                            initials: '$initials',
                            name: '$safeName',
                            mode: 'direct',
                            distanceMeters: 10,
                            note: 'Direct Wi-Fi P2P',
                            trusted: false,
                            blocked: false,
                            messages: []
                        };
                    }
                    refreshAllLists();
                    openRequestModal('$safeName', 'Direct Wi-Fi P2P', '10m', '$endpointId');
                }
            """.trimIndent()
            runOnUiThread { webView.evaluateJavascript(js, null) }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            outgoingRequests.remove(endpointId)
            if (result.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                connectedPeers.add(endpointId)
                val js = """
                    if (typeof peerData !== 'undefined' && peerData['$endpointId']) {
                        peerData['$endpointId'].trusted = true;
                        peerData['$endpointId'].mode = 'direct';
                        peerData['$endpointId'].note = 'Live P2P Connected';
                        saveState(true);
                        refreshAllLists();
                        showToast('✓ Connected with ' + peerData['$endpointId'].name);
                        openChat('$endpointId');
                    }
                """.trimIndent()
                runOnUiThread { webView.evaluateJavascript(js, null) }
            } else {
                val js = "showToast('❌ Connection rejected or failed');"
                runOnUiThread { webView.evaluateJavascript(js, null) }
            }
        }

        override fun onDisconnected(endpointId: String) {
            connectedPeers.remove(endpointId)
            val js = """
                if (typeof peerData !== 'undefined' && peerData['$endpointId']) {
                    peerData['$endpointId'].note = 'Disconnected';
                    refreshAllLists();
                    showToast('⚠️ Peer disconnected');
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
                                peerData['$endpointId'].messages.push({ sender: 'them', text: '$text' });
                                saveState(true);
                                refreshAllLists();
                                if (activePeer === '$endpointId') renderMessages();
                                else showToast('💬 New message from ' + peerData['$endpointId'].name);
                            }
                        """.trimIndent()
                        runOnUiThread { webView.evaluateJavascript(js, null) }
                    } else if (msgType == "file") {
                        val fileName = obj.optString("fileName", "File").replace("'", "\\'")
                        val kind = obj.optString("kind", "Document")
                        val dataUrl = obj.optString("dataUrl", "")
                        val js = """
                            if (typeof peerData !== 'undefined' && peerData['$endpointId']) {
                                peerData['$endpointId'].messages.push({
                                    sender: 'them',
                                    text: '📎 Received $kind: $fileName',
                                    fileUrl: '$dataUrl',
                                    fileName: '$fileName',
                                    isImage: ${dataUrl.startsWith("data:image/")}
                                });
                                saveState(true);
                                refreshAllLists();
                                if (activePeer === '$endpointId') renderMessages();
                                showToast('📥 File received: $fileName');
                            }
                        """.trimIndent()
                        runOnUiThread { webView.evaluateJavascript(js, null) }
                    }
                } catch (e: Exception) {}
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
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
