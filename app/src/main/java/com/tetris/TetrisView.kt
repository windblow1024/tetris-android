package com.tetris

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import com.tetris.*
import kotlin.math.abs

/**
 * Custom View — renders the full game and handles touch input.
 */
class TetrisView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    val game = Game()
    private var highScoreSaved = false

    // ── Layout ──────────────────────────────────────────
    private var cellSize = 0f
    private var boardPixelW = 0f
    private var boardPixelH = 0f
    private var boardLeft = 0f
    private var boardTop = 0f
    private var panelLeft = 0f
    private var panelRight = 0f
    private var panelCenterX = 0f
    private var btnBarTop = 0f
    private var btnSize = 0f

    // ── Paint objects ───────────────────────────────────
    private val bgPaint = Paint().apply { color = BG_COLOR }
    private val gridPaint = Paint().apply { color = GRID_COLOR; style = Paint.Style.STROKE; strokeWidth = 1f }
    private val panelPaint = Paint().apply { color = PANEL_BG; style = Paint.Style.FILL }
    private val textPaint = Paint().apply { color = WHITE; isAntiAlias = true }
    private val ghostPaint = Paint().apply { alpha = GHOST_ALPHA; style = Paint.Style.FILL }
    private val cellBorderPaint = Paint().apply {
        style = Paint.Style.STROKE; strokeWidth = 2f; color = WHITE
    }
    private val cellFillPaint = Paint().apply { style = Paint.Style.FILL }
    private val miniPaint = Paint().apply { style = Paint.Style.FILL }
    private val buttonPaint = Paint().apply {
        color = 0x33FFFFFF.toInt(); style = Paint.Style.FILL; isAntiAlias = true
    }
    private val buttonTextPaint = Paint().apply {
        color = WHITE; isAntiAlias = true; textAlign = Paint.Align.CENTER
    }
    private val overlayPaint = Paint().apply { color = 0xAA000000.toInt() }

    // ── Touch / DAS ────────────────────────────────────
    private var lastTapTime = 0L
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var isDragging = false

    // Repeating action for DAS
    private var dasAction: String? = null          // "left" / "right" / "soft"
    private var dasStartTime = 0L
    private var dasLastActionTime = 0L

    // Button rects
    private val leftBtn = RectF()
    private val rightBtn = RectF()
    private val softBtn = RectF()
    private val rotateBtn = RectF()
    private val hardBtn = RectF()
    private val holdBtn = RectF()

    // ── Game loop ──────────────────────────────────────
    private val handler = Handler(Looper.getMainLooper())
    private var lastFrameNanos = 0L
    private val frameRunnable = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtime()
            val dtMs = if (lastFrameNanos == 0L) FRAME_MS.toFloat() else (now - lastFrameNanos).toFloat()
            lastFrameNanos = now

            tickDAS(now)
            game.update(dtMs)

            if (game.state == GameState.GAME_OVER && !highScoreSaved) {
                val hs = game.saveHighScore()
                if (hs > 0) listener?.onHighScoreUpdated(hs)
                highScoreSaved = true
            }

            invalidate()
            handler.postDelayed(this, FRAME_MS.toLong())
        }
    }

    // ── Listener ───────────────────────────────────────
    interface Listener {
        fun onHighScoreUpdated(score: Int)
    }
    var listener: Listener? = null

    // ── Lifecycle ──────────────────────────────────────
    fun startLoop() {
        highScoreSaved = false
        lastFrameNanos = 0L
        handler.post(frameRunnable)
    }

    fun stopLoop() {
        handler.removeCallbacks(frameRunnable)
    }

    // ── Measure ────────────────────────────────────────
    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)

        val usableW = w.toFloat()
        val usableH = h.toFloat()

        // Reserve space for control buttons at bottom (~13 % of height)
        val controlH = (usableH * 0.13f).coerceAtLeast(80f)
        btnBarTop = usableH - controlH
        val gameH = btnBarTop
        btnSize = (usableW / 6f).coerceAtMost(controlH * 0.75f)

        // Side panel takes ~28 % of width
        val panelW = usableW * 0.28f
        panelRight = usableW - 8f
        panelLeft = panelRight - panelW
        boardPixelW = panelLeft - 8f
        boardPixelH = gameH - 8f

        cellSize = minOf(boardPixelW / BOARD_WIDTH, boardPixelH / BOARD_HEIGHT)
        boardPixelW = cellSize * BOARD_WIDTH
        boardPixelH = cellSize * BOARD_HEIGHT
        boardLeft = (panelLeft - boardPixelW) / 2f
        boardTop = 4f
        panelCenterX = (panelLeft + panelRight) / 2f

        // Button layout
        val cw = usableW / 7f
        val btnY = btnBarTop + (controlH - btnSize) / 2f
        leftBtn.set(cw * 0.5f - btnSize / 2, btnY, cw * 0.5f + btnSize / 2, btnY + btnSize)
        softBtn.set(cw * 1.5f - btnSize / 2, btnY, cw * 1.5f + btnSize / 2, btnY + btnSize)
        rotateBtn.set(cw * 2.5f - btnSize / 2, btnY, cw * 2.5f + btnSize / 2, btnY + btnSize)
        hardBtn.set(cw * 3.5f - btnSize / 2, btnY, cw * 3.5f + btnSize / 2, btnY + btnSize)
        holdBtn.set(cw * 4.5f - btnSize / 2, btnY, cw * 4.5f + btnSize / 2, btnY + btnSize)
        rightBtn.set(cw * 5.5f - btnSize / 2, btnY, cw * 5.5f + btnSize / 2, btnY + btnSize)
    }

    // ── Draw ───────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        drawBoard(canvas)
        if (game.state != GameState.READY) {
            drawHoldPanel(canvas)
            drawNextPanel(canvas)
            drawScorePanel(canvas)
            if (game.current != null) {
                drawGhost(canvas, game.current!!)
                drawPiece(canvas, game.current!!)
            }
        }
        drawButtons(canvas)

        when (game.state) {
            GameState.READY -> drawReadyScreen(canvas)
            GameState.PAUSED -> drawOverlay(canvas, "PAUSED", "Press P to continue", 36f)
            GameState.GAME_OVER -> drawOverlay(canvas, "GAME OVER",
                "Score: ${game.score}\nBest: ${game.highScore}\nTap to restart", 28f)
            else -> {}
        }
    }

    // ── Board ──────────────────────────────────────────
    private fun drawBoard(canvas: Canvas) {
        val l = boardLeft; val t = boardTop
        val bw = boardPixelW; val bh = boardPixelH

        // Background
        panelPaint.color = PANEL_BG
        canvas.drawRect(l, t, l + bw, t + bh, panelPaint)

        // Grid lines
        for (c in 0..BOARD_WIDTH) {
            val x = l + c * cellSize
            canvas.drawLine(x, t, x, t + bh, gridPaint)
        }
        for (r in 0..BOARD_HEIGHT) {
            val y = t + r * cellSize
            canvas.drawLine(l, y, l + bw, y, gridPaint)
        }

        // Locked cells
        for (r in 0 until BOARD_HEIGHT) {
            for (c in 0 until BOARD_WIDTH) {
                val color = game.board.grid[r + HIDDEN_ROWS][c]
                if (color != 0) drawCell(canvas, c, r, color)
            }
        }
    }

    private fun drawCell(canvas: Canvas, col: Int, row: Int, color: Int) {
        val x = boardLeft + col * cellSize + 1f
        val y = boardTop + row * cellSize + 1f
        val s = cellSize - 2f
        cellFillPaint.color = color
        canvas.drawRect(x, y, x + s, y + s, cellFillPaint)
        // Highlight border
        cellBorderPaint.color = lighten(color)
        canvas.drawRect(x, y, x + s, y + s, cellBorderPaint)
    }

    private fun drawPiece(canvas: Canvas, p: Tetromino) {
        val shape = p.shape
        for (r in shape.indices) {
            for (c in shape[r].indices) {
                if (shape[r][c] == 0) continue
                val boardRow = p.y + r
                if (boardRow < 0) continue
                drawCell(canvas, p.x + c, boardRow, p.color)
            }
        }
    }

    private fun drawGhost(canvas: Canvas, p: Tetromino) {
        val gy = game.board.getGhostY(p)
        val shape = p.shape
        ghostPaint.color = desaturate(p.color, 0.5f)
        ghostPaint.alpha = GHOST_ALPHA
        for (r in shape.indices) {
            for (c in shape[r].indices) {
                if (shape[r][c] == 0) continue
                val boardRow = gy + r
                if (boardRow < 0) continue
                val x = boardLeft + (p.x + c) * cellSize + 2f
                val y = boardTop + boardRow * cellSize + 2f
                val s = cellSize - 4f
                canvas.drawRect(x, y, x + s, y + s, ghostPaint)
            }
        }
    }

    // ── Panels ─────────────────────────────────────────
    private fun drawHoldPanel(canvas: Canvas) {
        val cx = panelCenterX; val top = 8f; val pw = panelRight - panelLeft - 16f
        val ph = cellSize * 3f + 40f
        canvas.drawRect(cx - pw / 2, top, cx + pw / 2, top + ph, panelPaint)
        textPaint.textSize = 20f; textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("HOLD", cx, top + 24f, textPaint)
        if (game.holdPiece != null) {
            drawMiniPiece(canvas, game.holdPiece!!, cx, top + 40f, cellSize * 0.55f)
        }
    }

    private fun drawNextPanel(canvas: Canvas) {
        val cx = panelCenterX
        val top = cellSize * 3f + 64f
        val pw = panelRight - panelLeft - 16f
        val ph = cellSize * 3f + 40f
        canvas.drawRect(cx - pw / 2, top, cx + pw / 2, top + ph, panelPaint)
        textPaint.textSize = 20f; textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("NEXT", cx, top + 24f, textPaint)
        if (game.nextPiece != null) {
            drawMiniPiece(canvas, game.nextPiece!!, cx, top + 40f, cellSize * 0.55f)
        }
    }

    private fun drawMiniPiece(canvas: Canvas, p: Tetromino, cx: Float, top: Float, scale: Float) {
        val shape = Tetromino.getShapes(p.type)[0]
        val rows = shape.size
        val cols = shape[0].size
        val totalW = cols * scale; val totalH = rows * scale
        val ox = cx - totalW / 2f; val oy = top
        miniPaint.color = p.color
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (shape[r][c] == 0) continue
                val x = ox + c * scale; val y = oy + r * scale
                canvas.drawRect(x, y, x + scale, y + scale, miniPaint)
            }
        }
    }

    private fun drawScorePanel(canvas: Canvas) {
        val cx = panelCenterX; val pw = panelRight - panelLeft - 16f
        val top = cellSize * 6.5f + 80f
        val ph = cellSize * 4f
        canvas.drawRect(cx - pw / 2, top, cx + pw / 2, top + ph, panelPaint)
        textPaint.textSize = 18f; textPaint.textAlign = Paint.Align.LEFT
        val xx = cx - pw / 2 + 12f
        textPaint.color = DARK_GRAY
        canvas.drawText("SCORE", xx, top + 24f, textPaint)
        textPaint.color = WHITE; textPaint.textSize = 30f
        canvas.drawText("${game.score}", xx, top + 54f, textPaint)
        textPaint.textSize = 16f; textPaint.color = DARK_GRAY
        canvas.drawText("BEST", xx, top + 80f, textPaint)
        textPaint.color = WHITE; textPaint.textSize = 24f
        canvas.drawText("${game.highScore}", xx, top + 106f, textPaint)
        textPaint.color = DARK_GRAY; textPaint.textSize = 16f
        canvas.drawText("LEVEL", xx, top + 130f, textPaint)
        textPaint.color = WHITE; textPaint.textSize = 24f
        canvas.drawText("${game.level}", xx, top + 156f, textPaint)
        textPaint.color = DARK_GRAY; textPaint.textSize = 16f
        canvas.drawText("LINES", xx, top + 180f, textPaint)
        textPaint.color = WHITE; textPaint.textSize = 24f
        canvas.drawText("${game.linesCleared}", xx, top + 206f, textPaint)
    }

    // ── Buttons ─────────────────────────────────────────
    private fun drawButtons(canvas: Canvas) {
        val r = btnSize / 4f
        fun drawBtn(rect: RectF, label: String) {
            buttonPaint.color = 0x33FFFFFF.toInt()
            canvas.drawRoundRect(rect, r, r, buttonPaint)
            buttonTextPaint.textSize = btnSize * 0.45f
            canvas.drawText(label, rect.centerX(), rect.centerY() + btnSize * 0.15f, buttonTextPaint)
        }
        drawBtn(leftBtn, "◀")
        drawBtn(rightBtn, "▶")
        drawBtn(softBtn, "▼")
        drawBtn(rotateBtn, "▲")
        drawBtn(hardBtn, "⬇")
        drawBtn(holdBtn, "C")
    }

    // ── Overlays ────────────────────────────────────────
    private fun drawReadyScreen(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 48f; textPaint.color = WHITE
        canvas.drawText("TETRIS", width / 2f, height * 0.35f, textPaint)
        textPaint.textSize = 22f
        canvas.drawText("Tap to start", width / 2f, height * 0.42f, textPaint)
        textPaint.textSize = 16f; textPaint.color = DARK_GRAY
        canvas.drawText("Best: ${game.highScore}", width / 2f, height * 0.48f, textPaint)
    }

    private fun drawOverlay(canvas: Canvas, title: String, subtitle: String, subSize: Float) {
        canvas.drawRect(boardLeft, boardTop, boardLeft + boardPixelW, boardTop + boardPixelH, overlayPaint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 42f; textPaint.color = WHITE
        canvas.drawText(title, boardLeft + boardPixelW / 2f, boardTop + boardPixelH * 0.32f, textPaint)
        textPaint.textSize = subSize
        canvas.drawText(subtitle, boardLeft + boardPixelW / 2f, boardTop + boardPixelH * 0.50f, textPaint)
    }

    // ── Touch ──────────────────────────────────────────
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x; val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = x; touchStartY = y
                isDragging = false
                lastTapTime = if (event.eventTime - lastTapTime < 300) 0 else event.eventTime

                when {
                    game.state == GameState.READY -> { game.startGame(); return true }
                    game.state == GameState.GAME_OVER -> { game.restart(); return true }
                }
                if (game.state != GameState.PLAYING) return true

                // Map touch to button
                when {
                    leftBtn.contains(x, y) -> { game.moveLeft(); startDAS("left") }
                    rightBtn.contains(x, y) -> { game.moveRight(); startDAS("right") }
                    softBtn.contains(x, y) -> { game.softDrop(); startDAS("soft") }
                    rotateBtn.contains(x, y) -> game.rotate()
                    hardBtn.contains(x, y) -> game.hardDrop()
                    holdBtn.contains(x, y) -> game.hold()
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (abs(x - touchStartX) > 20 || abs(y - touchStartY) > 20) {
                    isDragging = true
                    dasAction = null  // cancel DAS when dragging
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                dasAction = null
                if (!isDragging && game.state == GameState.PLAYING) {
                    // Tap on board area → rotate
                    val boardRight = boardLeft + boardPixelW
                    val boardBottom = boardTop + boardPixelH
                    if (x >= boardLeft && x <= boardRight && y >= boardTop && y <= boardBottom) {
                        game.rotate()
                    }
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    // ── DAS ─────────────────────────────────────────────
    private fun startDAS(action: String) {
        dasAction = action
        dasStartTime = SystemClock.elapsedRealtime()
        dasLastActionTime = dasStartTime
    }

    private fun tickDAS(now: Long) {
        val action = dasAction ?: return
        if (now - dasStartTime < DAS_DELAY_MS) return
        if (now - dasLastActionTime < DAS_INTERVAL_MS) return
        dasLastActionTime = now
        when (action) {
            "left" -> game.moveLeft()
            "right" -> game.moveRight()
            "soft" -> game.softDrop()
        }
    }

    // ── Colour helpers ──────────────────────────────────
    private fun lighten(color: Int): Int {
        val r = (color shr 16 and 0xFF).coerceAtMost(200) + 55
        val g = (color shr 8 and 0xFF).coerceAtMost(200) + 55
        val b = (color and 0xFF).coerceAtMost(200) + 55
        return 0xFF shl 24 or (r shl 16) or (g shl 8) or b
    }

    private fun desaturate(color: Int, factor: Float): Int {
        val r = (color shr 16 and 0xFF).toFloat()
        val g = (color shr 8 and 0xFF).toFloat()
        val b = (color and 0xFF).toFloat()
        val avg = (r + g + b) / 3f
        val nr = (r + (avg - r) * factor).toInt().coerceIn(0, 255)
        val ng = (g + (avg - g) * factor).toInt().coerceIn(0, 255)
        val nb = (b + (avg - b) * factor).toInt().coerceIn(0, 255)
        return 0xFF shl 24 or (nr shl 16) or (ng shl 8) or nb
    }
}