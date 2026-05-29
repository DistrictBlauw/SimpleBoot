package redhead.app.simpleboot

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.topjohnwu.superuser.Shell
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch
import redhead.app.simpleboot.model.IsoFile
import redhead.app.simpleboot.ui.theme.SimpleBootTheme
import redhead.app.simpleboot.util.*

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        LogManager.logToFile(this, "MainActivity.onCreate() - App launched")

        // Flexible root detection: prefer libsu but fall back to probing common su binaries
        if (!hasRootAccess()) {
            Toast.makeText(this, "Root access is required!", Toast.LENGTH_LONG).show()
            LogManager.logToFile(this, "Root access missing - closing app")
            finish()
        }

        setContent {
            SimpleBootTheme(darkTheme = isSystemInDarkTheme()) {
                LogManager.logToFile(this, "Compose UI started")
                AppScreen()
            }
        }
    }
}

/**
 * Attempts to detect and request root access from available su implementations.
 * Uses libsu/Shell first, then probes by executing `id` through known binaries
 * such as `ksu` (KernelSU) and `su` to trigger their grant dialogs.
 */
fun ComponentActivity.hasRootAccess(timeoutMs: Long = 2000): Boolean {
    // First, check libsu's idea of root
    try {
        if (Shell.getShell().isRoot) return true
        val r = Shell.cmd("id").exec()
        val out = (r.out + r.err).joinToString("\n")
        if (out.contains("uid=0")) return true
    } catch (e: Exception) {
        LogManager.logToFile(this, "hasRootAccess: libsu id check failed: ${e.message}")
    }

    // Try common su binaries that may be provided by KernelSU or other implementations.
    val bins = listOf("ksu", "su")
    for (bin in bins) {
        try {
            LogManager.logToFile(this, "hasRootAccess: attempting $bin to request permission")
            val proc = Runtime.getRuntime().exec(arrayOf(bin, "-c", "id"))
            if (!proc.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                proc.destroy()
                LogManager.logToFile(this, "hasRootAccess: $bin timed out")
                continue
            }
            val stdout = BufferedReader(InputStreamReader(proc.inputStream)).use { it.readText() }
            val stderr = BufferedReader(InputStreamReader(proc.errorStream)).use { it.readText() }
            val combined = (stdout + "\n" + stderr)
            LogManager.logToFile(this, "hasRootAccess: $bin output: ${combined.take(1000)}")
            if (combined.contains("uid=0")) return true
        } catch (e: Exception) {
            LogManager.logToFile(this, "hasRootAccess: $bin execution failed: ${e.message}")
        }
    }

    return false
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isoList by remember { mutableStateOf<List<IsoFile>>(emptyList()) }
    var currentMount by remember { mutableStateOf<MountStateStore.MountInfo?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    var statusText by remember { mutableStateOf("Status: Idle") }
    var showMountMenu by remember { mutableStateOf(false) }
    var selectedIso by remember { mutableStateOf<IsoFile?>(null) }
    var selectedMethod by remember { mutableStateOf(MountMethod.CONFIGFS) }
    var selectedUsbMode by remember { mutableStateOf(UsbMode.USB_HDD) }
    var showCredits by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        LogManager.logToFile(context, "AppScreen launched - checking storage access")

        if (!Environment.isExternalStorageManager()) {
            LogManager.logToFile(context, "External storage permission missing - launching settings")
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = "package:${context.packageName}".toUri()
            }
            context.startActivity(intent)
        } else {
            LogManager.logToFile(context, "External storage permission granted - loading ISOs")
            StorageManager.ensureDirectories()
            isoList = StorageManager.getIsoFileList()
            LogManager.logToFile(context, "Loaded ${isoList.size} ISO/IMG files")
            currentMount = MountStateStore.load(context)
            LogManager.logToFile(context, "Current mount: ${currentMount?.filePath ?: "None"}")
        }
    }

    fun handleMount(iso: IsoFile, method: MountMethod, mode: UsbMode) {
        coroutineScope.launch {
            LogManager.logToFile(context, "handleMount() - ISO=${iso.name}, method=$method, mode=$mode")

            val isMounted = currentMount?.filePath == iso.path
            if (isMounted) {
                LogManager.logToFile(context, "Unmounting existing ISO: ${iso.name}")
                val result = MountController.unmount(context)
                if (result.success) {
                    currentMount = null
                    statusText = "Unmounted: ${iso.name}"
                    snackbarHostState.showSnackbar(statusText)
                    isoList = StorageManager.getIsoFileList()
                    LogManager.logToFile(context, "Successfully unmounted ${iso.name}")
                } else {
                    val message = result.message.ifBlank { "Unknown error occurred." }
                    snackbarHostState.showSnackbar("Failed to unmount: $message")
                    LogManager.logToFile(context, "Unmount failed for ${iso.name}: $message")
                }
                return@launch
            }

            if (currentMount != null) {
                snackbarHostState.showSnackbar("Another ISO is already mounted.")
                LogManager.logToFile(context, "Mount blocked - another ISO already mounted.")
                return@launch
            }

            val result = MountController.mount(context, iso.path, method, mode)
            if (result.success) {
                currentMount = MountStateStore.load(context)
                statusText = "Mounted (${method.name}, ${mode.name}): ${iso.name}"
                snackbarHostState.showSnackbar(statusText)
                isoList = StorageManager.getIsoFileList()
                LogManager.logToFile(context, "Mount successful -> ${iso.name} (${method.name}/${mode.name})")
            } else {
                val message = result.message.ifBlank { "Unknown error occurred." }
                snackbarHostState.showSnackbar("Failed to mount: $message")
                LogManager.logToFile(context, "Mount failed for ${iso.name}: $message")
            }
        }
    }

    if (showCredits) {
        CreditsScreen(onBack = { showCredits = false })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SimpleBoot - v2.1") },
                actions = {
                    var menuExpanded by remember { mutableStateOf(false) }

                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            painter = painterResource(id = android.R.drawable.ic_menu_more),
                            contentDescription = "More options"
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Credits") },
                            onClick = {
                                menuExpanded = false
                                showCredits = true
                            }
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SimpleBoot v2.1",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        LogManager.logToFile(context, "Export log button clicked")
                        val intent = LogManager.exportLogFile(context)
                        if (intent != null) {
                            context.startActivity(Intent.createChooser(intent, "Share SimpleBoot Log"))
                            LogManager.logToFile(context, "Log export intent launched")
                        } else {
                            Toast.makeText(context, "No log file available", Toast.LENGTH_SHORT).show()
                            LogManager.logToFile(context, "No log file available to export")
                        }
                    }
                ) { Text("Export Log") }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                var methodExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = methodExpanded,
                    onExpandedChange = { methodExpanded = !methodExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedMethod.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Mount Method") },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                            .weight(1f)
                    )
                    ExposedDropdownMenu(
                        expanded = methodExpanded,
                        onDismissRequest = { methodExpanded = false }
                    ) {
                        MountMethod.entries.forEach { m: MountMethod ->
                            DropdownMenuItem(
                                text = { Text(m.name) },
                                onClick = {
                                    selectedMethod = m
                                    methodExpanded = false
                                    LogManager.logToFile(context, "Mount method selected: $m")
                                }
                            )
                        }
                    }
                }

                var usbExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = usbExpanded,
                    onExpandedChange = { usbExpanded = !usbExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedUsbMode.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("USB Method") },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                            .weight(1f)
                    )
                    ExposedDropdownMenu(
                        expanded = usbExpanded,
                        onDismissRequest = { usbExpanded = false }
                    ) {
                        UsbMode.entries.forEach { um: UsbMode ->
                            DropdownMenuItem(
                                text = { Text(um.name) },
                                onClick = {
                                    selectedUsbMode = um
                                    usbExpanded = false
                                    LogManager.logToFile(context, "USB mode selected: $um")
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(isoList) { iso ->
                    val isMounted = currentMount?.filePath == iso.path
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(12.dp)
                                .clickable {
                                    selectedIso = iso
                                    showMountMenu = true
                                    LogManager.logToFile(context, "ISO clicked: ${iso.name}")
                                }
                        ) {
                            Text(text = iso.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = if (isMounted) "Mounted" else "Not mounted",
                                color = if (isMounted) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                if (isoList.isEmpty()) {
                    item {
                        Text(
                            text = "No ISO/IMG files found in /storage/emulated/0/SimpleBootISOs.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                        LogManager.logToFile(context, "No ISO/IMG files found on device")
                    }
                }
            }
        }
    }

    if (showMountMenu && selectedIso != null) {
        AlertDialog(
            onDismissRequest = { showMountMenu = false },
            title = { Text("Mount Options") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("File: ${selectedIso?.name}")
                    Text("Method: ${selectedMethod.name}")
                    Text("USB Method: ${selectedUsbMode.name}")
                }
            },
            confirmButton = {
                Button(onClick = {
                    LogManager.logToFile(context, "Mount confirmed for ${selectedIso?.name}")
                    handleMount(selectedIso!!, selectedMethod, selectedUsbMode)
                    showMountMenu = false
                }) { Text("Mount") }
            },
            dismissButton = {
                Button(onClick = {
                    LogManager.logToFile(context, "Mount dialog cancelled")
                    showMountMenu = false
                }) { Text("Cancel") }
            }
        )
    }
}
