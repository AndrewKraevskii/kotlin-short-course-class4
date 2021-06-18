import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import org.jetbrains.skija.*
import org.jetbrains.skiko.SkiaLayer
import org.jetbrains.skiko.SkiaRenderer
import org.jetbrains.skiko.SkiaWindow
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.lang.Float.min
import java.lang.Math.sin
import java.sql.Timestamp
import java.time.Clock
import java.time.LocalTime
import java.util.*
import javax.swing.WindowConstants
import javax.swing.text.DateFormatter
import kotlin.concurrent.timer

fun main(args: Array<String>) {

    createWindow("tictaktoe (${args[0]})")
    startNetworking(args, "172.25.22.164", 2323)
}

fun createWindow(title: String) = runBlocking(Dispatchers.Swing) {
    val window = SkiaWindow()
    window.defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
    window.title = title

    window.layer.renderer = Renderer(window.layer)
    window.layer.addMouseListener(MouseListener)

    window.preferredSize = Dimension(800, 600)
    window.minimumSize = Dimension(100, 100)
    window.pack()
    window.layer.awaitRedraw()
    window.isVisible = true
}

data class Point(val x: Float, val y: Float, val isRemote: Boolean = false)

object State {
    var isServer = true
    var input: ByteReadChannel? = null
    var output: ByteWriteChannel? = null
    val points = mutableListOf<Point>()
}

val field: Field = Field(3, 3, org.jetbrains.skija.Point(0f, 0f), org.jetbrains.skija.Point(0f, 0f))

class Renderer(val layer: SkiaLayer) : SkiaRenderer {
    val typeface = Typeface.makeFromFile("fonts/JetBrainsMono-Regular.ttf")
    val paint = Paint().apply {
        color = 0xff0000ff.toInt()
        mode = PaintMode.FILL
        strokeWidth = 1f
    }
    val paintRemote = Paint().apply {
        color = 0xff00ff00.toInt()
        mode = PaintMode.FILL
        strokeWidth = 1f
    }

    override fun onRender(canvas: Canvas, width: Int, height: Int, nanoTime: Long) {
        val contentScale = layer.contentScale
        canvas.scale(contentScale, contentScale)
        val w = (width / contentScale).toInt()
        val h = (height / contentScale).toInt()

        println(System.currentTimeMillis())
        val time = System.currentTimeMillis()
        val offset = 0.1f
        field.match2window(w, h, offset + 0.04f * kotlin.math.sin(time / 3000.0).toFloat())
        field.draw(canvas)
        State.points.forEach { p ->
            canvas.drawCircle(p.x, p.y, 5f, if (p.isRemote) paintRemote else paint)
        }

        layer.needRedraw()
    }
}

object MouseListener : MouseAdapter() {
    override fun mouseClicked(event: MouseEvent?) {
        if ((event != null) /*&& field.isMyTurn*/) {
            if (event.button == MouseEvent.BUTTON1) {
                val squareNumber = field.getSquareByPosition(event.x, event.y)
                println(squareNumber)
                field.currentTurnChoise = squareNumber
                field.fieldSymbols[squareNumber] = 'X'
            }
            if (event.button == MouseEvent.BUTTON3) {
                val squareNumber = field.getSquareByPosition(event.x, event.y)
                println(squareNumber)
                field.currentTurnChoise = squareNumber
                field.fieldSymbols[squareNumber] = 'O'
            }
        }
    }

}

