package app.missu.ui.home

import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.ui.graphics.vector.ImageVector
import app.missu.R

data class Guide(
    val id: String,
    @param:StringRes val categoryRes: Int,
    @param:StringRes val titleRes: Int,
    @param:StringRes val summaryRes: Int,
    @param:ArrayRes val bodyRes: Int,
    val icon: ImageVector,
)

val guideCatalog = listOf(
    Guide(
        id = "desire",
        categoryRes = R.string.guide_desire_category,
        titleRes = R.string.guide_desire_title,
        summaryRes = R.string.guide_desire_summary,
        bodyRes = R.array.guide_desire_body,
        icon = Icons.Outlined.SelfImprovement,
    ),
    Guide(
        id = "intimacy",
        categoryRes = R.string.guide_intimacy_category,
        titleRes = R.string.guide_intimacy_title,
        summaryRes = R.string.guide_intimacy_summary,
        bodyRes = R.array.guide_intimacy_body,
        icon = Icons.Outlined.FavoriteBorder,
    ),
    Guide(
        id = "attention",
        categoryRes = R.string.guide_attention_category,
        titleRes = R.string.guide_attention_title,
        summaryRes = R.string.guide_attention_summary,
        bodyRes = R.array.guide_attention_body,
        icon = Icons.Outlined.AutoStories,
    ),
)

fun guideById(id: String): Guide? = guideCatalog.firstOrNull { it.id == id }
