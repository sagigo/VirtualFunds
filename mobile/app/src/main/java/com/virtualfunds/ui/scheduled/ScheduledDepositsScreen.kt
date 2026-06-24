package com.virtualfunds.ui.scheduled

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.virtualfunds.R
import com.virtualfunds.domain.formatAgoras
import com.virtualfunds.domain.formatIsraelDateTime
import com.virtualfunds.domain.model.ScheduleKind
import com.virtualfunds.domain.model.ScheduledDepositListItem
import com.virtualfunds.ui.theme.VirtualFundsTheme
import java.time.Instant

/**
 * Scheduled-deposit management screen (PR-8, E8) — mobile analogue of the desktop
 * `ScheduledDepositsDialog`.
 *
 * Stateful wrapper: collects [ScheduledDepositsViewModel] state and renders either the list or
 * the full-screen create/edit form ([ScheduledDepositFormContent]) depending on `state.form`.
 * Transient failures (toggle/delete) surface as snackbars via the VM's `messages` flow.
 */
@Composable
fun ScheduledDepositsScreen(
    portfolioId: String,
    portfolioName: String,
    onBack: () -> Unit,
    // hiltViewModel() pulls the VM from Hilt; the route args (portfolioId/portfolioName) arrive
    // via SavedStateHandle. The parameters above are only for a clear navigation call site.
    @Suppress("UNUSED_PARAMETER")
    viewModel: ScheduledDepositsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Collect one-shot message events and show them as snackbars. Resolving the string-res id
    // needs a Context (we're outside a @Composable scope inside the coroutine), hence getString.
    LaunchedEffect(Unit) {
        viewModel.messages.collect { messageRes ->
            snackbarHostState.showSnackbar(context.getString(messageRes))
        }
    }

    // While the form is open, the system back button should close the form, not leave the screen.
    BackHandler(enabled = state.form != null) { viewModel.onFormCancel() }

    val form = state.form
    if (form != null) {
        ScheduledDepositFormContent(
            form = form,
            onNameChange = viewModel::onFormNameChange,
            onFundChange = viewModel::onFormFundChange,
            onAmountChange = viewModel::onFormAmountChange,
            onKindChange = viewModel::onFormKindChange,
            onTimeChange = viewModel::onFormTimeChange,
            onWeekdayToggle = viewModel::onFormWeekdayToggle,
            onDayOfMonthChange = viewModel::onFormDayOfMonthChange,
            onDateChange = viewModel::onFormDateChange,
            onNoteChange = viewModel::onFormNoteChange,
            onEnabledChange = viewModel::onFormEnabledChange,
            onSave = viewModel::onFormSave,
            onCancel = viewModel::onFormCancel,
        )
    } else {
        ScheduledDepositsListContent(
            state = state,
            snackbarHostState = snackbarHostState,
            onBack = onBack,
            onAdd = viewModel::onAddClick,
            onEdit = viewModel::onEditClick,
            onToggle = viewModel::onToggleEnabled,
            onDelete = viewModel::onDeleteClick,
            onRetry = viewModel::onRetry,
        )
    }

    state.confirmDelete?.let { target ->
        DeleteDepositDialog(
            name = target.name,
            isSubmitting = state.deleteSubmitting,
            onConfirm = viewModel::onConfirmDelete,
            onDismiss = viewModel::onDismissDelete,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduledDepositsListContent(
    state: ScheduledDepositsUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (ScheduledDepositListItem) -> Unit,
    onToggle: (ScheduledDepositListItem) -> Unit,
    onDelete: (ScheduledDepositListItem) -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.sd_title),
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
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.sd_cd_back),
                        )
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
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.sd_cd_add),
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                state.isLoading && state.items.isEmpty() -> CenteredSpinner()
                state.errorMessageRes != 0 -> ErrorRetry(state.errorMessageRes, onRetry)
                state.items.isEmpty() -> EmptyState()
                else -> DepositList(
                    items = state.items,
                    onEdit = onEdit,
                    onToggle = onToggle,
                    onDelete = onDelete,
                    padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun DepositList(
    items: List<ScheduledDepositListItem>,
    onEdit: (ScheduledDepositListItem) -> Unit,
    onToggle: (ScheduledDepositListItem) -> Unit,
    onDelete: (ScheduledDepositListItem) -> Unit,
    padding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = padding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items, key = { it.scheduledDepositId }) { item ->
            DepositRow(
                item = item,
                onEdit = { onEdit(item) },
                onToggle = { onToggle(item) },
                onDelete = { onDelete(item) },
            )
        }
    }
}

