package dev.extframework.mixin.test.engine.analysis

import com.mxgraph.layout.hierarchical.mxHierarchicalLayout
import com.mxgraph.layout.mxIGraphLayout
import com.mxgraph.util.mxCellRenderer
import dev.extframework.archives.transform.ByteCodeUtils.opcodeToString
import dev.extframework.mixin.engine.analysis.CodeFlowAnalyzer
import dev.extframework.mixin.engine.analysis.CodeFlowAnalyzer.CFGNode
import dev.extframework.mixin.engine.analysis.CodeFlowAnalyzer.DominatorNode
import dev.extframework.mixin.engine.analysis.CodeFlowAnalyzer.intersectAll
import dev.extframework.mixin.engine.analysis.ObjectValueRef
import dev.extframework.mixin.engine.analysis.SimulatedFrame
import dev.extframework.mixin.engine.analysis.analyzeFrames
import dev.extframework.mixin.test.insnFor
import dev.extframework.mixin.test.engine.inject.impl.code.Dest
import dev.extframework.mixin.test.methodFor
import org.jgrapht.Graph
import org.jgrapht.ext.JGraphXAdapter
import org.jgrapht.graph.DefaultDirectedGraph
import org.jgrapht.graph.DefaultEdge
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.FrameNode
import org.objectweb.asm.tree.IincInsnNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.LineNumberNode
import org.objectweb.asm.tree.LookupSwitchInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MultiANewArrayInsnNode
import org.objectweb.asm.tree.TableSwitchInsnNode
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.VarInsnNode
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.Path
import kotlin.test.Test

class CodeFlowTests {

    @Test
    fun `Test full flow graph`() {
        val code = insnFor(Sample::class, "sampleMethod")
        val graph = CodeFlowAnalyzer.buildFullFlowGraph(
            code,
            listOf()
        )


    }

    @Test
    fun `Test edge contraction`() {
        val nodeA = CFGNode(listOf())
        val nodeB = CFGNode(listOf())
        val nodeC = CFGNode(listOf())
        val nodeD = CFGNode(listOf())

        nodeA.entersBy.add(nodeB)
        nodeB.exitsBy.add(nodeA)

        nodeA.exitsBy.add(nodeC)
        nodeC.entersBy.add(nodeA)

        nodeD.exitsBy.add(nodeB)
        nodeB.entersBy.add(nodeD)

        val contraction = CodeFlowAnalyzer.contract(
            nodeB,
            nodeA
        )

        check(contraction.exitsBy.toSet() == setOf(nodeC))
        check(contraction.entersBy.toSet() == setOf(nodeD))
    }

    @Test
    fun `Test multiple edge contractions`() {
        val nodeA = CFGNode(listOf(VarInsnNode(1, 1)))
        val nodeB = CFGNode(listOf(VarInsnNode(1, 2)))
        val nodeC = CFGNode(listOf(VarInsnNode(1, 2)))
        val nodeD = CFGNode(listOf(VarInsnNode(1, 2)))

        nodeA.exitsBy.add(nodeB)
        nodeB.entersBy.add(nodeA)

        nodeB.exitsBy.add(nodeC)
        nodeC.entersBy.add(nodeB)

        nodeC.exitsBy.add(nodeD)
        nodeD.entersBy.add(nodeC)

        val first = CodeFlowAnalyzer.contract(nodeA, nodeB)
        val second = CodeFlowAnalyzer.contract(first, nodeC)
        val third = CodeFlowAnalyzer.contract(second, nodeD)

        val expectedElements = listOf(nodeA, nodeB, nodeC, nodeD).flatMap { it.instructions }

        check(third.instructions == expectedElements) { "Elements dont match" }
    }

//    @Test
//    fun `Test code control flow analysis`() {
//        val block = CodeFlowAnalyzer.analyze(
//            insnFor(Sample::class, "sampleMethod"),
//            listOf(0, 0)
//        )
//
//        println("Test")
//    }

    private fun exportAsPng(
        cfgNode: CFGNode,
        insn: InsnList,
        path: Path
    ) {
        val all = HashSet<CFGNode>()
        CodeFlowAnalyzer.toList(cfgNode, all)

        val ordered = all.sortedBy {
            insn.indexOf(it.instructions.first())
        }

        fun toString(node: CFGNode): String {
            return ordered.indexOf(node).toString()
        }

        fun collect(
            node: CFGNode,
            vertices: MutableSet<String>,
            edges: MutableSet<Pair<String, String>>,
        ) {
            if (vertices.add(toString(node))) {
                node.exitsBy.forEach { t ->
                    edges.add(toString(node) to toString(t))
                    collect(t, vertices, edges)
                }
            }
        }

        class CustomEdge : DefaultEdge() {
            override fun toString(): String = ""
        }

        val graph = DefaultDirectedGraph<String, DefaultEdge>(CustomEdge::class.java)

        val vertices = HashSet<String>()
        val edges = HashSet<Pair<String, String>>()
        collect(cfgNode, vertices, edges)
        for (string in vertices) {
            graph.addVertex(string)
        }
        for (edge in edges) {
            graph.addEdge(edge.first, edge.second)
        }

        val graphAdapter = JGraphXAdapter<String, DefaultEdge>(graph)
        val layout: mxIGraphLayout = mxHierarchicalLayout(graphAdapter)
        layout.execute(graphAdapter.defaultParent)

        val image: BufferedImage = mxCellRenderer.createBufferedImage(
            graphAdapter, null, 10.0, Color.WHITE, true, null
        )
        val imgFile = path.toFile()
        ImageIO.write(image, "PNG", imgFile)


    }

