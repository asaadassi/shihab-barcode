package dev.fabik.bluetoothhid

import android.app.Activity
import android.content.ClipData
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalResources
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fabik.bluetoothhid.bt.BluetoothController
import dev.fabik.bluetoothhid.utils.ConnectionMode
import dev.fabik.bluetoothhid.utils.ZXingAnalyzer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextFieldDefaults.indicatorLine
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.fabik.bluetoothhid.shihab.BarcodeDates
import dev.fabik.bluetoothhid.ui.ConfirmDialog
import dev.fabik.bluetoothhid.ui.FilterModal
import dev.fabik.bluetoothhid.ui.model.HistoryViewModel
import dev.fabik.bluetoothhid.ui.rememberDialogState
import dev.fabik.bluetoothhid.ui.tooltip
import dev.fabik.bluetoothhid.utils.ComposableLifecycle
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.getPreferenceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun History(onBack: () -> Unit, onClick: (HistoryViewModel.HistoryEntry) -> Unit) = with(viewModel<HistoryViewModel>()) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            HistoryTopBar(scrollBehavior) {
                onBack()
            }
        },
        floatingActionButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ImportSheet()
                ExportSheet()
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            HistoryContent(onClick)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryViewModel.HistoryContent(onClick: (HistoryViewModel.HistoryEntry) -> Unit) {
    BackHandler(enabled = isSelecting) {
        clearSelection()
    }

    val types = stringArrayResource(R.array.code_types_values)
    val copyValue = rememberCopyValue()

    LazyColumn(Modifier.fillMaxSize()) {
        items(filteredHistory, key = { it.hashCode() }) { barcode ->
            val isSelected by remember(barcode) { derivedStateOf { isItemSelected(barcode) } }
            ListItem(
                overlineContent = {
                    val timeString = remember { BarcodeDates.dateTime(barcode.timestamp) }
                    Text(timeString)
                },
                headlineContent = {
                    Text(barcode.value)
                },
                supportingContent = {
                    Text(types.getOrNull(barcode.format) ?: "UNKNOWN")
                },
                trailingContent = {
                    val sendAgain = stringResource(R.string.shihab_send_again)
                    IconButton(onClick = { onClick(barcode) }, Modifier.tooltip(sendAgain)) {
                        Icon(Icons.AutoMirrored.Filled.Send, sendAgain)
                    }
                },
                tonalElevation = if (isSelected) 8.0.dp else 0.0.dp,
                modifier = Modifier
                    .animateItem()
                    .combinedClickable(onLongClick = {
                        setItemSelected(barcode, true)
                    }, onClick = {
                        // While selecting, a tap only toggles the selection (handled below)
                        if (!isSelecting) copyValue(barcode.value)
                    })
                    .then(
                        if (isSelecting) {
                            Modifier.toggleable(value = isSelected, onValueChange = {
                                setItemSelected(barcode, it)
                            })
                        } else Modifier
                    )
            )
            HorizontalDivider()
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun HistoryViewModel.HistoryTopBar(
    scrollBehavior: TopAppBarScrollBehavior, onExit: () -> Unit
) {
    val clearHistoryDialog = rememberDialogState()

    // Close search on back button
    BackHandler(enabled = isSearching) {
        isSearching = false
        searchQuery = ""
    }

    TopAppBar(
        title = {
            when (isSearching) {
                true -> AppBarTextField(
                    value = searchQuery, onValueChange = {
                        searchQuery = it
                    }, hint = stringResource(R.string.search_by_value)
                )

                false -> Text(stringResource(R.string.history))
            }
        },
        navigationIcon = {
            IconButton(
                onClick = {
                    if (isSearching) {
                        isSearching = false
                        searchQuery = ""
                    } else {
                        onExit()
                    }
                }, Modifier.tooltip(stringResource(R.string.back))
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
        },
        actions = {
            IconButton(
                onClick = {
                    if (isSearching) searchQuery = ""
                    else isSearching = true
                }, Modifier.tooltip(stringResource(R.string.search))
            ) {
                Icon(
                    if (isSearching) Icons.Outlined.Close
                    else Icons.Default.Search, "Search"
                )
            }
            val sessionOnlyLabel = stringResource(R.string.shihab_session_only)
            IconButton(
                onClick = { sessionOnly = !sessionOnly },
                Modifier.tooltip(sessionOnlyLabel)
            ) {
                Icon(
                    Icons.Default.Timelapse, sessionOnlyLabel,
                    tint = if (sessionOnly) MaterialTheme.colorScheme.primary
                    else LocalContentColor.current
                )
            }
            FilterModal(filteredTypes.toSet(), filterDateStart, filterDateEnd) { sel, a, b ->
                filteredTypes.clear()
                filteredTypes.addAll(sel)
                filterDateStart = a
                filterDateEnd = b
            }
            IconButton(
                onClick = {
                    clearHistoryDialog.open()
                }, Modifier.tooltip(stringResource(R.string.clear_history))
            ) {
                Icon(Icons.Default.Delete, "Clear history")
            }
        },
        scrollBehavior = scrollBehavior
    )

    ConfirmDialog(
        dialogState = clearHistoryDialog,
        title = if (isSelecting) {
            stringResource(R.string.delete_selection)
        } else {
            stringResource(R.string.clear_history)
        },
        onConfirm = {
            if (isSelecting) {
                deleteSelectedItems()
            } else {
                HistoryViewModel.clearHistory()
            }
            close()
        }
    ) {
        Text(
            if (isSelecting) {
                stringResource(R.string.delete_selection_desc)
            } else {
                stringResource(R.string.clear_history_desc)
            }
        )
    }
}

// adapted from: https://stackoverflow.com/a/73665177/21418508
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBarTextField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val textStyle = LocalTextStyle.current
    // make sure there is no background color in the decoration box
    val colors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
    )

    // If color is not provided via the text style, use content color as a default
    val textColor = textStyle.color.takeOrElse {
        MaterialTheme.colorScheme.onSurface
    }
    val mergedTextStyle =
        textStyle.merge(TextStyle(color = textColor, lineHeight = 16.sp, fontSize = 16.sp))

    // request focus when this composable is first initialized
    val focusRequester = remember { FocusRequester() }
    SideEffect {
        focusRequester.requestFocus()
    }

    // set the correct cursor position when this composable is first initialized
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(value, TextRange(value.length)))
    }
    textFieldValue = textFieldValue.copy(text = value) // make sure to keep the value updated

    BasicTextField(value = textFieldValue,
        onValueChange = {
            textFieldValue = it
            // remove newlines to avoid strange layout issues, and also because singleLine=true
            onValueChange(it.text.replace("\n", ""))
        },
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .indicatorLine(
                enabled = true,
                isError = false,
                interactionSource = interactionSource,
                colors = colors
            )
            .focusRequester(focusRequester),
        textStyle = mergedTextStyle,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interactionSource,
        singleLine = true,
        decorationBox = { innerTextField ->
            // places text field with placeholder and appropriate bottom padding
            TextFieldDefaults.DecorationBox(
                value = value,
                visualTransformation = VisualTransformation.None,
                innerTextField = innerTextField,
                placeholder = { Text(text = hint) },
                singleLine = true,
                enabled = true,
                isError = false,
                interactionSource = interactionSource,
                colors = colors,
                contentPadding = PaddingValues(bottom = 4.dp)
            )
        })
}

