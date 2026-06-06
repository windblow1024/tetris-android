package com.tetris

// ── Board dimensions ───────────────────────────────────
const val BOARD_WIDTH = 10
const val BOARD_HEIGHT = 20
const val HIDDEN_ROWS = 2
const val TOTAL_HEIGHT = BOARD_HEIGHT + HIDDEN_ROWS   // 22

// ── Timing ─────────────────────────────────────────────
const val LOCK_DELAY_MS = 500L
const val LOCK_MOVE_LIMIT = 15
const val LINES_PER_LEVEL = 10

/** Drop interval (ms) indexed by level. */
val LEVEL_SPEEDS = intArrayOf(800, 720, 630, 550, 470, 380, 300, 220, 140, 100)

/** Score earned per number of lines cleared (× level+1). */
val SCORE_TABLE = mapOf(1 to 100, 2 to 300, 3 to 500, 4 to 800)

// ── Colours (ARGB) ─────────────────────────────────────
const val BG_COLOR = 0xFF1a1a2e.toInt()
const val PANEL_BG = 0x22FFFFFF.toInt()
const val WHITE = 0xFFFFFFFF.toInt()
const val DARK_GRAY = 0xFF666666.toInt()
const val BLACK = 0xFF000000.toInt()
const val GHOST_ALPHA = 0x55       // 33 % alpha for ghost piece

val PIECE_COLORS = mapOf(
    'I' to 0xFF00FFFF.toInt(),      // Cyan
    'O' to 0xFFFFFF00.toInt(),      // Yellow
    'T' to 0xFF800080.toInt(),      // Purple
    'L' to 0xFFFFA500.toInt(),      // Orange
    'J' to 0xFF0000FF.toInt(),      // Blue
    'S' to 0xFF00FF00.toInt(),      // Green
    'Z' to 0xFFFF0000.toInt()       // Red
)

// ── DAS (Delayed Auto Shift) ──────────────────────────
const val DAS_DELAY_MS = 167L
const val DAS_INTERVAL_MS = 33L

// ── Frame timing ──────────────────────────────────────
const val FPS = 60
const val FRAME_MS = 1000 / FPS       // ~16.67
