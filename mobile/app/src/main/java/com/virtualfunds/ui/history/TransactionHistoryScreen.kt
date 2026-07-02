package com.virtualfunds.ui.history

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.virtualfunds.R
import com.virtualfunds.domain.formatAgoras
import com.virtualfunds.domain.model.FundFilterOption
import com.virtualfunds.domain.model.TransactionDetailItem
import com.virtualfunds.domain.model.TransactionGroup
import com.virtualfunds.domain.model.TransactionType
import com.virtualfunds.domain.model.TransactionTypeLabels
import com.virtualfunds.ui.export.CsvExportEffect
import com.virtualfunds.ui.export.buildCsvFileName
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * The transaction history screen (PR-7, E7.6) — mobile analogue of the desktop history panel
 * in `PortfolioWindow.xaml`.
 *
 * Stateful wrapper: collects [TransactionHistoryViewModel.state] and forwards plain values +
 * lambdas to the stateless [TransactionHistoryContent]. The split keeps the content composable
 * testable/previewable without the DI graph (same pattern as the fund + portfolio screens).
 */
@Composable
fun TransactionHistoryScreen(
    portfolioId: String,
    portfolioName: String,
    onBack: () -> Unit,
    // hiltViewModel() pulls the VM from Hilt's generated factory; the route args
    // (portfolioId, portfolioName) arrive via SavedStateHandle. The parameters above are unused
    // at runtime — they document the navigation call site, mirroring FundListScreen.
    @Suppress("UNUSED_PARAMETER")
    viewModel: TransactionHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Drive the system save picker off the VM's one-shot export events (E9.1). The file-name
    // prefix is resolved here (stringResource is composition-only); the lambda runs at export time
    // and stamps the current date via buildCsvFileName.
    val exportPrefix = stringResource(R.string.export_history_filename_prefix)
    val portfolioNameForFile = state.portfolioName
    CsvExportEffect(
        events = viewModel.csvExport,
        fileName = { buildCsvFileName(exportPrefix, portfolioNameForFile) },
        snackbarHostState = snackbarHostState,
    )

    TransactionHistoryContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onExport = viewModel::onExportClick,
        onFromDateChange = viewModel::onFromDateChange,
        onToDateChange = viewModel::onToDateChange,
        onFundFilterChange = viewModel::onFundFilterChange,
        onTypeFilterChange = viewModel::onTypeFilterChange,
        onClearFilters = viewModel::onClearFilters,
        onLoadMore = viewModel::onLoadMore,
        onRetry = viewModel::onRetry,
        onUndoClick = viewModel::onUndoClick,
        onUndoConfirm = viewModel::onUndoConfirm,
        onUndoDismiss = viewModel::onUndoDismiss,
        onUndoErrorShown = viewModel::onUndoErrorShown,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionHistoryContent(
    state: TransactionHistoryUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onExport: () -> Unit = {},
    onFromDateChange: (LocalDate?) -> Unit,
    onToDateChange: (LocalDate?) -> Unit,
    onFundFilterChange: (String?) -> Unit,
    onTypeFilterChange: (TransactionType?) -> Unit,
    onClearFilters: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onUndoClick: (TransactionGroup) -> Unit = {},
    onUndoConfirm: () -> Unit = {},
    onUndoDismiss: () -> Unit = {},
    onUndoErrorShown: () -> Unit = {},
) {
    // Snackbar host for one-shot undo-failure messages (E6.12 step 5 is an expected outcome, so
    // we surface it transiently instead of replacing the whole list). When undoErrorRes flips
    // non-zero we show it once, then tell the VM to clear it. getString (not stringResource) is
    // used because we're inside a coroutine effect, not composition.
    val context = LocalContext.current
    LaunchedEffect(state.undoErrorRes) {
        if (state.undoErrorRes != 0) {
            snackbarHostState.showSnackbar(context.getString(state.undoErrorRes))
            onUndoErrorShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    // Two-line title: screen name on top, portfolio name below — matches the
                    // fund screen's header style.
                    Column {
                        Text(
                            text = stringResource(R.string.history_title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = state.portfolioName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        // AutoMirrored variant flips automatically under RTL layout.
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.history_cd_back),
                        )
                    }
                },
                actions = {
                    // Export the filtered history to CSV (E9.1). A text action ("ייצוא ל-CSV")
                    // rather than an icon: the core icon set has no export/save glyph, and this
                    // mirrors the desktop history panel's "CSV" button.
                    TextButton(
                        onClick = onExport,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text(stringResource(R.string.history_action_export))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                // First load (nothing fetched yet) → full-screen spinner.
                state.isLoading && state.groups.isEmpty() && !state.hasActiveFilters ->
                    CenteredSpinner()

                state.errorMessageRes != 0 ->
                    ErrorRetry(messageRes = state.errorMessageRes, onRetry = onRetry)

                else -> Column(Modifier.fillMaxSize()) {
                    FilterBar(
                        state = state,
                        onFromDateChange = onFromDateChange,
                        onToDateChange = onToDateChange,
                        onFundFilterChange = onFundFilterChange,
                        onTypeFilterChange = onTypeFilterChange,
                        onClearFilters = onClearFilters,
                    )
                    HorizontalDivider()
                    if (state.isEmpty) {
                        EmptyHistory(hasActiveFilters = state.hasActiveFilters)
                    } else {
                        HistoryList(
                            state = state,
                            onLoadMore = onLoadMore,
                            onUndoClick = onUndoClick,
                        )
                    }
                }
            }
        }
    }

    // Undo confirmation (E6.12) — rendered above the screen when an operation is pending undo.
    state.undoConfirm?.let { group ->
        UndoConfirmDialog(
            group = group,
            isUndoing = state.isUndoing,
            onConfirm = onUndoConfirm,
            onDismiss = onUndoDismiss,
        )
    }
}

/** Yes/no confirmation before undoing an operation (E6.12). Mirrors desktop's prompt wording. */
@Composable
private fun UndoConfirmDialog(
    group: TransactionGroup,
    isUndoing: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isUndoing) onDismiss() },
        title = { Text(stringResource(R.string.history_undo_dialog_title)) },
        text = {
            Text(
                stringResource(
                    R.string.history_undo_dialog_message,
                    TransactionTypeLabels.label(group.transactionType),
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isUndoing) {
                Text(stringResource(R.string.history_undo_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isUndoing) {
                Text(stringResource(R.string.history_undo_dialog_cancel))
            }
        },
    )
}

// ──────────────────────────────────────────────────────────────────────────────
// Filter bar (E7.6 — date range, fund, type)
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun FilterBar(
    state: TransactionHistoryUiState,
    onFromDateChange: (LocalDate?) -> Unit,
    onToDateChange: (LocalDate?) -> Unit,
    onFundFilterChange: (String?) -> Unit,
    onTypeFilterChange: (TransactionType?) -> Unit,
    onClearFilters: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Date range: from / to, side by side.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateFilterField(
                label = stringResource(R.string.history_filter_from),
                value = state.fromDate,
                onChange = onFromDateChange,
                modifier = Modifier.weight(1f),
            )
            DateFilterField(
                label = stringResource(R.string.history_filter_to),
                value = state.toDate,
                onChange = onToDateChange,
                modifier = Modifier.weight(1f),
            )
        }

        // Fund filter (E7.6): "all funds" + every active/tombstoned fund.
        FilterDropdown(
            label = stringResource(R.string.history_filter_fund),
            selectedLabel = state.fundFilterOptions
                .firstOrNull { it.fundId == state.selectedFundId }
                ?.displayName
                ?: stringResource(R.string.history_filter_fund_all),
            entries = buildList {
                add(
                    DropdownEntry(
                        label = stringResource(R.string.history_filter_fund_all),
                        selected = state.selectedFundId == null,
                        onSelect = { onFundFilterChange(null) },
                    ),
                )
                state.fundFilterOptions.forEach { fund: FundFilterOption ->
                    add(
                        DropdownEntry(
                            label = fund.displayName,
                            selected = fund.fundId == state.selectedFundId,
                            onSelect = { onFundFilterChange(fund.fundId) },
                        ),
                    )
                }
            },
        )

        // Type filter (E7.6): "all types" + the filterable summary types.
        FilterDropdown(
            label = stringResource(R.string.history_filter_type),
            selectedLabel = state.selectedType
                ?.let { TransactionTypeLabels.label(it) }
                ?: stringResource(R.string.history_filter_type_all),
            entries = buildList {
                add(
                    DropdownEntry(
                        label = stringResource(R.string.history_filter_type_all),
                        selected = state.selectedType == null,
                        onSelect = { onTypeFilterChange(null) },
                    ),
                )
                state.typeFilterOptions.forEach { option ->
                    add(
                        DropdownEntry(
                            label = option.label,
                            selected = option.type == state.selectedType,
                            onSelect = { onTypeFilterChange(option.type) },
                        ),
                    )
                }
            },
        )

        // Clear-all button — only shown when there is something to clear (mirrors desktop "נקה").
        if (state.hasActiveFilters) {
            TextButton(
                onClick = onClearFilters,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.history_filter_clear))
            }
        }
    }
}

