package com.duovial

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.duovial.auth.AuthService
import com.duovial.auth.LocalAuthService
import com.duovial.screens.AccountScreen
import com.duovial.screens.EventsScreen
import com.duovial.screens.FatigueScreen
import com.duovial.screens.MonitorScreen
import com.duovial.screens.SettingsScreen
import com.duovial.state.AppStateManager
import com.duovial.state.CameraServiceManager
import com.duovial.state.LocalCameraServiceManager
import com.duovial.theme.DuoVialBackground
import com.duovial.theme.DuoVialCardBackground
import com.duovial.theme.DuoVialNeonGreen
import com.duovial.theme.DuoVialTextSecondary

enum class Tab(val label: String) {
    MONITOR("Monitor"),
    EVENTS("Eventos"),
    SETTINGS("Configurar"),
    ACCOUNT("Cuenta")
}

@Composable
fun DuoVialApp(
    serviceManager: CameraServiceManager? = null,
    authService: AuthService? = null
) {
    var activeTab by remember { mutableStateOf(Tab.MONITOR) }
    var showFatigue by remember { mutableStateOf(false) }
    val cameraState by AppStateManager.cameraState.collectAsState()

    CompositionLocalProvider(
        LocalCameraServiceManager provides serviceManager,
        LocalAuthService provides authService
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize().background(DuoVialBackground),
            containerColor = DuoVialBackground,
            bottomBar = {
                AnimatedVisibility(
                    visible = !showFatigue,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    NavigationBar(
                        containerColor = DuoVialCardBackground,
                        tonalElevation = 0.dp,
                        modifier = Modifier
                            .shadow(8.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                            .navigationBarsPadding()
                            .height(72.dp)
                    ) {
                        Tab.entries.forEach { tab ->
                            val selected = activeTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { activeTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = when (tab) {
                                            Tab.MONITOR -> Icons.Outlined.Dashboard
                                            Tab.EVENTS -> Icons.Outlined.VideoLibrary
                                            Tab.SETTINGS -> Icons.Outlined.Settings
                                            Tab.ACCOUNT -> Icons.Outlined.AccountCircle
                                        },
                                        contentDescription = tab.label,
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                label = {
                                    Text(tab.label, style = MaterialTheme.typography.labelMedium)
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = DuoVialNeonGreen,
                                    selectedTextColor = DuoVialNeonGreen,
                                    unselectedIconColor = DuoVialTextSecondary,
                                    unselectedTextColor = DuoVialTextSecondary,
                                    indicatorColor = DuoVialNeonGreen.copy(alpha = 0.1f)
                                )
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(padding)
            ) {
                when {
                    showFatigue -> FatigueScreen(
                        serviceManager = serviceManager,
                        onBack = { showFatigue = false }
                    )
                    else -> when (activeTab) {
                        Tab.MONITOR -> MonitorScreen(
                            serviceManager = serviceManager,
                            onOpenFatigue = { showFatigue = true }
                        )
                        Tab.EVENTS -> EventsScreen()
                        Tab.SETTINGS -> SettingsScreen(serviceManager = serviceManager)
                        Tab.ACCOUNT -> AccountScreen(authService = authService)
                    }
                }
            }
        }
    }
}
