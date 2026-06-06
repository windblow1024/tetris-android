package com.tetris

import com.tetris.*

/**
 * 10×22 grid (2 hidden rows above visible area).
 * Each cell: 0 = empty, non-zero = ARGB colour of locked piece.
 */
class Board {
    val grid = Array(TOTAL_HEIGHT) { IntArray(BOARD_WIDTH) }

    /** Check whether [piece] at (x+dx, y+dy) with [rotation] collides. */
    fun isCollision(piece: Tetromino, dx: Int = 0, dy: Int = 0, rotation: Int? = null): Boolean {
        val shape = if (rotation != null) Tetromino.getShapes(piece.type)[rotation % 4]
                    else piece.shape
        for (r in shape.indices) {
            for (c in shape[r].indices) {
                if (shape[r][c] == 0) continue
                val bx = piece.x + dx + c
                val by = piece.y + dy + r
                if (bx < 0 || bx >= BOARD_WIDTH || by >= TOTAL_HEIGHT) return true
                if (by < 0) continue
                if (grid[by][bx] != 0) return true
            }
        }
        return false
    }

    /** Lock [piece] into the grid. Returns true if game-over (lock above visible area). */
    fun lock(piece: Tetromino): Boolean {
        val color = piece.color
        var above = false
        for (r in piece.shape.indices) {
            for (c in piece.shape[r].indices) {
                if (piece.shape[r][c] == 0) continue
                val bx = piece.x + c
                val by = piece.y + r
                if (by < 0) { above = true; continue }
                grid[by][bx] = color
            }
        }
        return above
    }

    /** Remove full rows and return the count removed. */
    fun clearLines(): Int {
        var cleared = 0
        var writeRow = TOTAL_HEIGHT - 1
        for (r in TOTAL_HEIGHT - 1 downTo 0) {
            val full = grid[r].all { it != 0 }
            if (full) {
                cleared++
            } else {
                if (writeRow != r) grid[writeRow] = grid[r].copyOf()
                writeRow--
            }
        }
        // Fill empty rows at top
        for (r in writeRow downTo 0) {
            grid[r].fill(0)
        }
        return cleared
    }

    /** Check if any cell in the visible top row (row 0) is occupied. */
    fun isGameOver(): Boolean = grid[0].any { it != 0 }

    /** Calculate ghost-piece Y (bottom-most valid Y for [piece]). */
    fun getGhostY(piece: Tetromino): Int {
        var gy = piece.y
        while (!isCollision(piece, dy = gy - piece.y + 1)) gy++
        return gy
    }

    /** Reset the board. */
    fun reset() {
        for (r in 0 until TOTAL_HEIGHT) grid[r].fill(0)
    }
}