/**
 * A read-only field showing the selected date (or a placeholder), which opens a Material 3
 * [DatePickerDialog] when tapped. A trailing ✕ clears the bound when a date is set.
 *
 * Material 3's `DatePickerState.selectedDateMillis` is "UTC midnight of the chosen calendar
 * date" — we convert to/from [LocalDate] through [ZoneOffset.UTC] so the date the user sees in
 * the picker is exactly the date we filter on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateFilterField(
    label: String,
    value: LocalDate?,
    onChange: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(
            onClick = { showPicker = true },
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = value?.format(DATE_FORMATTER) ?: label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (value != null) {
            IconButton(onClick = { onChange(null) }) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = stringResource(R.string.history_cd_clear_date),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }

    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = value?.toUtcEpochMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onChange(it.toUtcLocalDate()) }
                    showPicker = false
                }) {
                    Text(stringResource(R.string.history_dialog_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(R.string.history_dialog_cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/** One choice inside a [FilterDropdown]. */
private data class DropdownEntry(
    val label: String,
    val selected: Boolean,
    val onSelect: () -> Unit,
)

/**
 * A labelled dropdown (Material 3 ExposedDropdownMenuBox) used for the fund + type filters.
 * The anchor is a read-only [OutlinedTextField] showing the current selection; tapping it opens
 * the menu of [entries].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDropdown(
    label: String,
    selectedLabel: String,
    entries: List<DropdownEntry>,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                // PrimaryNotEditable is the correct anchor for read-only selection fields.
                .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            entries.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(entry.label) },
                    onClick = {
                        expanded = false
                        entry.onSelect()
                    },
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// History list + rows (E7.2 summary/detail)
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun HistoryList(
    state: TransactionHistoryUiState,
    onLoadMore: () -> Unit,
    onUndoClick: (TransactionGroup) -> Unit,
) {
    val listState = rememberLazyListState()

    // Display paging: when the last item scrolls into view and more filtered groups remain,
    // reveal the next page. snapshotFlow turns the Compose "last visible index" state into a
    // cold Flow we can observe; distinctUntilChanged avoids re-triggering on every frame.
    // Keyed on (size, hasMore) so a fresh page relaunches the watcher with up-to-date bounds.
    LaunchedEffect(state.groups.size, state.hasMore) {
        if (!state.hasMore) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { lastVisible ->
                if (lastVisible >= state.groups.size - 1) onLoadMore()
            }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.groups, key = { it.operationId }) { group ->
            HistoryGroupCard(group = group, onUndoClick = onUndoClick)
        }
        if (state.hasMore) {
            item(key = "load-more") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

/** A single operation: collapsed summary row, tap to expand the per-fund detail rows (E7.2). */
@Composable
private fun HistoryGroupCard(
    group: TransactionGroup,
    onUndoClick: (TransactionGroup) -> Unit,
) {
    var expanded by rememberSaveable(group.operationId) { mutableStateOf(false) }
    val typeColor = TransactionTypeColor(group.transactionType)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            // Summary header: type label (start) — amount (end) — expand chevron.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = TransactionTypeLabels.label(group.transactionType),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = typeColor,
                    modifier = Modifier.weight(1f),
                )
                // Money rendered LTR so digits + ₪ read correctly inside the RTL bar.
                LtrText(
                    text = formatAgoras(group.amountAgoras),
                    style = MaterialTheme.typography.bodyMedium,
                    color = typeColor,
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp
                    else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                )
            }

            // Date/time (muted, LTR).
            LtrText(
                text = formatDateTime(group.committedAtUtc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (expanded) {
                Spacer(Modifier.height(4.dp))

                // Undo button — shown only for undoable operations (E6.12). isUndoable already
                // encodes both rules: an undoable type AND not already undone.
                if (group.isUndoable) {
                    TextButton(
                        onClick = { onUndoClick(group) },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text(stringResource(R.string.history_undo_button))
                    }
                }

                // Local val so the not-blank check smart-casts summaryText to non-null (no !!).
                val summaryText = group.summaryText
                if (!summaryText.isNullOrBlank()) {
                    Text(
                        text = summaryText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                group.details.forEach { detail ->
                    HistoryDetailRow(detail)
                }
            }
        }
    }
}

/**
 * One per-fund detail row (E7.2 / E7.7). Shows the resolved fund name + the detail-level type
 * label, and (for money rows) the before / change / after balances. Mirrors the desktop detail
 * template's "FundName — type" + "לפני: … — שינוי: … — אחרי: …" layout.
 */
@Composable
private fun HistoryDetailRow(detail: TransactionDetailItem) {
    Column(modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 2.dp)) {
        Text(
            text = "${detail.fundName} — ${TransactionTypeLabels.label(detail.transactionType)}",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            detail.beforeBalanceAgoras?.let { before ->
                MutedLabel(stringResource(R.string.history_detail_before))
                LtrText(
                    text = formatAgoras(before),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MutedLabel(" · ")
            }
            MutedLabel(stringResource(R.string.history_detail_change))
            LtrText(
                text = formatSignedAgoras(detail.amountAgoras),
                style = MaterialTheme.typography.bodySmall,
                color = SignedAmountColor(detail.amountAgoras),
            )
            detail.afterBalanceAgoras?.let { after ->
                MutedLabel(" · ")
                MutedLabel(stringResource(R.string.history_detail_after))
                LtrText(
                    text = formatAgoras(after),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Small shared building blocks
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun MutedLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Renders [text] forced to LTR so money / dates keep their natural digit order inside RTL. */
@Composable
private fun LtrText(
    text: String,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Text(text = text, style = style, color = color, maxLines = 1)
    }
}

@Composable
private fun CenteredSpinner() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyHistory(hasActiveFilters: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(
                if (hasActiveFilters) R.string.history_empty_filtered else R.string.history_empty,
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorRetry(messageRes: Int, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(messageRes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRetry) {
                Text(stringResource(R.string.history_retry))
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Display helpers
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Color-codes a summary type the same way the desktop history panel does:
 * deposit = green, withdrawal = red, transfer = teal, revaluation = amber, undo/other = muted.
 */
@Composable
private fun TransactionTypeColor(type: TransactionType): Color = when (type) {
    TransactionType.FundDeposit -> Color(0xFF2E7D32)
    TransactionType.FundWithdrawal -> MaterialTheme.colorScheme.error
    TransactionType.Transfer -> Color(0xFF00796B)
    TransactionType.PortfolioRevalued -> Color(0xFFF57F17)
    TransactionType.Undo -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.onSurface
}

/** Green for a credit, red for a debit, muted for zero (structural rows). */
@Composable
private fun SignedAmountColor(amountAgoras: Long): Color = when {
    amountAgoras > 0 -> Color(0xFF2E7D32)
    amountAgoras < 0 -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * Signed money for a detail row: "+100.00 ₪" for credits, "-50.00 ₪" for debits.
 * Mirrors desktop's `TransactionDetailItem.FormattedSignedAmount` ([formatAgoras] already
 * prepends "-" for negatives, so we only add the explicit "+").
 */
private fun formatSignedAgoras(amountAgoras: Long): String =
    if (amountAgoras >= 0) "+" + formatAgoras(amountAgoras) else formatAgoras(amountAgoras)

/** Date/time pattern shared with desktop's history panel (`dd/MM/yyyy HH:mm`). */
private val DATE_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

/** Date-only pattern for the filter fields. */
private val DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yyyy")

/** Formats a UTC commit instant in the device-local zone (E "general timestamp display"). */
private fun formatDateTime(instant: Instant): String =
    instant.atZone(ZoneId.systemDefault()).format(DATE_TIME_FORMATTER)

/** LocalDate → "UTC midnight" epoch millis, the format Material 3's DatePicker expects. */
private fun LocalDate.toUtcEpochMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/** "UTC midnight" epoch millis (from the DatePicker) → the calendar date the user picked. */
private fun Long.toUtcLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
