package com.example

import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DesktopMac
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.ui.theme.MyApplicationTheme

/*
 * README:
 * This app requires Winlator installed separately. Winlator provides Wine + Box64
 * to run .exe files. Download Winlator from GitHub: https://github.com/brunodev85/winlator
 *
 * This app delegates the opening of .exe files to the Winlator application by sending
 * an Intent.ACTION_VIEW with the selected .exe file URI.
 */
class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Handle shortcut launches
    if (intent?.action == "com.example.LAUNCH_EXE") {
      val fileUri = intent.data
      if (fileUri != null) {
        val winlatorIntent = Intent(Intent.ACTION_VIEW).apply {
          setDataAndType(fileUri, "application/x-msdownload")
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          if (intent.getBooleanExtra("auto_optimize", false)) {
            putExtra("env_WINEDLLOVERRIDES", "dxgi=n,b")
            putExtra("cpu_cores", intent.getIntExtra("num_cores", 4))
            putExtra("dxvk_version", "2.2")
            putExtra("csmt", 1)
          }
          val res = intent.getStringExtra("resolution")
          if (res != null && res != "Default") {
            putExtra("resolution", res)
          }
          if (intent.getBooleanExtra("show_fps_hud", false)) {
            putExtra("env_DXVK_HUD", "fps")
          }
          val cmdArgs = intent.getStringExtra("cmd_args")
          if (!cmdArgs.isNullOrEmpty()) {
            putExtra("command_line", cmdArgs)
          }
        }
        try {
          startActivity(winlatorIntent)
        } catch (e: ActivityNotFoundException) {
          try {
            winlatorIntent.setDataAndType(fileUri, "*/*")
            startActivity(winlatorIntent)
          } catch (e2: Exception) {
            Toast.makeText(this, "Could not open file", Toast.LENGTH_SHORT).show()
          }
        }
        finish()
        return
      }
    }

    enableEdgeToEdge()
    setContent {
      var currentScreen by remember { mutableStateOf("launcher") }
      MyApplicationTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          if (currentScreen == "launcher") {
            ExeRunnerScreen(
              onNavigateToBrowser = { currentScreen = "browser" },
              modifier = Modifier.padding(innerPadding)
            )
          } else {
            PCBrowserScreen(
              onNavigateBack = { currentScreen = "launcher" },
              modifier = Modifier.padding(innerPadding)
            )
          }
        }
      }
    }
  }
}

