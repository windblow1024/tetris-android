package com.tetris

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.tetris.*
import kotlin.math.abs
import kotlin.math.min

class TetrisView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    val game = Game()
    val soundManager = SoundManager(context).also { it.loadAll() }
    private var highScoreSaved = false

    // Layout
    private var cellSize = 0f
    private var boardPixelW = 0f
    private var boardPixelH = 0f
    private var boardLeft = 0f
    private var boardTop = 0f
    private var panelLeft = 0f
    private var panelRight = 0f
    private var panelCenterX = 0f
    private var btnBarTop = 0f

    // Paint objects
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1f
    }
    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val panelStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.5f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaintShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40000000.toInt() }
    private val ghostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        alpha = GHOST_ALPHA; style = Paint.Style.FILL
    }
    private val cellFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val cellHlPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val cellBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.5f
    }
    private val miniPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val miniHlPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33FFFFFF.toInt(); style = Paint.Style.FILL
    }
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xBB000000.toInt() }

    private val btnBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val btnShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x40000000.toInt(); style = Paint.Style.FILL
    }
    private val btnTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = WHITE; textAlign = Paint.Align.CENTER
    }
    private val btnBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1f
    }

    // Touch / DAS
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var isDragging = false
    private var dasAction: String? = null
    private var dasStartTime = 0L
    private var dasLastActionTime = 0L

    // Button rects — all equal
    private val btnRects = Array(6) { RectF() }
    private val ghostBtn = RectF()
    private val BTN_LEFT = 0; private val BTN_RIGHT = 1; private val BTN_SOFT = 2
    private val BTN_ROTATE = 3; private val BTN_HARD = 4; private val BTN_HOLD = 5

    // Game loop
    private val handler = Handler(Looper.getMainLooper())
    private var lastFrameNanos = 0L
    private val frameRunnable = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtime()
            val dtMs = if (lastFrameNanos == 0L) FRAME_MS.toFloat() else (now - lastFrameNanos).toFloat()
            lastFrameNanos = now

            tickDAS(now)
            game.update(dtMs)

            val evts = game.events.toList()
            game.events.clear()
            for (e in evts) {
                when (e) {
                    GameEvent.PIECE_MOVED -> soundManager.play(SoundManager.Sound.MOVE)
                    GameEvent.PIECE_ROTATED -> soundManager.play(SoundManager.Sound.ROTATE)
                    GameEvent.SOFT_DROP -> soundManager.play(SoundManager.Sound.SOFT_DROP)
                    GameEvent.HARD_DROP -> soundManager.play(SoundManager.Sound.HARD_DROP)
                    GameEvent.LOCK -> soundManager.play(SoundManager.Sound.LOCK)
                    GameEvent.LINE_CLEAR -> soundManager.play(SoundManager.Sound.CLEAR)
                    GameEvent.TETRIS -> soundManager.play(SoundManager.Sound.TETRIS)
                    GameEvent.LEVEL_UP -> soundManager.play(SoundManager.Sound.LEVEL_UP)
                    GameEvent.GAME_OVER -> soundManager.play(SoundManager.Sound.GAME_OVER)
                    GameEvent.HOLD -> soundManager.play(SoundManager.Sound.HOLD)
                }
            }

            if (game.state == GameState.GAME_OVER && !highScoreSaved) {
                val hs = game.saveHighScore()
                if (hs > 0) listener?.onHighScoreUpdated(hs)
                highScoreSaved = true
            }

            invalidate()
            handler.postDelayed(this, FRAME_MS.toLong())
        }
    }

    interface Listener { fun onHighScoreUpdated(score: Int) }
    var listener: Listener? = null

    fun startLoop() {
        highScoreSaved = false; lastFrameNanos = 0L; handler.post(frameRunnable)
    }

    fun stopLoop() {
        handler.removeCallbacks(frameRunnable); soundManager.release()
    }

    // Measure
    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        val uw = w.toFloat(); val uh = h.toFloat()

        val controlH = (uh * 0.20f).coerceAtLeast(140f)
        btnBarTop = uh - controlH
        val gameH = btnBarTop

        val panelW = uw * 0.26f
        panelRight = uw - 6f; panelLeft = panelRight - panelW
        boardPixelW = panelLeft - 6f; boardPixelH = gameH - 6f

        cellSize = minOf(boardPixelW / BOARD_WIDTH, boardPixelH / BOARD_HEIGHT)
        boardPixelW = cellSize * BOARD_WIDTH
        boardPixelH = cellSize * BOARD_HEIGHT
        boardLeft = (panelLeft - boardPixelW) / 2f
        boardTop = 3f
        panelCenterX = (panelLeft + panelRight) / 2f

        // All 6 buttons equal: 2 rows x 3 cols
        val rowH = controlH / 2f; val colW = uw / 3f
        val btnW = colW * 0.80f; val btnH = rowH * 0.76f
        val padX = (colW - btnW) / 2f; val padY = (rowH - btnH) / 2f

        val row0Y = btnBarTop + padY
        btnRects[BTN_ROTATE].set(padX, row0Y, padX + btnW, row0Y + btnH)
        btnRects[BTN_HARD].set(colW + padX, row0Y, colW + padX + btnW, row0Y + btnH)
        btnRects[BTN_HOLD].set(colW * 2 + padX, row0Y, colW * 2 + padX + btnW, row0Y + btnH)

        val row1Y = btnBarTop + rowH + padY
        btnRects[BTN_LEFT].set(padX, row1Y, padX + btnW, row1Y + btnH)
        btnRects[BTN_SOFT].set(colW + padX, row1Y, colW + padX + btnW, row1Y + btnH)
        btnRects[BTN_RIGHT].set(colW * 2 + padX, row1Y, colW * 2 + padX + btnW, row1Y + btnH)

        // Ghost toggle in right panel
        ghostBtn.set(panelLeft + 6f, btnBarTop - 36f, panelRight - 6f, btnBarTop - 6f)
    }

    // Draw
    override fun onDraw(canvas: Canvas) {
        drawBackground(canvas)
        drawBoard(canvas)

        when (game.state) {
            GameState.READY -> { drawReadyScreen(canvas); drawButtons(canvas); return }
            GameState.PLAYING, GameState.PAUSED -> {
                drawHoldPanel(canvas)
                drawNextPanel(canvas)
                drawScorePanel(canvas)
                if (game.current != null) {
                    if (game.ghostEnabled) drawGhost(canvas, game.current!!)
                    drawPiece(canvas, game.current!!)
                }
            }
            GameState.GAME_OVER -> {
                drawHoldPanel(canvas); drawNextPanel(canvas); drawScorePanel(canvas)
                drawOverlay(canvas)
            }
        }

        drawButtons(canvas)
        if (game.state != GameState.READY && game.state != GameState.GAME_OVER) drawGhostIndicator(canvas)
        if (game.state == GameState.PAUSED) drawOverlay(canvas)
    }

    // Background
    private fun drawBackground(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val gradient = LinearGradient(0f, 0f, w, h,
            intArrayOf(0xFF0d0d1a.toInt(), 0xFF1a1a2e.toInt(), 0xFF0f2040.toInt()),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        bgPaint.shader = gradient; canvas.drawRect(0f, 0f, w, h, bgPaint); bgPaint.shader = null
    }

    // Board
    private fun drawBoard(canvas: Canvas) {
        val l = boardLeft; val t = boardTop
        val bw = boardPixelW; val bh = boardPixelH
        val r = cellSize * 0.35f

        // Outer glow
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 3f; color = 0x336688FF.toInt()
        }
        canvas.drawRoundRect(l - 3f, t - 3f, l + bw + 3f, t + bh + 3f, r + 1f, r + 1f, glow)

        panelPaint.color = 0x22000000.toInt()
        canvas.drawRoundRect(l - 2f, t - 2f, l + bw + 2f, t + bh + 2f, r, r, panelPaint)
        panelStrokePaint.color = 0x558888FF.toInt()
        canvas.drawRoundRect(l - 2f, t - 2f, l + bw + 2f, t + bh + 2f, r, r, panelStrokePaint)

        panelPaint.color = 0x11000000.toInt()
        canvas.drawRect(l, t, l + bw, t + bh, panelPaint)

        gridPaint.color = GRID_COLOR
        for (c in 1 until BOARD_WIDTH) { val x = l + c * cellSize; canvas.drawLine(x, t, x, t + bh, gridPaint) }
        for (r in 1 until BOARD_HEIGHT) { val y = t + r * cellSize; canvas.drawLine(l, y, l + bw, y, gridPaint) }

        for (r in 0 until BOARD_HEIGHT)
            for (c in 0 until BOARD_WIDTH) {
                val col = game.board.grid[r + HIDDEN_ROWS][c]
                if (col != 0) drawCell(canvas, c, r, col)
            }
    }

    private fun drawCell(canvas: Canvas, col: Int, row: Int, cellColor: Int) {
        val x = boardLeft + col * cellSize + 1f
        val y = boardTop + row * cellSize + 1f
        val s = cellSize - 2f
        cellFillPaint.color = cellColor; canvas.drawRect(x, y, x + s, y + s, cellFillPaint)
        cellHlPaint.color = lighten(cellColor, 0.35f); cellHlPaint.alpha = 60
        canvas.drawRect(x, y, x + s, y + s * 0.22f, cellHlPaint)
        cellHlPaint.color = darken(cellColor, 0.25f); cellHlPaint.alpha = 80
        canvas.drawRect(x, y + s * 0.8f, x + s, y + s, cellHlPaint)
        cellBorderPaint.color = lighten(cellColor, 0.5f)
        canvas.drawRect(x, y, x + s, y + s, cellBorderPaint)
    }

    private fun drawPiece(canvas: Canvas, p: Tetromino) {
        for (r in p.shape.indices) for (c in p.shape[r].indices) {
            if (p.shape[r][c] == 0) continue
            val br = p.y + r - HIDDEN_ROWS; if (br < 0) continue
            drawCell(canvas, p.x + c, br, p.color)
        }
    }

    private fun drawGhost(canvas: Canvas, p: Tetromino) {
        val gy = game.board.getGhostY(p)
        ghostPaint.color = desaturate(p.color, 0.6f); ghostPaint.alpha = GHOST_ALPHA
        for (r in p.shape.indices) for (c in p.shape[r].indices) {
            if (p.shape[r][c] == 0) continue
            val br = gy + r - HIDDEN_ROWS; if (br < 0) continue
            val x = boardLeft + (p.x + c) * cellSize + 2f
            val y = boardTop + br * cellSize + 2f; val s = cellSize - 4f
            canvas.drawRect(x, y, x + s, y + s, ghostPaint)
            ghostPaint.style = Paint.Style.STROKE; ghostPaint.strokeWidth = 2f
            ghostPaint.alpha = (GHOST_ALPHA * 1.3f).toInt().coerceAtMost(255)
            canvas.drawRect(x, y, x + s, y + s, ghostPaint)
            ghostPaint.style = Paint.Style.FILL; ghostPaint.alpha = GHOST_ALPHA
        }
    }

    // Panels
    private fun drawPanelBg(canvas: Canvas, cx: Float, top: Float, pw: Float, ph: Float) {
        val r = 12f; panelPaint.color = 0x22000000.toInt()
        canvas.drawRoundRect(cx - pw / 2 - 1f, top - 1f, cx + pw / 2 + 1f, top + ph + 1f, r, r, panelPaint)
        panelStrokePaint.color = 0x338888FF.toInt()
        canvas.drawRoundRect(cx - pw / 2 - 1f, top - 1f, cx + pw / 2 + 1f, top + ph + 1f, r, r, panelStrokePaint)
    }

    private fun drawHoldPanel(canvas: Canvas) {
        val cx = panelCenterX; val top = 8f; val pw = panelRight - panelLeft - 12f
        val ph = cellSize * 3f + 48f; drawPanelBg(canvas, cx, top, pw, ph)
        textPaint.color = 0xAAFFFFFF.toInt(); textPaint.textSize = 22f; textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("HOLD", cx, top + 26f, textPaint)
        if (game.holdPiece != null) drawMiniPiece(canvas, game.holdPiece!!, cx, top + 38f, cellSize * 0.55f)
    }

    private fun drawNextPanel(canvas: Canvas) {
        val cx = panelCenterX; val top = cellSize * 3f + 72f; val pw = panelRight - panelLeft - 12f
        val ph = cellSize * 3f + 48f; drawPanelBg(canvas, cx, top, pw, ph)
        textPaint.color = 0xAAFFFFFF.toInt(); textPaint.textSize = 22f; textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("NEXT", cx, top + 26f, textPaint)
        if (game.nextPiece != null) drawMiniPiece(canvas, game.nextPiece!!, cx, top + 38f, cellSize * 0.55f)
    }

    private fun drawMiniPiece(canvas: Canvas, p: Tetromino, cx: Float, top: Float, scale: Float) {
        val shape = Tetromino.getShapes(p.type)[0]
        val rows = shape.size; val cols = shape[0].size
        val ox = cx - cols * scale / 2f; val oy = top + (cellSize * 1.5f - rows * scale) / 2f
        miniPaint.color = p.color
        for (r in 0 until rows) for (c in 0 until cols) {
            if (shape[r][c] == 0) continue
            val x = ox + c * scale; val y = oy + r * scale
            canvas.drawRect(x, y, x + scale, y + scale, miniPaint)
            canvas.drawRect(x, y, x + scale, y + scale * 0.2f, miniHlPaint)
        }
    }

    private fun drawScorePanel(canvas: Canvas) {
        val cx = panelCenterX; val pw = panelRight - panelLeft - 12f
        val top = cellSize * 6.5f + 100f; val ph = cellSize * 5.5f
        drawPanelBg(canvas, cx, top, pw, ph)

        val labelSize = 20f; val valueSize = 48f
        val xx = cx - pw / 2 + 16f; val lineH = (ph - 36f) / 4f

        textPaint.color = 0xAAFFFFFF.toInt(); textPaint.textSize = labelSize; textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("SCORE", xx, top + 26f, textPaint)
        textPaint.color = 0xFFFFCC00.toInt(); textPaint.textSize = valueSize
        canvas.drawText(formatNum(game.score), xx, top + 74f, textPaint)

        textPaint.color = 0xAAFFFFFF.toInt(); textPaint.textSize = labelSize
        canvas.drawText("BEST", xx, top + lineH + 26f, textPaint)
        textPaint.color = WHITE; textPaint.textSize = valueSize
        canvas.drawText(formatNum(game.highScore), xx, top + lineH + 74f, textPaint)

        textPaint.color = 0xAAFFFFFF.toInt(); textPaint.textSize = labelSize
        canvas.drawText("LEVEL", xx, top + lineH * 2 + 26f, textPaint)
        textPaint.color = 0xFF66FF88.toInt(); textPaint.textSize = valueSize
        canvas.drawText("${game.level}", xx, top + lineH * 2 + 74f, textPaint)

        textPaint.color = 0xAAFFFFFF.toInt(); textPaint.textSize = labelSize
        canvas.drawText("LINES", xx, top + lineH * 3 + 26f, textPaint)
        textPaint.color = WHITE; textPaint.textSize = valueSize
        canvas.drawText("${game.linesCleared}", xx, top + lineH * 3 + 74f, textPaint)
    }

    private fun formatNum(v: Int): String {
        if (v >= 1000000) return "${v / 1000000}.${(v % 1000000) / 100000}M"
        if (v >= 1000) return "${v / 1000}.${(v % 1000) / 100}K"
        return v.toString()
    }

    // Ghost toggle — right panel
    private fun drawGhostIndicator(canvas: Canvas) {
        val en = game.ghostEnabled
        panelPaint.color = if (en) 0x4400FF88.toInt() else 0x22000000.toInt()
        panelStrokePaint.color = if (en) 0x8800FF88.toInt() else 0x44444444.toInt()
        val r = ghostBtn.height() / 2f
        canvas.drawRoundRect(ghostBtn, r, r, panelPaint)
        canvas.drawRoundRect(ghostBtn, r, r, panelStrokePaint)
        textPaint.textAlign = Paint.Align.CENTER; textPaint.textSize = 17f
        textPaint.color = if (en) 0xFF88FFAA.toInt() else 0xFF666666.toInt()
        canvas.drawText(if (en) "👻 ON" else "👻 OFF", ghostBtn.centerX(), ghostBtn.centerY() + 7f, textPaint)
    }

    // Buttons
    private fun btnColor(type: String): Int = when (type) {
        "rotate" -> 0xFF3B5998.toInt(); "hard" -> 0xFFC0392B.toInt(); "hold" -> 0xFF2C3E50.toInt()
        "left" -> 0xFF2980B9.toInt(); "soft" -> 0xFF16A085.toInt(); "right" -> 0xFF2980B9.toInt()
        else -> 0xFF555555.toInt()
    }
    private fun btnLabelColor(type: String): Int = when (type) {
        "rotate" -> 0xFF88AAFF.toInt(); "hard" -> 0xFFFF8888.toInt(); "hold" -> 0xFF88CCFF.toInt()
        else -> WHITE
    }
    private fun btnSubLabel(type: String): String = when (type) {
        "rotate" -> "旋"; "hard" -> "硬"; "hold" -> "存"; "left" -> "左"; "soft" -> "降"; "right" -> "右"
        else -> ""
    }

    private fun drawButtons(canvas: Canvas) {
        val entries = listOf(
            Triple(BTN_ROTATE, "↻", "rotate"),
            Triple(BTN_HARD, "⬇", "hard"),
            Triple(BTN_HOLD, "C", "hold"),
            Triple(BTN_LEFT, "◀", "left"),
            Triple(BTN_SOFT, "▼", "soft"),
            Triple(BTN_RIGHT, "▶", "right")
        )
        for ((idx, label, type) in entries) {
            val rect = btnRects[idx]; val bg = btnColor(type); val rad = rect.height() / 3.2f

            btnShadowPaint.color = 0x50000000.toInt()
            canvas.drawRoundRect(rect.left + 2f, rect.top + 2f, rect.right + 2f, rect.bottom + 2f, rad, rad, btnShadowPaint)

            val grad = LinearGradient(rect.left, rect.top, rect.left, rect.bottom,
                intArrayOf(lightenAlpha(bg, 0.18f), bg, darkenAlpha(bg, 0.25f)),
                null, Shader.TileMode.CLAMP)
            btnBgPaint.shader = grad; canvas.drawRoundRect(rect, rad, rad, btnBgPaint); btnBgPaint.shader = null

            btnBorderPaint.color = lightenAlpha(bg, 0.25f)
            canvas.drawRoundRect(rect, rad, rad, btnBorderPaint)

            val fs = rect.height() * 0.46f
            btnTextPaint.textSize = fs; btnTextPaint.color = btnLabelColor(type)
            canvas.drawText(label, rect.centerX(), rect.centerY() + fs * 0.15f, btnTextPaint)

            btnTextPaint.textSize = rect.height() * 0.17f; btnTextPaint.color = 0x99FFFFFF.toInt()
            canvas.drawText(btnSubLabel(type), rect.centerX(), rect.bottom - rect.height() * 0.10f, btnTextPaint)
        }
    }

    // Overlays
    private fun drawReadyScreen(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, overlayPaint)
        textPaintShadow.textSize = 84f; textPaintShadow.textAlign = Paint.Align.CENTER
        textPaint.textSize = 84f; textPaint.textAlign = Paint.Align.CENTER
        textPaintShadow.color = 0x40000000.toInt()
        canvas.drawText("TETRIS", w / 2f + 3f, h * 0.28f + 3f, textPaintShadow)
        textPaint.textSize = 84f; textPaint.color = 0xFF66CCFF.toInt()
        canvas.drawText("TETRIS", w / 2f, h * 0.28f, textPaint)
        textPaint.color = 0x99FFFFFF.toInt(); textPaint.textSize = 26f
        canvas.drawText("Tap to start | Best: ${formatNum(game.highScore)}", w / 2f, h * 0.36f, textPaint)
        textPaint.color = 0x77FFFFFF.toInt(); textPaint.textSize = 20f
        val hints = arrayOf("↻ 旋转 | ⬇ 硬降 | C 暂存", "◀ 左移 | ▼ 软降 | ▶ 右移")
        val yy = h * 0.72f
        for ((i, line) in hints.withIndex()) canvas.drawText(line, w / 2f, yy + i * 30f, textPaint)
    }

    private fun drawOverlay(canvas: Canvas) {
        val l = boardLeft; val t = boardTop; val bw = boardPixelW; val bh = boardPixelH
        canvas.drawRect(l, t, l + bw, t + bh, overlayPaint)
        textPaintShadow.textAlign = Paint.Align.CENTER; textPaint.textAlign = Paint.Align.CENTER
        val isOver = game.state == GameState.GAME_OVER
        val title = if (isOver) "GAME OVER" else "PAUSED"
        val tc = if (isOver) 0xFFFF6666.toInt() else 0xFF66CCFF.toInt()
        textPaint.textSize = 56f; textPaint.color = tc
        textPaintShadow.textSize = 56f; textPaintShadow.color = 0x40000000.toInt()
        canvas.drawText(title, l + bw / 2f + 2f, t + bh * 0.28f + 2f, textPaintShadow)
        canvas.drawText(title, l + bw / 2f, t + bh * 0.28f, textPaint)
        if (isOver) {
            textPaint.textSize = 36f; textPaint.color = 0xFFFFCC00.toInt()
            canvas.drawText("Score: ${game.score}", l + bw / 2f, t + bh * 0.43f, textPaint)
            textPaint.textSize = 28f; textPaint.color = 0x99FFFFFF.toInt()
            canvas.drawText("Best: ${formatNum(game.highScore)}", l + bw / 2f, t + bh * 0.52f, textPaint)
            textPaint.textSize = 26f; textPaint.color = 0x77FFFFFF.toInt()
            canvas.drawText("Touch to restart", l + bw / 2f, t + bh * 0.62f, textPaint)
        } else {
            textPaint.textSize = 28f; textPaint.color = 0x99FFFFFF.toInt()
            canvas.drawText("Tap to continue", l + bw / 2f, t + bh * 0.44f, textPaint)
        }
    }

    // Touch
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x; val y = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = x; touchStartY = y; isDragging = false
                when {
                    game.state == GameState.READY -> { game.startGame(); soundManager.play(SoundManager.Sound.LEVEL_START); return true }
                    game.state == GameState.GAME_OVER -> { game.restart(); return true }
                }
                if (game.state != GameState.PLAYING) return true
                when {
                    ghostBtn.contains(x, y) -> game.ghostEnabled = !game.ghostEnabled
                    btnRects[BTN_LEFT].contains(x, y) -> { game.moveLeft(); startDAS("left") }
                    btnRects[BTN_RIGHT].contains(x, y) -> { game.moveRight(); startDAS("right") }
                    btnRects[BTN_SOFT].contains(x, y) -> { game.softDrop(); startDAS("soft") }
                    btnRects[BTN_ROTATE].contains(x, y) -> game.rotate()
                    btnRects[BTN_HARD].contains(x, y) -> game.hardDrop()
                    btnRects[BTN_HOLD].contains(x, y) -> game.hold()
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(x - touchStartX) > 20 || abs(y - touchStartY) > 20) { isDragging = true; dasAction = null }
                return true
            }
            MotionEvent.ACTION_UP -> {
                dasAction = null
                if (!isDragging && game.state == GameState.PLAYING) {
                    val br = boardLeft + boardPixelW; val bb = boardTop + boardPixelH
                    if (x in boardLeft..br && y in boardTop..bb) game.rotate()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    // DAS
    private fun startDAS(action: String) {
        dasAction = action; dasStartTime = SystemClock.elapsedRealtime(); dasLastActionTime = dasStartTime
    }
    private fun tickDAS(now: Long) {
        val a = dasAction ?: return
        if (now - dasStartTime < DAS_DELAY_MS || now - dasLastActionTime < DAS_INTERVAL_MS) return
        dasLastActionTime = now
        when (a) { "left" -> game.moveLeft(); "right" -> game.moveRight(); "soft" -> game.softDrop() }
    }

    // Colour helpers
    private fun lighten(c: Int, f: Float = 0.4f): Int {
        val a = c shr 24 and 0xFF; val r = min((c shr 16 and 0xFF) + (255 - (c shr 16 and 0xFF)) * f, 255f).toInt()
        val g = min((c shr 8 and 0xFF) + (255 - (c shr 8 and 0xFF)) * f, 255f).toInt()
        val b = min((c and 0xFF) + (255 - (c and 0xFF)) * f, 255f).toInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
    private fun darken(c: Int, f: Float): Int {
        val a = c shr 24 and 0xFF; val r = ((c shr 16 and 0xFF).toFloat() * (1f - f)).toInt().coerceIn(0, 255)
        val g = ((c shr 8 and 0xFF).toFloat() * (1f - f)).toInt().coerceIn(0, 255)
        val b = ((c and 0xFF).toFloat() * (1f - f)).toInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
    private fun darkenAlpha(c: Int, f: Float): Int {
        val a = (c shr 24 and 0xFF).toFloat(); val r = (c shr 16 and 0xFF).toFloat()
        val g = (c shr 8 and 0xFF).toFloat(); val b = (c and 0xFF).toFloat()
        return ((a * 0.7f).toInt() shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
    }
    private fun lightenAlpha(c: Int, f: Float): Int {
        val a = (c shr 24 and 0xFF).toFloat(); val r = (c shr 16 and 0xFF).toFloat()
        val g = (c shr 8 and 0xFF).toFloat(); val b = (c and 0xFF).toFloat()
        return (min(a + 255 * f, 255f).toInt() shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
    }
    private fun desaturate(c: Int, f: Float): Int {
        val a = c shr 24 and 0xFF; val r = (c shr 16 and 0xFF).toFloat()
        val g = (c shr 8 and 0xFF).toFloat(); val b = (c and 0xFF).toFloat()
        val avg = (r + g + b) / 3f
        return (a shl 24) or ((r + (avg - r) * f).toInt().coerceIn(0, 255) shl 16) or
                ((g + (avg - g) * f).toInt().coerceIn(0, 255) shl 8) or
                ((b + (avg - b) * f).toInt().coerceIn(0, 255))
    }
}