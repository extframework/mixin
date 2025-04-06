package dev.extframework.mixin.engine.analysis

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LookupSwitchInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.TableSwitchInsnNode
import org.objectweb.asm.tree.TryCatchBlockNode
import org.objectweb.asm.tree.TypeInsnNode

internal object CodeFlowAnalyzer {
    internal data class Block(
        val start: LabelNode,
        val end: LabelNode,
    )

    internal class CFGNode(
        val instructions: List<AbstractInsnNode>,
        val entersBy: MutableList<CFGNode> = mutableListOf(),
        val exitsBy: MutableList<CFGNode> = mutableListOf(),
    ) {
        // DO NOT implement equals or hashcode
    }

    private fun reasonableToThrow(
        node: AbstractInsnNode
    ) : Boolean {
        return when (node) {
            is MethodInsnNode -> true
            is InsnNode -> node.opcode == Opcodes.ATHROW
                    || (Opcodes.IDIV..Opcodes.DDIV).contains(node.opcode)
                    || (Opcodes.IASTORE..Opcodes.SASTORE).contains(node.opcode)
                    || (Opcodes.IALOAD..Opcodes.SALOAD).contains(node.opcode)
            is TypeInsnNode -> node.opcode == Opcodes.CHECKCAST
            else -> false
        }
    }

    internal fun buildFullFlowGraph(
        code: InsnList,
        tryCatch: List<TryCatchBlockNode>
    ): CFGNode {
        // Keeping track of entries and exits, fast at the cost of some memory overhead
        val exitsInto = HashMap<AbstractInsnNode, MutableList<CFGNode>>()
        val exitsBy = HashMap<CFGNode, List<AbstractInsnNode>>()
        val nodes = HashMap<AbstractInsnNode, CFGNode>()

        val tryCatchIndices = tryCatch.flatMapTo(HashSet()) { block ->
            ((code.indexOf(block.start))..(code.indexOf(block.end) - 1)).map {
                it to block
            }
        }.groupBy {
            it.first
        }.mapValues {
            it.value.map { entry -> entry.second }
        }

        // TODO intentionally not supporting JSR/RET
        // It's either 2*O(n) or O(n^2)
        code.forEach { thisNode ->
            val goesTo: List<AbstractInsnNode> = run {
                val tryCatchCase = if (tryCatchIndices.containsKey(code.indexOf(thisNode)) ) {
                    tryCatchIndices[code.indexOf(thisNode)]!!.map { it.handler }
                } else listOf()

                val basicCases = if (thisNode is JumpInsnNode) {
                    if (Opcodes.GOTO == thisNode.opcode) {
                        listOfNotNull(thisNode.label)
                    } else {
                        listOfNotNull(
                            thisNode.label, thisNode.next
                        )
                    }
                } else if (thisNode is TableSwitchInsnNode) {
                    thisNode.labels + thisNode.dflt
                } else if (thisNode is LookupSwitchInsnNode) {
                    thisNode.labels + thisNode.dflt
                } else if (isReturn(thisNode.opcode) || Opcodes.ATHROW == thisNode.opcode) {
                    listOf()
                } else {
                    listOfNotNull(thisNode.next)
                }

                tryCatchCase + basicCases
            }


            val node = CFGNode(
                listOf(thisNode),
                mutableListOf(),
                mutableListOf()
            )

            goesTo.forEach {
                exitsInto[it] = (exitsInto[it] ?: mutableListOf()).apply() { add(node) }
            }
            exitsBy[node] = goesTo
            nodes[thisNode] = node
        }

        return code.map { thisNode ->
            val node = nodes[thisNode]!!

            node.entersBy.addAll(exitsInto[thisNode] ?: listOf())
            node.exitsBy.addAll(exitsBy[node]!!.map {
                nodes.get(it)!!
            })

            node
        }.first()
    }

    private fun isReturn(
        opcode: Int
    ): Boolean {
        return (Opcodes.IRETURN..Opcodes.RETURN).contains(opcode)
    }

    // See https://en.wikipedia.org/wiki/Edge_contraction
    // *note that this is singly directed edge contraction meaning
    // there is much less work that needs to be done
    internal fun contract(
        first: CFGNode,
        second: CFGNode
    ): CFGNode {
        // The two nodes must be singly directed. First must exit
        // into Second.
        val correctlyDirected =
            // Both have to be singly directed (only 1 link between)
            first.exitsBy.size == 1 && second.entersBy.size == 1 &&
                    // One must point into the other
                    first.exitsBy.first() == second && second.entersBy.first() == first
                    // They cannot be circularly linked
                    && !(second.exitsBy.contains(first) && first.entersBy.contains(second))

        check(
            correctlyDirected
        ) { "Invariants not met" }

        val new = CFGNode(
            first.instructions + second.instructions,
            (first.entersBy + second.entersBy).filterNotTo(ArrayList()) {
                it == first
            },
            (first.exitsBy + second.exitsBy).filterNotTo(ArrayList()) {
                it == second
            }
        )

        // Not related to second at all
        first.entersBy.forEach {
            it.exitsBy.remove(first)
            it.exitsBy.add(new)
        }

        // Not related to first at all
        second.exitsBy.forEach {
            it.entersBy.remove(second)
            it.entersBy.add(new)
        }

        return new
    }

