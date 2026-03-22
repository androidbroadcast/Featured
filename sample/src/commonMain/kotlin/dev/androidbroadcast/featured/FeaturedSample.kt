@file:Suppress("ktlint:standard:function-naming")

package dev.androidbroadcast.featured

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Main sample screen demonstrating `@LocalFlag` and `@RemoteFlag` usage end-to-end.
 *
 * @param configValues The shared [ConfigValues] instance.
 * @param onOpenDebugUi Callback to navigate to [FeatureFlagsDebugScreen].
 *   Non-null in debug builds only — the button is absent in release.
 * @param modifier Optional [Modifier].
 */
@Composable
public fun FeaturedSample(
    configValues: ConfigValues,
    onOpenDebugUi: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val viewModel: SampleViewModel = viewModel { SampleViewModel(configValues) }
    val activate by viewModel.flagActive.collectAsStateWithLifecycle()
    val buttonColor by viewModel.mainButtonColor.collectAsStateWithLifecycle()
    val newFeatureSectionEnabled by viewModel.newFeatureSectionEnabled.collectAsStateWithLifecycle()
    val promoBannerEnabled by viewModel.promoBannerEnabled.collectAsStateWithLifecycle()
    val checkoutVariant by viewModel.checkoutVariant.collectAsStateWithLifecycle()

    Column(
        modifier =
            modifier
                .padding(16.dp)
                .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Featured Sample",
                style = MaterialTheme.typography.headlineSmall,
            )
            // Debug UI entry point — only wired in debug builds (onOpenDebugUi is null in release).
            if (onOpenDebugUi != null) {
                TextButton(onClick = onOpenDebugUi) {
                    Text("Debug flags")
                }
            }
        }

        // @LocalFlag: main_button_red — button colour driven by a local flag
        SectionLabel("@LocalFlag: main_button_red", isRemote = false)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Checkbox(
                checked = activate,
                onCheckedChange = viewModel::setMainButtonColorFlag,
            )
            Text("Enable red button")
        }
        MainButton(
            onClick = { viewModel.setMainButtonColorFlag(!activate) },
            buttonColor = buttonColor,
        )

        Spacer(modifier = Modifier.height(4.dp))

        // @LocalFlag: new_feature_section_enabled — isEnabled guard at a UI entry point.
        // When the flag is false the section is absent from the composition tree entirely,
        // not merely hidden — this is the recommended guard pattern for navigation entry points.
        SectionLabel("@LocalFlag: new_feature_section_enabled (isEnabled guard)", isRemote = false)
        if (newFeatureSectionEnabled) {
            NewFeatureSection()
        } else {
            Text(
                text = "New feature section disabled by flag.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // @RemoteFlag: promo_banner_enabled — banner visibility driven remotely.
        // In production wire a FirebaseConfigValueProvider to ConfigValues.remoteProvider.
        SectionLabel("@RemoteFlag: promo_banner_enabled", isRemote = true)
        if (promoBannerEnabled) {
            PromoBanner()
        } else {
            Text(
                text = "Promo banner off (remote flag default = false).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // @RemoteFlag: checkout_variant — multivariate enum flag resolved remotely.
        SectionLabel("@RemoteFlag: checkout_variant (enum)", isRemote = true)
        CheckoutVariantDisplay(variant = checkoutVariant)
    }
}

@Composable
public fun MainButton(
    onClick: () -> Unit,
    buttonColor: SampleViewModel.MainButtonColor,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        colors =
            ButtonDefaults.buttonColors(
                containerColor =
                    when (buttonColor) {
                        SampleViewModel.MainButtonColor.Red -> Color.Red
                        SampleViewModel.MainButtonColor.Blue -> Color.Blue
                    },
            ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = "Main Button",
            color = Color.White,
        )
    }
}

@Composable
private fun SectionLabel(
    text: String,
    isRemote: Boolean,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = if (isRemote) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
    )
}

// Gated by @LocalFlag new_feature_section_enabled.
// When the flag is false this composable is never entered — the section is excluded
// from the composition tree (not merely invisible).
@Composable
private fun NewFeatureSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "New Feature",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "This section is visible only when new_feature_section_enabled = true.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

// Shown when @RemoteFlag promo_banner_enabled is true.
@Composable
private fun PromoBanner() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Text(
            text = "Special offer! (promo_banner_enabled = true)",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

// Demonstrates multivariate @RemoteFlag: checkout_variant
@Composable
private fun CheckoutVariantDisplay(variant: CheckoutVariant) {
    val label =
        when (variant) {
            CheckoutVariant.LEGACY -> "Legacy checkout (default)"
            CheckoutVariant.NEW_SINGLE_PAGE -> "New single-page checkout"
            CheckoutVariant.NEW_MULTI_STEP -> "New multi-step checkout"
        }
    Text(
        text = "Active variant: $label",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
