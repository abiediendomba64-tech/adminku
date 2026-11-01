package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Domain
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.network.TelegramApiService
import com.example.data.prefs.AppSettings
import com.example.ui.theme.StatusActive
import com.example.ui.theme.StatusBlocked
import com.example.ui.theme.TelegramBrandColor

data class WorkspaceWebTab(
    val id: String = java.util.UUID.randomUUID().toString(),
    var title: String,
    var url: String,
    val icon: ImageVector,
    val isPreset: Boolean = false
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebWorkspaceScreen(
    settings: AppSettings,
    onTriggerReindex: (String) -> Unit,
    onCreateTicketForDomain: (String) -> Unit
) {
    val context = LocalContext.current

    val backofficeUrl = "https://${settings.baseDomain}${settings.backofficePath}"
    val marketingTagsUrl = "https://${settings.baseDomain}${settings.marketingTagsPath}"
    val gscUrl = settings.googleSearchConsoleUrl
    val trustPositifUrl = settings.trustPositifUrl
    val reqWebBotUrl = settings.reqWebBotUrl
    val webAppUrl = settings.telegramWebAppUrl

    val tabs = remember {
        mutableStateListOf(
            WorkspaceWebTab(
                title = "Panel /backoffice",
                url = backofficeUrl,
                icon = Icons.Default.Domain,
                isPreset = true
            ),
            WorkspaceWebTab(
                title = "Marketing Tags",
                url = marketingTagsUrl,
                icon = Icons.Default.Tag,
                isPreset = true
            ),
            WorkspaceWebTab(
                title = "Google Search Console",
                url = gscUrl,
                icon = Icons.Default.Search,
                isPreset = true
            ),
            WorkspaceWebTab(
                title = "Cek TrustPositif",
                url = trustPositifUrl,
                icon = Icons.Default.Shield,
                isPreset = true
            ),
            WorkspaceWebTab(
                title = "ReqWeb Bot",
                url = reqWebBotUrl,
                icon = Icons.Default.SmartToy,
                isPreset = true
            )
        )
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var urlInputText by remember { mutableStateOf(tabs.getOrNull(selectedTabIndex)?.url ?: backofficeUrl) }

    // Map to hold WebView instances for each tab to keep session and state alive
    val webViewsMap = remember { mutableMapOf<String, WebView>() }
    val rendererCrashedTabs = remember { mutableStateMapOf<String, String>() }

    // Clean up WebViews when Composable leaves
    DisposableEffect(Unit) {
        onDispose {
            webViewsMap.values.toList().forEach { destroyWebViewSafely(it) }
            webViewsMap.clear()
        }
    }

    val activeTab = tabs.getOrNull(selectedTabIndex) ?: tabs.first()

    // Sync input bar when tab changes
    remember(selectedTabIndex) {
        urlInputText = activeTab.url
    }

    var activeProgress by remember { mutableFloatStateOf(0f) }
    var isCurrentLoading by remember { mutableStateOf(false) }

    BackHandler(enabled = webViewsMap[activeTab.id]?.canGoBack() == true) {
        webViewsMap[activeTab.id]?.goBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // TOP 1: Tab Strip (Chrome-style multi-tab header)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEachIndexed { index, tab ->
                        val isSelected = index == selectedTabIndex
                        val tabColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant

                        Row(
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(tabColor)
                                .clickable {
                                    selectedTabIndex = index
                                    urlInputText = tab.url
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.widthIn(max = 120.dp)
                            )
                            if (tabs.size > 1) {
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = {
                                        val wasSelected = selectedTabIndex == index
                                        webViewsMap.remove(tab.id)?.let { destroyWebViewSafely(it) }
                                        rendererCrashedTabs.remove(tab.id)
                                        tabs.removeAt(index)
                                        selectedTabIndex = when {
                                            wasSelected -> index.coerceAtMost(tabs.lastIndex)
                                            index < selectedTabIndex -> selectedTabIndex - 1
                                            else -> selectedTabIndex
                                        }
                                        urlInputText = tabs[selectedTabIndex].url
                                    },
                                    modifier = Modifier.size(18.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Tutup Tab",
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Add custom new tab button
                    IconButton(
                        onClick = {
                            if (tabs.size >= MAX_OPEN_WEB_TABS) {
                                Toast.makeText(context, "Maksimum $MAX_OPEN_WEB_TABS tab. Tutup tab lain terlebih dahulu.", Toast.LENGTH_SHORT).show()
                            } else {
                                val newTab = WorkspaceWebTab(
                                    title = "Tab Baru",
                                    url = "https://google.com",
                                    icon = Icons.Default.Language
                                )
                                tabs.add(newTab)
                                selectedTabIndex = tabs.size - 1
                                urlInputText = newTab.url
                            }
                        },
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Tab Baru", modifier = Modifier.size(16.dp))
                    }
                }

                // TOP 2: Preset Quick Switch Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PresetTabChip(
                        label = "🌐 Panel /backoffice",
                        onClick = {
                            openOrSwitchToUrl(tabs, backofficeUrl, "Panel /backoffice", Icons.Default.Domain) {
                                selectedTabIndex = it
                            }
                        }
                    )
                    PresetTabChip(
                        label = "🏷️ Marketing Tags",
                        onClick = {
                            openOrSwitchToUrl(tabs, marketingTagsUrl, "Marketing Tags", Icons.Default.Tag) {
                                selectedTabIndex = it
                            }
                        }
                    )
                    PresetTabChip(
                        label = "🔍 Google Search Console",
                        onClick = {
                            openOrSwitchToUrl(tabs, gscUrl, "Google Search Console", Icons.Default.Search) {
                                selectedTabIndex = it
                            }
                        }
                    )
                    PresetTabChip(
                        label = "🛡️ Cek TrustPositif",
                        onClick = {
                            openOrSwitchToUrl(tabs, trustPositifUrl, "Cek TrustPositif", Icons.Default.Shield) {
                                selectedTabIndex = it
                            }
                        }
                    )
                    PresetTabChip(
                        label = "🤖 @reqweb_bot",
                        onClick = {
                            openOrSwitchToUrl(tabs, reqWebBotUrl, "ReqWeb Bot", Icons.Default.SmartToy) {
                                selectedTabIndex = it
                            }
                        }
                    )
                }

                // TOP 3: Browser Address & Controls Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val currentWv = webViewsMap[activeTab.id]
                    IconButton(
                        onClick = { currentWv?.goBack() },
                        enabled = currentWv?.canGoBack() == true,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = { currentWv?.goForward() },
                        enabled = currentWv?.canGoForward() == true,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward", modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = { currentWv?.reload() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload", modifier = Modifier.size(16.dp))
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    OutlinedTextField(
                        value = urlInputText,
                        onValueChange = { urlInputText = it },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = "SSL", modifier = Modifier.size(14.dp), tint = StatusActive)
                        },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clip.setPrimaryClip(ClipData.newPlainText("URL", urlInputText))
                                        Toast.makeText(context, "URL disalin!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Salin", modifier = Modifier.size(14.dp))
                                }
                                IconButton(
                                    onClick = {
                                        val clean = if (!urlInputText.startsWith("http")) "https://$urlInputText" else urlInputText
                                        activeTab.url = clean
                                        currentWv?.loadUrl(clean)
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Go", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(activeTab.url)).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // ignore
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Buka di Chrome", modifier = Modifier.size(16.dp))
                    }
                }

                // Progress Bar
                if (isCurrentLoading) {
                    LinearProgressIndicator(
                        progress = { activeProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }
        }

        // SMART ACTION DOCK (Salin Paste & Verify Helpers)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Action 1: Cek di TrustPositif
                ActionDockButton(
                    icon = Icons.Default.Shield,
                    text = "Cek TrustPositif",
                    tint = Color(0xFFEF4444)
                ) {
                    val domainOnly = extractDomainName(activeTab.url)
                    val tpUrl = "https://trustpositif.app/?domain=$domainOnly"
                    openOrSwitchToUrl(tabs, tpUrl, "Cek TrustPositif: $domainOnly", Icons.Default.Shield) {
                        selectedTabIndex = it
                    }
                    Toast.makeText(context, "Membuka pengecekan TrustPositif untuk: $domainOnly", Toast.LENGTH_SHORT).show()
                }

                // Action 2: Trigger Re-index GSC
                ActionDockButton(
                    icon = Icons.Default.Bolt,
                    text = "Trigger Re-index",
                    tint = Color(0xFF8B5CF6)
                ) {
                    val domainOnly = extractDomainName(activeTab.url)
                    onTriggerReindex(domainOnly)
                }

                // Action 3: Kirim ke @reqweb_bot
                ActionDockButton(
                    icon = Icons.Default.SmartToy,
                    text = "Kirim ke @reqweb_bot",
                    tint = TelegramBrandColor
                ) {
                    val domainOnly = extractDomainName(activeTab.url)
                    val reqPayload = "/request_domain domain: $domainOnly, type: mirror, dns: cloudflare"
                    val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clip.setPrimaryClip(ClipData.newPlainText("ReqWeb", reqPayload))
                    Toast.makeText(context, "Payload disalin! Membuka @reqweb_bot...", Toast.LENGTH_SHORT).show()

                    // Switch tab or launch Telegram
                    openOrSwitchToUrl(tabs, "https://t.me/reqweb_bot", "ReqWeb Bot", Icons.Default.SmartToy) {
                        selectedTabIndex = it
                    }
                    TelegramApiService.openTelegramChat(context, "reqweb_bot")
                }

                // Action 4: Buat Tiket Kendala
                ActionDockButton(
                    icon = Icons.Default.ConfirmationNumber,
                    text = "Buat Tiket Domain Ini",
                    tint = MaterialTheme.colorScheme.primary
                ) {
                    val domainOnly = extractDomainName(activeTab.url)
                    onCreateTicketForDomain(domainOnly)
                }

                // Action 5: Salin Meta Tag Template
                ActionDockButton(
                    icon = Icons.Default.ContentCopy,
                    text = "Salin Template Meta Tag",
                    tint = Color(0xFF10B981)
                ) {
                    val template = "<meta name=\"google-site-verification\" content=\"GOOGLE_CONSOLE_VERIFICATION_KEY\" />"
                    val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clip.setPrimaryClip(ClipData.newPlainText("MetaTag", template))
                    Toast.makeText(context, "Template meta tag disalin! Tempel di Marketing Tags.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // WEBVIEW CONTAINER
        Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
            val rendererMessage = rendererCrashedTabs[activeTab.id]
            if (rendererMessage != null) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Renderer browser terhenti",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = rendererMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            rendererCrashedTabs.remove(activeTab.id)
                            activeProgress = 0f
                            isCurrentLoading = true
                        }
                    ) {
                        Text("Buat ulang WebView")
                    }
                }
            } else {
                key(activeTab.id) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            val tabId = activeTab.id
                            val wv = webViewsMap.getOrPut(tabId) {
                                WebView(ctx).apply {
                                    this.settings.apply {
                                        javaScriptEnabled = true
                                        domStorageEnabled = true
                                        loadWithOverviewMode = true
                                        useWideViewPort = true
                                        setSupportZoom(true)
                                        builtInZoomControls = true
                                        displayZoomControls = false
                                        cacheMode = WebSettings.LOAD_DEFAULT
                                        mixedContentMode = WebSettings.MIXED_CONTENT.ALWAYS_ALLOW
                                    }

                                    webViewClient = object : WebViewClient() {
                                        override fun onRenderProcessGone(
                                            view: WebView,
                                            detail: RenderProcessGoneDetail
                                        ): Boolean {
                                            val affectedTabId = webViewsMap.entries
                                                .firstOrNull { it.value === view }?.key ?: tabId
                                            val wasTracked = webViewsMap[affectedTabId] === view
                                            if (wasTracked) webViewsMap.remove(affectedTabId)
                                            destroyWebViewSafely(view)

                                            if (wasTracked && tabs.any { it.id == affectedTabId }) {
                                                rendererCrashedTabs[affectedTabId] =
                                                    if (detail.didCrash()) {
                                                        "Proses Chromium mengalami crash. Buat ulang tab secara manual."
                                                    } else {
                                                        "Android menghentikan proses renderer. Buat ulang tab untuk melanjutkan."
                                                    }
                                            }
                                            if (affectedTabId == tabs.getOrNull(selectedTabIndex)?.id) {
                                                isCurrentLoading = false
                                                activeProgress = 0f
                                            }
                                            return true
                                        }

                                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                            super.onPageStarted(view, url, favicon)
                                            val isActiveTab = tabs.getOrNull(selectedTabIndex)?.id == tabId
                                            if (isActiveTab) isCurrentLoading = true
                                            url?.let {
                                                activeTab.url = it
                                                if (isActiveTab) urlInputText = it
                                            }
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            val isActiveTab = tabs.getOrNull(selectedTabIndex)?.id == tabId
                                            if (isActiveTab) isCurrentLoading = false
                                            url?.let {
                                                activeTab.url = it
                                                if (isActiveTab) urlInputText = it
                                            }
                                            view?.title?.let {
                                                if (it.isNotBlank()) activeTab.title = it
                                            }
                                        }
                                    }

                                    webChromeClient = object : WebChromeClient() {
                                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                            if (tabs.getOrNull(selectedTabIndex)?.id == tabId) {
                                                activeProgress = newProgress / 100f
                                                if (newProgress == 100) isCurrentLoading = false
                                            }
                                        }

                                        override fun onReceivedTitle(view: WebView?, title: String?) {
                                            super.onReceivedTitle(view, title)
                                            title?.let { if (it.isNotBlank()) activeTab.title = it }
                                        }
                                    }

                                    loadUrl(activeTab.url)
                                }
                            }
                            wv
                        },
                        update = { view ->
                            if (view.url != activeTab.url && activeTab.url.isNotBlank()) {
                                view.loadUrl(activeTab.url)
                            }
                        }
                    )
                }
            }
        }
    }
}

