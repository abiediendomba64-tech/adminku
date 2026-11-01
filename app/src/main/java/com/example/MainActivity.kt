@file:Suppress("DEPRECATION")

package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Domain
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Web
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Domain
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Web
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.MemberTicket
import com.example.ui.components.InAppWebBrowser
import com.example.ui.components.SettingsDialog
import com.example.ui.screens.AddTicketDialog
import com.example.ui.screens.BotCommandCenterScreen
import com.example.ui.screens.CloudFreeTierScreen
import com.example.ui.screens.DomainListScreen
import com.example.ui.screens.DomainRequestsManager
import com.example.ui.screens.GeminiChatScreen
import com.example.ui.screens.GscHistoryLogScreen
import com.example.ui.screens.MarketingTagsBrowserScreen
import com.example.ui.screens.PanelHubScreen
import com.example.ui.screens.TelegramReportScreen
import com.example.ui.screens.TicketDetailScreen
import com.example.ui.screens.TicketListScreen
import com.example.ui.screens.WebWorkspaceScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.StatusActive
import com.example.ui.theme.TelegramBrandColor
import com.example.ui.viewmodel.MainViewModel

enum class AppTab(val title: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector) {
    PANEL("Panel Hub", Icons.Filled.Domain, Icons.Outlined.Domain),
    WORKSPACE("Multi-Tab Web", Icons.Filled.Web, Icons.Outlined.Web),
    DATA("Tiket & Domain", Icons.Filled.ConfirmationNumber, Icons.Outlined.ConfirmationNumber),
    TELEGRAM("Bot Telegram", Icons.Filled.Send, Icons.Outlined.Send),
    GEMINI("Gemini AI", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppRoot()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppRoot(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(AppTab.PANEL) }
    var activeBrowserUrl by remember { mutableStateOf<String?>(null) }
    var activeBrowserTitle by remember { mutableStateOf("Browser") }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var telegramSubTabIndex by remember { mutableIntStateOf(0) }
    var dataSubTabIndex by remember { mutableIntStateOf(0) }
    var prefillDomainForTicket by remember { mutableStateOf<String?>(null) }
    var showDirectCreateTicketDialog by remember { mutableStateOf(false) }
    var selectedTicketForDetail by remember { mutableStateOf<MemberTicket?>(null) }
    var showMarketingTagsBrowser by remember { mutableStateOf(false) }
    var showGscHistoryLogScreen by remember { mutableStateOf(false) }

    val settings by viewModel.settings.collectAsState()
    val domains by viewModel.domains.collectAsState()
    val tickets by viewModel.tickets.collectAsState()
    val gscSubmissionLogs by viewModel.gscSubmissionLogs.collectAsState()
    val telegramStatus by viewModel.telegramStatus.collectAsState()
    val reindexStatus by viewModel.reindexStatus.collectAsState()
    val botLogs by viewModel.botLogs.collectAsState()
    val isPollerRunning by viewModel.isPollerRunning.collectAsState()
    val botCommandDeployResult by viewModel.botCommandDeployResult.collectAsState()
    val chatMessages by viewModel.chatMessages.collectAsState()
    val isChatLoading by viewModel.isChatLoading.collectAsState()
    val botVerificationResult by viewModel.botVerificationResult.collectAsState()
    val webhookResult by viewModel.webhookInfoResult.collectAsState()
    val webhookActionLoading by viewModel.webhookActionLoading.collectAsState()
    val ticketAiSuggestion by viewModel.ticketAiSuggestion.collectAsState()
    val isTicketAiLoading by viewModel.isTicketAiLoading.collectAsState()
    val cloudflareDeployStatus by viewModel.cloudflareDeployStatus.collectAsState()
    val isCloudflareDeploying by viewModel.isCloudflareDeploying.collectAsState()

    val openTicketsCount = tickets.count { it.status == MemberTicket.STATUS_OPEN }

    if (showGscHistoryLogScreen) {
        GscHistoryLogScreen(
            logs = gscSubmissionLogs,
            onBack = { showGscHistoryLogScreen = false },
            onSubmitUrl = { url, engine ->
                viewModel.submitUrlForIndexing(url, engine)
            },
            onDeleteLog = { log ->
                viewModel.deleteGscLog(log)
            },
            onClearAllLogs = {
                viewModel.clearAllGscLogs()
            },
            onSendReportToTelegram = { text ->
                viewModel.sendTelegramReport(settings.telegramAdminId, text)
            },
            onOpenInBrowser = { url, title ->
                activeBrowserUrl = url
                activeBrowserTitle = title
            }
        )
        return
    }

    if (selectedTicketForDetail != null) {
        TicketDetailScreen(
            ticket = selectedTicketForDetail!!,
            aiSuggestion = ticketAiSuggestion,
            isAiLoading = isTicketAiLoading,
            onBack = {
                selectedTicketForDetail = null
                viewModel.clearTicketAiSuggestion()
            },
            onUpdateStatus = { newStatus, notes ->
                viewModel.updateTicketStatus(selectedTicketForDetail!!, newStatus, notes)
                selectedTicketForDetail = selectedTicketForDetail?.copy(status = newStatus, adminNotes = notes)
            },
            onRequestAiSuggestion = {
                viewModel.requestAiSuggestionForTicket(selectedTicketForDetail!!)
            },
            onDispatchAiReplyToTelegram = { text ->
                viewModel.dispatchAiSuggestionToTelegram(selectedTicketForDetail!!, text)
            }
        )
        return
    }

    if (showMarketingTagsBrowser) {
        MarketingTagsBrowserScreen(
            settings = settings,
            onBack = { showMarketingTagsBrowser = false },
            onOpenInBrowser = { url, title ->
                activeBrowserUrl = url
                activeBrowserTitle = title
            }
        )
        return
    }

    if (activeBrowserUrl != null) {
        InAppWebBrowser(
            initialUrl = activeBrowserUrl!!,
            title = activeBrowserTitle,
            onClose = { activeBrowserUrl = null }
        )
        return
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(TelegramBrandColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Send,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Backoffice Bot Suite",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                },
                actions = {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showSettingsDialog = true }
                            .padding(end = 8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(StatusActive)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = settings.baseDomain,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp
                                ),
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Pengaturan",
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.navigationBarsPadding(),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                AppTab.values().forEach { tab ->
                    val isSelected = selectedTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = tab },
                        icon = {
                            if (tab == AppTab.DATA && openTicketsCount > 0) {
                                BadgedBox(badge = { Badge { Text(openTicketsCount.toString()) } }) {
                                    Icon(
                                        imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.title
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.title
                                )
                            }
                        },
                        label = {
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                            )
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                AppTab.PANEL -> {
                    PanelHubScreen(
                        settings = settings,
                        reindexStatus = reindexStatus,
                        tickets = tickets,
                        gscLogCount = gscSubmissionLogs.size,
                        onTriggerReindex = { targetDomain ->
                            viewModel.triggerDomainReindex(targetDomain)
                        },
                        onSubmitUrlToGsc = { targetUrl ->
                            viewModel.submitUrlForIndexing(targetUrl)
                        },
                        onOpenGscHistory = {
                            showGscHistoryLogScreen = true
                        },
                        onSelectTicket = { ticket ->
                            selectedTicketForDetail = ticket
                        },
                        onDismissReindex = {
                            viewModel.dismissReindexStatus()
                        },
                        onOpenUrlInBrowser = { url, title ->
                            activeBrowserUrl = url
                            activeBrowserTitle = title
                        },
                        onOpenExternalBrowser = { url ->
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // ignore
                            }
                        },
                        onNavigateToChat = { selectedTab = AppTab.GEMINI },
                        onOpenSettings = { showSettingsDialog = true },
                        onUpdateDomain = { newDomain ->
                            viewModel.saveSettings(settings.copy(baseDomain = newDomain))
                        },
                        onOpenMarketingTags = {
                            showMarketingTagsBrowser = true
                        }
                    )
                }

                // DEDICATED MULTI-TAB WEBAPP WORKSPACE
                AppTab.WORKSPACE -> {
                    WebWorkspaceScreen(
                        settings = settings,
                        onTriggerReindex = { domain ->
                            viewModel.triggerDomainReindex(domain)
                        },
                        onCreateTicketForDomain = { domain ->
                            prefillDomainForTicket = domain
                            showDirectCreateTicketDialog = true
                        }
                    )
                }

                // DOMAIN REQUESTS & MEMBER TICKETS & MARKETING TAGS & GSC LOG
                AppTab.DATA -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        TabRow(
                            selectedTabIndex = dataSubTabIndex,
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            Tab(
                                selected = dataSubTabIndex == 0,
                                onClick = { dataSubTabIndex = 0 },
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("🎫 Tiket Member", style = MaterialTheme.typography.labelMedium)
                                        if (openTicketsCount > 0) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Badge { Text(openTicketsCount.toString()) }
                                        }
                                    }
                                }
                            )
                            Tab(
                                selected = dataSubTabIndex == 1,
                                onClick = { dataSubTabIndex = 1 },
                                text = { Text("🌐 Request Domain", style = MaterialTheme.typography.labelMedium) }
                            )
                            Tab(
                                selected = dataSubTabIndex == 2,
                                onClick = { dataSubTabIndex = 2 },
                                text = { Text("🏷️ Marketing Tags", style = MaterialTheme.typography.labelMedium) }
                            )
                            Tab(
                                selected = dataSubTabIndex == 3,
                                onClick = { dataSubTabIndex = 3 },
                                text = { Text("📜 Log GSC (${gscSubmissionLogs.size})", style = MaterialTheme.typography.labelMedium) }
                            )
                        }

