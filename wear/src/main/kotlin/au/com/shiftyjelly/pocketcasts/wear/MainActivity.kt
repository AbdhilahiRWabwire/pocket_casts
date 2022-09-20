package au.com.shiftyjelly.pocketcasts.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.material.ScalingLazyListState
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import au.com.shiftyjelly.pocketcasts.ui.theme.Theme
import au.com.shiftyjelly.pocketcasts.wear.theme.WearAppTheme
import au.com.shiftyjelly.pocketcasts.wear.ui.DownloadsScreen
import au.com.shiftyjelly.pocketcasts.wear.ui.FilesScreen
import au.com.shiftyjelly.pocketcasts.wear.ui.FiltersScreen
import au.com.shiftyjelly.pocketcasts.wear.ui.UpNextScreen
import au.com.shiftyjelly.pocketcasts.wear.ui.WatchListScreen
import au.com.shiftyjelly.pocketcasts.wear.ui.authenticationGraph
import au.com.shiftyjelly.pocketcasts.wear.ui.player.NowPlayingScreen
import au.com.shiftyjelly.pocketcasts.wear.ui.podcast.PodcastScreen
import au.com.shiftyjelly.pocketcasts.wear.ui.podcasts.PodcastsScreen
import com.google.android.horologist.compose.navscaffold.NavScaffoldViewModel
import com.google.android.horologist.compose.navscaffold.WearNavScaffold
import com.google.android.horologist.compose.navscaffold.scalingLazyColumnComposable
import com.google.android.horologist.compose.navscaffold.wearNavComposable
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var theme: Theme

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // TODO add lines for radioactive theme
            WearApp(theme.activeTheme)
        }
    }
}

@Composable
fun WearApp(themeType: Theme.ThemeType) {
    WearAppTheme(themeType) {
        val navController = rememberSwipeDismissableNavController()

        WearNavScaffold(
            navController = navController,
            startDestination = WatchListScreen.route
        ) {

            scalingLazyColumnComposable(
                route = WatchListScreen.route,
                scrollStateBuilder = { ScalingLazyListState() }
            ) {
                WatchListScreen(navController::navigate, it.scrollableState)
            }

            wearNavComposable(NowPlayingScreen.route) { _, viewModel ->
                viewModel.timeTextMode = NavScaffoldViewModel.TimeTextMode.Off
                NowPlayingScreen()
            }

            scalingLazyColumnComposable(
                route = UpNextScreen.route,
                scrollStateBuilder = { ScalingLazyListState() }
            ) {
                UpNextScreen(listState = it.scrollableState)
            }

            scalingLazyColumnComposable(
                route = PodcastsScreen.route,
                scrollStateBuilder = { ScalingLazyListState() }
            ) {
                PodcastsScreen(
                    listState = it.scrollableState,
                    onNavigateToPodcast = { podcastUuid ->
                        navController.navigate(PodcastScreen.navigateRoute(podcastUuid))
                    }
                )
            }

            wearNavComposable(
                route = PodcastScreen.route,
                arguments = listOf(navArgument(PodcastScreen.argument) { type = NavType.StringType })
            ) { _, _ ->
                PodcastScreen()
            }

            wearNavComposable(FiltersScreen.route) { _, _ -> FiltersScreen() }
            wearNavComposable(DownloadsScreen.route) { _, _ -> DownloadsScreen() }
            wearNavComposable(FilesScreen.route) { _, _ -> FilesScreen() }

            authenticationGraph(navController)
        }
    }
}

@Preview(device = Devices.WEAR_OS_SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
    WearApp(Theme.ThemeType.DARK)
}