@Composable
fun ExeRunnerScreen(onNavigateToBrowser: () -> Unit, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
  var showDownloadDialog by remember { mutableStateOf(false) }
  var autoOptimize by remember { mutableStateOf(true) }
  var selectedResolution by remember { mutableStateOf("Default") }
  var showResolutionDropdown by remember { mutableStateOf(false) }
  var commandLineArgs by remember { mutableStateOf("") }
  var showFpsHud by remember { mutableStateOf(false) }

  val memoryInfo = remember { ActivityManager.MemoryInfo().also {
      (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it)
  } }
  val totalRamGb = memoryInfo.totalMem / (1024 * 1024 * 1024.0)
  val numCores = Runtime.getRuntime().availableProcessors()

  val performanceProfile = remember(totalRamGb, numCores) {
      val ramFormatted = String.format("%.1f", totalRamGb)
      when {
          totalRamGb >= 7.5 && numCores >= 8 -> "High Performance ($numCores Cores, ${ramFormatted}GB RAM)"
          totalRamGb >= 5.5 && numCores >= 6 -> "Balanced ($numCores Cores, ${ramFormatted}GB RAM)"
          else -> "Efficiency ($numCores Cores, ${ramFormatted}GB RAM)"
      }
  }

  val isWinlatorInstalled = remember {
    try {
      context.packageManager.getPackageInfo("com.winlator", 0)
      true
    } catch (e: PackageManager.NameNotFoundException) {
      false
    }
  }

  val launcher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri: Uri? ->
    uri?.let {
      try {
        context.contentResolver.takePersistableUriPermission(
          it,
          Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
      } catch (e: SecurityException) {
        // Ignore if provider doesn't support persistable permissions
      }
      selectedFileUri = it
    }
  }

  if (showDownloadDialog) {
    AlertDialog(
      onDismissRequest = { showDownloadDialog = false },
      title = { Text("Winlator Not Found") },
      text = { Text("Winlator is required to run .exe files. Would you like to download it from GitHub?") },
      confirmButton = {
        TextButton(onClick = {
          showDownloadDialog = false
          val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/brunodev85/winlator/releases"))
          context.startActivity(intent)
        }) {
          Text("Download")
        }
      },
      dismissButton = {
        TextButton(onClick = { showDownloadDialog = false }) {
          Text("Cancel")
        }
      }
    )
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(Color(0xFFF3F0F5))
      .padding(horizontal = 16.dp)
  ) {
    // Top Bar
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { }) {
            Icon(Icons.Default.Menu, contentDescription = "Menu")
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "EXE Runner",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF1C1B1F),
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onNavigateToBrowser) {
            Icon(Icons.Default.Language, contentDescription = "PC Browser")
        }
    }

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Status Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White.copy(alpha = 0.4f))
                .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(28.dp))
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text(
                            text = "Environment Status",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF49454F),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (isWinlatorInstalled) Color(0xFF1D6D1F) else Color(0xFFB3261E))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isWinlatorInstalled) "Winlator Installed" else "Winlator Missing",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1D1B20)
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFE8DEF8))
                            .padding(12.dp)
                    ) {
                        Icon(
                            imageVector = if (isWinlatorInstalled) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = Color(0xFF21005D),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (isWinlatorInstalled) "Wine + Box64 bridge is active. System ready to receive instruction sets." else "Winlator is required to run .exe files.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF49454F),
                    lineHeight = 16.sp
                )
            }
        }

        // File Selector
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFFFEF7FF))
                .border(1.dp, Color(0xFFCAC4D0), RoundedCornerShape(28.dp))
                .clickable { launcher.launch(arrayOf("*/*")) }
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEADDFF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FileOpen,
                    contentDescription = null,
                    tint = Color(0xFF21005D),
                    modifier = Modifier.size(32.dp)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (selectedFileUri != null) selectedFileUri?.lastPathSegment ?: "File Selected" else "No file selected",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF1D1B20),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = if (selectedFileUri != null) "Ready to run" else "Select an executable (.exe) to start",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF49454F)
                )
            }
            Button(
                onClick = { launcher.launch(arrayOf("*/*")) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                shape = CircleShape
            ) {
                Text(
                    text = "PICK EXE FILE",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
        }

        // Performance Optimization Card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.5f))
                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Auto-Optimize Settings",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1D1B20)
                )
                Text(
                    text = if (autoOptimize) performanceProfile else "Manual container settings",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF49454F)
                )
            }
            Switch(
                checked = autoOptimize,
                onCheckedChange = { autoOptimize = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF6750A4)
                )
            )
        }

        // Resolution Selector Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.5f))
                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                .clickable { showResolutionDropdown = true }
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Screen Resolution",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1D1B20)
                    )
                    Text(
                        text = selectedResolution,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF49454F)
                    )
                }
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Select Resolution",
                    tint = Color(0xFF21005D)
                )
            }
            
            DropdownMenu(
                expanded = showResolutionDropdown,
                onDismissRequest = { showResolutionDropdown = false }
            ) {
                val resolutions = listOf("Default", "800x600", "1024x768", "1280x720")
                resolutions.forEach { res ->
                    DropdownMenuItem(
                        text = { Text(res) },
                        onClick = {
                            selectedResolution = res
                            showResolutionDropdown = false
                        }
                    )
                }
            }
        }

        // Advanced Settings Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.5f))
                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Show FPS Counter",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1D1B20)
                    )
                    Text(
                        text = "DXVK HUD performance overlay",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF49454F)
                    )
                }
                Switch(
                    checked = showFpsHud,
                    onCheckedChange = { showFpsHud = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF6750A4)
                    )
                )
            }

            OutlinedTextField(
                value = commandLineArgs,
                onValueChange = { commandLineArgs = it },
                label = { Text("Command Line Arguments") },
                placeholder = { Text("e.g. -windowed -opengl") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color(0xFFCAC4D0),
                    focusedBorderColor = Color(0xFF6750A4)
                )
            )
        }

        // Quick Actions
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Run Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.6f))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                    .clickable(enabled = selectedFileUri != null) {
                        if (!isWinlatorInstalled) {
                            showDownloadDialog = true
                            return@clickable
                        }
                        try {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(selectedFileUri, "application/x-msdownload")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                if (autoOptimize) {
                                    putExtra("env_WINEDLLOVERRIDES", "dxgi=n,b")
                                    putExtra("cpu_cores", numCores)
                                    putExtra("dxvk_version", "2.2")
                                    putExtra("csmt", 1) // Enable CSMT for better CPU usage
                                }
                                if (selectedResolution != "Default") {
                                    putExtra("resolution", selectedResolution)
                                }
                                if (showFpsHud) {
                                    putExtra("env_DXVK_HUD", "fps")
                                }
                                if (commandLineArgs.isNotEmpty()) {
                                    putExtra("command_line", commandLineArgs)
                                }
                            }
                            context.startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            try {
                                val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(selectedFileUri, "*/*")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    if (autoOptimize) {
                                        putExtra("env_WINEDLLOVERRIDES", "dxgi=n,b")
                                        putExtra("cpu_cores", numCores)
                                        putExtra("dxvk_version", "2.2")
                                        putExtra("csmt", 1)
                                    }
                                    if (selectedResolution != "Default") {
                                        putExtra("resolution", selectedResolution)
                                    }
                                    if (showFpsHud) {
                                        putExtra("env_DXVK_HUD", "fps")
                                    }
                                    if (commandLineArgs.isNotEmpty()) {
                                        putExtra("command_line", commandLineArgs)
                                    }
                                }
                                context.startActivity(fallbackIntent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not open file", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFE8DEF8)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color(0xFF21005D)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Run with Winlator",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedFileUri != null) Color(0xFF1D1B20) else Color(0xFF1D1B20).copy(alpha = 0.4f)
                    )
                    Text(
                        text = "Launch current selection in container",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF49454F)
                    )
                }
            }

            // Open Desktop Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.6f))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                    .clickable {
                        if (isWinlatorInstalled) {
                            val launchIntent = context.packageManager.getLaunchIntentForPackage("com.winlator")
                            if (launchIntent != null) {
                                context.startActivity(launchIntent)
                            } else {
                                Toast.makeText(context, "Winlator couldn't be launched", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            showDownloadDialog = true
                        }
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFAD8FD)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DesktopMac,
                        contentDescription = null,
                        tint = Color(0xFF31111D)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Open Winlator Desktop",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1D1B20)
                    )
                    Text(
                        text = "Access the virtual Windows desktop",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF49454F)
                    )
                }
            }

            // Add Shortcut Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.6f))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                    .clickable(enabled = selectedFileUri != null) {
                        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
                            Toast.makeText(context, "Shortcuts not supported on this launcher", Toast.LENGTH_SHORT).show()
                            return@clickable
                        }
                        
                        val uri = selectedFileUri ?: return@clickable
                        val fileName = uri.lastPathSegment ?: "EXE Launcher"
                        
                        val shortcutIntent = Intent(context, MainActivity::class.java).apply {
                            action = "com.example.LAUNCH_EXE"
                            data = uri
                            if (autoOptimize) {
                                putExtra("auto_optimize", true)
                                putExtra("num_cores", numCores)
                            }
                            if (selectedResolution != "Default") {
                                putExtra("resolution", selectedResolution)
                            }
                            if (showFpsHud) {
                                putExtra("show_fps_hud", true)
                            }
                            if (commandLineArgs.isNotEmpty()) {
                                putExtra("cmd_args", commandLineArgs)
                            }
                        }

                        val shortcutInfo = ShortcutInfoCompat.Builder(context, "winlator_shortcut_${System.currentTimeMillis()}")
                            .setShortLabel(fileName)
                            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                            .setIntent(shortcutIntent)
                            .build()

                        ShortcutManagerCompat.requestPinShortcut(context, shortcutInfo, null)
                        Toast.makeText(context, "Shortcut requested", Toast.LENGTH_SHORT).show()
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFEADDFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AddToHomeScreen,
                        contentDescription = null,
                        tint = Color(0xFF21005D)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Add to Home Screen",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedFileUri != null) Color(0xFF1D1B20) else Color(0xFF1D1B20).copy(alpha = 0.4f)
                    )
                    Text(
                        text = "Create a shortcut to launch this file directly",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF49454F)
                    )
                }
            }
        }
    }

    // Footer
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "EXE Runner requires Winlator (Wine+Box64) to be installed separately. Always ensure you are running trusted software.",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF49454F).copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .width(96.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(Color(0xFF1C1B1F).copy(alpha = 0.2f))
        )
    }
  }
}

