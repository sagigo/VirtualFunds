package com.virtualfunds.ui.export

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import com.virtualfunds.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One-shot event a screen's ViewModel emits when the user asks to export CSV. Built in the VM
 * (which holds the data); the file destination + writing happen in the UI via [CsvExportEffect].
 */
sealed interface CsvExportEvent {
    /** CSV text is ready — open the system save picker and write [content] to the chosen file. */
    data class Ready(val content: String) : CsvExportEvent

    /** Nothing matched the current view — show a "no data" message instead of writing an empty file. */
    data object NoData : CsvExportEvent
}

private val FILE_NAME_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT)

private val ILLEGAL_FILENAME_CHARS = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|').toSet()

/** UTF-8 byte-order mark (EF BB BF). Excel looks for it to open a UTF-8 CSV with the right encoding. */
private val UTF8_BOM_BYTES = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

/**
 * Builds a suggested file name like `"היסטוריה_פנסיה_20260628.csv"`, mirroring the desktop's
 * SaveFileDialog default (`{prefix}_{portfolioName}_{yyyyMMdd}`). Characters illegal in file names
 * are replaced with '_' so the system picker accepts the suggestion.
 */
fun buildCsvFileName(prefix: String, portfolioName: String): String {
    val safeName = buildString {
        for (c in portfolioName) append(if (c in ILLEGAL_FILENAME_CHARS || c.isISOControl()) '_' else c)
    }.trim()
    return "${prefix}_${safeName}_${LocalDate.now().format(FILE_NAME_DATE)}.csv"
}

/**
 * Wires a CSV-export event flow to Android's Storage Access Framework (SAF).
 *
 * On [CsvExportEvent.Ready] it opens the system "create document" picker (suggested name from
 * [fileName]); when the user picks a destination it writes the CSV — UTF-8 **with a BOM**, for
 * Excel/Hebrew compatibility, matching the desktop writer — and reports success/failure via
 * [snackbarHostState]. [CsvExportEvent.NoData] shows a "nothing to export" message.
 *
 * SAF note (for the developer, new to Android): `ActivityResultContracts.CreateDocument` launches
 * the OS file picker. Because the *user* chooses the location, the app needs no storage permission
 * and no `FileProvider`. The picker hands back a `content://` Uri we write through the
 * `ContentResolver`. `rememberLauncherForActivityResult` is the Compose way to register for that
 * result without manually overriding `Activity.onActivityResult`.
 */
@Composable
fun CsvExportEffect(
    events: SharedFlow<CsvExportEvent>,
    fileName: () -> String,
    snackbarHostState: SnackbarHostState,
) {
    // context is for ContentResolver I/O; resources is for string lookups inside coroutines
    // (LocalResources, unlike LocalContext, recomposes if the configuration changes).
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    // Bridges "user tapped export" (we have the content) and "user picked a destination" (we get
    // the Uri): the content is stashed here until the picker returns, then cleared.
    var pendingContent by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri: Uri? ->
        val content = pendingContent
        pendingContent = null
        if (uri == null || content == null) return@rememberLauncherForActivityResult // user cancelled
        scope.launch {
            val ok = withContext(Dispatchers.IO) { writeCsvToUri(context, uri, content) }
            snackbarHostState.showSnackbar(
                resources.getString(if (ok) R.string.export_success else R.string.export_failure),
            )
        }
    }

    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is CsvExportEvent.Ready -> {
                    pendingContent = event.content
                    launcher.launch(fileName())
                }
                CsvExportEvent.NoData ->
                    snackbarHostState.showSnackbar(resources.getString(R.string.export_no_data))
            }
        }
    }
}

/**
 * Writes [content] to [uri] as UTF-8 with a leading BOM so Excel opens Hebrew CSVs with the
 * correct encoding (mirrors the desktop's UTF8Encoding-with-BOM writer). Returns true on success.
 * "wt" truncates any existing content if the user picked an existing file to overwrite.
 */
private fun writeCsvToUri(context: Context, uri: Uri, content: String): Boolean = try {
    context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
        out.write(UTF8_BOM_BYTES)
        out.write(content.toByteArray(Charsets.UTF_8))
    } != null
} catch (e: Exception) {
    false
}
