package com.tetris

/** All 7 standard tetrominoes with SRS rotation states and wall-kick tables. */
data class Tetromino(
    val type: Char,                     // I / O / T / L / J / S / Z
    var x: Int,
    var y: Int,
    var rotation: Int = 0               // 0-3
) {
    // ── Shape data (4 rotation states each) ─────────────
    companion object {
        /** Raw shapes; each entry is list-of-rows for rotation 0. */
        val SHAPES_0 = mapOf(
            'I' to listOf(
                listOf(0,0,0,0),
                listOf(1,1,1,1),
                listOf(0,0,0,0),
                listOf(0,0,0,0)
            ),
            'O' to listOf(
                listOf(1,1),
                listOf(1,1)
            ),
            'T' to listOf(
                listOf(0,1,0),
                listOf(1,1,1),
                listOf(0,0,0)
            ),
            'L' to listOf(
                listOf(0,0,1),
                listOf(1,1,1),
                listOf(0,0,0)
            ),
            'J' to listOf(
                listOf(1,0,0),
                listOf(1,1,1),
                listOf(0,0,0)
            ),
            'S' to listOf(
                listOf(0,1,1),
                listOf(1,1,0),
                listOf(0,0,0)
            ),
            'Z' to listOf(
                listOf(1,1,0),
                listOf(0,1,1),
                listOf(0,0,0)
            )
        )

        /** Spawn offsets so the piece appears centred at the top. */
        val SPAWN_X = mapOf('O' to 4, 'I' to 3)                    // others default 3
        val SPAWN_Y = mapOf('I' to -2)                              // others default -1

        /** Pre-computed all 4 rotations for each type. */
        private val allShapes: MutableMap<Char, List<List<List<Int>>>> = mutableMapOf()

        fun getShapes(type: Char): List<List<List<Int>>> {
            if (allShapes.isEmpty()) precompute()
            return allShapes[type]!!
        }

        private fun precompute() {
            for ((t, m0) in SHAPES_0) {
                val states = mutableListOf(m0)
                var m = m0
                repeat(3) {
                    m = rotateCW(m)
                    states.add(m)
                }
                allShapes[t] = states
            }
        }

        /** Rotate a matrix 90° clockwise. */
        private fun rotateCW(matrix: List<List<Int>>): List<List<Int>> {
            val n = matrix.size
            val out = MutableList(n) { MutableList(n) { 0 } }
            for (r in 0 until n)
                for (c in 0 until n)
                    out[c][n - 1 - r] = matrix[r][c]
            return out
        }

        // ── Wall-kick offsets (SRS) ──────────────────────
        // Key: "from->to" for JLSTZ and I blocks.
        private val KICKS_JLSTZ = mapOf(
            "0->1" to listOf(0 to 0, -1 to 0, -1 to 1, 0 to -2, -1 to -2),
            "1->2" to listOf(0 to 0, 1 to 0, 1 to -1, 0 to 2, 1 to 2),
            "2->3" to listOf(0 to 0, 1 to 0, 1 to 1, 0 to -2, 1 to -2),
            "3->0" to listOf(0 to 0, -1 to 0, -1 to -1, 0 to 2, -1 to 2)
        )
        private val KICKS_I = mapOf(
            "0->1" to listOf(0 to 0, -2 to 0, 1 to 0, -2 to -1, 1 to 2),
            "1->2" to listOf(0 to 0, -1 to 0, 2 to 0, -1 to 2, 2 to -1),
            "2->3" to listOf(0 to 0, 2 to 0, -1 to 0, 2 to 1, -1 to -2),
            "3->0" to listOf(0 to 0, 1 to 0, -2 to 0, 1 to -2, -2 to 1)
        )

        fun getWallKicks(type: Char, from: Int, to: Int): List<Pair<Int,Int>> {
            val key = "$from->$to"
            return if (type == 'I') KICKS_I[key] ?: listOf(0 to 0)
                   else KICKS_JLSTZ[key] ?: listOf(0 to 0)
        }

        /** Create a new Tetromino with default spawn position. */
        fun create(type: Char): Tetromino {
            val x = SPAWN_X[type] ?: 3
            val y = SPAWN_Y[type] ?: -1
            return Tetromino(type, x, y, 0)
        }
    }

    // ── Instance helpers ──────────────────────────────────

    /** Return the shape matrix for the current rotation. */
    val shape: List<List<Int>>
        get() = getShapes(type)[rotation]

    /** Return the colour for this piece type. */
    val color: Int get() = PIECE_COLORS[type] ?: WHITE

    /** Deep clone. */
    fun copy(): Tetromino = Tetromino(type, x, y, rotation)

    /** Reset position for spawning. */
    fun resetPosition() {
        x = SPAWN_X[type] ?: 3
        y = SPAWN_Y[type] ?: -1
        rotation = 0
    }
}
