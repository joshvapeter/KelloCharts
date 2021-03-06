package co.csadev.kellocharts.renderer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import co.csadev.kellocharts.computator.ChartComputator
import co.csadev.kellocharts.formatter.BubbleChartValueFormatter
import co.csadev.kellocharts.model.BubbleValue
import co.csadev.kellocharts.model.SelectedValue.SelectedValueType
import co.csadev.kellocharts.model.ValueShape
import co.csadev.kellocharts.model.Viewport
import co.csadev.kellocharts.model.set
import co.csadev.kellocharts.provider.BubbleChartDataProvider
import co.csadev.kellocharts.util.ChartUtils
import co.csadev.kellocharts.view.Chart

class BubbleChartRenderer(context: Context, chart: Chart, private val dataProvider: BubbleChartDataProvider) : AbstractChartRenderer(context, chart) {

    /**
     * Additional value added to bubble radius when drawing highlighted bubble, used to give touch feedback.
     */
    private val touchAdditional: Int

    /**
     * Scales for bubble radius value, only one is used depending on screen orientation;
     */
    private var bubbleScaleX: Float = 0.toFloat()
    private var bubbleScaleY: Float = 0.toFloat()

    /**
     * True if bubbleScale = bubbleScaleX so the renderer should used [ChartComputator.computeRawDistanceX]
     * , if false bubbleScale = bubbleScaleY and renderer should use
     * [ChartComputator.computeRawDistanceY].
     */
    private var isBubbleScaledByX = true

    /**
     * Maximum bubble radius.
     */
    private var maxRadius: Float = 0.toFloat()

    /**
     * Minimal bubble radius in pixels.
     */
    private var minRawRadius: Float = 0.toFloat()
    private val bubbleCenter = PointF()
    private val bubblePaint = Paint()

    /**
     * Rect used for drawing bubbles with SHAPE_SQUARE.
     */
    private val bubbleRect = RectF()

    private var hasLabels: Boolean = false
    private var hasLabelsOnlyForSelected: Boolean = false
    private var valueFormatter: BubbleChartValueFormatter? = null
    private val tempMaximumViewport = Viewport()

    init {

        touchAdditional = ChartUtils.dp2px(density, DEFAULT_TOUCH_ADDITIONAL_DP)

        bubblePaint.isAntiAlias = true
        bubblePaint.style = Paint.Style.FILL

    }

    override fun onChartSizeChanged() {
        val computator = chart.chartComputator
        val contentRect = computator.contentRectMinusAllMargins
        isBubbleScaledByX = contentRect.width() < contentRect.height()
    }

    override fun onChartDataChanged() {
        super.onChartDataChanged()
        val data = dataProvider.bubbleChartData
        this.hasLabels = data.hasLabels
        this.hasLabelsOnlyForSelected = data.hasLabelsOnlyForSelected
        this.valueFormatter = data.formatter

        onChartViewportChanged()
    }

    override fun onChartViewportChanged() {
        if (isViewportCalculationEnabled) {
            calculateMaxViewport()
            computator.maximumViewport = tempMaximumViewport
            computator.currentViewport = computator.maximumViewport
        }
    }

    override fun draw(canvas: Canvas) {
        drawBubbles(canvas)
        if (isTouched) {
            highlightBubbles(canvas)
        }
    }

    override fun drawUnclipped(canvas: Canvas) {}

    override fun checkTouch(touchX: Float, touchY: Float): Boolean {
        selectedValue.clear()
        val data = dataProvider.bubbleChartData
        var valueIndex = 0
        for (bubbleValue in data.values) {
            val rawRadius = processBubble(bubbleValue, bubbleCenter)

            if (ValueShape.SQUARE == bubbleValue.shape) {
                if (bubbleRect.contains(touchX, touchY)) {
                    selectedValue[valueIndex, valueIndex] = SelectedValueType.NONE
                }
            } else if (ValueShape.CIRCLE == bubbleValue.shape) {
                val diffX = touchX - bubbleCenter.x
                val diffY = touchY - bubbleCenter.y
                val touchDistance = Math.sqrt((diffX * diffX + diffY * diffY).toDouble()).toFloat()

                if (touchDistance <= rawRadius) {
                    selectedValue[valueIndex, valueIndex] = SelectedValueType.NONE
                }
            } else {
                throw IllegalArgumentException("Invalid bubble shape: " + bubbleValue.shape!!)
            }

            ++valueIndex
        }

        return isTouched
    }