    internal fun buildCFG(
        node: CFGNode,
        processed: MutableSet<AbstractInsnNode> = HashSet()
    ): CFGNode {
        var node = node

        processed.add(node.instructions.first())

        while (true) {
            val shouldContract = !(node.exitsBy.size > 1 || node.exitsBy.any { it.entersBy.size > 1 })

            // Exits could be 0
            if (shouldContract && node.exitsBy.isNotEmpty()) {
                node = contract(node, node.exitsBy.first())
            } else { // If there are no exits, or it shouldn't contract we end
                // Edge contractions will rope 'this' node in so all that is needed is to
                // call buildCFG on the next elements.
                node.exitsBy
                    // Important for concurrent modification
                    .toList()
                    // In the case of for statements or any self referencing loop we need to
                    // make sure not to process it infinitely
                    .filterNot { processed.contains(it.instructions.first()) }
                    .forEach { buildCFG(it, processed) }

                return node
            }
        }
    }

    data class DominatorNode(
        val node: CFGNode,
        val children: Set<DominatorNode>
    ) {
        override fun hashCode(): Int {
            return node.hashCode()
        }
    }

    // Using the Direct Solution (see wiki). Would be faster to use
    // Lengauer/Tarjan, but in practical time since n is always quite small
    // it will make hardly any difference. (I could imagine a noticeable
    // difference only if computing hundreds of methods with very long
    // and complex CFGs)
    fun buildDominator(root: CFGNode): DominatorNode {
        val allNodes = LinkedHashSet<CFGNode>()
        toList(root, allNodes)

        val dominators = HashMap<CFGNode, Set<CFGNode>>()

        for (node in allNodes) {
            if (node == root) {
                dominators[node] = setOf(root)
            } else {
                dominators[node] = allNodes
            }
        }

        var changes = true
        while (changes) {
            changes = false

            for (node in allNodes) {
                if (node != root) {
                    val newDom = setOf(node) + (if (node.entersBy.size == 1) {
                        dominators[node.entersBy.first()]!!
                    } else {
                        node.entersBy
                            // TODO could dominators be null?
                            .mapNotNull() { dominators[it] }
                            .reduce { acc, it -> acc intersect it }
                    })

                    if (newDom != dominators[node]) {
                        changes = true
                        dominators[node] = newDom
                    }
                }
            }
        }

        fun iDom(node: CFGNode): CFGNode? {
            if (node == root) return null

            val strictDominators = dominators[node]!! - node

            return strictDominators.find { candidate ->
                strictDominators.none { it -> candidate != it && dominators[it]!!.contains(candidate) }
            }
        }

        val preIDoms = HashMap<CFGNode, MutableSet<CFGNode>>()
        for (currNode in allNodes) {
            val iDom = iDom(currNode) ?: continue

            preIDoms
                .getOrPut(iDom) { HashSet() }
                .add(currNode)
        }

        fun buildTree(node: CFGNode): DominatorNode {
            return DominatorNode(
                node,
                preIDoms[node]?.mapTo(HashSet(), ::buildTree) ?: setOf()
            )
        }

        val buildTree = buildTree(root)
        return buildTree
    }

    private fun findPredecessors(node: CFGNode): Set<CFGNode> {
        return node.entersBy.toSet()

    }

    // Dijkstras
    public fun shortestPath(
        start: CFGNode,
        end: CFGNode
    ): List<CFGNode> {
        val perimeter = ArrayList<CFGNode>()
        val visited = HashSet<CFGNode>()

        val distTo = HashMap<CFGNode, Int>()
        val edgeTo = HashMap<CFGNode, CFGNode>()

        perimeter.add(start)
        distTo[start] = 0

        fun assemble(curr: CFGNode?): List<CFGNode> {
            if (curr == null) return listOf()

            return listOf(curr) + assemble(edgeTo[curr])
        }

        while (!perimeter.isEmpty()) {
            val current = perimeter.removeAt(0)
            if (!visited.add(current)) continue

            if (current == end) return assemble(current)

            for (exit in current.exitsBy) {
                perimeter.add(exit)

                val currentDist = distTo[current]!! + exit.instructions.size
                val previousDist = distTo[exit] ?: Int.MAX_VALUE

                if (currentDist < previousDist) {
                    distTo[exit] = currentDist
                    edgeTo[exit] = current
                }
            }
        }

        throw Exception("No path between CFG nodes?")
    }

    // All CFGs must eventually exit to the same node, picking 1 path
    // over another cannot be guaranteed to be faster so always following
    // the first available is sufficient.
    private fun findEnd(node: CFGNode): CFGNode {
        if (node.exitsBy.isEmpty()) return node
        return findEnd(node.exitsBy.first())
    }

    internal fun findDominancePaths(
        node: CFGNode,
        processed: MutableSet<CFGNode> = mutableSetOf()
    ): List<List<CFGNode>> {
        if (node.entersBy.isEmpty()) return listOf(listOf(node))
        processed.add(node)

        return node.entersBy
            .filterNot { processed.contains(it) }
            .flatMap { e -> findDominancePaths(e, processed).map { listOf(node) + it } }
    }

    internal fun toList(
        node: CFGNode,
        processed: MutableSet<CFGNode>,
    ) {
        processed.add(node)

        node.exitsBy
            .filterNot { processed.contains(it) }
            .forEach { toList(it, processed) }
    }

    internal fun <T> List<List<T>>.intersectAll(): List<T> {
        if (isEmpty()) return emptyList()

        return reduce { acc, it ->
            acc.intersect(it).toList()
        }
    }
}