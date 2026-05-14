package com.virtualfunds.ui.portfolio

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
import androidx.compose.material.icons.automirrored.filled.ExitToApp
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.virtualfunds.R
import com.virtualfunds.domain.formatAgoras
import com.virtualfunds.domain.model.PortfolioListItem
import com.virtualfunds.ui.theme.VirtualFundsTheme

/**
 * The portfolio list screen — mobile analogue of desktop `MainWindow` (PR-4, E5.3–E5.5).
 *
 * Stateful wrapper: collects [PortfolioListViewModel.state] and forwards plain values +
 * lambdas to the stateless [PortfolioListContent]. The split keeps the content composable
 * testable / previewable without the DI graph.
 */
@Composable
fun PortfolioListScreen(
    // Navigation callback: invoked when the user taps a portfolio row. Hoisted to the screen
    // so the ViewModel stays navigation-agnostic.
    onOpenPortfolio: (PortfolioListItem) -> Unit = {},
    // hiltViewModel() reaches into Hilt's generated factory for @HiltViewModel-annotated
    // classes (see PortfolioListViewModel) and returns an instance tied to the nearest
    // ViewModelStoreOwner — here the host Activity, so the same instance is reused across
    // configuration changes. We never `new` the ViewModel ourselves; Hilt does.
    viewModel: PortfolioListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    PortfolioListContent(
        state = state,
        onCreate = viewModel::onCreateClick,
        onOpen = onOpenPortfolio,
        onRename = viewModel::onRenameClick,
        onDelete = viewModel::onDeleteClick,
        onDialogNameChange = viewModel::onDialogNameChange,
        onDialogConfirm = viewModel::onDialogConfirm,
        onDialogDismiss = viewModel::onDialogDismiss,
        onConfirmDelete = viewModel::onConfirmDelete,
        onSignOut = viewModel::onSignOut,
        onRetry = viewModel::onRetry,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PortfolioListContent(
    state: PortfolioListUiState,
    onCreate: () -> Unit,
    onOpen: (PortfolioListItem) -> Unit,
    onRename: (PortfolioListItem) -> Unit,
    onDelete: (PortfolioListItem) -> Unit,
    onDialogNameChange: (String) -> Unit,
    onDialogConfirm: () -> Unit,
    onDialogDismiss: () -> Unit,
    onConfirmDelete: () -> Unit,
    onSignOut: () -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.portfolio_list_title)) },
                // The sign-out action is shown as an icon button. Hebrew label is preserved
                // via contentDescription so TalkBack reads "התנתקות".
                actions = {
                    IconButton(
                        onClick = onSignOut,
                        enabled = !state.isSigningOut,
                    ) {
                        // AutoMirrored.Filled.ExitToApp flips automatically when LayoutDirection
                        // is RTL — the arrow points the correct way in Hebrew without us
                        // hand-mirroring it.
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = stringResource(R.string.auth_button_sign_out),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        floatingActionButton = {
            // Material 3 extended FAB carries the "+ תיק חדש" affordance. Disabling it while
            // a refresh is in flight matches the desktop's IsLoading-bound IsEnabled.
            FloatingActionButton(onClick = onCreate) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.portfolio_action_create),
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
                // Loading with no data to show → centred spinner. This covers both the
                // first-ever load AND a retry after a failed load (where `portfolios` is
                // still empty). Reloads after a successful load keep the existing list
                // visible — no flash to a blank screen — and let the data swap when it
                // arrives (the `else` branch keeps matching).
                state.isLoading && state.portfolios.isEmpty() ->
                    CenteredSpinner()

                state.errorMessageRes != 0 ->
                    ErrorRetry(
                        messageRes = state.errorMessageRes,
                        onRetry = onRetry,
                    )

                state.portfolios.isEmpty() ->
                    EmptyState()

                else ->
                    PortfolioList(
                        portfolios = state.portfolios,
                        onOpen = onOpen,
                        onRename = onRename,
                        onDelete = onDelete,
                        padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    )
            }
        }
    }

    // Modal dialogs — rendered as siblings to the Scaffold's content. Compose handles the
    // overlay / scrim via AlertDialog itself.
    when (val dlg = state.dialog) {
        is PortfolioDialog.NameInput -> NameInputDialog(
            dialog = dlg,
            onNameChange = onDialogNameChange,
            onConfirm = onDialogConfirm,
            onDismiss = onDialogDismiss,
        )
        is PortfolioDialog.ConfirmDelete -> DeleteConfirmDialog(
            dialog = dlg,
            onConfirm = onConfirmDelete,
            onDismiss = onDialogDismiss,
        )
        PortfolioDialog.None -> { /* nothing to render */ }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// List + sub-states
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun PortfolioList(
    portfolios: List<PortfolioListItem>,
    onOpen: (PortfolioListItem) -> Unit,
    onRename: (PortfolioListItem) -> Unit,
    onDelete: (PortfolioListItem) -> Unit,
    padding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = padding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // `items(..., key = ...)` lets Compose track each row across recompositions, which
        // matters for animations and avoids re-creating the row's `remember` state when the
        // underlying list is replaced after a refresh.
        items(portfolios, key = { it.portfolioId }) { portfolio ->
            PortfolioRow(
                portfolio = portfolio,
                onOpen = { onOpen(portfolio) },
                onRename = { onRename(portfolio) },
                onDelete = { onDelete(portfolio) },
            )
        }
    }
}