    /**
     * Removes empty spaces on sides of chart(left-right for landscape, top-bottom for portrait). *This method should be
     * called after layout had been drawn*. Because most often chart is drawn as rectangle with proportions other than
     * 1:1 and bubbles have to be drawn as circles not ellipses I am unable to calculate correct margins based on chart
     * data only. I need to know chart dimension to remove extra empty spaces, that bad because viewport depends a
     * little on contentRectMinusAllMargins.
     */
    fun removeMargins() {
        val contentRect = computator.contentRectMinusAllMargins
        if (contentRect.height() == 0 || contentRect.width() == 0) {
            // View probably not yet measured, skip removing margins.
            return
        }
        val pxX = computator.computeRawDistanceX(maxRadius * bubbleScaleX)
        val pxY = computator.computeRawDistanceY(maxRadius * bubbleScaleY)
        val scaleX = computator.maximumViewport.width() / contentRect.width()
        val scaleY = computator.maximumViewport.height() / contentRect.height()
        var dx = 0f
        var dy = 0f
        if (isBubbleScaledByX) {
            dy = (pxY - pxX) * scaleY * 0.75f
        } else {
            dx = (pxX - pxY) * scaleX * 0.75f
        }

        val maxViewport = computator.maximumViewport
        maxViewport.inset(dx, dy)
        val currentViewport = computator.currentViewport
        currentViewport.inset(dx, dy)
        computator.maximumViewport = maxViewport
        computator.currentViewport = currentViewport
    }

    private fun drawBubbles(canvas: Canvas) {
        val data = dataProvider.bubbleChartData
        for (bubbleValue in data.values) {
            drawBubble(canvas, bubbleValue)
        }
    }

    private fun drawBubble(canvas: Canvas, bubbleValue: BubbleValue) {
        var rawRadius = processBubble(bubbleValue, bubbleCenter)
        // Not touched bubbles are a little smaller than touched to give user touch feedback.
        rawRadius -= touchAdditional.toFloat()
        bubbleRect.inset(touchAdditional.toFloat(), touchAdditional.toFloat())
        bubblePaint.color = bubbleValue.color
        drawBubbleShapeAndLabel(canvas, bubbleValue, rawRadius, MODE_DRAW)

    }

    private fun drawBubbleShapeAndLabel(canvas: Canvas, bubbleValue: BubbleValue, rawRadius: Float, mode: Int) {
        if (ValueShape.SQUARE == bubbleValue.shape) {
            canvas.drawRect(bubbleRect, bubblePaint)
        } else if (ValueShape.CIRCLE == bubbleValue.shape) {
            canvas.drawCircle(bubbleCenter.x, bubbleCenter.y, rawRadius, bubblePaint)
        } else {
            throw IllegalArgumentException("Invalid bubble shape: " + bubbleValue.shape!!)
        }

        if (MODE_HIGHLIGHT == mode) {
            if (hasLabels || hasLabelsOnlyForSelected) {
                drawLabel(canvas, bubbleValue, bubbleCenter.x, bubbleCenter.y)
            }
        } else if (MODE_DRAW == mode) {
            if (hasLabels) {
                drawLabel(canvas, bubbleValue, bubbleCenter.x, bubbleCenter.y)
            }
        } else {
            throw IllegalStateException("Cannot process bubble in mode: " + mode)
        }
    }

    private fun highlightBubbles(canvas: Canvas) {
        val data = dataProvider.bubbleChartData
        val bubbleValue = data.values[selectedValue.firstIndex]
        highlightBubble(canvas, bubbleValue)
    }

    private fun highlightBubble(canvas: Canvas, bubbleValue: BubbleValue) {
        val rawRadius = processBubble(bubbleValue, bubbleCenter)
        bubblePaint.color = bubbleValue.darkenColor
        drawBubbleShapeAndLabel(canvas, bubbleValue, rawRadius, MODE_HIGHLIGHT)
    }

    /**
     * Calculate bubble radius and center x and y coordinates. Center x and x will be stored in point parameter, radius
     * will be returned as float value.
     */
    private fun processBubble(bubbleValue: BubbleValue, point: PointF): Float {
        val rawX = computator.computeRawX(bubbleValue.x)
        val rawY = computator.computeRawY(bubbleValue.y)
        var radius = Math.sqrt(Math.abs(bubbleValue.z) / Math.PI).toFloat()
        var rawRadius: Float
        if (isBubbleScaledByX) {
            radius *= bubbleScaleX
            rawRadius = computator.computeRawDistanceX(radius)
        } else {
            radius *= bubbleScaleY
            rawRadius = computator.computeRawDistanceY(radius)
        }

        if (rawRadius < minRawRadius + touchAdditional) {
            rawRadius = minRawRadius + touchAdditional
        }

        bubbleCenter.set(rawX, rawY)
        if (ValueShape.SQUARE == bubbleValue.shape) {
            bubbleRect.set(rawX - rawRadius, rawY - rawRadius, rawX + rawRadius, rawY + rawRadius)
        }
        return rawRadius
    }

