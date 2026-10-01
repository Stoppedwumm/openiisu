package org.openiisu.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.openiisu.core.ConfigLoader

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val consoles = ConfigLoader.parseCatalog(
            assets.open("emuladores_default.jsonc").bufferedReader().readText()
        ).consoles
        setContent {
            MaterialTheme {
                Column(Modifier.padding(16.dp)) {
                    Text("openiisu", style = MaterialTheme.typography.headlineMedium)
                    LazyColumn {
                        items(remember { consoles }) { Text("${it.longName} (${it.manufacturer})") }
                    }
                }
            }
        }
    }
}
