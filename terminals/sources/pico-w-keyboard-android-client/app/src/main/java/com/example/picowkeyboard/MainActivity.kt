package com.example.picowkeyboard

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.picowkeyboard.ui.MainScreen
import com.example.picowkeyboard.ui.theme.PicoWKeyboardTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by lazy { MainViewModel(application) }

    /** Runtime permissions needed on Android 12+ (BLUETOOTH_SCAN / BLUETOOTH_CONNECT). */
    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val scanGranted = result[Manifest.permission.BLUETOOTH_SCAN] ?: true
        val connectGranted = result[Manifest.permission.BLUETOOTH_CONNECT] ?: true
        if (!(scanGranted && connectGranted)) {
            Toast.makeText(this, "蓝牙权限被拒绝，无法扫描或连接设备", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestBluetoothPermissionsIfNeeded()

        setContent {
            PicoWKeyboardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        viewModel = viewModel,
                        onEnsureBluetoothEnabled = ::ensureBluetoothEnabled
                    )
                }

                DisposableEffect(Unit) {
                    onDispose {
                        viewModel.stopScan()
                    }
                }
            }
        }
    }

    private fun requestBluetoothPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scanGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
            val connectGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

            if (!(scanGranted && connectGranted)) {
                bluetoothPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT,
                    )
                )
            }
        }
    }

    private fun ensureBluetoothEnabled() {
        val manager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val adapter: BluetoothAdapter? = manager.adapter
        if (adapter == null || !adapter.isEnabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // On Android 12+ the system prompt is used to enable Bluetooth.
                startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            } else {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivity(enableBtIntent)
            }
        }
    }
}
