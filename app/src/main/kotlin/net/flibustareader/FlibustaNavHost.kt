package net.flibustareader

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import net.flibustareader.core.designsystem.Flibusta
import net.flibustareader.core.designsystem.components.FloatingTabBar
import net.flibustareader.core.designsystem.components.GlassScope
import net.flibustareader.core.designsystem.components.TabSpec
import net.flibustareader.feature.library.AboutScreen
import net.flibustareader.feature.library.AddToCollectionSheet
import net.flibustareader.feature.library.CollectionsSheet
import net.flibustareader.feature.library.HomeScreen
import net.flibustareader.feature.library.LibraryHostActions
import net.flibustareader.feature.library.LibraryMessage
import net.flibustareader.feature.library.LibraryScreen
import net.flibustareader.feature.library.LibraryViewModel
import net.flibustareader.feature.library.SearchScreen
import net.flibustareader.feature.library.Shelf
import net.flibustareader.feature.library.ShelfScreen
import net.flibustareader.feature.reader.ReaderRoute
import net.flibustareader.feature.catalog.CatalogScreen
import net.flibustareader.feature.catalog.CatalogViewModel
import net.flibustareader.feature.catalog.R as CatalogR
import kotlinx.coroutines.flow.Flow
import net.flibustareader.feature.library.R as LibraryR
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.runtime.CompositionLocalProvider
import net.flibustareader.core.designsystem.BookTransition
import net.flibustareader.core.designsystem.LocalNavAnimatedScope
import net.flibustareader.core.designsystem.LocalSharedTransitionScope
import net.flibustareader.core.designsystem.bookTransition

private const val MAIN = "main"
private const val READER = "reader/{bookId}"

private const val TABS = "tabs"
private const val SHELF = "shelf/{key}"
private const val SEARCH = "search"
private const val ABOUT = "about"