class Field constructor
    (
    private var fieldSize_x: Int = 3,
    private var fieldSize_y: Int = 3,
    private var fieldPosition: org.jetbrains.skija.Point,
    private var squareSize: org.jetbrains.skija.Point,
) {

    init {
        assert(fieldSize_x >= 0)
        assert(fieldSize_y >= 0)
        assert(squareSize.x >= 0)
        assert(squareSize.y >= 0)

    }

    var isMyTurn: Boolean = true
    var currentTurnChoise: Int = -1

    val fieldPaint = Paint().apply {
        color = 0xFF000000.toInt()
        mode = PaintMode.STROKE
        strokeWidth = 2f
    }

    val crossPaint = Paint().apply {
        color = 0xFFFF0000.toInt()
        strokeWidth = 3f
    }

    val circlePaint = Paint().apply {
        color = 0xFF0000FF.toInt()
        strokeWidth = 3f
        mode = PaintMode.STROKE
    }

    val fieldSymbols = MutableList<Char>(fieldSize_x * fieldSize_y) { '_' }

    private fun drawCross(
        canvas: Canvas,
        point: org.jetbrains.skija.Point,
        squareSize: org.jetbrains.skija.Point,
        offset: Float
    ) {
        canvas.drawLine(
            point.x + squareSize.x * offset, point.y + squareSize.y * offset,
            point.x + squareSize.x * (1 - offset), point.y + squareSize.y * (1 - offset), crossPaint
        )
        canvas.drawLine(
            point.x + squareSize.x * (1 - offset), point.y + squareSize.y * offset,
            point.x + squareSize.x * offset, point.y + squareSize.y * (1 - offset), crossPaint
        )
    }

    private fun drawZero(
        canvas: Canvas,
        point: org.jetbrains.skija.Point,
        squareSize: org.jetbrains.skija.Point,
        offset: Float
    ) {
        canvas.drawCircle(
            point.x + squareSize.x / 2,
            point.y + squareSize.y / 2,
            min(squareSize.x, squareSize.y) * (0.5f - offset),
            circlePaint
        )
    }


    fun match2window(w: Int, h: Int, offset: Float = 0.1f) {
        if (h <= w) {
            this.fieldPosition = org.jetbrains.skija.Point(w / 2 - h * (0.5f - offset), h * offset)
            this.squareSize =
                org.jetbrains.skija.Point(h * (1 - offset * 2) / fieldSize_y, h * (1 - offset * 2) / fieldSize_y)
        } else {
            this.fieldPosition = org.jetbrains.skija.Point(w * 0.1f, h / 2 - w * 0.4f)
            this.squareSize =
                org.jetbrains.skija.Point(w * (1 - offset * 2) / fieldSize_x, w * (1 - offset * 2) / fieldSize_x)
        }
    }

    fun draw(canvas: Canvas) {

//        рисуем поле

        for (index in 0..fieldSize_x) {
            canvas.drawLine(
                fieldPosition.x + index * squareSize.x,
                fieldPosition.y,
                fieldPosition.x + index * squareSize.x,
                fieldPosition.y + fieldSize_y * squareSize.y,
                fieldPaint
            )
        }
        for (index in 0..fieldSize_y) {
            canvas.drawLine(
                fieldPosition.x,
                fieldPosition.y + index * squareSize.y,
                fieldPosition.x + fieldSize_x * squareSize.x,
                fieldPosition.y + index * squareSize.y,
                fieldPaint
            )
        }

//        рисуем символы

        for (i in 0 until fieldSize_y) {
            for (j in 0 until fieldSize_x) {
                if (fieldSymbols[i * fieldSize_x + j] == 'X') {
                    drawCross(
                        canvas,
                        org.jetbrains.skija.Point(
                            (fieldPosition.x + squareSize.x * j),
                            (fieldPosition.y + squareSize.y * i)
                        ),
                        squareSize,
                        0.3f
                    )
                } else if ((fieldSymbols[i * fieldSize_x + j] == 'O')) {
                    drawZero(
                        canvas,
                        org.jetbrains.skija.Point(
                            (fieldPosition.x + squareSize.x * j),
                            (fieldPosition.y + squareSize.y * i)
                        ),
                        squareSize,
                        0.3f
                    )
                }
            }
        }

    }

    fun getSquareByPosition(x: Int, y: Int): Int {
        val dist = org.jetbrains.skija.Point(x - fieldPosition.x, y - fieldPosition.y)
        if ((dist.x < 0) or (dist.y < 0)) {
            return -1
        }
        val squareX = (dist.x / squareSize.x).toInt()
        val squareY = (dist.y / squareSize.y).toInt()
        if ((squareX >= fieldSize_x) or (squareY >= fieldSize_y))
            return -1

        return squareY * fieldSize_x + squareX
    }
}