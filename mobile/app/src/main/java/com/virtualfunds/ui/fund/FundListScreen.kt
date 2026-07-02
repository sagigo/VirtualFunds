package com.virtualfunds.ui.fund

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.virtualfunds.R
import com.virtualfunds.domain.formatAgoras
import com.virtualfunds.domain.model.FundListItem
import com.virtualfunds.ui.export.CsvExportEffect
import com.virtualfunds.ui.export.buildCsvFileName
import com.virtualfunds.ui.theme.VirtualFundsTheme
import java.time.Instant

/**
 * The fund list screen — mobile analogue of the desktop `PortfolioWindow` fund table
 * (PR-5, E5.6–E5.10).
 *
 * Stateful wrapper: collects [FundListViewModel.state] and forwards plain values + lambdas
 * to the stateless [FundListContent]. The split keeps the content composable testable and
 * previewable without the DI graph.
 */
@Composable
fun FundListScreen(
    portfolioId: String,
    portfolioName: String,
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenScheduledDeposits: () -> Unit,
    // hiltViewModel() pulls the ViewModel from Hilt's generated factory. SavedStateHandle is
    // populated by Navigation Compose with the route arguments (portfolioId, portfolioName),
    // which the VM reads via `toRoute<Routes.FundList>()`. The `portfolioId`/`portfolioName`
    // parameters above are unused at runtime — they're here only so the call site reads
    // clearly at the navigation call.
    @Suppress("UNUSED_PARAMETER")
    viewModel: FundListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Drive the system save picker off the VM's one-shot snapshot-export events (E9.2).
    val exportPrefix = stringResource(R.string.export_snapshot_filename_prefix)
    val portfolioNameForFile = state.portfolioName
    CsvExportEffect(
        events = viewModel.csvExport,
        fileName = { buildCsvFileName(exportPrefix, portfolioNameForFile) },
        snackbarHostState = snackbarHostState,
    )

    FundListContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onOpenHistory = onOpenHistory,
        onOpenScheduledDeposits = onOpenScheduledDeposits,
        onCreate = viewModel::onCreateClick,
        onExportSnapshot = viewModel::onExportSnapshotClick,
        onSelectFund = viewModel::onSelectFund,
        onRename = viewModel::onRenameClick,
        onDelete = viewModel::onDeleteClick,
        onDeposit = viewModel::onDepositClick,
        onWithdraw = viewModel::onWithdrawClick,
        onTransfer = viewModel::onTransferClick,
        onRevalue = viewModel::onRevalueClick,
        onDialogNameChange = viewModel::onDialogNameChange,
        onDialogAmountChange = viewModel::onDialogAmountChange,
        onDialogRevalueAmountChange = viewModel::onRevalueAmountChange,
        onDialogTransferDestinationChange = viewModel::onTransferDestinationChange,
        onDialogConfirm = viewModel::onDialogConfirm,
        onDialogDismiss = viewModel::onDialogDismiss,
        onConfirmDelete = viewModel::onConfirmDelete,
        onRetry = viewModel::onRetry,
        onSortModeChange = viewModel::onSortModeChange,
        onMoveUp = viewModel::onMoveSelectedUp,
        onMoveDown = viewModel::onMoveSelectedDown,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FundListContent(
    state: FundListUiState,
    // Defaulted so the @Preview call sites don't each need to supply it; the real call site
    // (FundListScreen) passes a remembered instance that backs the export-result snackbars.
    snackbarHostState: SnackbarHostState = SnackbarHostState(),
    onBack: () -> Unit,
    // Defaulted to {} so the @Preview call sites below don't each need to pass them; the real
    // call site (FundListScreen) always supplies the navigation lambdas.
    onOpenHistory: () -> Unit = {},
    onOpenScheduledDeposits: () -> Unit = {},
    onCreate: () -> Unit,
    onExportSnapshot: () -> Unit = {},
    onSelectFund: (String?) -> Unit,
    onRename: (FundListItem) -> Unit,
    onDelete: (FundListItem) -> Unit,
    onDeposit: (FundListItem) -> Unit,
    onWithdraw: (FundListItem) -> Unit,
    onTransfer: (FundListItem) -> Unit,
    onRevalue: () -> Unit,
    onDialogNameChange: (String) -> Unit,
    onDialogAmountChange: (String) -> Unit,
    onDialogRevalueAmountChange: (String) -> Unit,
    onDialogTransferDestinationChange: (String) -> Unit,
    onDialogConfirm: () -> Unit,
    onDialogDismiss: () -> Unit,
    onConfirmDelete: () -> Unit,
    onRetry: () -> Unit,
    onSortModeChange: (FundSortMode) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    // Two-line title: portfolio name on top, total amount below. Money is
                    // rendered LTR so digits + ₪ read correctly inside the RTL bar.
                    Column {
                        Text(
                            text = state.portfolioName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = stringResource(
                                    R.string.fund_list_total_label,
                                    formatAgoras(state.totalAgoras),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        // AutoMirrored variant flips automatically under RTL layout.
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.fund_list_cd_back),
                        )
                    }
                },
                actions = {
                    // Opens the read-only transaction history for this portfolio (PR-7, E7.6).
                    IconButton(onClick = onOpenHistory) {
                        // AutoMirrored "list" icon — flips correctly under RTL; core icon set
                        // (no material-icons-extended dependency, so no dedicated History icon).
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = stringResource(R.string.fund_list_cd_history),
                        )
                    }
                    SortMenu(
                        current = state.sortMode,
                        onChange = onSortModeChange,
                    )
                    // Top-bar overflow menu hosts portfolio-level actions (not per-fund):
                    // revaluation, scheduled-deposit management, and snapshot CSV export.
                    PortfolioActionsMenu(
                        onRevalue = onRevalue,
                        onOpenScheduledDeposits = onOpenScheduledDeposits,
                        onExportSnapshot = onExportSnapshot,
                    )
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
            FloatingActionButton(onClick = onCreate) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.fund_action_create),
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
                state.isLoading && state.funds.isEmpty() ->
                    CenteredSpinner()

                // Full-screen error + retry only when there's no data to show — i.e. a genuine
                // load failure. Operation failures (deposit/withdraw/transfer/revalue/delete)
                // arrive with the list still populated and are handled by the banner below, so
                // they don't blow the fund list away behind a misleading "retry" button.
                state.errorMessageRes != 0 && state.funds.isEmpty() ->
                    ErrorRetry(messageRes = state.errorMessageRes, onRetry = onRetry)

                state.funds.isEmpty() ->
                    EmptyState()

                else ->
                    Column(Modifier.fillMaxSize()) {
                        // Non-destructive error banner for operation failures, matching the
                        // desktop's persistent ErrorMessage text. Clears on the next refresh
                        // or when the user opens another money-operation dialog.
                        if (state.errorMessageRes != 0) {
                            ErrorBanner(messageRes = state.errorMessageRes)
                        }
                        FundList(
                            state = state,
                            onSelectFund = onSelectFund,
                            onRename = onRename,
                            onDelete = onDelete,
                            onDeposit = onDeposit,
                            onWithdraw = onWithdraw,
                            onTransfer = onTransfer,
                            onMoveUp = onMoveUp,
                            onMoveDown = onMoveDown,
                            padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
            }
        }
    }

    when (val dlg = state.dialog) {
        is FundDialog.CreateInput -> CreateFundDialog(
            dialog = dlg,
            onNameChange = onDialogNameChange,
            onAmountChange = onDialogAmountChange,
            onConfirm = onDialogConfirm,
            onDismiss = onDialogDismiss,
        )
        is FundDialog.RenameInput -> RenameFundDialog(
            dialog = dlg,
            onNameChange = onDialogNameChange,
            onConfirm = onDialogConfirm,
            onDismiss = onDialogDismiss,
        )
        is FundDialog.ConfirmDelete -> DeleteFundDialog(
            dialog = dlg,
            onConfirm = onConfirmDelete,
            onDismiss = onDialogDismiss,
        )
        is FundDialog.DepositInput -> DepositFundDialog(
            dialog = dlg,
            onAmountChange = onDialogAmountChange,
            onConfirm = onDialogConfirm,
            onDismiss = onDialogDismiss,
        )
        is FundDialog.WithdrawInput -> WithdrawFundDialog(
            dialog = dlg,
            onAmountChange = onDialogAmountChange,
            onConfirm = onDialogConfirm,
            onDismiss = onDialogDismiss,
        )
        is FundDialog.TransferInput -> TransferFundDialog(
            dialog = dlg,
            onAmountChange = onDialogAmountChange,
            onDestinationChange = onDialogTransferDestinationChange,
            onConfirm = onDialogConfirm,
            onDismiss = onDialogDismiss,
        )
        is FundDialog.ConfirmOperation -> ConfirmOperationDialog(
            dialog = dlg,
            onConfirm = onDialogConfirm,
            onDismiss = onDialogDismiss,
        )
        is FundDialog.RevalueInput -> RevalueDialog(
            dialog = dlg,
            onAmountChange = onDialogRevalueAmountChange,
            onConfirm = onDialogConfirm,
            onDismiss = onDialogDismiss,
        )
        is FundDialog.ConfirmRevalue -> ConfirmRevalueDialog(
            dialog = dlg,
            onConfirm = onDialogConfirm,
            onDismiss = onDialogDismiss,
        )
        FundDialog.None -> { /* nothing to render */ }
    }
}

