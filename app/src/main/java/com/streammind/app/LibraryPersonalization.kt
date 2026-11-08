package com.streammind.app

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

internal data class LibraryPersonalizationPreferences(
    val selectedCategories: Set<String> = emptySet(),
    val completed: Boolean = false
)

internal class LibraryPersonalizationStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = context.getSharedPreferences(
        "streammind_library_preferences_${BuildConfig.USE_FAKE_AI}",
        Context.MODE_PRIVATE
    )

    fun load(): LibraryPersonalizationPreferences {
        return LibraryPersonalizationPreferences(
            selectedCategories = preferences.getStringSet(KEY_CATEGORIES, emptySet()).orEmpty(),
            completed = preferences.getBoolean(KEY_COMPLETED, false)
        )
    }

    /**
     * Android may restore SharedPreferences after an uninstall/reinstall. Use
     * the package install timestamp as the installation identity so restored
     * onboarding choices do not suppress the first-run dialog.
     *
     * @return true when this is a new installation and stored preferences were
     * reset.
     */
    fun resetIfNewInstallation(): Boolean {
        // noBackupFilesDir is intentionally not restored by Android backup.
        // SharedPreferences can be restored after uninstall/reinstall, so the
        // absence of this marker is the reliable fresh-install signal.
        val marker = appContext.noBackupFilesDir.resolve(INSTALL_MARKER_FILE)
        val isNewInstallation = !marker.exists()
        if (isNewInstallation) {
            preferences.edit().clear().apply()
            marker.writeText("installed")
        }
        return isNewInstallation
    }

    fun save(selectedCategories: Set<String>) {
        preferences.edit()
            .putStringSet(KEY_CATEGORIES, selectedCategories)
            .putBoolean(KEY_COMPLETED, true)
            .apply()
    }

    private companion object {
        const val KEY_CATEGORIES = "selected_categories"
        const val KEY_COMPLETED = "personalization_completed"
        const val INSTALL_MARKER_FILE = "streammind_install_marker"
    }
}

internal val personalizationCategories = listOf("Animation", "Sci-Fi", "Fantasy")

@Composable
internal fun PersonalizeLibraryDialog(
    initialCategories: Set<String>,
    onSave: (Set<String>) -> Unit,
    onSkip: () -> Unit
) {
    var selectedCategories by remember(initialCategories) {
        mutableStateOf(initialCategories)
    }

    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text("Personalize my library") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = "StreamMind logo",
                    modifier = Modifier
                        .size(64.dp)
                        .padding(bottom = 8.dp)
                )
                Text(
                    "Choose the topics you want to see more often. You can change this anytime.",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium
                )
                personalizationCategories.forEach { category ->
                    FilterChip(
                        selected = category in selectedCategories,
                        onClick = {
                            selectedCategories = if (category in selectedCategories) {
                                selectedCategories - category
                            } else {
                                selectedCategories + category
                            }
                        },
                        label = { Text(category) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(selectedCategories) },
                enabled = selectedCategories.isNotEmpty()
            ) {
                Text("Save preferences")
            }
        },
        dismissButton = {
            TextButton(onClick = onSkip) { Text("Skip for now") }
        }
    )
}
