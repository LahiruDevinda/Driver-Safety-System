package com.devinda.driverSafetySystem.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devinda.driverSafetySystem.model.Command
import com.devinda.driverSafetySystem.ui.TelemetryViewModel

@Composable
fun TelemetryScreen(
    viewModel: TelemetryViewModel = viewModel()
) {
    val status by viewModel.statusMessage.collectAsState()

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
            Button(onClick = { viewModel.switchTransport(viewModel.bluetoothModel) }) {
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