@Composable
private fun PortfolioRow(
    portfolio: PortfolioListItem,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        // The card body is tappable to open the portfolio's fund list. The trailing overflow
        // menu intercepts its own clicks so the row click does not fire when the user opens
        // the rename/delete menu. Matches the desktop's double-click-to-open behaviour.
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        ) {
            Text(
                text = portfolio.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically),
            )

            // Money is always rendered LTR so the digits + currency sign read correctly
            // even though the surrounding screen is RTL. Same rule as desktop's
            // `FlowDirection=LeftToRight` override on money TextBlocks.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text = formatAgoras(portfolio.totalBalanceAgoras),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .padding(end = 8.dp),
                )
            }

            PortfolioRowMenu(
                onRename = onRename,
                onDelete = onDelete,
            )
        }
    }
}

@Composable
private fun PortfolioRowMenu(
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    // The overflow menu is per-row local UI state — it doesn't survive process death and
    // doesn't need to. `remember { mutableStateOf(false) }` is the standard Compose pattern
    // for transient view state that has no business logic implications.
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.portfolio_list_cd_actions),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.portfolio_action_rename)) },
                onClick = {
                    expanded = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.portfolio_action_delete)) },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

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
            text = stringResource(R.string.portfolio_list_empty),
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
            Text(stringResource(R.string.portfolio_list_retry))
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Dialogs
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun NameInputDialog(
    dialog: PortfolioDialog.NameInput,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val titleRes = if (dialog.isRename) {
        R.string.portfolio_dialog_rename_title
    } else {
        R.string.portfolio_dialog_create_title
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            Column {
                OutlinedTextField(
                    value = dialog.name,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.portfolio_dialog_name_label)) },
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
            TextButton(
                onClick = onConfirm,
                enabled = !dialog.isSubmitting,
            ) {
                if (dialog.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.portfolio_dialog_ok))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !dialog.isSubmitting,
            ) {
                Text(stringResource(R.string.portfolio_dialog_cancel))
            }
        },
    )
}

@Composable
private fun DeleteConfirmDialog(
    dialog: PortfolioDialog.ConfirmDelete,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.portfolio_dialog_delete_title)) },
        text = {
            Text(
                text = stringResource(
                    R.string.portfolio_dialog_delete_message,
                    dialog.target.name,
                ),
            )
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
                    Text(stringResource(R.string.portfolio_dialog_delete_confirm))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !dialog.isSubmitting,
            ) {
                Text(stringResource(R.string.portfolio_dialog_cancel))
            }
        },
    )
}

// ──────────────────────────────────────────────────────────────────────────────
// Previews — render in Android Studio without Hilt / a device.
// ──────────────────────────────────────────────────────────────────────────────

@Preview(showBackground = true, locale = "he")
@Composable
private fun PortfolioListPreview_Populated() {
    VirtualFundsTheme {
        PortfolioListContent(
            state = PortfolioListUiState(
                portfolios = listOf(
                    PortfolioListItem("a", "פנסיה", 1_234_567L),
                    PortfolioListItem("b", "חיסכון לדירה", 250_00L),
                    PortfolioListItem("c", "טיולים", 0L),
                ),
            ),
            onCreate = {}, onOpen = {}, onRename = {}, onDelete = {},
            onDialogNameChange = {}, onDialogConfirm = {}, onDialogDismiss = {},
            onConfirmDelete = {}, onSignOut = {}, onRetry = {},
        )
    }
}

@Preview(showBackground = true, locale = "he")
@Composable
private fun PortfolioListPreview_Empty() {
    VirtualFundsTheme {
        PortfolioListContent(
            state = PortfolioListUiState(),
            onCreate = {}, onOpen = {}, onRename = {}, onDelete = {},
            onDialogNameChange = {}, onDialogConfirm = {}, onDialogDismiss = {},
            onConfirmDelete = {}, onSignOut = {}, onRetry = {},
        )
    }
}

@Preview(showBackground = true, locale = "he")
@Composable
private fun PortfolioListPreview_LoadError() {
    VirtualFundsTheme {
        PortfolioListContent(
            state = PortfolioListUiState(
                errorMessageRes = R.string.portfolio_list_error_load,
            ),
            onCreate = {}, onOpen = {}, onRename = {}, onDelete = {},
            onDialogNameChange = {}, onDialogConfirm = {}, onDialogDismiss = {},
            onConfirmDelete = {}, onSignOut = {}, onRetry = {},
        )
    }
}

@Preview(showBackground = true, locale = "he")
@Composable
private fun PortfolioListPreview_RenameDialog() {
    VirtualFundsTheme {
        PortfolioListContent(
            state = PortfolioListUiState(
                portfolios = listOf(PortfolioListItem("a", "פנסיה", 1_234_567L)),
                dialog = PortfolioDialog.NameInput(
                    target = PortfolioListItem("a", "פנסיה", 1_234_567L),
                    name = "פנסיה",
                    errorMessageRes = R.string.portfolio_error_duplicate_name,
                ),
            ),
            onCreate = {}, onOpen = {}, onRename = {}, onDelete = {},
            onDialogNameChange = {}, onDialogConfirm = {}, onDialogDismiss = {},
            onConfirmDelete = {}, onSignOut = {}, onRetry = {},
        )
    }
}
