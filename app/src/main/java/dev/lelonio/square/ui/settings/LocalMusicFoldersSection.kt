package dev.lelonio.square.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.lelonio.square.R
import dev.lelonio.square.SquareApplication
import dev.lelonio.square.data.local.*
import dev.lelonio.square.ui.components.FilterChip
import dev.lelonio.square.ui.theme.InkDim
import kotlinx.coroutines.launch

@Composable
internal fun LocalMusicFoldersSection() {
    val context = LocalContext.current
    val repository = remember(context) { (context.applicationContext as SquareApplication).localMusic }
    val state by repository.state.collectAsState()
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf(false) }
    var needFolder by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) scope.launch {
            try { repository.addFolder(uri); error = false; needFolder = false }
            catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; error = true }
        }
    }
    // Revalidate grants after returning from the system picker or permission settings.
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { repository.start() }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.local_folders_settings), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.local_folders_note), color = InkDim, style = MaterialTheme.typography.bodySmall)
        for (mode in LocalFolderMode.entries) FilterChip(stringResource(if (mode == LocalFolderMode.ALL) R.string.local_mode_all else R.string.local_mode_selected),
            state.catalog.snapshot.folderMode == mode, onClick = { scope.launch {
                try { needFolder = !repository.folderMode(mode) } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; error = true }
            } })
        if (needFolder) Text(stringResource(R.string.local_need_folder), color = InkDim)
        if (state.folderFallback) Text(stringResource(R.string.local_folder_fallback), color = InkDim)
        for (folder in state.catalog.snapshot.folders) Column {
            Text(folder.name, style = MaterialTheme.typography.titleMedium)
            Text(folder.uri, color = InkDim, style = MaterialTheme.typography.bodySmall)
            Text(stringResource(if (folder.accessible) R.string.local_folder_granted else R.string.local_folder_unavailable), color = InkDim)
            FilterChip(stringResource(R.string.local_remove_folder), false, onClick = { scope.launch {
                try { repository.removeFolder(folder.uri) } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; error = true }
            } })
        }
        FilterChip(stringResource(R.string.local_add_folder), false, onClick = { picker.launch(null) })
        FilterChip(stringResource(R.string.local_rescan), false, onClick = { repository.rescan() })
        Text(stringResource(R.string.local_saf_restrictions), color = InkDim, style = MaterialTheme.typography.bodySmall)
        if (error || state.error) Text(stringResource(R.string.local_operation_failed), color = InkDim)
        if (state.scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}
