// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import de.remph.urlaubsplaner.MainActivity
import de.remph.urlaubsplaner.R
import de.remph.urlaubsplaner.ui.TabRequest

private val SMALL_PADDING = 6.dp
private val NUMBER_PADDING = 8.dp

/** The two widgets in the picker, and with them the two layouts. */
enum class WidgetLayout { SMALL, BIG }

/** The widget as the launcher shows it: the background, the tap, and the layout for its size. */
@Composable
fun WidgetContent(
    model: WidgetModel,
    style: WidgetStyle,
    layout: WidgetLayout,
) {
    val context = LocalContext.current
    val palette = remember(style.colors) { WidgetPalette.of(context, style.colors) }
    val wide = layout == WidgetLayout.BIG
    // The size the launcher gives this widget, and the font: the tiers follow both.
    val size = LocalSize.current
    val fontScale = context.resources.configuration.fontScale
    val tier = if (model is WidgetModel.Status) wideTier(size, fontScale) else WideTier.FULL
    val numberOnly =
        !wide && model is WidgetModel.Status && smallTier(size, fontScale) == SmallTier.NUMBER
    // The lower tiers give back what the padding takes, so the text keeps its room.
    val padding =
        when {
            numberOnly -> NUMBER_PADDING to SMALL_PADDING
            wide && tier != WideTier.FULL -> 12.dp to 5.dp
            wide -> 16.dp to 8.dp
            else -> 16.dp to SMALL_PADDING
        }
    Box(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .widgetBackground(
                    palette,
                    style.backgroundAlpha,
                    24.dp,
                    R.drawable.widget_background,
                ).clickable(openApp(context, model))
                .padding(horizontal = padding.first, vertical = padding.second),
        contentAlignment = Alignment.CenterStart,
    ) {
        when {
            model is WidgetModel.Prompt -> {
                Prompt(model, palette, wide)
            }

            model is WidgetModel.Status && wide -> {
                WideStatus(
                    model,
                    palette,
                    tier,
                    showYear(size, fontScale),
                    tier != WideTier.MINIMAL || showTripCost(size, fontScale),
                )
            }

            model is WidgetModel.Status -> {
                if (numberOnly) {
                    NumberOnlyStatus(
                        model,
                        palette,
                        numberSize(
                            size,
                            NUMBER_PADDING,
                            SMALL_PADDING,
                            fontScale,
                            model.number,
                            model.unit,
                        ),
                    )
                } else {
                    SmallStatus(model, palette)
                }
            }
        }
    }
}