private val importMimeTypes = arrayOf(
    "application/epub+zip",
    "application/x-fictionbook+xml",
    "application/x-fictionbook",
    "application/zip",
    "application/x-zip-compressed",
    "application/octet-stream",
    "text/xml",
    "application/xml",
)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun FlibustaNavHost(openRequests: Flow<String>) {
    val nav = rememberNavController()
    val openBook: (String) -> Unit = { id -> nav.navigate("reader/${Uri.encode(id)}") { launchSingleTop = true } }

    LaunchedEffect(openRequests) {
        openRequests.collect { id ->
            BookTransition.key = null
            nav.popBackStack(MAIN, inclusive = false)
            openBook(id)
        }
    }

    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavHost(
                navController = nav,
                startDestination = MAIN,
                modifier = Modifier.fillMaxSize().background(Flibusta.colors.background),
            ) {
                composable(MAIN) {
                    CompositionLocalProvider(LocalNavAnimatedScope provides this) { MainTabs(onOpen = openBook) }
                }
                composable(
                    READER,
                    arguments = listOf(navArgument("bookId") { type = NavType.StringType }),
                    // Out of the tapped cover and back into it; a plain zoom when opened from elsewhere.
                    enterTransition = {
                        if (BookTransition.key != null) fadeIn(tween(420)) else scaleIn(tween(320), initialScale = 0.92f) + fadeIn(tween(220))
                    },
                    exitTransition = { fadeOut(tween(180)) },
                    popEnterTransition = { fadeIn(tween(220)) },
                    popExitTransition = {
                        if (BookTransition.key != null) fadeOut(tween(400)) else scaleOut(tween(260), targetScale = 0.92f) + fadeOut(tween(220))
                    },
                ) {
                    CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                        Box(Modifier.fillMaxSize().bookTransition(BookTransition.key)) {
                            ReaderRoute(onClose = { nav.popBackStack() })
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tabs «Главная / Библиотека / Каталог» with their inner screens. The floating tab bar is shown on those
 * screens only; search replaces it with its own field, and selection mode with an action bar.
 */
@Composable
private fun MainTabs(onOpen: (String) -> Unit, vm: LibraryViewModel = hiltViewModel(), catalog: CatalogViewModel = hiltViewModel()) {
    val inner = rememberNavController()
    val entry by inner.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val resources = LocalResources.current
    var collectionsOpen by rememberSaveable { mutableStateOf(false) }
    var addToCollection by remember { mutableStateOf<Set<String>?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.import(it) }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) vm.watchFolder(uri) }

    LaunchedEffect(vm) {
        vm.messages.collect { message ->
            val text = when (message) {
                is LibraryMessage.Added -> resources.getQuantityString(LibraryR.plurals.import_added, message.count, message.count)
                LibraryMessage.AlreadyThere -> resources.getString(LibraryR.string.import_exists)
                is LibraryMessage.Failed -> resources.getString(LibraryR.string.import_failed, message.name)
            }
            snackbar.showSnackbar(text)
        }
    }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    fun selectTab(index: Int) {
        vm.endSelection()
        // A tab tap from a pushed screen (collection, «Предыдущие») returns to the tabs first.
        if (route != TABS) inner.popBackStack(TABS, inclusive = false)
        tab = index
    }
    val openShelf: (Shelf) -> Unit = { shelf ->
        collectionsOpen = false
        inner.navigate("shelf/${shelf.key}")
    }
    val tabBarVisible = route != SEARCH && route != ABOUT && !vm.selecting
    val bottomPadding = PaddingValues(bottom = 96.dp)
    val hostActions = LibraryHostActions(
        onAddBooks = { importLauncher.launch(importMimeTypes) },
        onPickFolder = { folderLauncher.launch(null) },
        onAbout = { inner.navigate(ABOUT) },
        onCollections = { collectionsOpen = true },
        onAddToCollection = { addToCollection = it },
    )

    GlassScope { source ->
        Box(Modifier.fillMaxSize().background(Flibusta.colors.background)) {
            NavHost(
                navController = inner,
                startDestination = TABS,
                modifier = source.fillMaxSize().background(Flibusta.colors.background),
                // Tabs switch instantly, as in iOS; animations here only delayed the switch.
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
            ) {
                composable(TABS) {
                    // Both tabs stay composed (state and scroll kept); only the active one is laid
                    // out and drawn, so a switch is a single frame with nothing overlapping.
                    TabStack(selected = tab) {
                        HomeScreen(
                            vm, bottomPadding, onOpen = onOpen, onOpenShelf = openShelf,
                            onAddBooks = hostActions.onAddBooks,
                            onAddToCollection = { addToCollection = setOf(it.id) },
                        )
                        LibraryScreen(vm, bottomPadding, onOpen = onOpen, actions = hostActions)
                        CatalogScreen(catalog, bottomPadding, onOpen = onOpen)
                    }
                }
                composable(SHELF, arguments = listOf(navArgument("key") { type = NavType.StringType })) { e ->
                    val shelf = Shelf.fromKey(e.arguments?.getString("key").orEmpty())
                    ShelfScreen(vm, shelf, bottomPadding, onBack = { inner.popBackStack() }, onOpen = onOpen, onAddToCollection = { addToCollection = it })
                }
                composable(SEARCH) { SearchScreen(vm, onClose = { inner.popBackStack() }, onOpen = onOpen) }
                composable(ABOUT) {
                    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }
                    AboutScreen(onBack = { inner.popBackStack() }, versionName = version)
                }
            }

            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp))

            AnimatedVisibility(
                visible = tabBarVisible,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                FloatingTabBar(
                    tabs = listOf(
                        TabSpec(stringResource(LibraryR.string.tab_home), Icons.Rounded.Home),
                        TabSpec(stringResource(LibraryR.string.tab_library), Icons.AutoMirrored.Rounded.LibraryBooks),
                        TabSpec(stringResource(CatalogR.string.tab_catalog), Icons.Rounded.TravelExplore),
                    ),
                    selected = tab,
                    onSelect = ::selectTab,
                    onSearch = { inner.navigate(SEARCH) { launchSingleTop = true } },
                    searchDescription = stringResource(LibraryR.string.tab_search),
                )
            }
        }
    }

    if (collectionsOpen) CollectionsSheet(vm, onOpen = openShelf, onDismiss = { collectionsOpen = false })
    addToCollection?.let { ids -> AddToCollectionSheet(vm, ids, onDone = { addToCollection = null; vm.endSelection() }) }
}

/** Lays out and draws only the [selected] child; the others stay composed but invisible and untouchable. */
@Composable
private fun TabStack(selected: Int, content: @Composable () -> Unit) {
    Layout(content) { measurables, constraints ->
        val placeable = measurables.getOrNull(selected)?.measure(constraints)
        layout(constraints.maxWidth, constraints.maxHeight) { placeable?.place(0, 0) }
    }
}