/**
 * Top app bar overflow menu for portfolio-level actions. Sits next to the [SortMenu] in
 * the actions slot. A ⋮ icon expands a small dropdown — the standard Material 3 pattern
 * for "more actions" on a screen.
 */
@Composable
private fun PortfolioActionsMenu(
    onRevalue: () -> Unit,
    onOpenScheduledDeposits: () -> Unit,
    onExportSnapshot: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.fund_list_cd_more_actions),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.fund_action_revalue_portfolio)) },
                onClick = {
                    expanded = false
                    onRevalue()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.fund_action_scheduled_deposits)) },
                onClick = {
                    expanded = false
                    onOpenScheduledDeposits()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.fund_action_export_snapshot)) },
                onClick = {
                    expanded = false
                    onExportSnapshot()
                },
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Sort menu (top app bar)
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun SortMenu(
    current: FundSortMode,
    onChange: (FundSortMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        // A text + chevron affordance, sitting in the top app bar's action slot. Chosen over
        // a bare icon because the current mode name is information the user wants to see
        // at a glance, and "אחוז הקצאה" wouldn't fit a tooltip well.
        Row(
            modifier = Modifier
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(sortLabelRes(current)),
                style = MaterialTheme.typography.bodyMedium,
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(R.string.fund_list_cd_sort),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            FundSortMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(stringResource(sortLabelRes(mode))) },
                    onClick = {
                        expanded = false
                        onChange(mode)
                    },
                )
            }
        }
    }
}

