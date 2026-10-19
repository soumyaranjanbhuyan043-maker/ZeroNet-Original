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
import java.nio.charset.StandardCharsets

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private lateinit var connectionsClient: ConnectionsClient
    private val SERVICE_ID = "com.soumya.zeronet.MESH_SERVICE"
    private var myNickname = "ZeroNetUser"

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

        webView.loadUrl("file:///android_asset/index.html")
    }

    private fun setupWebView() {
        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
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
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })
    }

    private fun injectNativeBridgeHooks() {
        val jsHook = """
            (function() {
                if (window.nativeHooked) return;
                window.nativeHooked = true;

                const origStartApp = window.startApp;
                window.startApp = function() {
                    if (origStartApp) origStartApp();
                    if (window.AndroidBridge && window.currentUser) {
                        window.AndroidBridge.startMeshEngine(window.currentUser);
                    }
                };

                const origRescan = window.rescanPeers;
                window.rescanPeers = function() {
                    if (origRescan) origRescan();
                    if (window.AndroidBridge && window.currentUser) {
                        window.AndroidBridge.rescanNearby(window.currentUser);
                    }
                };

                const origSend = window.sendMessage;
                window.sendMessage = function() {
                    const input = document.getElementById('messageInput');
                    const textVal = input ? input.value.trim() : '';
                    if (origSend) origSend();
                    if (textVal && window.AndroidBridge && window.activePeer) {
                        window.AndroidBridge.sendP2PMessage(window.activePeer, textVal);
                    }
                };

                const origCloseReq = window.closeRequestModal;
                window.closeRequestModal = function(action, fromBack) {
                    const targetKey = window.pendingReqKey;
                    if (origCloseReq) origCloseReq(action, fromBack);
                    if (window.AndroidBridge && targetKey) {
                        if (action === 'accepted') window.AndroidBridge.respondToPeer(targetKey, true);
                        else if (action === 'rejected' || action === 'blocked') window.AndroidBridge.respondToPeer(targetKey, false);
                    }
                };

                if (window.currentUser && window.currentUser.length > 0 && window.AndroidBridge) {
                    window.AndroidBridge.startMeshEngine(window.currentUser);
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
        fun respondToPeer(endpointId: String, accept: Boolean) {
            if (accept) {
                connectionsClient.acceptConnection(endpointId, payloadCallback)
            } else {
                connectionsClient.rejectConnection(endpointId)
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
                            distanceMeters: 15,
                            note: 'Live Nearby P2P',
                            trusted: false,
                            blocked: false,
                            messages: []
                        };
                    }
                    refreshAllLists();
                    showToast('📡 Found nearby peer: $safeName');
                }
            """.trimIndent()
            runOnUiThread { webView.evaluateJavascript(js, null) }
            connectionsClient.requestConnection(myNickname, endpointId, connectionLifecycleCallback)
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
            val js = """
                if (typeof peerData !== 'undefined') {
                    if (!peerData['$endpointId']) {
                        peerData['$endpointId'] = {
                            key: '$endpointId',
                            initials: '$initials',
                            name: '$safeName',
                            mode: 'direct',
                            distanceMeters: 12,
                            note: 'Direct Wi-Fi P2P',
                            trusted: false,
                            blocked: false,
                            messages: []
                        };
                    }
                    refreshAllLists();
                    openRequestModal('$safeName', 'Direct P2P', '12m', '$endpointId');
                }
            """.trimIndent()
            runOnUiThread { webView.evaluateJavascript(js, null) }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                val js = """
                    if (typeof peerData !== 'undefined' && peerData['$endpointId']) {
                        peerData['$endpointId'].trusted = true;
                        peerData['$endpointId'].mode = 'direct';
                        peerData['$endpointId'].note = 'Connected Live P2P';
                        saveState(true);
                        refreshAllLists();
                        showToast('✓ Live P2P Connected!');
                    }
                """.trimIndent()
                runOnUiThread { webView.evaluateJavascript(js, null) }
            }
        }

        override fun onDisconnected(endpointId: String) {
            val js = """
                if (typeof peerData !== 'undefined' && peerData['$endpointId']) {
                    peerData['$endpointId'].note = 'Disconnected';
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
                    val text = obj.optString("text", "").replace("'", "\\'")
                    val js = """
                        if (typeof peerData !== 'undefined' && peerData['$endpointId']) {
                            peerData['$endpointId'].messages.push({ sender: 'them', text: '$text' });
                            saveState(true);
                            refreshAllLists();
                            if (window.activePeer === '$endpointId') renderMessages();
                        }
                    """.trimIndent()
                    runOnUiThread { webView.evaluateJavascript(js, null) }
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
