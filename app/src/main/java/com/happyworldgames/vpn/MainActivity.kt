package com.happyworldgames.vpn

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.happyworldgames.vpn.ui.theme.HWGVPNTheme
import java.util.stream.Collectors

class MainActivity : ComponentActivity() {
    interface Prefs {
        companion object {
            const val NAME = "connection"
            const val SERVER_ADDRESS = "server.address"
            const  val SERVER_PORT = "server.port"
            const val SHARED_SECRET = "shared.secret"
            const  val PROXY_HOSTNAME = "proxy_host"
            const  val PROXY_PORT = "proxy_port"
            const  val ALLOW = "allow"
            const  val PACKAGES = "packages"
        }
    }

    private var startServiceResult = registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            startService(getServiceIntent().setAction(HwgVpnService.ACTION_CONNECT))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HWGVPNTheme {
                Text(
                    text = "Loading...",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        val prefs: SharedPreferences = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)

        val mainViewModelFactory = MainViewModelFactory(
            serverAddress = prefs.getString(Prefs.SERVER_ADDRESS, "")?: "",
            serverPort = prefs.getInt(Prefs.SERVER_PORT, 0),
            sharedSecret = prefs.getString(Prefs.SHARED_SECRET, "")?: "",
            proxyHost = prefs.getString(Prefs.PROXY_HOSTNAME, "")?: "",
            proxyPort = prefs.getInt(Prefs.PROXY_PORT, 0),
            allowed = prefs.getBoolean(Prefs.ALLOW, true),
            packages = prefs.getStringSet(Prefs.PACKAGES, mutableSetOf()) as MutableSet<String>
        )
        val mainViewModel = ViewModelProvider(
            owner = this,
            factory = mainViewModelFactory
        )[MainViewModel::class.java]

        mainViewModel.onConnectClick = fun() {
            if (!checkProxyConfigs(mainViewModel.proxyHost.value,
                    mainViewModel.proxyPort.value)) {
                return
            }

            val packageSet: Set<String> =
                mainViewModel.packages.value
                    .map(String::trim)
                    .filter{s -> s.isNotEmpty() }.toSet()
            if (!checkPackages(packageSet)) {
                return
            }
            val serverPortNum: Int = try {
                mainViewModel.serverPort.value.toInt()
            } catch (e: NumberFormatException) {
                0
            }
            val proxyPortNum: Int = try {
                mainViewModel.proxyPort.value.toInt()
            } catch (e: NumberFormatException) {
                0
            }
            prefs.edit()
                .putString(Prefs.SERVER_ADDRESS, mainViewModel.serverAddress.value)
                .putInt(Prefs.SERVER_PORT, serverPortNum)
                .putString(Prefs.SHARED_SECRET, mainViewModel.sharedSecret.value)
                .putString(Prefs.PROXY_HOSTNAME, mainViewModel.proxyHost.value)
                .putInt(Prefs.PROXY_PORT, proxyPortNum)
                .putBoolean(Prefs.ALLOW, mainViewModel.allowed.value)
                .putStringSet(Prefs.PACKAGES, packageSet)
                .apply()
            val intent: Intent? = VpnService.prepare(this)
            if (intent != null) {
                startServiceResult.launch(intent)
            } else {
                startService(getServiceIntent().setAction(HwgVpnService.ACTION_CONNECT))
            }
        }
        mainViewModel.onDisconnectClick = fun() {
            startService(getServiceIntent().setAction(HwgVpnService.ACTION_DISCONNECT))
        }

        setContent {
            HWGVPNTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        mainViewModel = mainViewModel
                    )
                }
            }
        }
    }

    private fun checkProxyConfigs(proxyHost: String, proxyPort: String): Boolean {
        val hasIncompleteProxyConfigs = proxyHost.isEmpty() != proxyPort.isEmpty()
        if (hasIncompleteProxyConfigs) {
            Toast.makeText(this, R.string.incomplete_proxy_settings, Toast.LENGTH_SHORT).show()
        }
        return !hasIncompleteProxyConfigs
    }

    @SuppressLint("QueryPermissionsNeeded")
    private fun checkPackages(packageNames: Set<String>): Boolean {
        val hasCorrectPackageNames = packageNames.isEmpty() ||
                packageManager.getInstalledPackages(0).stream().map { pi -> pi.packageName }
                    .collect(Collectors.toSet()).containsAll(packageNames)
        if (!hasCorrectPackageNames) {
            Toast.makeText(this, R.string.unknown_package_names, Toast.LENGTH_SHORT).show()
        }
        return hasCorrectPackageNames
    }

    private fun getServiceIntent(): Intent {
        return Intent(this, HwgVpnService::class.java)
    }
}

