package ovh.gabrielhuav.pow.features.settings.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ovh.gabrielhuav.pow.R
import ovh.gabrielhuav.pow.data.auth.AuthManager

/** Slot Android de Ajustes: Firebase, Intents y Toast no entran en `commonMain`. */
@Composable
fun AndroidAccountSettings(
    authManager: AuthManager,
    onAccountDeleted: () -> Unit,
) {
    val context = LocalContext.current
    val reloginNeededMessage = stringResource(R.string.settings_account_relogin_needed)
    var signedIn by remember { mutableStateOf(authManager.isSignedIn()) }
    var accountLabel by remember {
        mutableStateOf(authManager.currentEmail() ?: authManager.currentDisplayName())
    }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        authManager.handleSignInResult(result.data) { ok, err ->
            if (ok) {
                signedIn = true
                accountLabel = authManager.currentEmail() ?: authManager.currentDisplayName()
            } else if (!err.isNullOrBlank()) {
                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            stringResource(R.string.settings_account_desc),
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 13.sp,
            textAlign = TextAlign.Justify,
        )
        if (signedIn) {
            Text(
                stringResource(R.string.settings_account_signed_in_as, accountLabel ?: ""),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedButton(
                onClick = { authManager.signOut { signedIn = false; accountLabel = null } },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFF6B1C3A)),
            ) { Text(stringResource(R.string.settings_account_sign_out)) }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFB71C1C),
                    contentColor = Color.White,
                ),
            ) {
                Text(
                    stringResource(R.string.settings_account_delete),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
        } else {
            Text(
                stringResource(R.string.settings_account_not_signed_in),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
            )
            Button(
                onClick = { signInLauncher.launch(authManager.signInIntent()) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6B1C3A),
                    contentColor = Color.White,
                ),
            ) {
                Text(
                    stringResource(R.string.settings_account_sign_in),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        val privacyUrl = stringResource(R.string.settings_privacy_url)
        TextButton(
            onClick = {
                runCatching {
                    context.startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(privacyUrl),
                        ),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFD4AF37)),
        ) {
            Text(
                stringResource(R.string.settings_account_privacy),
                color = Color(0xFFD4AF37),
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = Color(0xFF2A1C21),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFCCCCCC),
            title = { Text(stringResource(R.string.settings_account_delete_confirm_title)) },
            text = { Text(stringResource(R.string.settings_account_delete_confirm_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        authManager.deleteAccount { ok, err ->
                            if (ok) {
                                signedIn = false
                                accountLabel = null
                                Toast.makeText(
                                    context,
                                    R.string.settings_account_delete_done,
                                    Toast.LENGTH_LONG,
                                ).show()
                                onAccountDeleted()
                            } else {
                                Toast.makeText(
                                    context,
                                    err ?: reloginNeededMessage,
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFD32F2F)),
                ) {
                    Text(
                        stringResource(R.string.settings_account_delete),
                        color = Color(0xFFD32F2F),
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirm = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                ) {
                    Text(stringResource(R.string.menu_cancel))
                }
            },
        )
    }
}
