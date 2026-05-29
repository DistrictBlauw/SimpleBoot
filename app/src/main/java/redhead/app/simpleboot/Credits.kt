package redhead.app.simpleboot

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scroll = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_version_label)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(id = android.R.drawable.ic_media_previous),
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // RedHead logo
            Image(
                painter = painterResource(id = R.drawable.redhead_logo),
                contentDescription = stringResource(R.string.redhead_logo_desc),
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .aspectRatio(3.2f)
                    .padding(bottom = 16.dp),
                contentScale = ContentScale.Fit
            )

            // --- Original Author ---
            Text(
                text = stringResource(R.string.original_author_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Image(
                painter = painterResource(id = R.drawable.matthew_profile),
                contentDescription = stringResource(R.string.matthew_name),
                modifier = Modifier
                    .size(112.dp)
                    .padding(bottom = 12.dp)
                    .clip(CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.onBackground, CircleShape),
                contentScale = ContentScale.Crop
            )

            Text(
                text = stringResource(R.string.matthew_name),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = stringResource(R.string.matthew_role),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // --- Fork Maintainer ---
            Text(
                text = stringResource(R.string.fork_maintainer_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = stringResource(R.string.fork_maintainer_name),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = stringResource(R.string.fork_maintainer_github),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // --- App Info ---
            Text(
                text = stringResource(R.string.app_description),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.license_text),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(onClick = {
                val url = "https://redheadindustries.xyz/".toUri()
                val intent = Intent(Intent.ACTION_VIEW, url)
                context.startActivity(intent)
            }) {
                Text(stringResource(R.string.visit_redhead))
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(onClick = {
                val url = "https://github.com/DistrictBlauw/SimpleBoot".toUri()
                val intent = Intent(Intent.ACTION_VIEW, url)
                context.startActivity(intent)
            }) {
                Text(stringResource(R.string.visit_github))
            }

            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = stringResource(R.string.copyright_text),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Light
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PreviewCreditsScreen() {
    MaterialTheme {
        CreditsScreen(onBack = {})
    }
}
