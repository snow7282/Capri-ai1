
package com.capri.ai

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.capri.llama.GgufHeaderValidator

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                CapriHome(
                    onValidate = { uri -> validateGguf(uri) }
                )
            }
        }
    }

    private fun validateGguf(uri: Uri): String {
        return try {
            contentResolver.openInputStream(uri)?.use { input ->
                val header = ByteArray(4)
                val read = input.read(header)
                if (read == 4 && GgufHeaderValidator.hasGgufMagic(header)) {
                    "GGUF file recognized. Import UI is working, but model inference is not connected yet."
                } else {
                    "This file does not appear to have a valid GGUF header."
                }
            } ?: "Could not open the selected file."
        } catch (e: Exception) {
            "Could not read file: ${e.message ?: "unknown error"}"
        }
    }
}

@Composable
private fun CapriHome(onValidate: (Uri) -> String) {
    var selectedName by remember {
        mutableStateOf("No model selected")
    }
    var status by remember {
        mutableStateOf("Choose a .gguf model file to check its format.")
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedName =
                uri.lastPathSegment?.substringAfterLast('/')
                    ?: "Selected GGUF file"
            status = onValidate(uri)
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Capri AI",
                style = MaterialTheme.typography.headlineLarge
            )

            Text(
                "Android starter build",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                "This starter app can select a GGUF file and validate its file signature. " +
                    "The actual llama.cpp inference engine still needs to be integrated."
            )

            Button(onClick = {
                picker.launch(arrayOf("*/*"))
            }) {
                Text("Import GGUF")
            }

            Text("Selected file", style = MaterialTheme.typography.titleSmall)
            Text(selectedName)

            HorizontalDivider()

            Text("Status", style = MaterialTheme.typography.titleSmall)
            Text(status)

            Text(
                "Build note: this APK is a project/build validation starter, not yet a full offline AI assistant.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