private fun sortLabelRes(mode: FundSortMode): Int = when (mode) {
    FundSortMode.CreatedDate -> R.string.fund_sort_created_date
    FundSortMode.Name -> R.string.fund_sort_name
    FundSortMode.Balance -> R.string.fund_sort_balance
    FundSortMode.AllocationPercent -> R.string.fund_sort_allocation
    FundSortMode.Custom -> R.string.fund_sort_custom
}

// ──────────────────────────────────────────────────────────────────────────────
// List + rows
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun FundList(
    state: FundListUiState,
    onSelectFund: (String?) -> Unit,
    onRename: (FundListItem) -> Unit,
    onDelete: (FundListItem) -> Unit,
    onDeposit: (FundListItem) -> Unit,
    onWithdraw: (FundListItem) -> Unit,
    onTransfer: (FundListItem) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    padding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = padding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.funds, key = { it.fundId }) { fund ->
            val isSelected = fund.fundId == state.selectedFundId
            FundRow(
                fund = fund,
                isSelected = isSelected,
                showMoveButtons = state.sortMode == FundSortMode.Custom,
                canMoveUp = isSelected && state.canMoveSelectedUp,
                canMoveDown = isSelected && state.canMoveSelectedDown,
                onClick = {
                    // Tap toggles selection in Custom mode (so the user knows which fund the
                    // up/down buttons act on). In other modes selection is irrelevant so a
                    // tap is a no-op for now.
                    if (state.sortMode == FundSortMode.Custom) {
                        onSelectFund(if (isSelected) null else fund.fundId)
                    }
                },
                onRename = { onRename(fund) },
                onDelete = { onDelete(fund) },
                onDeposit = { onDeposit(fund) },
                onWithdraw = { onWithdraw(fund) },
                onTransfer = { onTransfer(fund) },
                onMoveUp = onMoveUp,
                onMoveDown = onMoveDown,
            )
        }
    }
}

