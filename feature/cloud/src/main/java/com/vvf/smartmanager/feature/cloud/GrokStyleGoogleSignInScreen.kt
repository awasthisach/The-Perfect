package com.vvf.smartmanager.feature.cloud

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import com.vvf.smartmanager.core.common.R as CommonR

private val SoftGold = Color(0xFFD4A95A)

/**
 * Full-screen Google sign-in inspired by clean product login flows
 * (dark canvas, pill CTAs, minimal chrome).
 */
@Composable
fun GrokStyleGoogleSignInScreen(
    isLoading: Boolean,
    statusMessage: String?,
    onContinueWithGoogle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canvas = Color(0xFF0A0A0A)
    val pill = Color(0xFF1C1C1E)
    val pillBorder = Color(0xFF2C2C2E)
    val titleWhite = Color(0xFFF5F5F7)
    val bodyGray = Color(0xFF8E8E93)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(canvas)
            .testTag("grok_style_google_sign_in")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(0.28f))

            Image(
                painter = painterResource(id = CommonR.drawable.vvf_foundation_logo),
                contentDescription = "VVF",
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = "Drive Semantic Search",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = titleWhite
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Connect Google Drive to build a searchable local index of your files. Keyword search works without neural-search consent.",
                style = MaterialTheme.typography.bodyLarge,
                color = bodyGray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            if (!statusMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = SoftGold,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.weight(0.35f))

            Button(
                onClick = onContinueWithGoogle,
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("continue_with_google_btn"),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = pill,
                    contentColor = titleWhite,
                    disabledContainerColor = pill.copy(alpha = 0.6f)
                ),
                border = BorderStroke(1.dp, pillBorder)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = titleWhite
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Connecting…", fontWeight = FontWeight.SemiBold)
                } else {
                    Text(
                        text = "G",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4285F4),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Continue with Google",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = "Google sign-in connects the Drive account you choose. Sync updates the local search index and does not move or delete Drive files. Optional neural search requires separate consent.",
                style = MaterialTheme.typography.labelSmall,
                color = bodyGray.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            Spacer(modifier = Modifier.height(36.dp))
        }
    }
}
