package ru.snowadv.kinopoiskfeaturedmovies.presentation

import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import ru.snowadv.kinopoiskfeaturedmovies.feat.util.UiEvent
import ru.snowadv.kinopoiskfeaturedmovies.presentation.event.EventAggregator
import ru.snowadv.kinopoiskfeaturedmovies.feat.util.MainScreen
import ru.snowadv.kinopoiskfeaturedmovies.feat.util.NavigationEvent
import ru.snowadv.kinopoiskfeaturedmovies.presentation.ui.film.info.FilmInfoScreen
import ru.snowadv.kinopoiskfeaturedmovies.presentation.ui.home.HomeScreen
import ru.snowadv.kinopoiskfeaturedmovies.presentation.ui.theme.KinopoiskFeaturedMoviesTheme
import ru.snowadv.kinopoiskfeaturedmovies.presentation.view_model.FilmInfoViewModel
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var eventAggregator: EventAggregator
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val snackbarHostState = remember { SnackbarHostState() }
            val navController = rememberNavController()
            val navBackStackEntry = navController.currentBackStackEntryAsState()
            val filmInfoViewModel: FilmInfoViewModel = hiltViewModel()
            val filmInfoIdState = rememberSaveable { mutableStateOf<Long?>(null) }
            val isHorizontal =
                LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
            val coroutineScope = rememberCoroutineScope()

            BackHandler(
                enabled = isHorizontal && filmInfoIdState.value != null
            ) {
                filmInfoIdState.value = null
            }

            val showFilmInfo = isHorizontal && filmInfoIdState.value != null

            val animateNavHostWidth = animateFloatAsState(
                targetValue = if (showFilmInfo) 0.5f else 1f,
                label = "nav host width animation"
            )
            val animateFilmInfoWidth = animateFloatAsState(
                targetValue = if (showFilmInfo) 1f else 0f,
                label = "nav host width animation"
            )

            LaunchedEffect(isHorizontal) {
                if (isHorizontal && navBackStackEntry.value?.destination?.route?.startsWith(
                        MainScreen.FilmInfo.noArgRoute
                    ) == true
                ) {
                    navController.popBackStack()
                } else if (!isHorizontal && filmInfoIdState.value != null) {
                    coroutineScope.launch {
                        eventAggregator.navigationChannel.send(
                            NavigationEvent.ToFilmInfo(
                                filmInfoIdState.value
                            )
                        )
                    }
                }
            }

            LaunchedEffect(true) {
                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    eventAggregator.eventChannel.receiveAsFlow().onEach {
                        when (it) {
                            is UiEvent.ShowSnackbar -> {
                                snackbarHostState.showSnackbar(
                                    message = it.message,
                                    duration = SnackbarDuration.Short
                                )
                            }
                        }
                    }.launchIn(this)

                    eventAggregator.navigationChannel.receiveAsFlow().onEach {
                        when (it) {
                            is NavigationEvent.ToHome -> {
                                navController.navigate(it.route) {
                                    popUpTo(navController.graph.startDestinationRoute!!) {
                                        inclusive = true
                                        saveState = true
                                    }
                                    restoreState = true
                                    launchSingleTop = true
                                }
                            }

                            is NavigationEvent.ToFilmInfo -> {
                                if (isHorizontal) {
                                    filmInfoIdState.value = it.id
                                } else {
                                    navController.navigate(it.route)
                                }
                            }
                        }
                    }.launchIn(this)
                }
            }

            val snackbarPadding =
                animateDpAsState(
                    targetValue = if (navBackStackEntry.value?.destination?.route == MainScreen.Home.route) 80.dp else 0.dp,
                    label = "snackbar position animation"
                )

            KinopoiskFeaturedMoviesTheme {
                // A surface container using the 'background' color from the theme
                Scaffold(
                    snackbarHost = {
                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier
                                .padding(bottom = snackbarPadding.value)
                                .fillMaxWidth(),
                            snackbar = {
                                Snackbar(
                                    snackbarData = it,
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(it)
                    ) {
                        NavHost(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(animateNavHostWidth.value),
                            navController = navController,
                            startDestination = MainScreen.Home.route
                        ) {
                            composable(
                                route = MainScreen.FilmInfo.route,
                                arguments = listOf(
                                    navArgument("id") {
                                        type = NavType.StringType
                                        nullable = true
                                        defaultValue = null
                                    }
                                )
                            ) { navBackStackEntry ->
                                val filmId =
                                    navBackStackEntry.arguments?.getString("id")?.toLongOrNull()
                                LaunchedEffect(filmId) {
                                    if (!isHorizontal && filmId != null) {
                                        filmInfoIdState.value = filmId
                                    }
                                }
                                FilmInfoScreen(
                                    modifier = Modifier.fillMaxSize(),
                                    onBackClick = {
                                        navController.popBackStack()
                                        filmInfoIdState.value = null
                                        Log.d(
                                            "TAG",
                                            "onCreate: erased filmInfoID state ; w now is ${filmInfoIdState.value}"
                                        )
                                    },
                                    filmInfoViewModel = filmInfoViewModel,
                                    filmId = filmInfoIdState
                                )
                            }

                            composable(MainScreen.Home.route) {
                                HomeScreen(modifier = Modifier.fillMaxWidth())
                            }
                        }

                        if(animateFilmInfoWidth.value > 0f) {
                            FilmInfoScreen(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(animateFilmInfoWidth.value),
                                onBackClick = {
                                    filmInfoIdState.value = null
                                },
                                filmInfoViewModel = filmInfoViewModel,
                                filmId = filmInfoIdState
                            )
                        }
                    }
                }
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    KinopoiskFeaturedMoviesTheme {

    }
}