    @Test
    fun `Test build CFG`() {
        val code = insnFor(Sample::class, "sampleMethod")
        val graph = CodeFlowAnalyzer.buildFullFlowGraph(
            code,
            listOf()
        )

        val cfg = CodeFlowAnalyzer.buildCFG(graph)

        exportAsPng(cfg, code, Path("src/test/resources/cfg_graph.png"))
    }

    @Test
    fun `Test build another CFG`() {
        val method = methodFor(Dest::class, "sample")
        val code = method.instructions
        val graph = CodeFlowAnalyzer.buildFullFlowGraph(
            code,
            method.tryCatchBlocks
        )

        val cfg = CodeFlowAnalyzer.buildCFG(graph)

        blockedInsn(cfg, code)
        exportAsPng(cfg, code, Path("src/test/resources/sample2_cfg_graph.png"))
    }

    @Test
    fun `Build large CFG`() {
        val code = insnFor(
            this::class.java.getResourceAsStream("/Minecraft.class")!!,
            "<init>"
        )
        val graph = CodeFlowAnalyzer.buildFullFlowGraph(
            code,
            listOf()
        )

        val cfg = CodeFlowAnalyzer.buildCFG(graph)

        exportAsPng(cfg, code, Path("src/test/resources/minecraft_graph.png"))
    }

    @Test
    fun `Test find dominance paths`() {
        val nodeA = CFGNode(listOf(VarInsnNode(1, 1)))
        val nodeB = CFGNode(listOf(VarInsnNode(1, 2)))
        val nodeC = CFGNode(listOf(VarInsnNode(1, 2)))
        val nodeD = CFGNode(listOf(VarInsnNode(1, 2)))

        nodeA.exitsBy.add(nodeB)
        nodeB.entersBy.add(nodeA)

        nodeB.exitsBy.add(nodeC)
        nodeC.entersBy.add(nodeB)

        nodeC.exitsBy.add(nodeD)
        nodeD.entersBy.add(nodeC)

        val aDomPath = CodeFlowAnalyzer.findDominancePaths(nodeA)

        check(aDomPath.size == 1)
        check(aDomPath[0] == listOf(nodeA))

        val bDomPath = CodeFlowAnalyzer.findDominancePaths(nodeB)

        check(bDomPath.size == 1)
        check(bDomPath[0] == listOf(nodeB, nodeA))

        val dDomPath = CodeFlowAnalyzer.findDominancePaths(nodeD)

        check(dDomPath.size == 1)
        check(dDomPath[0] == listOf(nodeD, nodeC, nodeB, nodeA))
    }

    @Test
    fun `Test total intersection`() {
        val elements = listOf(
            listOf(5, 6, 7),
            listOf(4, 3, 5),
            listOf(8, 7, 5),
        )

        check(elements.intersectAll() == listOf(5))
    }

    @Test
    fun `Test build linear dominance tree`() {
        val nodeA = CFGNode(listOf(VarInsnNode(1, 1)))
        val nodeB = CFGNode(listOf(VarInsnNode(1, 2)))
        val nodeC = CFGNode(listOf(VarInsnNode(1, 2)))

        nodeA.exitsBy.add(nodeB)
        nodeB.entersBy.add(nodeA)

        nodeB.exitsBy.add(nodeC)
        nodeC.entersBy.add(nodeB)

        val result = CodeFlowAnalyzer.buildDominator(nodeA)

        val expected = DominatorNode(
            nodeA,
            mutableSetOf(
                DominatorNode(
                    nodeB,
                    mutableSetOf(
                        DominatorNode(
                            nodeC,
                            mutableSetOf(),
                        )
                    ),
                )
            ),
        )

        check(result == expected)
    }

