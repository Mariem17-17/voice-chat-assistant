package com.example.ai_voice_assistant.ui.navigation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.ai_voice_assistant.ui.theme.*

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object Assistant : BottomNavItem("assistant", "Assistant", Icons.Default.Assistant)
    object History : BottomNavItem("history", "History", Icons.Default.History)
}

@Composable
fun BottomNavigationBar(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar(
        modifier = modifier.fillMaxWidth(),
        containerColor = GlassSurface.copy(alpha = 0.1f), // Very subtle glass background
        tonalElevation = 0.dp
    ) {
        listOf(
            BottomNavItem.Assistant,
            BottomNavItem.History
        ).forEach { item ->
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title,
                        tint = if (currentRoute == item.route) {
                            TextPrimary
                        } else {
                            TextSecondary
                        }
                    )
                },
                label = {
                    Text(
                        text = item.title,
                        color = if (currentRoute == item.route) {
                            TextPrimary
                        } else {
                            TextSecondary
                        }
                    )
                },
                selected = currentRoute == item.route,
                onClick = {
                    if (currentRoute != item.route) {
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.startDestinationId)
                            launchSingleTop = true
                        }
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = TextPrimary,
                    selectedTextColor = TextPrimary,
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary,
                    indicatorColor = GlassSurface.copy(alpha = 0.2f)
                )
            )
        }
    }
}