@Composable
private fun DepositRow(
    item: ScheduledDepositListItem,
    onEdit: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = scheduleSummary(item),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.sd_row_fund_label, item.fundName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        R.string.sd_next_run_label,
                        formatIsraelDateTime(item.nextRunAtUtc),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                // Money always LTR so digits + ₪ read correctly inside the RTL row.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = formatAgoras(item.amountAgoras),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Text(
                    text = stringResource(
                        if (item.isEnabled) R.string.sd_status_enabled else R.string.sd_status_disabled,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (item.isEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            DepositRowMenu(
                isEnabled = item.isEnabled,
                onEdit = onEdit,
                onToggle = onToggle,
                onDelete = onDelete,
            )
        }
    }
}

@Composable
private fun DepositRowMenu(
    isEnabled: Boolean,
    onEdit: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.sd_cd_row_actions),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sd_action_edit)) },
                onClick = { expanded = false; onEdit() },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (isEnabled) R.string.sd_action_disable else R.string.sd_action_enable,
                        ),
                    )
                },
                onClick = { expanded = false; onToggle() },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sd_action_delete)) },
                onClick = { expanded = false; onDelete() },
            )
        }
    }
}

@Composable
private fun DeleteDepositDialog(
    name: String,
    isSubmitting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sd_delete_title)) },
        text = { Text(stringResource(R.string.sd_delete_message, name)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isSubmitting,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.sd_delete_confirm))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text(stringResource(R.string.sd_dialog_cancel))
            }
        },
    )
}

// ── Sub-states ──────────────────────────────────────────────────────────────────

@Composable
private fun CenteredSpinner() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.sd_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

@Composable
private fun ErrorRetry(messageRes: Int, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(messageRes),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry) { Text(stringResource(R.string.sd_retry)) }
    }
}

// ── Previews ──────────────────────────────────────────────────────────────────

private val previewItems = listOf(
    ScheduledDepositListItem(
        scheduledDepositId = "sd1",
        portfolioId = "p1",
        fundId = "f1",
        fundName = "מניות",
        name = "חיסכון שבועי",
        note = null,
        isEnabled = true,
        amountAgoras = 50_000L,
        scheduleKind = ScheduleKind.Weekly,
        timeOfDayMinutes = 9 * 60,
        weekdayMask = 0b0000101,
        dayOfMonth = null,
        nextRunAtUtc = Instant.parse("2026-06-28T06:00:00Z"),
    ),
    ScheduledDepositListItem(
        scheduledDepositId = "sd2",
        portfolioId = "p1",
        fundId = "f2",
        fundName = "אג״ח",
        name = "בונוס שנתי",
        note = "מתנה",
        isEnabled = false,
        amountAgoras = 120_000L,
        scheduleKind = ScheduleKind.OneTime,
        timeOfDayMinutes = null,
        weekdayMask = null,
        dayOfMonth = null,
        nextRunAtUtc = Instant.parse("2026-07-01T11:30:00Z"),
    ),
)

@Preview(showBackground = true, locale = "he")
@Composable
private fun ScheduledDepositsPreview_Populated() {
    VirtualFundsTheme {
        ScheduledDepositsListContent(
            state = ScheduledDepositsUiState(portfolioName = "פנסיה", items = previewItems),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {}, onAdd = {}, onEdit = {}, onToggle = {}, onDelete = {}, onRetry = {},
        )
    }
}

@Preview(showBackground = true, locale = "he")
@Composable
private fun ScheduledDepositsPreview_Empty() {
    VirtualFundsTheme {
        ScheduledDepositsListContent(
            state = ScheduledDepositsUiState(portfolioName = "תיק ריק"),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {}, onAdd = {}, onEdit = {}, onToggle = {}, onDelete = {}, onRetry = {},
        )
    }
}
