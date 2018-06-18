package co.csadev.kellocharts.model.dsl

import android.graphics.Color
import android.graphics.PathEffect
import android.graphics.Typeface
import co.csadev.kellocharts.formatter.*
import co.csadev.kellocharts.model.*
import co.csadev.kellocharts.model.Axis.Companion.DEFAULT_MAX_AXIS_LABEL_CHARS
import co.csadev.kellocharts.util.ChartUtils
import java.util.*

@DslMarker
annotation class AxisDsl

fun axis(block: AxisBuilder.() -> Unit): Axis = AxisBuilder().apply(block).build()
@AxisDsl
class AxisBuilder {
    private var values: MutableList<AxisValue> = ArrayList()
    var name: String? = null
    var isAutoGenerated: Boolean = true
    var hasLines: Boolean = false
    var isInside: Boolean = false
    var textColor: Int = Color.LTGRAY
    var lineColor: Int = ChartUtils.DEFAULT_DARKEN_COLOR
    var textSize: Int = Axis.DEFAULT_TEXT_SIZE_SP
    var maxLabelChars: Int = DEFAULT_MAX_AXIS_LABEL_CHARS
    var typeface: Typeface? = null
    var formatter: AxisValueFormatter = SimpleAxisValueFormatter()
    var hasSeparationLine: Boolean = true
    var hasTiltedLabels: Boolean = false
    var isReversed: Boolean = false
    var maxLabels: Int = -1

    fun values(block: AXISVALUES.() -> Unit) {
        values.addAll(AXISVALUES().apply(block))
    }

    fun build(): Axis = Axis(
            values,
            name,
            isAutoGenerated,
            hasLines,
            isInside,
            textColor,
            lineColor,
            textSize,
            maxLabelChars,
            typeface,
            formatter,
            hasSeparationLine,
            hasTiltedLabels,
            isReversed,
            maxLabels
    )
}

@DslMarker
annotation class ColumnDsl

fun column(block: ColumnBuilder.() -> Unit): Column = ColumnBuilder().apply(block).build()
class ColumnBuilder {
    private var values: MutableList<SubcolumnValue> = ArrayList()
    var hasLabels: Boolean = false
    var hasLabelsOnlyForSelected: Boolean = false
    var formatter: ColumnChartValueFormatter = SimpleColumnChartValueFormatter()

    fun columnValues(block: SUBCOLUMNVALUES.() -> Unit) {
        values.addAll(SUBCOLUMNVALUES().apply(block))
    }

    fun build(): Column = Column(values, hasLabels, hasLabelsOnlyForSelected, formatter)
}
@ColumnDsl
class COLUMNS: ArrayList<Column>() {
    fun column(block: ColumnBuilder.() -> Unit) {
        add(ColumnBuilder().apply(block).build())
    }
}

@DslMarker
annotation class LineDsl

fun line(block: LineBuilder.() -> Unit): Line = LineBuilder().apply(block).build()
@LineDsl
class LineBuilder {

    private var values: MutableList<PointValue> = ArrayList()

    var color: Int = ChartUtils.DEFAULT_COLOR
    var pointColor: Int = color
    var darkenColor: Int = ChartUtils.darkenColor(color)
    var formatter: LineChartValueFormatter = SimpleLineChartValueFormatter()
    var shape: ValueShape = ValueShape.CIRCLE
    var isFilled: Boolean = false
    var isSquare: Boolean = false
    var isCubic: Boolean = false
    var pointRadius: Int = Line.DEFAULT_POINT_RADIUS_DP
    var areaTransparency: Int = Line.DEFAULT_AREA_TRANSPARENCY
    var strokeWidth: Int = Line.DEFAULT_LINE_STROKE_WIDTH_DP
    var hasPoints: Boolean = true
    var hasLines: Boolean = true
    var hasLabels: Boolean = true
    var hasLabelsOnlyForSelected: Boolean = true
    var pathEffect: PathEffect? = null

    fun pointValues(block: POINTVALUES.() -> Unit) {
        values.addAll(POINTVALUES().apply(block))
    }

    fun build(): Line = Line(
            values,
            color,
            pointColor,
            darkenColor,
            formatter,
            shape,
            isFilled,
            isSquare,
            isCubic,
            pointRadius,
            areaTransparency,
            strokeWidth,
            hasPoints,
            hasLines,
            hasLabels,
            hasLabelsOnlyForSelected,
            pathEffect
    )
}
@LineDsl
class LINES: ArrayList<Line>() {
    fun line(block: LineBuilder.() -> Unit) {
        add(LineBuilder().apply(block).build())
    }
}

fun viewport(block: Viewport.() -> Unit): Viewport = Viewport().apply(block)
