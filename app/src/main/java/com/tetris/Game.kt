package com.tetris

import com.tetris.*
import kotlin.random.Random

enum class GameState { READY, PLAYING, PAUSED, GAME_OVER }

/**
 * Central game state machine �?scoring, levels, lock delay, hold, 7-bag.
 */
class Game {

    val board = Board()
    var state = GameState.READY

    var current: Tetromino? = null
    var nextPiece: Tetromino? = null
    var holdPiece: Tetromino? = null
    var holdUsed = false

    var score = 0
    var highScore = 0
    var level = 0
    var linesCleared = 0

    // Timing
    var dropTimer = 0f
    var lockTimer = 0f
    var lockMoves = 0
    var isLocking = false

    private val bag = mutableListOf<Char>()
    private var highScoreLoaded = false

    // ── Bag randomizer ───────────────────────────────────

    private fun fillBag() {
        val types = Tetromino.SHAPES_0.keys.toMutableList()
        types.shuffle(Random)
        bag.addAll(types)
    }

    private fun nextFromBag(): Char {
        if (bag.isEmpty()) fillBag()
        return bag.removeAt(0)
    }

    // ── Spawning ─────────────────────────────────────────

    private fun spawn() {
        if (nextPiece != null) {
            current = nextPiece
        } else {
            current = Tetromino.create(nextFromBag())
            current!!.resetPosition()
        }

        if (board.isCollision(current!!)) {
            state = GameState.GAME_OVER
            saveHighScore()
            return
        }

        nextPiece = Tetromino.create(nextFromBag())
        nextPiece!!.resetPosition()
        isLocking = false
        lockTimer = 0f
        lockMoves = 0
        holdUsed = false
    }

    // ── Public API ───────────────────────────────────────

    fun startGame() {
        if (!highScoreLoaded) { highScore = loadHighScore(); highScoreLoaded = true }
        score = 0; level = 0; linesCleared = 0
        holdPiece = null; holdUsed = false
        board.reset()
        bag.clear()
        fillBag()
        spawn()
        state = GameState.PLAYING
    }

    fun moveLeft(): Boolean = move(0, -1)
    fun moveRight(): Boolean = move(0, 1)
    fun softDrop(): Boolean {
        val p = current ?: return false
        if (!board.isCollision(p, dy = 1)) {
            p.y += 1
            dropTimer = 0f
            if (isLocking && !board.isCollision(p, dy = 1)) isLocking = false
            score += 1
            return true
        }
        return false
    }

    fun hardDrop() {
        val p = current ?: return
        var dist = 0
        while (!board.isCollision(p, dy = 1)) { p.y += 1; dist++ }
        score += dist * 2
        lock()
    }

    fun rotate() {
        val p = current ?: return
        val fromRot = p.rotation
        val toRot = (fromRot + 1) % 4
        val kicks = Tetromino.getWallKicks(p.type, fromRot, toRot)
        for ((dx, dy) in kicks) {
            if (!board.isCollision(p, dx = dx, dy = dy, rotation = toRot)) {
                p.x += dx; p.y += dy; p.rotation = toRot
                onMove()
                return
            }
        }
    }

    fun hold() {
        if (holdUsed || current == null) return
        val curType = current!!.type

        if (holdPiece != null) {
            val holdType = holdPiece!!.type
            holdPiece = Tetromino.create(curType).also { it.resetPosition() }
            current = Tetromino.create(holdType).also { it.resetPosition() }
            if (board.isCollision(current!!)) {
                state = GameState.GAME_OVER
                saveHighScore()
                return
            }
        } else {
            holdPiece = Tetromino.create(curType).also { it.resetPosition() }
            spawn()
        }
        holdUsed = true
        isLocking = false; lockTimer = 0f; lockMoves = 0; dropTimer = 0f
    }

    fun togglePause() {
        when (state) {
            GameState.PLAYING -> state = GameState.PAUSED
            GameState.PAUSED -> state = GameState.PLAYING
            else -> {}
        }
    }

    fun restart() {
        state = GameState.READY
        score = 0; level = 0; linesCleared = 0
        holdPiece = null; holdUsed = false
        current = null; nextPiece = null
        board.reset()
        bag.clear()
        dropTimer = 0f; lockTimer = 0f; lockMoves = 0; isLocking = false
        highScore = loadHighScore()
    }

    // ── Frame update ─────────────────────────────────────

    fun update(dtMs: Float) {
        if (state != GameState.PLAYING || current == null) return
        val p = current!!

        val onSurface = board.isCollision(p, dy = 1)

        if (onSurface) {
            if (!isLocking) {
                isLocking = true
                lockTimer = 0f
                lockMoves = 0
            } else {
                lockTimer += dtMs
                if (lockTimer >= LOCK_DELAY_MS) { lock(); return }
            }
        } else {
            isLocking = false
        }

        if (!isLocking) {
            dropTimer += dtMs
            val interval = dropInterval
            if (dropTimer >= interval) {
                if (!board.isCollision(p, dy = 1)) p.y += 1
                dropTimer = 0f
            }
        }
    }

    val dropInterval: Float
        get() = LEVEL_SPEEDS[minOf(level, LEVEL_SPEEDS.lastIndex)].toFloat()

    // ── Internal ─────────────────────────────────────────

    private fun move(rdx: Int, rdy: Int): Boolean {
        val p = current ?: return false
        if (!board.isCollision(p, dx = rdx, dy = rdy)) {
            p.x += rdx; p.y += rdy
            onMove()
            return true
        }
        return false
    }

    private fun onMove() {
        if (isLocking) {
            lockTimer = 0f
            lockMoves++
            if (lockMoves >= LOCK_MOVE_LIMIT) lock()
        }
    }

    private fun lock() {
        val p = current ?: return
        val aboveTop = board.lock(p)
        val lines = board.clearLines()
        if (lines > 0) {
            linesCleared += lines
            score += (SCORE_TABLE[lines] ?: 0) * (level + 1)
            level = linesCleared / LINES_PER_LEVEL
        }
        if (aboveTop || board.isGameOver()) {
            state = GameState.GAME_OVER
            saveHighScore()
            return
        }
        spawn()
    }

    // ── High score (SharedPreferences) ───────────────────

    private fun loadHighScore(): Int {
        highScoreLoaded = true
        // Loaded from SharedPreferences in the Activity; fallback 0.
        return 0
    }

    fun loadHighScore(saved: Int) {
        highScore = saved
        highScoreLoaded = true
    }

    fun saveHighScore(): Int {
        if (score > highScore) {
            highScore = score
            return highScore
        }
        return -1
    }
}
