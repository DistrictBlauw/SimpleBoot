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
import androidx.compose.ui.res.stringResource
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
            Toast.makeText(this, getString(R.string.root_required), Toast.LENGTH_LONG).show()
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
    var statusText by remember { mutableStateOf("") }
    var showMountMenu by remember { mutableStateOf(false) }
    var selectedIso by remember { mutableStateOf<IsoFile?>(null) }
    var selectedMethod by remember { mutableStateOf(MountMethod.CONFIGFS) }
    var selectedUsbMode by remember { mutableStateOf(UsbMode.USB_HDD) }
    var showCredits by remember { mutableStateOf(false) }
    var showCreateBlank by remember { mutableStateOf(false) }

    val strStatusIdle = stringResource(R.string.status_idle)
    val strUnknownError = stringResource(R.string.unknown_error)
    val strAnotherMounted = stringResource(R.string.another_mounted)
    val strUnmountedMsg = stringResource(R.string.unmounted_msg)
    val strUnmountFailed = stringResource(R.string.unmount_failed)
    val strMountedStatus = stringResource(R.string.mounted_status)
    val strMountFailed = stringResource(R.string.mount_failed)
    val strShareLog = stringResource(R.string.share_log_title)
    val strNoLogFile = stringResource(R.string.no_log_file)
    val strFileLabel = stringResource(R.string.file_label)
    val strMethodLabel = stringResource(R.string.method_label)
    val strUsbMethodLabel = stringResource(R.string.usb_method_label)
    val strBlankCreated = stringResource(R.string.blank_iso_created)
    val strBlankFailed = stringResource(R.string.blank_iso_failed)
    val strBlankExists = stringResource(R.string.blank_iso_exists)
    val strBlankInvalidName = stringResource(R.string.blank_iso_invalid_name)

    LaunchedEffect(Unit) {
        if (statusText.isEmpty()) statusText = strStatusIdle
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
                    statusText = String.format(strUnmountedMsg, iso.name)
                    snackbarHostState.showSnackbar(statusText)
                    isoList = StorageManager.getIsoFileList()
                    LogManager.logToFile(context, "Successfully unmounted ${iso.name}")
                } else {
                    val message = result.message.ifBlank { strUnknownError }
                    snackbarHostState.showSnackbar(String.format(strUnmountFailed, message))
                    LogManager.logToFile(context, "Unmount failed for ${iso.name}: $message")
                }
                return@launch
            }

            if (currentMount != null) {
                snackbarHostState.showSnackbar(strAnotherMounted)
                LogManager.logToFile(context, "Mount blocked - another ISO already mounted.")
                return@launch
            }

            val result = MountController.mount(context, iso.path, method, mode)
            if (result.success) {
                currentMount = MountStateStore.load(context)
                statusText = String.format(strMountedStatus, method.name, mode.name, iso.name)
                snackbarHostState.showSnackbar(statusText)
                isoList = StorageManager.getIsoFileList()
                LogManager.logToFile(context, "Mount successful -> ${iso.name} (${method.name}/${mode.name})")
            } else {
                val message = result.message.ifBlank { strUnknownError }
                snackbarHostState.showSnackbar(String.format(strMountFailed, message))
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
                title = { Text(stringResource(R.string.app_version_label)) },
                actions = {
                    var menuExpanded by remember { mutableStateOf(false) }

                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            painter = painterResource(id = android.R.drawable.ic_menu_more),
                            contentDescription = stringResource(R.string.more_options)
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.create_blank_iso)) },
                            onClick = {
                                menuExpanded = false
                                showCreateBlank = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.credits)) },
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
                    text = stringResource(R.string.app_version_short),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        LogManager.logToFile(context, "Export log button clicked")
                        val intent = LogManager.exportLogFile(context)
                        if (intent != null) {
                            context.startActivity(Intent.createChooser(intent, strShareLog))
                            LogManager.logToFile(context, "Log export intent launched")
                        } else {
                            Toast.makeText(context, strNoLogFile, Toast.LENGTH_SHORT).show()
                            LogManager.logToFile(context, "No log file available to export")
                        }
                    }
                ) { Text(stringResource(R.string.export_log)) }
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
                    onExpandedChange = { methodExpanded = !methodExpanded },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = selectedMethod.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.mount_method)) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                            .fillMaxWidth()
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
                    onExpandedChange = { usbExpanded = !usbExpanded },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = selectedUsbMode.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.usb_method)) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                            .fillMaxWidth()
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
                                text = if (isMounted) stringResource(R.string.mounted) else stringResource(R.string.not_mounted),
                                color = if (isMounted) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                if (isoList.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.no_iso_found),
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
            title = { Text(stringResource(R.string.mount_options)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(String.format(strFileLabel, selectedIso?.name ?: ""))
                    Text(String.format(strMethodLabel, selectedMethod.name))
                    Text(String.format(strUsbMethodLabel, selectedUsbMode.name))
                }
            },
            confirmButton = {
                Button(onClick = {
                    LogManager.logToFile(context, "Mount confirmed for ${selectedIso?.name}")
                    handleMount(selectedIso!!, selectedMethod, selectedUsbMode)
                    showMountMenu = false
                }) { Text(stringResource(R.string.mount)) }
            },
            dismissButton = {
                Button(onClick = {
                    LogManager.logToFile(context, "Mount dialog cancelled")
                    showMountMenu = false
                }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showCreateBlank) {
        var blankName by remember { mutableStateOf("") }
        var blankSize by remember { mutableStateOf("64") }
        var blankExt by remember { mutableStateOf("iso") }

        AlertDialog(
            onDismissRequest = { showCreateBlank = false },
            title = { Text(stringResource(R.string.create_blank_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = blankName,
                        onValueChange = { blankName = it },
                        label = { Text(stringResource(R.string.file_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = blankSize,
                        onValueChange = { blankSize = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.file_size_mb)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("iso" to "ISO", "img" to "IMG").forEach { (ext, label) ->
                            FilterChip(
                                selected = blankExt == ext,
                                onClick = { blankExt = ext },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val sizeMB = blankSize.toIntOrNull() ?: 0
                    LogManager.logToFile(context, "Create blank image: name=$blankName, ext=$blankExt, size=${sizeMB}MB")
                    val result = StorageManager.createBlankImage(context, blankName, blankExt, sizeMB)
                    if (result != null) {
                        statusText = String.format(strBlankCreated, result.name)
                        snackbarHostState.showSnackbar(statusText)
                        isoList = StorageManager.getIsoFileList()
                        LogManager.logToFile(context, "Blank image created: ${result.name}")
                        showCreateBlank = false
                    } else if (blankName.isBlank()) {
                        snackbarHostState.showSnackbar(strBlankInvalidName)
                    } else {
                        snackbarHostState.showSnackbar(strBlankExists)
                    }
                }) { Text(stringResource(R.string.create)) }
            },
            dismissButton = {
                Button(onClick = { showCreateBlank = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
