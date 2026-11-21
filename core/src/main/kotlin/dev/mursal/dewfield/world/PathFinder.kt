package dev.mursal.dewfield.world

import dev.mursal.dewfield.model.Cell
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * A* over the walkable grid with 8-way moves (no cutting corners past walls),
 * then string-pulling so the farmer walks in straight lines instead of zig-zags.
 */
class PathFinder(private val walkable: (Int, Int) -> Boolean, private val width: Int, private val height: Int) {

    constructor(loc: Location) : this({ x, y -> !loc.isSolid(x, y) }, loc.width, loc.height)

    private data class Node(val idx: Int, val f: Float)

    /** Cells from [start] to the cheapest reachable cell in [goals], or null if none is reachable. */
    fun findCells(start: Cell, goals: Collection<Cell>): List<Cell>? {
        val goalSet = goals.filter { inside(it.x, it.y) && walkable(it.x, it.y) }.map { it.y * width + it.x }.toHashSet()
        if (goalSet.isEmpty()) return null
        val startIdx = start.y * width + start.x
        if (!inside(start.x, start.y)) return null
        if (startIdx in goalSet) return listOf(start)

        val g = FloatArray(width * height) { Float.MAX_VALUE }
        val from = IntArray(width * height) { -1 }
        val closed = BooleanArray(width * height)
        val open = PriorityQueue<Node>(compareBy { it.f })
        val goalCells = goalSet.map { Cell(it % width, it / width) }
        fun h(i: Int): Float {
            val x = i % width
            val y = i / width
            return goalCells.minOf { octile(abs(it.x - x), abs(it.y - y)) }
        }
        g[startIdx] = 0f
        open += Node(startIdx, h(startIdx))
        while (open.isNotEmpty()) {
            val cur = open.poll().idx
            if (closed[cur]) continue
            if (cur in goalSet) return rebuild(from, cur)
            closed[cur] = true
            val cx = cur % width
            val cy = cur / width
            for (dy in -1..1) for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nx = cx + dx
                val ny = cy + dy
                // The start cell may be "solid" if the farmer stands half inside an object; let them leave it.
                if (!inside(nx, ny) || !walkable(nx, ny)) continue
                if (dx != 0 && dy != 0 && (!walkable(cx + dx, cy) || !walkable(cx, cy + dy))) continue
                val ni = ny * width + nx
                if (closed[ni]) continue
                val cost = g[cur] + if (dx != 0 && dy != 0) DIAG else 1f
                if (cost < g[ni]) {
                    g[ni] = cost
                    from[ni] = cur
                    open += Node(ni, cost + h(ni))
                }
            }
        }
        return null
    }

    private fun rebuild(from: IntArray, end: Int): List<Cell> {
        val out = ArrayList<Cell>()
        var i = end
        while (i != -1) {
            out += Cell(i % width, i / width)
            i = from[i]
        }
        out.reverse()
        return out
    }

    /**
     * Waypoints (world coordinates of cell centers) from a position to the best goal cell,
     * smoothed by line of sight. The first point is the next place to walk to.
     */
    fun findPath(fromX: Float, fromY: Float, goals: Collection<Cell>): List<Pair<Float, Float>>? {
        val start = Cell(floor(fromX).toInt(), floor(fromY).toInt())
        val cells = findCells(start, goals) ?: return null
        val points = cells.map { it.x + 0.5f to it.y + 0.5f }
        if (points.size == 1) return points
        val out = ArrayList<Pair<Float, Float>>()
        var anchor = fromX to fromY
        var i = 1
        while (i < points.size) {
            // Skip ahead as far as the straight line stays clear.
            var j = points.size - 1
            while (j > i && !clearLine(anchor.first, anchor.second, points[j].first, points[j].second)) j--
            out += points[j]
            anchor = points[j]
            i = j + 1
        }
        return out
    }

    /** True if a body of radius [RADIUS] can slide straight from a to b. */
    fun clearLine(ax: Float, ay: Float, bx: Float, by: Float): Boolean {
        val dx = bx - ax
        val dy = by - ay
        val len = sqrt(dx * dx + dy * dy)
        if (len < 1e-4f) return true
        val nx = -dy / len * RADIUS
        val ny = dx / len * RADIUS
        val steps = max(2, (len / 0.1f).toInt())
        for (s in 0..steps) {
            val t = s / steps.toFloat()
            val px = ax + dx * t
            val py = ay + dy * t
            if (!free(px, py) || !free(px + nx, py + ny) || !free(px - nx, py - ny)) return false
        }
        return true
    }

    private fun free(x: Float, y: Float): Boolean {
        val cx = floor(x).toInt()
        val cy = floor(y).toInt()
        return inside(cx, cy) && walkable(cx, cy)
    }

    private fun inside(x: Int, y: Int) = x in 0 until width && y in 0 until height

    companion object {
        const val DIAG = 1.4142135f
        const val RADIUS = 0.28f
        fun octile(dx: Int, dy: Int): Float = (max(dx, dy) - min(dx, dy)) + DIAG * min(dx, dy)
    }
}
