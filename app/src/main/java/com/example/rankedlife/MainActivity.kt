package com.example.rankedlife

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {

    private lateinit var model: AppModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = AppModel(this)
        setContent {
            RankedTheme {
                AppRoot(model)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        model.sync()
        model.resumeTick = model.resumeTick + 1
    }
}