@Composable
private fun FundRow(
    fund: FundListItem,
    isSelected: Boolean,
    showMoveButtons: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDeposit: () -> Unit,
    onWithdraw: () -> Unit,
    onTransfer: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.surface,
        ),
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
                    text = fund.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Allocation percentage on its own line (E5.9). One decimal place is
                // enough — exact ratios are not money math, just a presentation hint.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = stringResource(
                            R.string.fund_list_allocation_format,
                            fund.allocationPercent,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Money always LTR — same rule as the portfolio list.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text = formatAgoras(fund.balanceAgoras),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }

            if (showMoveButtons) {
                // Show up/down buttons only in Custom mode. They are disabled (greyed) when
                // the fund isn't selected or the move would be out of bounds.
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.fund_list_cd_move_up),
                    )
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.fund_list_cd_move_down),
                    )
                }
            }

            FundRowMenu(
                onRename = onRename,
                onDelete = onDelete,
                onDeposit = onDeposit,
                onWithdraw = onWithdraw,
                onTransfer = onTransfer,
            )
        }
    }
}

@Composable
private fun FundRowMenu(
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDeposit: () -> Unit,
    onWithdraw: () -> Unit,
    onTransfer: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.fund_list_cd_actions),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            // Money operations come first — they're the common case (E6.8–E6.10).
            DropdownMenuItem(
                text = { Text(stringResource(R.string.fund_action_deposit)) },
                onClick = {
                    expanded = false
                    onDeposit()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.fund_action_withdraw)) },
                onClick = {
                    expanded = false
                    onWithdraw()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.fund_action_transfer)) },
                onClick = {
                    expanded = false
                    onTransfer()
                },
            )
            HorizontalDivider()
            // Structural actions at the bottom, separated by a divider.
            DropdownMenuItem(
                text = { Text(stringResource(R.string.fund_action_rename)) },
                onClick = {
                    expanded = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.fund_action_delete)) },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Empty / loading / error sub-states
// ──────────────────────────────────────────────────────────────────────────────

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
            text = stringResource(R.string.fund_list_empty),
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
        Button(onClick = onRetry) {
            Text(stringResource(R.string.fund_list_retry))
        }
    }
}

/**
 * A non-destructive error banner shown above the fund list when an operation fails but the
 * list is still valid (e.g. an insufficient-balance withdrawal). Unlike [ErrorRetry] it does
 * not replace the screen content — it mirrors the desktop's persistent ErrorMessage text.
 */
@Composable
private fun ErrorBanner(messageRes: Int) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(messageRes),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Dialogs
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun CreateFundDialog(
    dialog: FundDialog.CreateInput,
    onNameChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fund_dialog_create_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = dialog.name,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.fund_dialog_name_label)) },
                    singleLine = true,
                    enabled = !dialog.isSubmitting,
                    isError = dialog.errorMessageRes != 0,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                // Numeric input rendered LTR (matches desktop's FlowDirection=LeftToRight on
                // the amount field). KeyboardOptions hints to the keyboard that we want the
                // numeric layout with a decimal point.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    OutlinedTextField(
                        value = dialog.amountText,
                        onValueChange = onAmountChange,
                        label = { Text(stringResource(R.string.fund_dialog_initial_amount_label)) },
                        supportingText = {
                            Text(stringResource(R.string.fund_dialog_initial_amount_hint))
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        enabled = !dialog.isSubmitting,
                        isError = dialog.errorMessageRes != 0,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (dialog.errorMessageRes != 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(dialog.errorMessageRes),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !dialog.isSubmitting) {
                if (dialog.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.fund_dialog_ok))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !dialog.isSubmitting) {
                Text(stringResource(R.string.fund_dialog_cancel))
            }
        },
    )
}

