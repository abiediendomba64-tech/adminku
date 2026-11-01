package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.prefs.AppSettings
import com.example.ui.theme.StatusActive
import com.example.ui.theme.StatusBlocked
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class MarketingTagItem(
    val id: String,
    val name: String,
    val category: String,
    val tagIdOrCode: String,
    var isActive: Boolean,
    val icon: ImageVector,
    val codeSnippet: String
)

@Composable
fun MarketingTagsBrowserScreen(
    settings: AppSettings,
    onBack: (() -> Unit)? = null,
    onOpenInBrowser: (String, String) -> Unit
) {
    if (onBack != null) {
        BackHandler { onBack() }
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isValidating by remember { mutableStateOf(false) }
    var validationResult by remember { mutableStateOf<String?>(null) }

    val tags = remember {
        mutableStateListOf(
            MarketingTagItem(
                id = "gsc_meta",
                name = "Google Site Verification (GSC)",
                category = "Search Console",
                tagIdOrCode = "google-site-verification=AbC987_xyzSEO_ConsoleToken",
                isActive = true,
                icon = Icons.Default.Tag,
                codeSnippet = """<meta name="google-site-verification" content="AbC987_xyzSEO_ConsoleToken" />"""
            ),
            MarketingTagItem(
                id = "gtm",
                name = "Google Tag Manager (GTM)",
                category = "Tag Management",
                tagIdOrCode = "GTM-88VIPX9",
                isActive = true,
                icon = Icons.Default.Analytics,
                codeSnippet = """
                    <!-- Google Tag Manager -->
                    <script>(function(w,d,s,l,i){w[l]=w[l]||[];w[l].push({'gtm.start':
                    new Date().getTime(),event:'gtm.js'});var f=d.getElementsByTagName(s)[0],
                    j=d.createElement(s),dl=l!='dataLayer'?'&l='+l:'';j.async=true;j.src=
                    'https://www.googletagmanager.com/gtm.js?id='+i+dl;f.parentNode.insertBefore(j,f);
                    })(window,document,'script','dataLayer','GTM-88VIPX9');</script>
                    <!-- End Google Tag Manager -->
                """.trimIndent()
            ),
            MarketingTagItem(
                id = "ga4",
                name = "Google Analytics 4 (GA4)",
                category = "Web Analytics",
                tagIdOrCode = "G-P88XYZ1234",
                isActive = true,
                icon = Icons.Default.Language,
                codeSnippet = """
                    <!-- Google tag (gtag.js) -->
                    <script async src="https://www.googletagmanager.com/gtag/js?id=G-P88XYZ1234"></script>
                    <script>
                      window.dataLayer = window.dataLayer || [];
                      function gtag(){dataLayer.push(arguments);}
                      gtag('js', new Date());
                      gtag('config', 'G-P88XYZ1234');
                    </script>
                """.trimIndent()
            ),
            MarketingTagItem(
                id = "meta_pixel",
                name = "Meta / Facebook Pixel",
                category = "Ad Tracking",
                tagIdOrCode = "Pixel ID: 9812739182371",
                isActive = false,
                icon = Icons.Default.Campaign,
                codeSnippet = """
                    <!-- Meta Pixel Code -->
                    <script>
                    !function(f,b,e,v,n,t,s)
                    {if(f.fbq)return;n=f.fbq=function(){n.callMethod?
                    n.callMethod.apply(n,arguments):n.queue.push(arguments)};
                    if(!f._fbq)f._fbq=n;n.push=n;n.loaded=!0;n.version='2.0';
                    n.queue=[];t=b.createElement(e);t.async=!0;
                    t.src=v;s=b.getElementsByTagName(e)[0];
                    s.parentNode.insertBefore(t,s)}(window, document,'script',
                    'https://connect.facebook.net/en_US/fbevents.js');
                    fbq('init', '9812739182371');
                    fbq('track', 'PageView');
                    </script>
                """.trimIndent()
            ),
            MarketingTagItem(
                id = "tiktok_pixel",
                name = "TikTok Event Pixel",
                category = "Ad Tracking",
                tagIdOrCode = "Pixel ID: C98VIP88TIKTOK",
                isActive = false,
                icon = Icons.Default.Campaign,
                codeSnippet = """
                    <script>
                    !function (w, d, t) {
                      w.TiktokAnalyticsObject=t;var ttq=w[t]=w[t]||[];ttq.methods=["page","track","identify","instances","debug","on","off","once","ready","alias","group","enableCookie","disableCookie"];
                    }(window, document, 'ttq');
                    ttq.load('C98VIP88TIKTOK');
                    ttq.page();
                    </script>
                """.trimIndent()
            ),
            MarketingTagItem(
                id = "cloudflare_analytics",
                name = "Cloudflare Web Analytics",
                category = "DNS & CDN",
                tagIdOrCode = "Token: cf_beacon_98821",
                isActive = true,
                icon = Icons.Default.Security,
                codeSnippet = """<script defer src='https://static.cloudflareinsights.com/beacon.min.js' data-cf-beacon='{"token": "cf_beacon_98821"}'></script>"""
            )
        )
    }

    fun copyToClipboard(label: String, text: String) {
        val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clip.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "$label disalin ke clipboard", Toast.LENGTH_SHORT).show()
    }

    fun validateLiveTagsOnDomain() {
        scope.launch {
            isValidating = true
            validationResult = null
            val baseDomain = settings.baseDomain
            val url = "https://$baseDomain"

            val result = withContext(Dispatchers.IO) {
                try {
                    val client = OkHttpClient.Builder()
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(15, TimeUnit.SECONDS)
                        .build()

                    val req = Request.Builder().url(url).build()
                    val resp = client.newCall(req).execute()
                    val html = resp.body?.string().orEmpty()
                    val code = resp.code

                    val hasGsc = html.contains("google-site-verification")
                    val hasGtm = html.contains("googletagmanager.com/gtm.js") || html.contains("GTM-")
                    val hasGa4 = html.contains("gtag/js") || html.contains("G-")
                    val hasFb = html.contains("connect.facebook.net")

                    buildString {
                        append("🔍 Hasil Audit Live di ").append(url).append(" (HTTP ").append(code).append("):\n")
                        append("• Google Site Verification: ").append(if (hasGsc) "✅ Terdeteksi" else "⚠️ Belum terdeteksi di HTML").append("\n")
                        append("• Google Tag Manager: ").append(if (hasGtm) "✅ Aktif" else "⚪ Tidak ditemukan").append("\n")
                        append("• Google Analytics 4: ").append(if (hasGa4) "✅ Aktif" else "⚪ Tidak ditemukan").append("\n")
                        append("• Meta Pixel: ").append(if (hasFb) "✅ Aktif" else "⚪ Tidak ditemukan")
                    }
                } catch (e: Exception) {
                    "❌ Gagal mengakses $url: ${e.message}\n(Pastikan domain aktif dan terhubung ke internet)"
                }
            }

            isValidating = false
            validationResult = result
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // HEADER CARD
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Tag, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Marketing Tags & Tracking Browser",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "/backoffice/settings/marketing-tags",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Kelola script pelacakan marketing, tag verifikasi kepemilikan Google Search Console, dan status aktivasi live di domain ${settings.baseDomain}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { validateLiveTagsOnDomain() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isValidating) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Mengaudit HTML...", style = MaterialTheme.typography.labelSmall)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Validasi Live di Domain", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                val url = "https://${settings.baseDomain}${settings.marketingTagsPath}"
                                onOpenInBrowser(url, "Marketing Tags")
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Buka di Web Panel", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // VALIDATION RESULT BANNER
        if (!validationResult.isNullOrBlank()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (validationResult!!.contains("✅")) StatusActive.copy(alpha = 0.15f) else StatusBlocked.copy(alpha = 0.15f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = validationResult!!,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, lineHeight = 18.sp),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { validationResult = null }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Tutup")
                        }
                    }
                }
            }
        }

        // TAGS LIST
        items(tags, key = { it.id }) { tag ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (tag.isActive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (tag.isActive) StatusActive.copy(alpha = 0.15f) else Color.LightGray.copy(alpha = 0.3f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    tag.icon,
                                    contentDescription = null,
                                    tint = if (tag.isActive) StatusActive else Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = tag.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "${tag.category} • ${if (tag.isActive) "🟢 AKTIF" else "⚪ NONAKTIF"}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.sp,
                                        color = if (tag.isActive) StatusActive else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }

                        Switch(
                            checked = tag.isActive,
                            onCheckedChange = { checked ->
                                val index = tags.indexOfFirst { it.id == tag.id }
                                if (index != -1) {
                                    tags[index] = tag.copy(isActive = checked)
                                    val statusStr = if (checked) "diaktifkan" else "dinonaktifkan"
                                    Toast.makeText(context, "${tag.name} $statusStr", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tag.tagIdOrCode,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                ),
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { copyToClipboard(tag.name, tag.codeSnippet) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Salin Code", modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