@Composable
private fun Prompt(
    model: WidgetModel.Prompt,
    palette: WidgetPalette,
    wide: Boolean,
) {
    Column {
        Text(
            model.title,
            style = textStyle(palette, 14.sp, FontWeight.Bold) { it.primary },
            maxLines = 1,
        )
        if (wide) {
            Text(
                model.subtitle,
                style = textStyle(palette, 12.sp) { it.muted },
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun SmallStatus(
    model: WidgetModel.Status,
    palette: WidgetPalette,
) {
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Number(model, palette, 26.sp)
            Spacer(GlanceModifier.width(6.dp))
            Text(
                model.unit,
                style = textStyle(palette, 12.sp) { it.muted },
                maxLines = 1,
                modifier = GlanceModifier.padding(bottom = 3.dp),
            )
        }
        Text(model.summary, style = textStyle(palette, 12.sp) { it.ink }, maxLines = 1)
    }
}

/** The lowest small widget: the number as big as the height allows, and what it counts. */
@Composable
private fun NumberOnlyStatus(
    model: WidgetModel.Status,
    palette: WidgetPalette,
    numberSize: androidx.compose.ui.unit.TextUnit,
) {
    Row(verticalAlignment = Alignment.Bottom) {
        Number(model, palette, numberSize)
        Spacer(GlanceModifier.width(UNIT_GAP_DP.dp))
        Text(
            model.unit,
            style = textStyle(palette, UNIT_SP.sp) { it.muted },
            maxLines = 1,
        )
    }
}

@Composable
private fun WideStatus(
    model: WidgetModel.Status,
    palette: WidgetPalette,
    tier: WideTier,
    showYear: Boolean,
    showCost: Boolean,
) {
    Column(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Number(model, palette, if (tier == WideTier.FULL) 40.sp else 32.sp)
                Text(
                    if (showYear) model.unitWithYear else model.unit,
                    style = textStyle(palette, 12.sp) { it.muted },
                    maxLines = 1,
                )
            }
            Box(
                modifier =
                    GlanceModifier
                        .width(1.dp)
                        .height(48.dp)
                        .background(palette.provider(alpha = 0.3f) { it.muted }),
            ) {}
            Column(modifier = GlanceModifier.defaultWeight().padding(start = 16.dp)) {
                // Two lines each: "Noch kein Urlaub geplant" and "30. April – 2. Mai" do not fit one
                // line next to the number (the maintainer, 2026-10-26, on a phone).
                Text(model.tripLabel, style = textStyle(palette, 11.sp) { it.muted }, maxLines = 2)
                Text(
                    model.tripWhen,
                    style =
                        textStyle(
                            palette,
                            if (tier ==
                                WideTier.FULL
                            ) {
                                17.sp
                            } else {
                                15.sp
                            },
                            FontWeight.Bold,
                        ) { it.ink },
                    maxLines = 2,
                )
                if (model.tripCost != null && showCost) {
                    Text(
                        model.tripCost,
                        style = textStyle(palette, 12.sp) { it.muted },
                        maxLines = 1,
                    )
                }
            }
        }
        // Too low for the strip: the number and the trip are what the widget is for.
        if (tier != WideTier.MINIMAL) {
            Spacer(GlanceModifier.height(if (tier == WideTier.FULL) 6.dp else 3.dp))
            if (model.warning != null) {
                val compact = tier == WideTier.COMPACT
                Text(
                    if (compact) model.warningShort ?: model.warning else model.warning,
                    style = textStyle(palette, 12.sp) { it.onWarning },
                    maxLines = if (compact) 1 else 2,
                    modifier =
                        GlanceModifier
                            .fillMaxWidth()
                            .widgetBackground(
                                palette,
                                1f,
                                8.dp,
                                R.drawable.widget_background_small,
                            ) { it.warning }
                            .padding(horizontal = 8.dp, vertical = if (compact) 2.dp else 4.dp),
                )
            } else {
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    for (figure in model.figures) {
                        Text(
                            figure,
                            style = textStyle(palette, 12.sp) { it.muted },
                            maxLines = 1,
                            modifier = GlanceModifier.padding(end = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Number(
    model: WidgetModel.Status,
    palette: WidgetPalette,
    size: androidx.compose.ui.unit.TextUnit,
) {
    Text(
        model.number,
        style =
            textStyle(
                palette,
                size,
                FontWeight.Bold,
            ) { if (model.overbooked) it.error else it.primary },
        maxLines = 1,
    )
}

private fun textStyle(
    palette: WidgetPalette,
    size: androidx.compose.ui.unit.TextUnit,
    weight: FontWeight = FontWeight.Normal,
    pick: (WidgetSwatch) -> androidx.compose.ui.graphics.Color,
) = TextStyle(color = palette.provider(pick = pick), fontSize = size, fontWeight = weight)

/**
 * A background with rounded corners. From Android 12 the launcher rounds the view itself; before
 * that Glance cannot, so a rounded shape drawable stretched over the view stands in, tinted with
 * the colour (it also carries the transparency there).
 */
private fun GlanceModifier.widgetBackground(
    palette: WidgetPalette,
    alpha: Float,
    radius: androidx.compose.ui.unit.Dp,
    shape: Int,
    pick: (WidgetSwatch) -> androidx.compose.ui.graphics.Color = { it.background },
): GlanceModifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        background(palette.provider(alpha, pick)).cornerRadius(radius)
    } else {
        background(
            ImageProvider(shape),
            alpha = alpha,
            colorFilter = ColorFilter.tint(palette.provider(pick = pick)),
        )
    }

private fun openApp(
    context: Context,
    model: WidgetModel,
): Action =
    actionStartActivity(
        android.content
            .Intent(
                context,
                MainActivity::class.java,
            ).setAction(TabRequest.action(model.opens)),
    )