@Composable
@ExperimentalMaterial3Api
fun HistoryViewModel.ExportSheet(trigger: (@Composable (open: () -> Unit) -> Unit)? = null) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val controller = LocalController.current

    val exportString = stringResource(R.string.export)
    val types = stringArrayResource(R.array.code_types_values)

    val scope = rememberCoroutineScope()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showSheet by rememberSaveable { mutableStateOf(false) }
    var exportData by rememberSaveable { mutableStateOf("") }

    // Typing on the computer needs a link: a Bluetooth device, the PC receiver, or External mode (plugins)
    val currentDevice by controller?.currentDevice?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(null) }
    val connectionMode by context.getPreferenceState(PreferenceStore.CONNECTION_MODE)
    val canType = currentDevice != null || connectionMode == ConnectionMode.EXTERNAL.ordinal ||
            connectionMode == ConnectionMode.PC.ordinal

    // Additional MIME aliases for CSV (in many providers)
    val CSV_MIME_CANDIDATES = arrayOf(
        "text/csv",
        "application/csv",
        "text/comma-separated-values",
        "application/vnd.ms-excel",
        "application/vnd.msexcel"
    )

    val open = { showSheet = true }
    if (trigger != null) {
        trigger(open)
    } else {
        ExtendedFloatingActionButton(
            text = {
                Text(
                    if (isSelecting) pluralStringResource(
                        R.plurals.export_items, selectionSize, selectionSize
                    ) else stringResource(R.string.export),
                    modifier = Modifier.animateContentSize(),
                )
            },
            icon = { Icon(Icons.Default.FileDownload, stringResource(R.string.export)) },
            onClick = open
        )
    }

    val startForResult =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val fileUri = result.data?.data
                fileUri?.let {
                    runCatching {
                        context.contentResolver.openOutputStream(it)?.use { outputStream ->
                            outputStream.bufferedWriter().use { writer ->
                                writer.write(exportData)
                            }
                        }
                    }.onFailure {
                        Log.e("History", "Error saving history to file!", it)
                    }
                }
            }
            exportData = ""
        }

    if (showSheet) {
        ModalBottomSheet(
            sheetState = state,
            onDismissRequest = { showSheet = false },
            content = {
                ExportSheetContent(
                    onSelect = { typ, dedup, saveToFile ->
                        scope.launch { state.hide() }.invokeOnCompletion { showSheet = state.isVisible }

                        // A byte order mark lets Excel on the computer read Arabic text in a CSV correctly
                        val data = exportHistory(typ, dedup, types)
                            .let { if (typ == HistoryViewModel.ExportType.CSV) "﻿$it" else it }

                        // (baseMime for picker, extension, extraMimeTypes [for CSV])
                        val params: Triple<String, String, Array<String>?> = when (typ) {
                            HistoryViewModel.ExportType.CSV   -> Triple("*/*",             "csv",  CSV_MIME_CANDIDATES)
                            HistoryViewModel.ExportType.JSON  -> Triple("application/json", "json", null)
                            HistoryViewModel.ExportType.XML   -> Triple("text/xml",         "xml",  null)
                            HistoryViewModel.ExportType.LINES -> Triple("text/plain",       "txt",  null)
                        }
                        val (baseMime, extension, extraMimes) = params
                        val fileName = "barcode-${EXPORT_STAMP.format(LocalDateTime.now())}.$extension"

                        runCatching {
                            if (saveToFile) {
                                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    type = baseMime
                                    putExtra(Intent.EXTRA_TITLE, fileName)
                                    extraMimes?.let { putExtra(Intent.EXTRA_MIME_TYPES, it) }
                                }
                                exportData = data
                                startForResult.launch(intent)
                            } else {
                                // Shared as a file, not as text, so it can reach a computer (Quick Share, email, WhatsApp)
                                val shareMime = when (typ) {
                                    HistoryViewModel.ExportType.CSV   -> "text/csv"
                                    HistoryViewModel.ExportType.JSON  -> "application/json"
                                    HistoryViewModel.ExportType.XML   -> "text/xml"
                                    HistoryViewModel.ExportType.LINES -> "text/plain"
                                }
                                val dir = context.cacheDir.resolve("export")
                                // Earlier exports were already handed over: only the newest is kept
                                dir.deleteRecursively()
                                dir.mkdirs()
                                val file = dir.resolve(fileName)
                                file.writeText(data)
                                val uri = FileProvider.getUriForFile(
                                    context, "${context.packageName}.fileprovider", file
                                )
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = shareMime
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    clipData = ClipData.newRawUri(fileName, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                val shareIntent = Intent.createChooser(intent, exportString)
                                context.startActivity(shareIntent)
                            }
                        }.onFailure {
                            Log.e("History", "Error starting activity!", it)
                        }
                    },
                    onTypeOnComputer = if (controller != null && canType) { dedup ->
                        scope.launch { state.hide() }.invokeOnCompletion { showSheet = state.isVisible }

                        val entries = entriesToExport(dedup)
                        val message = if (typeOnComputer(controller, entries)) {
                            resources.getString(R.string.shihab_type_on_pc_started, entries.size)
                        } else {
                            resources.getString(R.string.shihab_type_on_pc_busy)
                        }
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    } else null
                )
            }
        )
    }
}

