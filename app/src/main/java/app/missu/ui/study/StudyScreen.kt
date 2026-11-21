package app.missu.ui.study

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.missu.R
import app.missu.ui.components.PageTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(
    onOpenPoem: (String) -> Unit,
    onOpenEnglish: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    Column(modifier.fillMaxSize()) {
        PageTopBar(title = stringResource(R.string.nav_study))
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(stringResource(R.string.study_poetry_tab)) },
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(stringResource(R.string.study_english_tab)) },
            )
        }
        when (selectedTab) {
            0 -> PoetryList(onOpen = onOpenPoem)
            else -> EnglishList(onOpen = onOpenEnglish)
        }
    }
}
