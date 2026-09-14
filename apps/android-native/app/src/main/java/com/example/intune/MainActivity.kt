package com.example.intune

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.rememberCoroutineScope
import com.example.intune.data.GoalsRepository
import com.example.intune.data.createNudgeApi
import com.example.intune.ui.GoalAwareApp
import com.example.intune.ui.theme.IntuneTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IntuneTheme {
                GoalAwareApp(GoalsRepository(applicationContext), createNudgeApi(), rememberCoroutineScope())
            }
        }
    }
}