@Composable
private fun RenameFundDialog(
    dialog: FundDialog.RenameInput,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fund_dialog_rename_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = dialog.name,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.fund_dialog_name_label)) },
                    singleLine = true,
                    enabled = !dialog.isSubmitting,
                    isError = dialog.errorMessageRes != 0,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (dialog.errorMessageRes != 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(dialog.errorMessageRes),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !dialog.isSubmitting) {
                if (dialog.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.fund_dialog_ok))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !dialog.isSubmitting) {
                Text(stringResource(R.string.fund_dialog_cancel))
            }
        },
    )
}

@Composable
private fun DeleteFundDialog(
    dialog: FundDialog.ConfirmDelete,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fund_dialog_delete_title)) },
        text = {
            Text(stringResource(R.string.fund_dialog_delete_message, dialog.target.name))
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !dialog.isSubmitting,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                if (dialog.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.fund_dialog_delete_confirm))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !dialog.isSubmitting) {
                Text(stringResource(R.string.fund_dialog_cancel))
            }
        },
    )
}

/**
 * Deposit/Withdraw share the same shape: a read-only fund-name line + a positive-shekel
 * amount field. We render them with one private composable so the two dialogs can't drift.
 *
 * `titleRes`/`errorAreaShown`/etc. are passed in so each call site can supply its own title
 * and submitting-state without duplicating layout.
 */
@Composable
private fun AmountOnlyDialog(
    titleRes: Int,
    fundName: String,
    amountText: String,
    isSubmitting: Boolean,
    errorMessageRes: Int,
    onAmountChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.fund_dialog_fund_label, fundName),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = onAmountChange,
                        label = { Text(stringResource(R.string.fund_dialog_amount_label)) },
                        supportingText = {
                            Text(stringResource(R.string.fund_dialog_amount_hint))
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        enabled = !isSubmitting,
                        isError = errorMessageRes != 0,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (errorMessageRes != 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(errorMessageRes),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isSubmitting) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.fund_dialog_ok))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text(stringResource(R.string.fund_dialog_cancel))
            }
        },
    )
}

@Composable
private fun DepositFundDialog(
    dialog: FundDialog.DepositInput,
    onAmountChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) = AmountOnlyDialog(
    titleRes = R.string.fund_dialog_deposit_title,
    fundName = dialog.target.name,
    amountText = dialog.amountText,
    isSubmitting = dialog.isSubmitting,
    errorMessageRes = dialog.errorMessageRes,
    onAmountChange = onAmountChange,
    onConfirm = onConfirm,
    onDismiss = onDismiss,
)

@Composable
private fun WithdrawFundDialog(
    dialog: FundDialog.WithdrawInput,
    onAmountChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) = AmountOnlyDialog(
    titleRes = R.string.fund_dialog_withdraw_title,
    fundName = dialog.target.name,
    amountText = dialog.amountText,
    isSubmitting = dialog.isSubmitting,
    errorMessageRes = dialog.errorMessageRes,
    onAmountChange = onAmountChange,
    onConfirm = onConfirm,
    onDismiss = onDismiss,
)