private val EXPORT_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm", Locale.ROOT)

// One typing run at a time, so two runs never mix their lines on the computer
private var typingJob: Job? = null

/**
 * Types each entry on the computer through the current link, followed by the usual suffix (Enter by
 * default), so the readings fill a column in Excel or any field one after another.
 *
 * @return false when an earlier run is still typing
 */
private fun typeOnComputer(
    controller: BluetoothController,
    entries: List<HistoryViewModel.HistoryEntry>
): Boolean {
    if (typingJob?.isActive == true) return false
    typingJob = CoroutineScope(Dispatchers.IO).launch {
        entries.forEach { entry ->
            // sendString drops a value while another one is being typed: wait until the link is free
            controller.isSending.first { !it }
            controller.sendString(
                entry.value,
                true,
                "HISTORY",
                entry.timestamp,
                ZXingAnalyzer.index2String(entry.format)
            )
        }
    }
    return true
}

@Composable
@Preview
private fun ExportSheetContent(
    onSelect: (HistoryViewModel.ExportType, Boolean, Boolean) -> Unit = { _, _, _ -> },
    onTypeOnComputer: ((Boolean) -> Unit)? = null,
) {
    var (deduplicateChecked, setDeduplicate) = rememberSaveable { mutableStateOf(true) }
    var (saveIntoFileChecked, setSaveIntoFile) = rememberSaveable { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            stringResource(R.string.export_as),
            style = MaterialTheme.typography.titleLarge,
        )

        Spacer(Modifier.height(16.dp))

        Row(
            Modifier
                .toggleable(
                    value = deduplicateChecked,
                    role = Role.Checkbox,
                    onValueChange = setDeduplicate
                )
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = deduplicateChecked, onCheckedChange = null)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.exclude_duplicates))
        }

        Row(
            Modifier
                .toggleable(
                    value = saveIntoFileChecked,
                    role = Role.Checkbox,
                    onValueChange = setSaveIntoFile
                )
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = saveIntoFileChecked, onCheckedChange = null)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.save_to_file))
        }

        Spacer(Modifier.height(4.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(HistoryViewModel.ExportType.entries) { type ->
                ListItem(
                    headlineContent = { Text(stringResource(id = type.label)) },
                    supportingContent = { Text(stringResource(id = type.description)) },
                    leadingContent = { Icon(type.icon, null) },
                    modifier = Modifier
                        .clickable { onSelect(type, deduplicateChecked, saveIntoFileChecked) }
                        .clip(MaterialTheme.shapes.medium)
                )
            }

            onTypeOnComputer?.let { onType ->
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.shihab_type_on_pc)) },
                        supportingContent = { Text(stringResource(R.string.shihab_type_on_pc_desc)) },
                        leadingContent = { Icon(Icons.Default.Computer, null) },
                        modifier = Modifier
                            .clickable { onType(deduplicateChecked) }
                            .clip(MaterialTheme.shapes.medium)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

