package com.synthbyte.scanmate

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.synthbyte.scanmate.data.SettingsRepository
import com.synthbyte.scanmate.data.ThemeMode
import com.synthbyte.scanmate.ui.navigation.Routes
import com.synthbyte.scanmate.ui.screens.AiScreen
import com.synthbyte.scanmate.ui.screens.CameraScreen
import com.synthbyte.scanmate.ui.screens.DocumentDetailScreen
import com.synthbyte.scanmate.ui.screens.FileManagerScreen
import com.synthbyte.scanmate.ui.screens.HomeScreen
import com.synthbyte.scanmate.ui.screens.PageEditorScreen
import com.synthbyte.scanmate.ui.screens.PdfToolsScreen
import com.synthbyte.scanmate.ui.screens.OcrTranslateScreen
import com.synthbyte.scanmate.ui.screens.OnboardingScreen
import com.synthbyte.scanmate.ui.screens.QrScannerScreen
import com.synthbyte.scanmate.ui.screens.QrScreen
import com.synthbyte.scanmate.ui.screens.SettingsScreen
import com.synthbyte.scanmate.ui.screens.SignatureScreen
import com.synthbyte.scanmate.ui.screens.ZipScreen
import com.synthbyte.scanmate.ui.screens.VaultScreen
import com.synthbyte.scanmate.ui.theme.ScanMateTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var shortcutRouteState: MutableState<String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        publishLauncherShortcuts()
        setContent {
            val settingsRepository = remember { SettingsRepository(applicationContext) }
            val themeMode by settingsRepository.themeModeFlow.collectAsState(initial = ThemeMode.SYSTEM)
            val onboardingCompleteState by settingsRepository.onboardingCompleteFlow.collectAsState(initial = null)
            val scope = rememberCoroutineScope()
            val requestedShortcutRoute = remember {
                mutableStateOf(normalizeShortcutRoute(intent?.getStringExtra(EXTRA_SHORTCUT_ROUTE)))
            }
            shortcutRouteState = requestedShortcutRoute

            ScanMateTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val onboardingComplete = onboardingCompleteState
                    if (onboardingComplete == null) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        val navController = rememberNavController()

                        LaunchedEffect(requestedShortcutRoute.value) {
                            val route = requestedShortcutRoute.value
                            if (route != Routes.HOME) {
                                navController.navigate(route) {
                                    launchSingleTop = true
                                    restoreState = true
                                }
                                requestedShortcutRoute.value = Routes.HOME
                            }
                        }

                        val startDestination = if (onboardingComplete) Routes.HOME else Routes.ONBOARDING
                        NavHost(navController = navController, startDestination = startDestination) {
                        composable(Routes.ONBOARDING) {
                            OnboardingScreen(
                                onFinish = {
                                    scope.launch { settingsRepository.setOnboardingComplete(true) }
                                    navController.navigate(Routes.HOME) {
                                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }
                        composable(Routes.HOME) {
                            HomeScreen(
                                onNavigateToCamera = { navController.navigate(Routes.CAMERA_SCAN) },
                                onNavigateToDoc = { id -> navController.navigate(Routes.docDetail(id)) },
                                onNavigateToQr = { navController.navigate(Routes.QR_TOOLS) },
                                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                                onNavigateToAi = { navController.navigate(Routes.AI_ASSISTANT) },
                                onNavigateToZip = { navController.navigate(Routes.ZIP_TOOLS) },
                                onNavigateToFiles = { navController.navigate(Routes.FILE_MANAGER) },
                                onNavigateToPdfTools = { navController.navigate(Routes.PDF_TOOLS) },
                                onNavigateToTranslate = { navController.navigate(Routes.OCR_TRANSLATE) },
                                onNavigateToVault = { navController.navigate(Routes.VAULT) }
                            )
                        }
                        composable(Routes.CAMERA_SCAN) {
                            CameraScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onScanFinished = { id ->
                                    navController.popBackStack()
                                    navController.navigate(Routes.docDetail(id))
                                }
                            )
                        }
                        composable(Routes.DOC_DETAIL) { backStackEntry ->
                            val docIdStr = backStackEntry.arguments?.getString("docId") ?: "0"
                            DocumentDetailScreen(
                                docId = docIdStr.toLongOrNull() ?: 0L,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToPageEditor = { documentId, pageId -> navController.navigate(Routes.pageEditor(documentId, pageId)) },
                                onNavigateToSignature = { documentId -> navController.navigate(Routes.signature(documentId)) }
                            )
                        }

                        composable(Routes.PAGE_EDITOR) { backStackEntry ->
                            val docIdStr = backStackEntry.arguments?.getString("docId") ?: "0"
                            val pageIdStr = backStackEntry.arguments?.getString("pageId") ?: "0"
                            PageEditorScreen(
                                docId = docIdStr.toLongOrNull() ?: 0L,
                                pageId = pageIdStr.toLongOrNull() ?: 0L,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable(Routes.PDF_TOOLS) {
                            PdfToolsScreen(onNavigateBack = { navController.popBackStack() })
                        }
                        composable(Routes.SIGNATURE) { backStackEntry ->
                            val docIdStr = backStackEntry.arguments?.getString("docId") ?: "0"
                            SignatureScreen(
                                docId = docIdStr.toLongOrNull() ?: 0L,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable(Routes.QR_TOOLS) {
                            QrScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onOpenCameraScanner = { navController.navigate(Routes.QR_SCANNER) }
                            )
                        }
                        composable(Routes.QR_SCANNER) {
                            QrScannerScreen(onNavigateBack = { navController.popBackStack() })
                        }
                        composable(Routes.SETTINGS) {
                            SettingsScreen(onNavigateBack = { navController.popBackStack() })
                        }
                        composable(Routes.AI_ASSISTANT) {
                            AiScreen(onNavigateBack = { navController.popBackStack() })
                        }
                        composable(Routes.OCR_TRANSLATE) {
                            OcrTranslateScreen(onNavigateBack = { navController.popBackStack() })
                        }
                        composable(Routes.ZIP_TOOLS) {
                            ZipScreen(onNavigateBack = { navController.popBackStack() })
                        }
                        composable(Routes.FILE_MANAGER) {
                            FileManagerScreen(onNavigateBack = { navController.popBackStack() })
                        }
                        composable(Routes.VAULT) {
                            VaultScreen(onNavigateBack = { navController.popBackStack() })
                        }
                    }
                }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        shortcutRouteState?.value = normalizeShortcutRoute(intent.getStringExtra(EXTRA_SHORTCUT_ROUTE))
    }

    override fun onDestroy() {
        shortcutRouteState = null
        super.onDestroy()
    }

    private fun publishLauncherShortcuts() {
        val shortcuts = listOf(
            buildShortcut("scan", "Scan", "Open camera scanner", Routes.CAMERA_SCAN),
            buildShortcut("ai", "AI", "Open AI Workspace", Routes.AI_ASSISTANT),
            buildShortcut("qr", "QR", "Open QR tools", Routes.QR_TOOLS),
            buildShortcut("pdf", "PDF", "Open PDF tools", Routes.PDF_TOOLS)
        )
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(this, shortcuts) }
    }

    private fun buildShortcut(id: String, label: String, longLabel: String, route: String): ShortcutInfoCompat {
        val shortcutIntent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(EXTRA_SHORTCUT_ROUTE, route)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return ShortcutInfoCompat.Builder(this, id)
            .setShortLabel(label)
            .setLongLabel(longLabel)
            .setIcon(IconCompat.createWithResource(this, R.mipmap.ic_launcher))
            .setIntent(shortcutIntent)
            .build()
    }

    private fun normalizeShortcutRoute(raw: String?): String {
        return when (raw) {
            Routes.CAMERA_SCAN, Routes.AI_ASSISTANT, Routes.QR_TOOLS, Routes.PDF_TOOLS, Routes.OCR_TRANSLATE -> raw
            else -> Routes.HOME
        }
    }

    companion object {
        const val EXTRA_SHORTCUT_ROUTE = "shortcut_route"
    }
}
