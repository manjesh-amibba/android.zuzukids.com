package com.zuzukids.app

import android.app.Activity
import android.os.Build
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat

@Composable
fun SetSystemBarsColor() {
    val context = LocalContext.current

    if (context is Activity) {
        LaunchedEffect(Unit) {
            try {
                val window = context.window
                val useDarkIcons = true
                
        val statusBarColor = Color(android.graphics.Color.parseColor("#ffffff"))
        window.statusBarColor = statusBarColor.toArgb()
        WindowCompat.getInsetsController(window, window.decorView)?.isAppearanceLightStatusBars = useDarkIcons
    

                
        val navBarColor = Color(android.graphics.Color.parseColor("#000000"))
        window.navigationBarColor = navBarColor.toArgb()
        WindowCompat.getInsetsController(window, window.decorView)?.isAppearanceLightNavigationBars = useDarkIcons
    
                
            } catch (e: Exception) {
                Log.e("SystemBarsColor", "Failed to set system bar colors", e)
            }
        }
    } else {
        Log.w("SystemBarsColor", "Context is not an Activity. Skipping system bar color setup.")
    }
}