/**
 * Transfer dialog (E6.10). Shows source (read-only), a destination picker
 * (`ExposedDropdownMenuBox` — Material 3's combo-box equivalent), and an amount field.
 *
 * The dropdown lists [FundDialog.TransferInput.candidates] which the VM populated when
 * the dialog was opened. We don't refresh it during typing, so a near-simultaneous list
 * refresh won't reset the selection mid-edit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransferFundDialog(
    dialog: FundDialog.TransferInput,
    onAmountChange: (String) -> Unit,
    onDestinationChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedDestination = dialog.candidates.firstOrNull { it.fundId == dialog.destinationFundId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fund_dialog_transfer_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.fund_dialog_transfer_source_label, dialog.source.name),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { if (!dialog.isSubmitting) expanded = it },
                ) {
                    OutlinedTextField(
                        value = selectedDestination?.name
                            ?: stringResource(R.string.fund_dialog_transfer_destination_placeholder),
                        onValueChange = { /* read-only — selection only via dropdown */ },
                        readOnly = true,
                        label = { Text(stringResource(R.string.fund_dialog_transfer_destination_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        enabled = !dialog.isSubmitting,
                        isError = dialog.errorMessageRes != 0,
                        modifier = Modifier
                            .fillMaxWidth()
                            // PrimaryEditable mismatches readOnly fields; PrimaryNotEditable is correct.
                            .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = !dialog.isSubmitting),
                    )
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        dialog.candidates.forEach { candidate ->
                            DropdownMenuItem(
                                text = { Text(candidate.name) },
                                onClick = {
                                    expanded = false
                                    onDestinationChange(candidate.fundId)
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    OutlinedTextField(
                        value = dialog.amountText,
                        onValueChange = onAmountChange,
                        label = { Text(stringResource(R.string.fund_dialog_amount_label)) },
                        supportingText = {
                            Text(stringResource(R.string.fund_dialog_amount_hint))
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        enabled = !dialog.isSubmitting,
                        isError = dialog.errorMessageRes != 0,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (dialog.errorMessageRes != 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(dialog.errorMessageRes),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !dialog.isSubmitting) {
                if (dialog.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.fund_dialog_ok))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !dialog.isSubmitting) {
                Text(stringResource(R.string.fund_dialog_cancel))
            }
        },
    )
}

/**
 * Final yes/no confirmation step matching desktop's `ConfirmationRequested` pattern.
 * Shows the formatted amount + target fund(s) so the user can verify before committing.
 * Cancel → close (back to underlying list). Confirm → fire the repo via [onConfirm].
 */
@Composable
private fun ConfirmOperationDialog(
    dialog: FundDialog.ConfirmOperation,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val amountFormatted = formatAgoras(dialog.amountAgoras)
    val message = when (dialog.kind) {
        FundDialog.ConfirmOperation.Kind.Deposit ->
            stringResource(R.string.fund_dialog_confirm_deposit, amountFormatted, dialog.sourceFund.name)
        FundDialog.ConfirmOperation.Kind.Withdraw ->
            stringResource(R.string.fund_dialog_confirm_withdraw, amountFormatted, dialog.sourceFund.name)
        FundDialog.ConfirmOperation.Kind.Transfer ->
            stringResource(
                R.string.fund_dialog_confirm_transfer,
                amountFormatted,
                dialog.sourceFund.name,
                // ! safe: TransferKind always carries a destinationFund (constructed by VM).
                dialog.destinationFund!!.name,
            )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fund_dialog_confirm_title)) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !dialog.isSubmitting) {
                if (dialog.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.fund_dialog_confirm_button))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !dialog.isSubmitting) {
                Text(stringResource(R.string.fund_dialog_cancel))
            }
        },
    )
}

/**
 * Revaluation input dialog (E6.11) — first step. Single LTR amount field for the new total.
 * Same shape as the deposit/withdraw amount field, but no fund-name context (the
 * operation is portfolio-level).
 */
@Composable
private fun RevalueDialog(
    dialog: FundDialog.RevalueInput,
    onAmountChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fund_dialog_revalue_title)) },
        text = {
            Column {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    OutlinedTextField(
                        value = dialog.newTotalText,
                        onValueChange = onAmountChange,
                        label = { Text(stringResource(R.string.fund_dialog_revalue_new_total_label)) },
                        supportingText = {
                            Text(stringResource(R.string.fund_dialog_revalue_amount_hint))
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        enabled = !dialog.isSubmitting,
                        isError = dialog.errorMessageRes != 0,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (dialog.errorMessageRes != 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(dialog.errorMessageRes),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !dialog.isSubmitting) {
                if (dialog.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.fund_dialog_ok))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !dialog.isSubmitting) {
                Text(stringResource(R.string.fund_dialog_cancel))
            }
        },
    )
}

/**
 * Revaluation confirmation dialog (E6.11) — second step. Shows the formatted old → new
 * totals so the user can verify before committing. Distinct from [ConfirmOperationDialog]
 * because the wording references the portfolio total (not a fund + amount).
 */
