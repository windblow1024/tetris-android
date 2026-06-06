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

/**
 * Custom View — renders the full game and handles touch input.
 */
class TetrisView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    val game = Game()
    val soundManager = SoundManager(context).also { it.loadAll() }
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

    // ── Paint objects ───────────────────────────────────
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GRID_COLOR; style = Paint.Style.STROKE; strokeWidth = 1f
    }
    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val panelStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.5f; color = 0x44FFFFFF.toInt()
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaintShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x40000000.toInt()
    }
    private val ghostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        alpha = GHOST_ALPHA; style = Paint.Style.FILL
    }
    private val cellBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f; color = WHITE
    }
    private val cellFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val miniPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xBB000000.toInt() }

    // Button paints
    private val btnBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val btnShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x40000000.toInt(); style = Paint.Style.FILL
    }
    private val btnTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = WHITE; textAlign = Paint.Align.CENTER
    }
    private val btnSubTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; color = 0x88FFFFFF.toInt()
    }

    // ── Touch / DAS ────────────────────────────────────
    private var lastTapTime = 0L
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var isDragging = false

    private var dasAction: String? = null
    private var dasStartTime = 0L
    private var dasLastActionTime = 0L

    // Button rects
    private val leftBtn = RectF()
    private val rightBtn = RectF()
    private val softBtn = RectF()
    private val rotateBtn = RectF()
    private val hardBtn = RectF()
    private val holdBtn = RectF()
    private val ghostBtn = RectF()

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

            // Play sounds for events
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
        soundManager.release()
    }

    // ── Measure ────────────────────────────────────────
    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)

        val usableW = w.toFloat()
        val usableH = h.toFloat()

        // Bottom controls take ~22 % height
        val controlH = (usableH * 0.22f).coerceAtLeast(150f)
        btnBarTop = usableH - controlH
        val gameH = btnBarTop

        // Side panel ~26 % of width
        val panelW = usableW * 0.26f
        panelRight = usableW - 6f
        panelLeft = panelRight - panelW
        boardPixelW = panelLeft - 6f
        boardPixelH = gameH - 6f

        cellSize = minOf(boardPixelW / BOARD_WIDTH, boardPixelH / BOARD_HEIGHT)
        boardPixelW = cellSize * BOARD_WIDTH
        boardPixelH = cellSize * BOARD_HEIGHT
        boardLeft = (panelLeft - boardPixelW) / 2f
        boardTop = 3f
        panelCenterX = (panelLeft + panelRight) / 2f

        // ── Symmetric 2-row button layout ──────────────
        // Row 2 (bottom, bigger): [◀ left]   [▼ soft]   [▶ right]
        // Row 1 (top, smaller):   [↻ rotate] [⬇⬇ hard] [C hold]
        val row2H = controlH * 0.56f
        val row1H = controlH * 0.44f
        val row2Y = btnBarTop + (controlH - row2H)
        val row1Y = btnBarTop

        // 3 equal columns using full width
        val colW = usableW / 3f
        val btnW = colW * 0.82f
        val btnPad = (colW - btnW) / 2f

        // Row 2 — centred by default since colW = usableW/3
        val btnH2 = row2H * 0.82f
        val topMargin2 = (row2H - btnH2) / 2f
        leftBtn.set(btnPad, row2Y + topMargin2, btnPad + btnW, row2Y + topMargin2 + btnH2)
        softBtn.set(colW + btnPad, row2Y + topMargin2, colW + btnPad + btnW, row2Y + topMargin2 + btnH2)
        rightBtn.set(colW * 2 + btnPad, row2Y + topMargin2, colW * 2 + btnPad + btnW, row2Y + topMargin2 + btnH2)

        // Row 1 — same centering
        val btnH1 = row1H * 0.78f
        val topMargin1 = (row1H - btnH1) / 2f
        rotateBtn.set(btnPad, row1Y + topMargin1, btnPad + btnW, row1Y + topMargin1 + btnH1)
        hardBtn.set(colW + btnPad, row1Y + topMargin1, colW + btnPad + btnW, row1Y + topMargin1 + btnH1)
        holdBtn.set(colW * 2 + btnPad, row1Y + topMargin1, colW * 2 + btnPad + btnW, row1Y + topMargin1 + btnH1)
    }

    // ── Draw ───────────────────────────────────────────
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
                drawHoldPanel(canvas)
                drawNextPanel(canvas)
                drawScorePanel(canvas)
                drawOverlay(canvas)
            }
        }

        drawButtons(canvas)

        if (game.state == GameState.PAUSED) {
            drawOverlay(canvas)
        }
    }

    // ── Background ──────────────────────────────────────
    private fun drawBackground(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()

        // Gradient background
        val gradient = LinearGradient(0f, 0f, w, h,
            intArrayOf(0xFF0d0d1a.toInt(), 0xFF1a1a2e.toInt(), 0xFF0f2040.toInt()),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        bgPaint.shader = gradient
        canvas.drawRect(0f, 0f, w, h, bgPaint)
        bgPaint.shader = null
    }

    // ── Board ──────────────────────────────────────────
    private fun drawBoard(canvas: Canvas) {
        val l = boardLeft; val t = boardTop
        val bw = boardPixelW; val bh = boardPixelH

        // Board background with rounded outline
        panelPaint.color = 0x22000000.toInt()
        val r = cellSize * 0.3f
        canvas.drawRoundRect(l - 2f, t - 2f, l + bw + 2f, t + bh + 2f, r, r, panelPaint)
        panelStrokePaint.color = 0x448888FF.toInt()
        canvas.drawRoundRect(l - 2f, t - 2f, l + bw + 2f, t + bh + 2f, r, r, panelStrokePaint)

        // Inner grid bg
        panelPaint.color = 0x11000000.toInt()
        canvas.drawRect(l, t, l + bw, t + bh, panelPaint)

        // Grid lines
        gridPaint.color = GRID_COLOR
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

    private fun drawCell(canvas: Canvas, col: Int, row: Int, cellColor: Int) {
        val x = boardLeft + col * cellSize + 1f
        val y = boardTop + row * cellSize + 1f
        val s = cellSize - 2f

        // Fill
        cellFillPaint.color = cellColor
        canvas.drawRect(x, y, x + s, y + s, cellFillPaint)

        // Inner highlight (top-left shine)
        val hl = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = lighten(cellColor, 0.3f); style = Paint.Style.FILL; alpha = 60
        }
        canvas.drawRect(x, y, x + s, y + s * 0.25f, hl)

        // Border
        cellBorderPaint.color = lighten(cellColor, 0.5f)
        canvas.drawRect(x, y, x + s, y + s, cellBorderPaint)
    }

    private fun drawPiece(canvas: Canvas, p: Tetromino) {
        val shape = p.shape
        for (r in shape.indices) {
            for (c in shape[r].indices) {
                if (shape[r][c] == 0) continue
                val boardRow = p.y + r - HIDDEN_ROWS
                if (boardRow < 0) continue
                drawCell(canvas, p.x + c, boardRow, p.color)
            }
        }
    }

    private fun drawGhost(canvas: Canvas, p: Tetromino) {
        val gy = game.board.getGhostY(p)
        val shape = p.shape
        ghostPaint.color = desaturate(p.color, 0.6f)
        ghostPaint.alpha = GHOST_ALPHA
        for (r in shape.indices) {
            for (c in shape[r].indices) {
                if (shape[r][c] == 0) continue
                val boardRow = gy + r - HIDDEN_ROWS
                if (boardRow < 0) continue
                val x = boardLeft + (p.x + c) * cellSize + 2f
                val y = boardTop + boardRow * cellSize + 2f
                val s = cellSize - 4f
                canvas.drawRect(x, y, x + s, y + s, ghostPaint)
            }
        }
    }

    // ── Panels ─────────────────────────────────────────
    private fun drawPanelBg(canvas: Canvas, cx: Float, top: Float, pw: Float, ph: Float) {
        val r = 10f
        panelPaint.color = 0x22000000.toInt()
        canvas.drawRoundRect(cx - pw / 2 - 1f, top - 1f, cx + pw / 2 + 1f, top + ph + 1f, r, r, panelPaint)
        panelStrokePaint.color = 0x338888FF.toInt()
        canvas.drawRoundRect(cx - pw / 2 - 1f, top - 1f, cx + pw / 2 + 1f, top + ph + 1f, r, r, panelStrokePaint)
    }

    private fun drawHoldPanel(canvas: Canvas) {
        val cx = panelCenterX; val top = 8f; val pw = panelRight - panelLeft - 12f
        val ph = cellSize * 3f + 44f
        drawPanelBg(canvas, cx, top, pw, ph)

        textPaint.color = 0x88FFFFFF.toInt(); textPaint.textSize = 16f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("HOLD", cx, top + 24f, textPaint)

        if (game.holdPiece != null) {
            drawMiniPiece(canvas, game.holdPiece!!, cx, top + 36f, cellSize * 0.5f)
        }
    }

    private fun drawNextPanel(canvas: Canvas) {
        val cx = panelCenterX
        val top = cellSize * 3f + 64f
        val pw = panelRight - panelLeft - 12f
        val ph = cellSize * 3f + 44f
        drawPanelBg(canvas, cx, top, pw, ph)

        textPaint.color = 0x88FFFFFF.toInt(); textPaint.textSize = 16f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("NEXT", cx, top + 24f, textPaint)

        if (game.nextPiece != null) {
            drawMiniPiece(canvas, game.nextPiece!!, cx, top + 36f, cellSize * 0.5f)
        }
    }

    private fun drawMiniPiece(canvas: Canvas, p: Tetromino, cx: Float, top: Float, scale: Float) {
        val shape = Tetromino.getShapes(p.type)[0]
        val rows = shape.size; val cols = shape[0].size
        val totalW = cols * scale; val totalH = rows * scale
        val ox = cx - totalW / 2f; val oy = top + (cellSize * 1.5f - totalH) / 2f
        miniPaint.color = p.color
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (shape[r][c] == 0) continue
                val x = ox + c * scale; val y = oy + r * scale
                canvas.drawRect(x, y, x + scale, y + scale, miniPaint)
                // highlight
                val hlPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0x33FFFFFF.toInt(); style = Paint.Style.FILL
                }
                canvas.drawRect(x, y, x + scale, y + scale * 0.2f, hlPaint)
            }
        }
    }

    private fun drawScorePanel(canvas: Canvas) {
        val cx = panelCenterX; val pw = panelRight - panelLeft - 12f
        val top = cellSize * 6.5f + 86f
        val ph = cellSize * 5.5f
        drawPanelBg(canvas, cx, top, pw, ph)

        textPaint.color = 0x88FFFFFF.toInt(); textPaint.textSize = 15f
        textPaint.textAlign = Paint.Align.LEFT
        val xx = cx - pw / 2 + 14f
        val lineH = (ph - 32f) / 4f

        // SCORE
        canvas.drawText("SCORE", xx, top + 24f, textPaint)
        textPaint.color = 0xFFFFCC00.toInt(); textPaint.textSize = 34f
        canvas.drawText("${game.score}", xx, top + 56f, textPaint)

        // BEST
        textPaint.color = 0x88FFFFFF.toInt(); textPaint.textSize = 15f
        canvas.drawText("BEST", xx, top + lineH + 24f, textPaint)
        textPaint.color = WHITE; textPaint.textSize = 30f
        canvas.drawText("${game.highScore}", xx, top + lineH + 56f, textPaint)

        // LEVEL
        textPaint.color = 0x88FFFFFF.toInt(); textPaint.textSize = 15f
        canvas.drawText("LEVEL", xx, top + lineH * 2 + 24f, textPaint)
        textPaint.color = 0xFF66FF88.toInt(); textPaint.textSize = 30f
        canvas.drawText("${game.level}", xx, top + lineH * 2 + 56f, textPaint)

        // LINES
        textPaint.color = 0x88FFFFFF.toInt(); textPaint.textSize = 15f
        canvas.drawText("LINES", xx, top + lineH * 3 + 24f, textPaint)
        textPaint.color = WHITE; textPaint.textSize = 30f
        canvas.drawText("${game.linesCleared}", xx, top + lineH * 3 + 56f, textPaint)
    }

    // ── Ghost toggle — tappable ───────────────────────
    private fun drawGhostIndicator(canvas: Canvas) {
        val cx = panelCenterX
        val pw = 80f
        val ph = 26f
        val topBtn = btnBarTop - 34f
        ghostBtn.set(cx - pw / 2, topBtn - ph, cx + pw / 2, topBtn + 4f)

        val enabled = game.ghostEnabled
        val bgColor = if (enabled) 0x4400FF88.toInt() else 0x33000000.toInt()
        val borderColor = if (enabled) 0x8800FF88.toInt() else 0x44444444.toInt()
        val r = ph / 2f

        panelPaint.color = bgColor
        canvas.drawRoundRect(ghostBtn, r, r, panelPaint)
        panelStrokePaint.color = borderColor
        canvas.drawRoundRect(ghostBtn, r, r, panelStrokePaint)

        textPaint.textSize = 13f; textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = if (enabled) 0xFF88FFAA.toInt() else 0xFF888888.toInt()
        canvas.drawText(if (enabled) "👻 ON" else "👻 OFF", cx, topBtn, textPaint)
    }

    // ── Buttons ─────────────────────────────────────────
    private fun btnColors(type: String): Int {
        return when (type) {
            "rotate" -> 0xFF3B5998.toInt()
            "hard" -> 0xFFC0392B.toInt()
            "hold" -> 0xFF2C3E50.toInt()
            "left" -> 0xFF2980B9.toInt()
            "soft" -> 0xFF16A085.toInt()
            "right" -> 0xFF2980B9.toInt()
            else -> 0xFF555555.toInt()
        }
    }

    private fun btnLabelColors(type: String): Int {
        return when (type) {
            "rotate" -> 0xFF88AAFF.toInt()
            "hard" -> 0xFFFF8888.toInt()
            "hold" -> 0xFF88CCFF.toInt()
            "left" -> 0xFFFFFFFF.toInt()
            "soft" -> 0xFFFFFFFF.toInt()
            "right" -> 0xFFFFFFFF.toInt()
            else -> WHITE
        }
    }

    private fun drawButtons(canvas: Canvas) {
        data class BtnInfo(val rect: RectF, val label: String, val subLabel: String, val type: String)

        val buttons = listOf(
            BtnInfo(rotateBtn, "↻", "旋转", "rotate"),
            BtnInfo(hardBtn,  "⬇",  "硬降", "hard"),
            BtnInfo(holdBtn,  "C",  "暂存", "hold"),
            BtnInfo(leftBtn,  "◀",  "左移", "left"),
            BtnInfo(softBtn,  "▼",  "下移", "soft"),
            BtnInfo(rightBtn, "▶",  "右移", "right")
        )

        for (btn in buttons) {
            val r = btn.rect.height() / 3.5f
            val bg = btnColors(btn.type)
            val rect = btn.rect

            // Shadow
            btnShadowPaint.color = 0x60000000.toInt()
            canvas.drawRoundRect(rect.left + 2f, rect.top + 2f, rect.right + 2f, rect.bottom + 2f, r, r, btnShadowPaint)

            // Gradient background
            val gradient = LinearGradient(rect.left, rect.top, rect.left, rect.bottom,
                intArrayOf(lightenAlpha(bg, 0.15f), bg, darkenAlpha(bg, 0.2f)),
                null, Shader.TileMode.CLAMP)
            btnBgPaint.shader = gradient
            canvas.drawRoundRect(rect, r, r, btnBgPaint)
            btnBgPaint.shader = null

            // Border
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 1f; color = lightenAlpha(bg, 0.3f)
            }
            canvas.drawRoundRect(rect, r, r, borderPaint)

            // Main label
            val fontSize = rect.height() * 0.52f
            btnTextPaint.textSize = fontSize
            btnTextPaint.color = btnLabelColors(btn.type)
            canvas.drawText(btn.label, rect.centerX(), rect.centerY() + fontSize * 0.32f, btnTextPaint)

            // Sub label
            btnSubTextPaint.textSize = 11f
            btnSubTextPaint.color = 0x66FFFFFF.toInt()
            canvas.drawText(btn.subLabel, rect.centerX(), rect.top + 14f, btnSubTextPaint)
        }

        // Ghost toggle indicator above the button bar
        drawGhostIndicator(canvas)
    }

    // ── Overlays ────────────────────────────────────────
    private fun drawReadyScreen(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()

        // Dimmed background
        canvas.drawRect(0f, 0f, w, h, overlayPaint)

        // Title
        textPaintShadow.textSize = 60f; textPaintShadow.textAlign = Paint.Align.CENTER
        textPaint.textSize = 60f; textPaint.textAlign = Paint.Align.CENTER

        // Shadow layer
        textPaintShadow.color = 0x40000000.toInt()
        canvas.drawText("TETRIS", w / 2f + 3f, h * 0.32f + 3f, textPaintShadow)
        textPaint.textSize = 60f; textPaint.color = 0xFF66CCFF.toInt()
        canvas.drawText("TETRIS", w / 2f, h * 0.32f, textPaint)

        // Subtitle
        textPaint.color = 0x99FFFFFF.toInt(); textPaint.textSize = 20f
        canvas.drawText("Tap to start  |  Best: ${game.highScore}", w / 2f, h * 0.395f, textPaint)

        // Controls hint
        textPaint.color = 0x55FFFFFF.toInt(); textPaint.textSize = 15f
        val hints = arrayOf("←→ Move  |  ↑ Rotate  |  ↓ Soft  |  ␣ Hard  |  C Hold")
        val yy = h * 0.75f
        for ((i, line) in hints.withIndex()) {
            canvas.drawText(line, w / 2f, yy + i * 18f, textPaint)
        }
    }

    private fun drawOverlay(canvas: Canvas) {
        val l = boardLeft; val t = boardTop
        val bw = boardPixelW; val bh = boardPixelH
        canvas.drawRect(l, t, l + bw, t + bh, overlayPaint)

        textPaintShadow.textAlign = Paint.Align.CENTER
        textPaint.textAlign = Paint.Align.CENTER

        val isOver = game.state == GameState.GAME_OVER
        val title = if (isOver) "GAME OVER" else "PAUSED"
        val titleColor = if (isOver) 0xFFFF6666.toInt() else 0xFF66CCFF.toInt()

        textPaint.textSize = 42f; textPaint.color = titleColor
        textPaintShadow.textSize = 42f; textPaintShadow.color = 0x40000000.toInt()
        canvas.drawText(title, l + bw / 2f + 2f, t + bh * 0.30f + 2f, textPaintShadow)
        canvas.drawText(title, l + bw / 2f, t + bh * 0.30f, textPaint)

        if (isOver) {
            textPaint.textSize = 28f; textPaint.color = 0xFFFFCC00.toInt()
            canvas.drawText("Score: ${game.score}", l + bw / 2f, t + bh * 0.42f, textPaint)
            textPaint.textSize = 22f; textPaint.color = 0x99FFFFFF.toInt()
            canvas.drawText("Best: ${game.highScore}", l + bw / 2f, t + bh * 0.50f, textPaint)
            textPaint.textSize = 18f; textPaint.color = 0x77FFFFFF.toInt()
            canvas.drawText("Tap to restart", l + bw / 2f, t + bh * 0.60f, textPaint)
        } else {
            textPaint.textSize = 20f; textPaint.color = 0x99FFFFFF.toInt()
            canvas.drawText("Press P to continue", l + bw / 2f, t + bh * 0.44f, textPaint)
        }
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
                    game.state == GameState.READY -> { game.startGame(); soundManager.play(SoundManager.Sound.LEVEL_START); return true }
                    game.state == GameState.GAME_OVER -> { game.restart(); return true }
                }
                if (game.state != GameState.PLAYING) return true

                when {
                    ghostBtn.contains(x, y) -> { game.ghostEnabled = !game.ghostEnabled }
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
                    dasAction = null
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                dasAction = null
                if (!isDragging && game.state == GameState.PLAYING) {
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
    private fun lighten(color: Int, factor: Float = 0.4f): Int {
        val a = color shr 24 and 0xFF
        val r = min((color shr 16 and 0xFF) + (255 - (color shr 16 and 0xFF)) * factor, 255f).toInt()
        val g = min((color shr 8 and 0xFF) + (255 - (color shr 8 and 0xFF)) * factor, 255f).toInt()
        val b = min((color and 0xFF) + (255 - (color and 0xFF)) * factor, 255f).toInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun darkenAlpha(color: Int, factor: Float): Int {
        val a = (color shr 24 and 0xFF).toFloat()
        val r = (color shr 16 and 0xFF).toFloat()
        val g = (color shr 8 and 0xFF).toFloat()
        val b = (color and 0xFF).toFloat()
        return ((a * 0.7f).toInt() shl 24) or
                (r.toInt() shl 16) or
                (g.toInt() shl 8) or
                b.toInt()
    }

    private fun lightenAlpha(color: Int, factor: Float): Int {
        val a = (color shr 24 and 0xFF).toFloat()
        val r = (color shr 16 and 0xFF).toFloat()
        val g = (color shr 8 and 0xFF).toFloat()
        val b = (color and 0xFF).toFloat()
        return (min(a + 255 * factor, 255f).toInt() shl 24) or
                (r.toInt() shl 16) or
                (g.toInt() shl 8) or
                b.toInt()
    }

    private fun desaturate(color: Int, factor: Float): Int {
        val a = color shr 24 and 0xFF
        val r = (color shr 16 and 0xFF).toFloat()
        val g = (color shr 8 and 0xFF).toFloat()
        val b = (color and 0xFF).toFloat()
        val avg = (r + g + b) / 3f
        val nr = (r + (avg - r) * factor).toInt().coerceIn(0, 255)
        val ng = (g + (avg - g) * factor).toInt().coerceIn(0, 255)
        val nb = (b + (avg - b) * factor).toInt().coerceIn(0, 255)
        return (a shl 24) or (nr shl 16) or (ng shl 8) or nb
    }
}