    private fun textify(list: List<AbstractInsnNode>): List<String> {
        return list.associateWith {
            when (it) {
                is FieldInsnNode -> "${it.owner} ${it.name} ${it.desc}"
                is IincInsnNode -> "${it.`var`} ${it.incr}"
                is IntInsnNode -> "${it.operand}"
                is InvokeDynamicInsnNode -> "${it.name} ${it.desc}"
                is JumpInsnNode -> "${it.label.label}"
                is LdcInsnNode -> "${it.cst}"
                is LineNumberNode -> "${it.line} ${it.start}"
                is LookupSwitchInsnNode -> "${it.dflt.label} ${it.keys} ${it.labels}"
                is MethodInsnNode -> "${it.owner} ${it.name} ${it.desc} ${it.itf}"
                is MultiANewArrayInsnNode -> it.desc
                is TableSwitchInsnNode -> "${it.min} ${it.max} ${it.dflt.label} ${it.labels}"
                is TypeInsnNode -> it.desc
                is VarInsnNode -> "${it.`var`}"
                is LabelNode -> "LABEL ${it.label}"
                else -> ""
            }
        }.map { "${opcodeToString(it.key.opcode)?.let { s -> "$s " } ?: ""}${it.value}" }

    }

    private fun blockedInsn(
        rootCFG: CFGNode,
        insn: InsnList
    ) {
        val all = HashSet<CFGNode>()
        CodeFlowAnalyzer.toList(rootCFG, all)

        val ordered = all.sortedBy {
            insn.indexOf(it.instructions.first())
        }

        fun toString(node: CFGNode): String {
            return ordered.indexOf(node).toString()
        }

        // 204
        ordered.forEach {
            println()
            println("---------------- " + toString(it))
            println()
            textify(it.instructions)
                .zip(it.instructions)
                .filterNot { it.first.isBlank() }
                .forEach { (it, node) ->
                    println("${insn.indexOf(node)} $it")
                }
        }
    }

    private fun exportAsPng(
        rootCFG: CFGNode,
        dom: DominatorNode,
        insn: InsnList,
        path: Path
    ) {
        val all = HashSet<CFGNode>()
        CodeFlowAnalyzer.toList(rootCFG, all)

        val ordered = all.sortedBy {
            insn.indexOf(it.instructions.first())
        }

        fun toString(node: CFGNode): String {
            return ordered.indexOf(node).toString()
        }

        val graph = DefaultDirectedGraph<String, DefaultEdge>(DefaultEdge::class.java)


        fun populate(
            graph: Graph<String, *>,
            node: DominatorNode
        ) {
            graph.addVertex(toString(node.node))

            node.children.forEach {
                populate(graph, it)
                graph.addEdge(toString(node.node), toString(it.node))
            }
        }

        populate(graph, dom)

        val graphAdapter = JGraphXAdapter<String, DefaultEdge>(graph)
        val layout: mxIGraphLayout = mxHierarchicalLayout(graphAdapter)
        layout.execute(graphAdapter.defaultParent)

        val image: BufferedImage = mxCellRenderer.createBufferedImage(
            graphAdapter, null, 5.0, Color.WHITE, true, null
        )
        val imgFile = path.toFile()
        ImageIO.write(image, "PNG", imgFile)
    }

    @Test
    fun `Test build dominator tree`() {
        val code = insnFor(Sample::class, "sampleMethod")
        val graph = CodeFlowAnalyzer.buildFullFlowGraph(
            code,
            listOf()
        )

        val cfg = CodeFlowAnalyzer.buildCFG(graph)

        val dominator = CodeFlowAnalyzer.buildDominator(cfg)

        exportAsPng(cfg, dominator, code, Path("src/test/resources/dominator_graph.png"))
        blockedInsn(cfg, code)
    }

    @Test
    fun `Test shortest path`() {
        val code = insnFor(Sample::class, "sampleMethod")
        val graph = CodeFlowAnalyzer.buildFullFlowGraph(
            code,
            listOf()
        )

        val cfg = CodeFlowAnalyzer.buildCFG(graph)

        val all = HashSet<CFGNode>()
        CodeFlowAnalyzer.toList(cfg, all)

        val ordered = all.sortedBy {
            code.indexOf(it.instructions.first())
        }

        val end = ordered[4]

        val shortest = CodeFlowAnalyzer.shortestPath(
            cfg, end
        )

        val shortestOrdered = shortest.map {
            ordered.indexOf(it)
        }

        check(shortestOrdered == listOf(4, 3, 1, 0))
        println(shortestOrdered)
    }

    @Test
    fun `Produces same result as frames`() {
        val method = methodFor(Dest::class, Dest::sample.name)
        val frames = method.instructions.filterIsInstance<FrameNode>()

        frames.forEach { f ->
            val simulated = analyzeFrames(
                f,
                SimulatedFrame(
                    listOf(),
                    listOf(ObjectValueRef(Type.getType(Dest::class.java)))
                )
            )

            val mappedFrame = f.stack.map {

            }
        }
    }
}