/** Copies one value to the clipboard and says so. */
@Composable
fun rememberCopyValue(): (String) -> Unit {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copied = stringResource(R.string.copied_to_clipboard)

    return remember(clipboard, copied) {
        { value ->
            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", value))) }
            Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
@ExperimentalMaterial3Api
fun HistoryViewModel.ImportSheet() {
    val context = LocalContext.current
    val types = stringArrayResource(R.array.code_types_values)

    val scope = rememberCoroutineScope()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showSheet by rememberSaveable { mutableStateOf(false) }
    var selectedFormat by remember { mutableStateOf<HistoryViewModel.ImportFormat?>(null) }

    ExtendedFloatingActionButton(
        text = { Text(stringResource(R.string.import_text)) },
        icon = { Icon(Icons.Default.FileUpload, stringResource(R.string.import_text)) },
        onClick = { showSheet = true }
    )

    val filePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val fileUri = result.data?.data
                val format = selectedFormat

                if (fileUri != null && format != null) {
                    runCatching {
                        val content = context.contentResolver.openInputStream(fileUri)?.use {
                            it.bufferedReader().readText()
                        } ?: ""

                        val count = HistoryViewModel.importHistory(content, format, types)
                        Toast.makeText(
                            context,
                            "Imported $count entries",
                            Toast.LENGTH_SHORT
                        ).show()
                    }.onFailure {
                        Log.e("History", "Error importing file!", it)
                        Toast.makeText(
                            context,
                            "Error: ${it.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            selectedFormat = null
        }

    if (showSheet) {
        ModalBottomSheet(
            sheetState = state,
            onDismissRequest = { showSheet = false },
            content = {
                ImportSheetContent { format ->
                    scope.launch { state.hide() }.invokeOnCompletion { showSheet = state.isVisible }

                    selectedFormat = format

                    // Launch file picker with appropriate MIME type
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = format.baseMime
                        format.extraMimeTypes?.let { putExtra(Intent.EXTRA_MIME_TYPES, it) }
                    }

                    runCatching {
                        filePickerLauncher.launch(intent)
                    }.onFailure {
                        Log.e("History", "Error starting file picker!", it)
                    }
                }
            }
        )
    }
}

@Composable
@Preview
private fun ImportSheetContent(
    onSelect: (HistoryViewModel.ImportFormat) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            stringResource(R.string.import_from),
            style = MaterialTheme.typography.titleLarge,
        )

        Spacer(Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(HistoryViewModel.ImportFormat.entries) { format ->
                ListItem(
                    headlineContent = { Text(stringResource(id = format.label)) },
                    supportingContent = { Text(stringResource(id = format.description)) },
                    leadingContent = { Icon(format.icon, null) },
                    modifier = Modifier
                        .clickable { onSelect(format) }
                        .clip(MaterialTheme.shapes.medium)
                )
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun PersistHistory() {
    val context = LocalContext.current
    val persistHistory by context.getPreferenceState(PreferenceStore.PERSIST_HISTORY)

    persistHistory?.let {
        ComposableLifecycle { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    if (it) CoroutineScope(Dispatchers.IO).launch {
                        HistoryViewModel.restoreHistory(
                            context
                        )
                    }
                }

                Lifecycle.Event.ON_DESTROY -> {
                    if (it) CoroutineScope(Dispatchers.IO).launch {
                        HistoryViewModel.saveHistory(
                            context
                        )
                    }
                }

                else -> {}
            }
        }
    }
}