                        when (dataSubTabIndex) {
                            0 -> {
                                TicketListScreen(
                                    tickets = tickets,
                                    onAddTicket = { mem, cat, prio, dom, desc, notes ->
                                        viewModel.createMemberTicket(mem, cat, prio, dom, desc, notes, "MANUAL")
                                    },
                                    onUpdateStatus = { ticket, status ->
                                        viewModel.updateTicketStatus(ticket, status)
                                    },
                                    onDeleteTicket = { ticket ->
                                        viewModel.deleteTicket(ticket)
                                    },
                                    onSelectTicket = { ticket ->
                                        selectedTicketForDetail = ticket
                                    }
                                )
                            }
                            1 -> {
                                DomainRequestsManager(
                                    domains = domains,
                                    onAddRequest = { name, cat, path, dns, notes ->
                                        viewModel.addDomain(name, cat, path, dns, notes)
                                    },
                                    onApproveRequest = { dom ->
                                        viewModel.approveDomainRequest(dom)
                                    },
                                    onRejectRequest = { dom, reason ->
                                        viewModel.rejectDomainRequest(dom, reason)
                                    },
                                    onDeleteRequest = { dom ->
                                        viewModel.deleteDomain(dom)
                                    },
                                    onSubmitToGsc = { url ->
                                        viewModel.submitUrlForIndexing(url)
                                    }
                                )
                            }
                            2 -> {
                                MarketingTagsBrowserScreen(
                                    settings = settings,
                                    onOpenInBrowser = { url, title ->
                                        activeBrowserUrl = url
                                        activeBrowserTitle = title
                                    }
                                )
                            }
                            else -> {
                                GscHistoryLogScreen(
                                    logs = gscSubmissionLogs,
                                    onBack = { dataSubTabIndex = 0 },
                                    onSubmitUrl = { url, engine ->
                                        viewModel.submitUrlForIndexing(url, engine)
                                    },
                                    onDeleteLog = { log ->
                                        viewModel.deleteGscLog(log)
                                    },
                                    onClearAllLogs = {
                                        viewModel.clearAllGscLogs()
                                    },
                                    onSendReportToTelegram = { text ->
                                        viewModel.sendTelegramReport(settings.telegramAdminId, text)
                                    },
                                    onOpenInBrowser = { url, title ->
                                        activeBrowserUrl = url
                                        activeBrowserTitle = title
                                    }
                                )
                            }
                        }
                    }
                }

                // TELEGRAM BOT DISPATCHER & BOT COMMAND CENTER
                AppTab.TELEGRAM -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        TabRow(
                            selectedTabIndex = telegramSubTabIndex,
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            Tab(
                                selected = telegramSubTabIndex == 0,
                                onClick = { telegramSubTabIndex = 0 },
                                text = { Text("📋 Laporan", style = MaterialTheme.typography.labelMedium) }
                            )
                            Tab(
                                selected = telegramSubTabIndex == 1,
                                onClick = { telegramSubTabIndex = 1 },
                                text = { Text("🤖 Perintah & WebApp", style = MaterialTheme.typography.labelMedium) }
                            )
                            Tab(
                                selected = telegramSubTabIndex == 2,
                                onClick = { telegramSubTabIndex = 2 },
                                text = { Text("☁️ Cloudflare & GitHub", style = MaterialTheme.typography.labelMedium) }
                            )
                        }

                        when (telegramSubTabIndex) {
                            0 -> {
                                TelegramReportScreen(
                                    settings = settings,
                                    telegramStatus = telegramStatus,
                                    onSendReportToAdmin = { text ->
                                        viewModel.sendTelegramReport(settings.telegramAdminId, text)
                                    },
                                    onSendReportToGroup = { text ->
                                        viewModel.sendTelegramReport(settings.telegramGroupId, text)
                                    },
                                    onSendGscReport = {
                                        viewModel.sendGscReportToTelegram()
                                    },
                                    onResetStatus = { viewModel.resetTelegramStatus() },
                                    onOpenSettings = { showSettingsDialog = true },
                                    reportTextGenerator = { incDom, incTick, onlyOpen ->
                                        viewModel.generateReportText(incDom, incTick, onlyOpen)
                                    }
                                )
                            }
                            1 -> {
                                BotCommandCenterScreen(
                                    settings = settings,
                                    isPollerRunning = isPollerRunning,
                                    botLogs = botLogs,
                                    deployResult = botCommandDeployResult,
                                    onTogglePoller = { viewModel.toggleBotPoller() },
                                    onDeployCommands = { viewModel.deployBotCommands() },
                                    onDeployWebApp = { viewModel.deployWebAppButton() },
                                    onClearDeployResult = { viewModel.clearBotVerification() }
                                )
                            }
                            else -> {
                                CloudFreeTierScreen(
                                    settings = settings,
                                    webhookResult = webhookResult,
                                    isLoading = webhookActionLoading,
                                    isRemoteDeploying = isCloudflareDeploying,
                                    remoteDeployStatus = cloudflareDeployStatus,
                                    onSetWebhook = { url -> viewModel.setTelegramWebhook(url) },
                                    onGetWebhookInfo = { viewModel.getTelegramWebhookInfo() },
                                    onDeleteWebhook = { viewModel.deleteTelegramWebhook() },
                                    onClearResult = { viewModel.clearWebhookResult() },
                                    onRemoteDeploy = { token, acc, sub -> viewModel.deployRemoteToCloudflare(token, acc, sub) },
                                    onSaveSettings = { updated -> viewModel.saveSettings(updated) }
                                )
                            }
                        }
                    }
                }

                // GEMINI AI ASSISTANT
                AppTab.GEMINI -> {
                    GeminiChatScreen(
                        messages = chatMessages,
                        isLoading = isChatLoading,
                        onSendMessage = { text -> viewModel.sendChatMessage(text) },
                        onClearChat = { viewModel.clearChat() },
                        onAnalyzeIssues = { viewModel.askAiToAnalyzeIssues() },
                        onAskGscGuide = { viewModel.askAiForGscGuide() }
                    )
                }
            }
        }
    }

    if (showDirectCreateTicketDialog) {
        AddTicketDialog(
            onDismiss = { showDirectCreateTicketDialog = false },
            onConfirm = { mem, cat, prio, dom, desc, notes ->
                viewModel.createMemberTicket(
                    memberUsername = mem,
                    category = cat,
                    priority = prio,
                    domainAffected = if (dom.isNotBlank()) dom else (prefillDomainForTicket ?: ""),
                    issueDescription = desc,
                    adminNotes = notes,
                    reportedVia = "WORKSPACE_DOCK"
                )
                showDirectCreateTicketDialog = false
                prefillDomainForTicket = null
            }
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            currentSettings = settings,
            botVerificationStatus = botVerificationResult,
            onVerifyToken = { token -> viewModel.verifyBotToken(token) },
            onSave = { newSettings -> viewModel.saveSettings(newSettings) },
            onDismiss = {
                showSettingsDialog = false
                viewModel.clearBotVerification()
            }
        )
    }
}