    private fun drawLabel(canvas: Canvas, bubbleValue: BubbleValue, rawX: Float, rawY: Float) {
        val contentRect = computator.contentRectMinusAllMargins
        val numChars = valueFormatter!!.formatChartValue(labelBuffer, bubbleValue)

        if (numChars == 0) {
            // No need to draw empty label
            return
        }

        val labelWidth = labelPaint.measureText(labelBuffer, labelBuffer.size - numChars, numChars)
        val labelHeight = Math.abs(fontMetrics.ascent)
        var left = rawX - labelWidth / 2 - labelMargin.toFloat()
        var right = rawX + labelWidth / 2 + labelMargin.toFloat()
        var top = rawY - (labelHeight / 2).toFloat() - labelMargin.toFloat()
        var bottom = rawY + (labelHeight / 2).toFloat() + labelMargin.toFloat()

        if (top < contentRect.top) {
            top = rawY
            bottom = rawY + labelHeight.toFloat() + (labelMargin * 2).toFloat()
        }
        if (bottom > contentRect.bottom) {
            top = rawY - labelHeight.toFloat() - (labelMargin * 2).toFloat()
            bottom = rawY
        }
        if (left < contentRect.left) {
            left = rawX
            right = rawX + labelWidth + (labelMargin * 2).toFloat()
        }
        if (right > contentRect.right) {
            left = rawX - labelWidth - (labelMargin * 2).toFloat()
            right = rawX
        }

        labelBackgroundRect.set(left, top, right, bottom)
        drawLabelTextAndBackground(canvas, labelBuffer, labelBuffer.size - numChars, numChars,
                bubbleValue.darkenColor)

    }

    private fun calculateMaxViewport() {
        var maxZ = java.lang.Float.MIN_VALUE
        tempMaximumViewport.set(java.lang.Float.MAX_VALUE, java.lang.Float.MIN_VALUE, java.lang.Float.MIN_VALUE, java.lang.Float.MAX_VALUE)
        val data = dataProvider.bubbleChartData
        // TODO: Optimize.
        for (bubbleValue in data.values) {
            if (Math.abs(bubbleValue.z) > maxZ) {
                maxZ = Math.abs(bubbleValue.z)
            }
            if (bubbleValue.x < tempMaximumViewport.left) {
                tempMaximumViewport.left = bubbleValue.x
            }
            if (bubbleValue.x > tempMaximumViewport.right) {
                tempMaximumViewport.right = bubbleValue.x
            }
            if (bubbleValue.y < tempMaximumViewport.bottom) {
                tempMaximumViewport.bottom = bubbleValue.y
            }
            if (bubbleValue.y > tempMaximumViewport.top) {
                tempMaximumViewport.top = bubbleValue.y
            }
        }

        maxRadius = Math.sqrt(maxZ / Math.PI).toFloat()

        // Number 4 is determined by trials and errors method, no magic behind it:).
        bubbleScaleX = tempMaximumViewport.width() / (maxRadius * 4)
        if (bubbleScaleX == 0f) {
            // case for 0 viewport width.
            bubbleScaleX = 1f
        }

        bubbleScaleY = tempMaximumViewport.height() / (maxRadius * 4)
        if (bubbleScaleY == 0f) {
            // case for 0 viewport height.
            bubbleScaleY = 1f
        }

        // For cases when user sets different than 1 bubble scale in BubbleChartData.
        bubbleScaleX *= data.bubbleScale
        bubbleScaleY *= data.bubbleScale

        // Prevent cutting of bubbles on the edges of chart area.
        tempMaximumViewport.inset(-maxRadius * bubbleScaleX, -maxRadius * bubbleScaleY)

        minRawRadius = ChartUtils.dp2px(density, dataProvider.bubbleChartData.minBubbleRadius).toFloat()
    }

    companion object {
        private val DEFAULT_TOUCH_ADDITIONAL_DP = 4
        private val MODE_DRAW = 0
        private val MODE_HIGHLIGHT = 1
    }

}
