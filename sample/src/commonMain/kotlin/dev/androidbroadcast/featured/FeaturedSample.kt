package dev.androidbroadcast.featured

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun FeaturedSample(
    modifier: Modifier = Modifier,
    configValues: ConfigValues = createDefaultConfigValues()
) {
    val viewModel: SampleViewModel = viewModel { SampleViewModel(configValues) }
    val activate by viewModel.flagActive.collectAsStateWithLifecycle()

    Box(
        modifier = modifier.padding(16.dp)
            .fillMaxSize(),
    ) {
        Checkbox(
            activate,
            onCheckedChange = viewModel::setMainButtonColorFlag,
        )

        val buttonColor by viewModel.mainButtonColor.collectAsStateWithLifecycle()
        MainButton(
            onClick = { viewModel.setMainButtonColorFlag(!activate) },
            buttonColor = buttonColor
        )
    }
}

@Composable
fun MainButton(
    onClick: () -> Unit,
    buttonColor: SampleViewModel.MainButtonColor
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = when (buttonColor) {
                SampleViewModel.MainButtonColor.Red -> Color.Red
                SampleViewModel.MainButtonColor.Blue -> Color.Blue
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Main Button",
            color = Color.White
        )
    }
}

// Helper function to create default ConfigValues for demonstration
@Composable
fun createDefaultConfigValues(): ConfigValues {
    return remember { ConfigValues(localProvider = InMemoryConfigValueProvider()) }
}