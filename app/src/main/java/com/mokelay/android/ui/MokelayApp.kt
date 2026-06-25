package com.mokelay.android.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mokelay.android.api.MokelayPageApi
import com.mokelay.android.navigation.PageDestination
import com.mokelay.android.navigation.PageSource
import com.mokelay.android.navigation.decodeRouteValue
import com.mokelay.android.navigation.toPageRoute

@Composable
fun MokelayApp(apiClient: MokelayPageApi = remember { MokelayPageApi() }) {
    val navController = rememberNavController()
    val context = LocalContext.current

    fun navigate(destination: PageDestination) {
        navController.navigate(destination.toPageRoute())
    }

    fun openExternal(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(onOpenPage = ::navigate)
        }

        composable(
            route = "page/{source}/{uuid}",
            arguments = listOf(
                navArgument("source") { type = NavType.StringType },
                navArgument("uuid") { type = NavType.StringType },
            ),
        ) { entry ->
            val source = PageSource.fromWireValue(entry.arguments?.getString("source"))
            val uuid = decodeRouteValue(entry.arguments?.getString("uuid").orEmpty())
            PageScreen(
                uuid = uuid,
                source = source,
                apiClient = apiClient,
                onBack = { navController.popBackStack() },
                onNavigatePage = ::navigate,
                onOpenExternalUrl = ::openExternal,
            )
        }
    }
}