class MainViewModel(
    serverAddress: String,
    serverPort: Int,
    sharedSecret: String,
    proxyHost: String,
    proxyPort: Int,
    allowed: Boolean,
    packages: MutableSet<String>
): ViewModel() {
    val serverAddress = mutableStateOf(serverAddress)
    val serverPort = mutableStateOf(serverPort.toString())
    val sharedSecret = mutableStateOf(sharedSecret)
    val proxyHost = mutableStateOf(proxyHost)
    val proxyPort = mutableStateOf(proxyPort.toString())
    val allowed = mutableStateOf(allowed)
    @SuppressLint("MutableCollectionMutableState")
    val packages = mutableStateOf(packages)

    var onConnectClick: (() -> Unit)? = null
    var onDisconnectClick: (() -> Unit)? = null
}

class MainViewModelFactory(
    val serverAddress: String,
    val serverPort: Int,
    val sharedSecret: String,
    val proxyHost: String,
    val proxyPort: Int,
    val allowed: Boolean,
    val packages: MutableSet<String>
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(serverAddress, serverPort, sharedSecret, proxyHost, proxyPort,
                allowed, packages) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

@Preview(showBackground = true)
@Composable
fun MainPreview() {
    HWGVPNTheme {
        MainScreen(
            mainViewModel = MainViewModel(
                serverAddress = "127.0.0.1",
                serverPort = 0,
                sharedSecret = "Secret",
                proxyHost = "127.0.0.1",
                proxyPort = 0,
                allowed = true,
                packages = mutableSetOf("Test1", "Test2")
            )
        )
    }
}

@Composable
fun MainScreen(modifier: Modifier = Modifier, mainViewModel: MainViewModel) {
    val repeatList: List<Pair<MutableState<String>, String>> = listOf(
        Pair(mainViewModel.serverAddress, "Server Address"),
        Pair(mainViewModel.serverPort, "Server Port"),

        Pair(mainViewModel.sharedSecret, "Shared Secret"),

        Pair(mainViewModel.proxyHost, "Proxy Host"),
        Pair(mainViewModel.proxyPort, "Proxy Port"),
    )
    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        repeat(5) { times ->
            InputField(repeatList[times].first, repeatList[times].second)
        }
        PackagesList(
            state = mainViewModel.allowed,
            packages = mainViewModel.packages
        )
        ConnectDisconnectButtons(
            onConnectClick = mainViewModel.onConnectClick?: {},
            onDisconnectClick = mainViewModel.onDisconnectClick?: {}
        )
    }
}

@Composable
fun InputField(
    field: MutableState<String> = mutableStateOf(""),
    placeHolderText: String = "Test"
) {
    OutlinedTextField(
        value = field.value,
        onValueChange = { field.value = it },
        placeholder = { Text(text = placeHolderText, color = Color.Gray) },
        modifier = Modifier.padding(2.dp)
    )
}

@Composable
fun PackagesList(
    state: MutableState<Boolean>,
    packages: MutableState<MutableSet<String>>
) {
    Column {
        Text(text = "Packages", modifier = Modifier.padding(10.dp))
        Column(Modifier.selectableGroup()) {
            Row{
                RadioButton(
                    selected = state.value,
                    onClick = { state.value = true }
                )
                Text("Allowed")
            }
            Row{
                RadioButton(
                    selected = !state.value,
                    onClick = { state.value = false }
                )
                Text("Disallowed")
            }
        }

        TextField(
            value = packages.value.joinToString { ", " },
            onValueChange = { packages.value = it.split(", ").toMutableSet() }
        )
    }
}

@Composable
fun ConnectDisconnectButtons(
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit
)  {
    Column(modifier = Modifier.padding(2.dp)) {
        Button(
            onClick = onConnectClick
        ) { Text(text = "Connect") }
        Button(
            onClick = onDisconnectClick
        ) { Text(text = "Disconnect") }
    }
}