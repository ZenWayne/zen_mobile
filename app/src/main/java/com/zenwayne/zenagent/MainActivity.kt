package com.zenwayne.zenagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.zenwayne.zenagent.ui.AppRoot
import com.zenwayne.zenagent.ui.theme.ZenAgentTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Dev flow: open the external app dirs so `adb push` can stage the
        // .litertlm model over USB. getExternalFilesDir defaults to mode 0700
        // (adb shell's ext_data_rw group locked out after pm clear).
        runCatching {
            for (sub in listOf("", "models")) {
                val d = applicationContext.getExternalFilesDir(sub)
                if (d != null) {
                    Runtime.getRuntime().exec(
                        arrayOf("chmod", "0771", d.absolutePath),
                    )
                }
            }
        }
        setContent {
            ZenAgentTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot()
                }
            }
        }
    }
}
