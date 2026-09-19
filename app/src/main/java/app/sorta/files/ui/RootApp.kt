package app.sorta.files.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.sorta.files.AppContainer
import app.sorta.files.R
import app.sorta.files.ui.browse.BrowseRootScreen
import app.sorta.files.ui.browse.CategoryScreen
import app.sorta.files.ui.browse.FolderScreen
import app.sorta.files.ui.home.HomeScreen
import app.sorta.files.ui.home.RecentScreen
import app.sorta.files.ui.navigation.Dest
import app.sorta.files.ui.preview.ImagePreviewScreen
import app.sorta.files.ui.preview.PdfPreviewScreen
import app.sorta.files.ui.preview.TextPreviewScreen
import app.sorta.files.ui.search.SearchScreen
import app.sorta.files.ui.components.OperationProgressHost
import app.sorta.files.ui.onboarding.OnboardingGate
import androidx.navigation.NavGraph.Companion.findStartDestination

private data class Tab(val route: String, val labelRes: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val tabs = listOf(
    Tab(Dest.HOME, R.string.tab_home, Icons.Outlined.Home),
    Tab(Dest.INBOX, R.string.tab_inbox, Icons.Outlined.Inbox),
    Tab(Dest.BROWSE, R.string.tab_browse, Icons.Outlined.Folder),
    Tab(Dest.STORAGE, R.string.tab_storage, Icons.Outlined.Storage),
)

@Composable
fun RootApp(container: AppContainer) {
    val nav = rememberNavController()
    OnboardingGate(container) {
        Scaffold(
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
            bottomBar = {
                val backStack by nav.currentBackStackEntryAsState()
                val current = backStack?.destination?.route
                // Show the bar on tab roots and browsable screens only.
                val showBar = current == null ||
                    current in tabs.map { it.route } ||
                    current.startsWith("folder") || current.startsWith("category")
                if (showBar) NavigationBar {
                    tabs.forEach { t ->
                        val badgeCount = if (t.route == Dest.INBOX)
                            container.inboxBadgeCount.collectAsState().value else 0
                        NavigationBarItem(
                            selected = current == t.route,
                            onClick = {
                                nav.navigate(t.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    restoreState = true
                                    launchSingleTop = true
                                }
                            },
                            icon = {
                                androidx.compose.material3.BadgedBox(
                                    badge = {
                                        if (badgeCount > 0) {
                                            androidx.compose.material3.Badge {
                                                Text(if (badgeCount > 99) "99+" else "$badgeCount")
                                            }
                                        }
                                    }
                                ) { Icon(t.icon, contentDescription = null) }
                            },
                            label = { Text(stringResource(t.labelRes)) },
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                SortaNavHost(nav, container)
                // Conflict prompt is rendered inside OperationProgressHost's sheet.
                OperationProgressHost(container, nav)
            }
        }
    }
}

@Composable
fun SortaNavHost(nav: NavHostController, container: AppContainer) {
    NavHost(nav, startDestination = Dest.HOME) {
        composable(Dest.HOME) { HomeScreen(nav, container) }
        composable(Dest.INBOX) { app.sorta.files.ui.inbox.InboxScreen(nav, container) }
        composable(Dest.BROWSE) { BrowseRootScreen(nav, container) }
        composable(Dest.STORAGE) { app.sorta.files.ui.storage.StorageScreen(nav, container) }
        composable(Dest.SEARCH) { SearchScreen(nav, container) }
        composable(Dest.RECENT) { RecentScreen(nav, container) }
        composable(Dest.TRASH) { app.sorta.files.ui.storage.TrashScreen(nav, container) }
        composable(Dest.HISTORY) { app.sorta.files.ui.storage.HistoryScreen(nav, container) }
        composable(Dest.BATCH_RENAME, arguments = listOf(navArgument("paths") { type = NavType.StringType })) { e ->
            app.sorta.files.ui.rename.BatchRenameScreen(nav, container,
                Dest.decode(e.arguments?.getString("paths")).split("|"))
        }
        composable(Dest.RULES) { app.sorta.files.ui.rules.RulesScreen(nav, container) }
        composable(Dest.RULE_PREVIEW) { app.sorta.files.ui.rules.RulePreviewScreen(nav, container) }
        composable(Dest.INBOX_SOURCES) { app.sorta.files.ui.inbox.InboxSourcesScreen(nav, container) }
        composable(Dest.SETTINGS) { app.sorta.files.ui.settings.SettingsScreen(nav, container) }
        composable(Dest.LARGE_FILES) { app.sorta.files.ui.storage.LargeFilesScreen(nav, container) }
        composable(Dest.DUPLICATES) { app.sorta.files.ui.storage.DuplicatesScreen(nav, container) }
        composable(
            Dest.FOLDER,
            arguments = listOf(navArgument("path") { type = NavType.StringType })
        ) { entry ->
            FolderScreen(nav, container, Dest.decode(entry.arguments?.getString("path")))
        }
        composable(
            Dest.CATEGORY,
            arguments = listOf(navArgument("cat") { type = NavType.StringType })
        ) { entry ->
            CategoryScreen(nav, container, entry.arguments?.getString("cat") ?: "")
        }
        composable(
            Dest.IMAGE_PREVIEW,
            arguments = listOf(navArgument("path") { type = NavType.StringType })
        ) { entry ->
            ImagePreviewScreen(nav, Dest.decode(entry.arguments?.getString("path")))
        }
        composable(
            Dest.TEXT_PREVIEW,
            arguments = listOf(navArgument("path") { type = NavType.StringType })
        ) { entry ->
            TextPreviewScreen(nav, Dest.decode(entry.arguments?.getString("path")))
        }
        composable(
            Dest.PDF_PREVIEW,
            arguments = listOf(navArgument("path") { type = NavType.StringType })
        ) { entry ->
            PdfPreviewScreen(nav, Dest.decode(entry.arguments?.getString("path")))
        }
    }
}
