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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
    // hiltViewModel() pulls the ViewModel from Hilt's generated factory. SavedStateHandle is
    // populated by Navigation Compose with the route arguments (portfolioId, portfolioName),
    // which the VM reads via `toRoute<Routes.FundList>()`. The `portfolioId`/`portfolioName`
    // parameters above are unused at runtime — they're here only so the call site reads
    // clearly at the navigation call.
    @Suppress("UNUSED_PARAMETER")
    viewModel: FundListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    FundListContent(
        state = state,
        onBack = onBack,
        onCreate = viewModel::onCreateClick,
        onSelectFund = viewModel::onSelectFund,
        onRename = viewModel::onRenameClick,
        onDelete = viewModel::onDeleteClick,
        onDialogNameChange = viewModel::onDialogNameChange,
        onDialogAmountChange = viewModel::onDialogAmountChange,
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
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onSelectFund: (String?) -> Unit,
    onRename: (FundListItem) -> Unit,
    onDelete: (FundListItem) -> Unit,
    onDialogNameChange: (String) -> Unit,
    onDialogAmountChange: (String) -> Unit,
    onDialogConfirm: () -> Unit,
    onDialogDismiss: () -> Unit,
    onConfirmDelete: () -> Unit,
    onRetry: () -> Unit,
    onSortModeChange: (FundSortMode) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Scaffold(
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
                    SortMenu(
                        current = state.sortMode,
                        onChange = onSortModeChange,
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

                state.errorMessageRes != 0 ->
                    ErrorRetry(messageRes = state.errorMessageRes, onRetry = onRetry)

                state.funds.isEmpty() ->
                    EmptyState()

                else ->
                    FundList(
                        state = state,
                        onSelectFund = onSelectFund,
                        onRename = onRename,
                        onDelete = onDelete,
                        onMoveUp = onMoveUp,
                        onMoveDown = onMoveDown,
                        padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    )
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
        FundDialog.None -> { /* nothing to render */ }
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

            FundRowMenu(onRename = onRename, onDelete = onDelete)
        }
    }
}

@Composable
private fun FundRowMenu(
    onRename: () -> Unit,
    onDelete: () -> Unit,
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
            onDialogNameChange = {}, onDialogAmountChange = {},
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
            onDialogNameChange = {}, onDialogAmountChange = {},
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
            onDialogNameChange = {}, onDialogAmountChange = {},
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
            onDialogNameChange = {}, onDialogAmountChange = {},
            onDialogConfirm = {}, onDialogDismiss = {}, onConfirmDelete = {},
            onRetry = {}, onSortModeChange = {}, onMoveUp = {}, onMoveDown = {},
        )
    }
}
