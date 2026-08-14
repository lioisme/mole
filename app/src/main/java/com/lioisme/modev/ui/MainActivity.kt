package com.lioisme.modev.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lioisme.modev.ui.theme.ModevTheme

/**
 * 宿主 App 唯一 Activity:承载底部导航与各页面。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ModevTheme {
                ModevNav()
            }
        }
    }
}