@Composable
private fun ConfirmRevalueDialog(
    dialog: FundDialog.ConfirmRevalue,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fund_dialog_confirm_title)) },
        text = {
            Text(
                stringResource(
                    R.string.fund_dialog_confirm_revalue,
                    formatAgoras(dialog.oldTotalAgoras),
                    formatAgoras(dialog.newTotalAgoras),
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !dialog.isSubmitting) {
                if (dialog.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.fund_dialog_confirm_button))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !dialog.isSubmitting) {
                Text(stringResource(R.string.fund_dialog_cancel))
            }
        },
    )
}

// ──────────────────────────────────────────────────────────────────────────────
// Previews
// ──────────────────────────────────────────────────────────────────────────────

private val sampleFunds = listOf(
    FundListItem(
        fundId = "f1",
        name = "מניות",
        balanceAgoras = 500_000L,
        allocationPercent = 50.0,
        createdAtUtc = Instant.parse("2026-01-15T10:00:00Z"),
    ),
    FundListItem(
        fundId = "f2",
        name = "אג״ח",
        balanceAgoras = 300_000L,
        allocationPercent = 30.0,
        createdAtUtc = Instant.parse("2026-02-01T10:00:00Z"),
    ),
    FundListItem(
        fundId = "f3",
        name = "מזומן",
        balanceAgoras = 200_000L,
        allocationPercent = 20.0,
        createdAtUtc = Instant.parse("2026-03-10T10:00:00Z"),
    ),
)

@Preview(showBackground = true, locale = "he")
@Composable
private fun FundListPreview_Populated() {
    VirtualFundsTheme {
        FundListContent(
            state = FundListUiState(
                portfolioName = "פנסיה",
                funds = sampleFunds,
                totalAgoras = 1_000_000L,
            ),
            onBack = {}, onCreate = {}, onSelectFund = {}, onRename = {}, onDelete = {},
            onDeposit = {}, onWithdraw = {}, onTransfer = {}, onRevalue = {},
            onDialogNameChange = {}, onDialogAmountChange = {},
            onDialogRevalueAmountChange = {},
            onDialogTransferDestinationChange = {},
            onDialogConfirm = {}, onDialogDismiss = {}, onConfirmDelete = {},
            onRetry = {}, onSortModeChange = {}, onMoveUp = {}, onMoveDown = {},
        )
    }
}

@Preview(showBackground = true, locale = "he")
@Composable
private fun FundListPreview_Empty() {
    VirtualFundsTheme {
        FundListContent(
            state = FundListUiState(portfolioName = "תיק ריק"),
            onBack = {}, onCreate = {}, onSelectFund = {}, onRename = {}, onDelete = {},
            onDeposit = {}, onWithdraw = {}, onTransfer = {}, onRevalue = {},
            onDialogNameChange = {}, onDialogAmountChange = {},
            onDialogRevalueAmountChange = {},
            onDialogTransferDestinationChange = {},
            onDialogConfirm = {}, onDialogDismiss = {}, onConfirmDelete = {},
            onRetry = {}, onSortModeChange = {}, onMoveUp = {}, onMoveDown = {},
        )
    }
}

@Preview(showBackground = true, locale = "he")
@Composable
private fun FundListPreview_CustomMode() {
    VirtualFundsTheme {
        FundListContent(
            state = FundListUiState(
                portfolioName = "פנסיה",
                funds = sampleFunds,
                totalAgoras = 1_000_000L,
                sortMode = FundSortMode.Custom,
                selectedFundId = "f2",
            ),
            onBack = {}, onCreate = {}, onSelectFund = {}, onRename = {}, onDelete = {},
            onDeposit = {}, onWithdraw = {}, onTransfer = {}, onRevalue = {},
            onDialogNameChange = {}, onDialogAmountChange = {},
            onDialogRevalueAmountChange = {},
            onDialogTransferDestinationChange = {},
            onDialogConfirm = {}, onDialogDismiss = {}, onConfirmDelete = {},
            onRetry = {}, onSortModeChange = {}, onMoveUp = {}, onMoveDown = {},
        )
    }
}