private const val MAX_OPEN_WEB_TABS = 5

private fun destroyWebViewSafely(webView: WebView) {
    runCatching { (webView.parent as? ViewGroup)?.removeView(webView) }
    runCatching { webView.stopLoading() }
    runCatching { webView.destroy() }
}

@Composable
fun PresetTabChip(label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun ActionDockButton(
    icon: ImageVector,
    text: String,
    tint: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
        color = tint.copy(alpha = 0.12f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = tint)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                color = tint
            )
        }
    }
}

private fun openOrSwitchToUrl(
    tabs: MutableList<WorkspaceWebTab>,
    targetUrl: String,
    title: String,
    icon: ImageVector,
    onSelectIndex: (Int) -> Unit
) {
    val existingIndex = tabs.indexOfFirst { it.url.startsWith(targetUrl) || targetUrl.startsWith(it.url) }
    if (existingIndex >= 0) {
        onSelectIndex(existingIndex)
    } else if (tabs.size < MAX_OPEN_WEB_TABS) {
        val newTab = WorkspaceWebTab(
            title = title,
            url = targetUrl,
            icon = icon
        )
        tabs.add(newTab)
        onSelectIndex(tabs.size - 1)
    }
}

private fun extractDomainName(url: String): String {
    return try {
        val uri = Uri.parse(url)
        val host = uri.host
        if (!host.isNullOrEmpty()) host else url.replace("https://", "").replace("http://", "").split("/")[0]
    } catch (e: Exception) {
        url.replace("https://", "").replace("http://", "").split("/")[0]
    }
}
