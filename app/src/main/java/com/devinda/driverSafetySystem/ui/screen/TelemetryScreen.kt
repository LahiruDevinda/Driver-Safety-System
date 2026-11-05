package com.devinda.driverSafetySystem.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devinda.driverSafetySystem.model.Command
import com.devinda.driverSafetySystem.ui.TelemetryViewModel

@Composable
fun TelemetryScreen(
    viewModel: TelemetryViewModel = viewModel()
) {
    val context = LocalContext.current
    val status by viewModel.statusMessage.collectAsState()

    // Determine required permissions depending on Android version
    val requiredPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        val allGranted = permissionsMap.values.all { it }
        if (allGranted) {
            viewModel.switchTransport(viewModel.bluetoothModel)
        } else {
            Toast.makeText(
                context,
                "Bluetooth and Location permissions are required to connect",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = status, style = MaterialTheme.typography.titleMedium)

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = {
                    val allGranted = requiredPermissions.all { perm ->
                        ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
                    }

                    if (allGranted) {
                        viewModel.switchTransport(viewModel.bluetoothModel)
                    } else {
                        permissionLauncher.launch(requiredPermissions)
                    }
                }
            ) {
                Text("Bluetooth")
            }

            Button(onClick = { viewModel.switchTransport(viewModel.wifiModel) }) {
                Text("Wi-Fi")
            }

            Button(onClick = { viewModel.switchTransport(viewModel.usbModel) }) {
                Text("USB")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { viewModel.sendManualCommand(Command.VIBRATE_MOTOR) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Vibrate Motor")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { viewModel.sendManualCommand(Command.ACCELERATE_ENABLE) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Enable Accelerate")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { viewModel.sendManualCommand(Command.BRAKE_ENABLE) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Enable Brake")
        }
    }
}