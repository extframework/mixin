package dev.extframework.mixin.test.internal.analysis

import com.mxgraph.layout.hierarchical.mxHierarchicalLayout
import com.mxgraph.layout.mxIGraphLayout
import com.mxgraph.util.mxCellRenderer
import dev.extframework.mixin.internal.analysis.CodeFlowAnalyzer
import dev.extframework.mixin.internal.analysis.CodeFlowAnalyzer.CFGNode
import dev.extframework.mixin.internal.analysis.CodeFlowAnalyzer.DominatorNode
import dev.extframework.mixin.internal.analysis.CodeFlowAnalyzer.intersectAll
import org.jgrapht.Graph
import org.jgrapht.ext.JGraphXAdapter
import org.jgrapht.graph.DefaultDirectedGraph
import org.jgrapht.graph.DefaultEdge
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.VarInsnNode
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.InputStream
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.Path
import kotlin.reflect.KClass
import kotlin.test.Test

class CodeFlowTests {
    fun insnFor(
        stream: InputStream,
        method: String,
    ): InsnList {
        val reader = ClassReader(stream)
        val node = ClassNode()
        reader.accept(node, 0)

        return node.methods.find {
            it.name == method
        }!!.instructions
    }

    fun insnFor(
        cls: KClass<*>,
        method: String,
    ): InsnList {
        val stream = CodeFlowTests::class.java.getResourceAsStream("/${cls.java.name.replace('.', '/')}.class")!!

        return insnFor(stream, method)
    }

    @Test
    fun `Test full flow graph`() {
        val code = insnFor(Sample::class, "sampleMethod")
        val graph = CodeFlowAnalyzer.buildFullFlowGraph(
            code,
        )


    }

    // The OB/GYN references go crazy
    @Test
    fun `Test small edge contraction`() {
        val element = InsnNode(5)
        val contraction = CodeFlowAnalyzer.contract(
            CodeFlowAnalyzer.CFGNode(listOf(element)),
            CodeFlowAnalyzer.CFGNode(listOf(element)),
        )

        check(contraction.instructions.toList() == listOf(element, element))
        check(contraction.entersBy.isEmpty())
        check(contraction.exitsBy.isEmpty())
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
        )

        val cfg = CodeFlowAnalyzer.buildCFG(graph)

        exportAsPng(cfg, code, Path("src/test/resources/cfg_graph.png"))

        fun roundUp(
            node: CFGNode,
            processed: MutableSet<CFGNode>,
        ): List<CFGNode> {
            processed.add(node)
            return listOf(node) + node.exitsBy
                .filterNot(processed::contains)
                .flatMap { roundUp(it, processed) }
        }
    }

    @Test
    fun `Build large CFG`() {
        val code = insnFor(
            this::class.java.getResourceAsStream("/Minecraft.class")!!,
            "<init>"
        )
        val graph = CodeFlowAnalyzer.buildFullFlowGraph(
            code,
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

        ordered.forEach { node ->
            println(toString(node))
            println(node.instructions.joinToString("\n"))
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
        )

        val cfg = CodeFlowAnalyzer.buildCFG(graph)

        val dominator = CodeFlowAnalyzer.buildDominator(cfg)

        exportAsPng(cfg, dominator, code, Path("src/test/resources/dominator_graph.png"))
        blockedInsn(cfg, code)
    }
}