@Preview(showBackground = true, locale = "he")
@Composable
private fun FundListPreview_CreateDialog() {
    VirtualFundsTheme {
        FundListContent(
            state = FundListUiState(
                portfolioName = "פנסיה",
                funds = sampleFunds,
                totalAgoras = 1_000_000L,
                dialog = FundDialog.CreateInput(name = "מניות נוספות", amountText = "1500.50"),
            ),
            onBack = {}, onCreate = {}, onSelectFund = {}, onRename = {}, onDelete = {},
            onDeposit = {}, onWithdraw = {}, onTransfer = {}, onRevalue = {},
            onDialogNameChange = {}, onDialogAmountChange = {},
            onDialogRevalueAmountChange = {},
            onDialogTransferDestinationChange = {},
            onDialogConfirm = {}, onDialogDismiss = {}, onConfirmDelete = {},
            onRetry = {}, onSortModeChange = {}, onMoveUp = {}, onMoveDown = {},
        )
    }
}

@Preview(showBackground = true, locale = "he")
@Composable
private fun FundListPreview_DepositDialog() {
    VirtualFundsTheme {
        FundListContent(
            state = FundListUiState(
                portfolioName = "פנסיה",
                funds = sampleFunds,
                totalAgoras = 1_000_000L,
                dialog = FundDialog.DepositInput(target = sampleFunds[0], amountText = "250.00"),
            ),
            onBack = {}, onCreate = {}, onSelectFund = {}, onRename = {}, onDelete = {},
            onDeposit = {}, onWithdraw = {}, onTransfer = {}, onRevalue = {},
            onDialogNameChange = {}, onDialogAmountChange = {},
            onDialogRevalueAmountChange = {},
            onDialogTransferDestinationChange = {},
            onDialogConfirm = {}, onDialogDismiss = {}, onConfirmDelete = {},
            onRetry = {}, onSortModeChange = {}, onMoveUp = {}, onMoveDown = {},
        )
    }
}

@Preview(showBackground = true, locale = "he")
@Composable
private fun FundListPreview_RevalueDialog() {
    VirtualFundsTheme {
        FundListContent(
            state = FundListUiState(
                portfolioName = "פנסיה",
                funds = sampleFunds,
                totalAgoras = 1_000_000L,
                dialog = FundDialog.RevalueInput(newTotalText = "12000.00"),
            ),
            onBack = {}, onCreate = {}, onSelectFund = {}, onRename = {}, onDelete = {},
            onDeposit = {}, onWithdraw = {}, onTransfer = {}, onRevalue = {},
            onDialogNameChange = {}, onDialogAmountChange = {},
            onDialogRevalueAmountChange = {},
            onDialogTransferDestinationChange = {},
            onDialogConfirm = {}, onDialogDismiss = {}, onConfirmDelete = {},
            onRetry = {}, onSortModeChange = {}, onMoveUp = {}, onMoveDown = {},
        )
    }
}

@Preview(showBackground = true, locale = "he")
@Composable
private fun FundListPreview_TransferDialog() {
    VirtualFundsTheme {
        FundListContent(
            state = FundListUiState(
                portfolioName = "פנסיה",
                funds = sampleFunds,
                totalAgoras = 1_000_000L,
                dialog = FundDialog.TransferInput(
                    source = sampleFunds[0],
                    candidates = sampleFunds.drop(1),
                    destinationFundId = sampleFunds[1].fundId,
                    amountText = "1000.00",
                ),
            ),
            onBack = {}, onCreate = {}, onSelectFund = {}, onRename = {}, onDelete = {},
            onDeposit = {}, onWithdraw = {}, onTransfer = {}, onRevalue = {},
            onDialogNameChange = {}, onDialogAmountChange = {},
            onDialogRevalueAmountChange = {},
            onDialogTransferDestinationChange = {},
            onDialogConfirm = {}, onDialogDismiss = {}, onConfirmDelete = {},
            onRetry = {}, onSortModeChange = {}, onMoveUp = {}, onMoveDown = {},
        )
    }
}