@Composable
fun PCBrowserScreen(onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var urlInput by remember { mutableStateOf("https://www.google.com") }
    var webViewRef by remember { mutableStateOf<android.webkit.WebView?>(null) }

    Column(modifier = modifier.fillMaxSize().background(Color.White)) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Go
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onGo = {
                        var finalUrl = urlInput
                        if (!finalUrl.startsWith("http://") && !finalUrl.startsWith("https://")) {
                            finalUrl = "https://$finalUrl"
                        }
                        webViewRef?.loadUrl(finalUrl)
                    }
                ),
                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
            )
            IconButton(onClick = { webViewRef?.reload() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }

        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx ->
                android.webkit.WebView(ctx).apply {
                    webViewClient = object : android.webkit.WebViewClient() {
                        override fun doUpdateVisitedHistory(view: android.webkit.WebView?, url: String?, isReload: Boolean) {
                            if (url != null) {
                                urlInput = url
                            }
                            super.doUpdateVisitedHistory(view, url, isReload)
                        }
                    }
                    webChromeClient = android.webkit.WebChromeClient()
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // Spoof PC User Agent
                    settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true

                    setDownloadListener { dlUrl, userAgent, contentDisposition, mimetype, contentLength ->
                        val request = android.app.DownloadManager.Request(Uri.parse(dlUrl))
                        request.setMimeType(mimetype)
                        val cookies = android.webkit.CookieManager.getInstance().getCookie(dlUrl)
                        request.addRequestHeader("cookie", cookies)
                        request.addRequestHeader("User-Agent", userAgent)
                        request.setDescription("Downloading file...")
                        val fileName = android.webkit.URLUtil.guessFileName(dlUrl, contentDisposition, mimetype)
                        request.setTitle(fileName)
                        request.allowScanningByMediaScanner()
                        request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        request.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName)

                        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                        dm.enqueue(request)
                        Toast.makeText(ctx, "Downloading $fileName", Toast.LENGTH_LONG).show()
                    }
                    loadUrl("https://www.google.com")
                    webViewRef = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
