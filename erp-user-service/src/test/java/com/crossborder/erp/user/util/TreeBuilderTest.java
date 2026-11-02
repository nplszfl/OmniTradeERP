package com.crossborder.erp.user.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 树构建工具测试
 *
 * <p>⚠️ 这个工具的第一版有个很隐蔽的 bug：用「父 id」去取子节点，
 * 实际取到的是「兄弟列表」（包含自己）→ 自引用 → JSON 序列化时 StackOverflowError。
 * 单测全绿也测不出来，因为树的规模小、且没人真的去序列化它。
 * 这里用「自引用检查」把它钉死。</p>
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
class TreeBuilderTest {

    static class Node {
        Long id;
        Long parentId;
        String name;
        List<Node> children = new ArrayList<>();

        Node(Long id, Long parentId, String name) {
            this.id = id;
            this.parentId = parentId;
            this.name = name;
        }

        void setChildren(List<Node> children) {
            this.children = children;
        }
    }

    private List<Node> build(List<Node> nodes) {
        return TreeBuilder.build(nodes, n -> n.id, n -> n.parentId, Node::setChildren, 0L);
    }

    @Test
    @DisplayName("构建正确的父子关系")
    void buildsHierarchy() {
        Node root = new Node(1L, 0L, "root");
        Node child = new Node(2L, 1L, "child");
        Node grandChild = new Node(3L, 2L, "grandChild");

        List<Node> roots = build(List.of(root, child, grandChild));

        assertEquals(1, roots.size());
        assertEquals("root", roots.get(0).name);
        assertEquals(1, roots.get(0).children.size());
        assertEquals("child", roots.get(0).children.get(0).name);
        assertEquals(1, roots.get(0).children.get(0).children.size());
        assertEquals("grandChild", roots.get(0).children.get(0).children.get(0).name);
    }

    @Test
    @DisplayName("子节点列表不能包含自己（自引用会导致序列化栈溢出）")
    void childrenDoNotContainSelf() {
        Node root = new Node(1L, 0L, "root");
        Node child = new Node(2L, 1L, "child");

        List<Node> roots = build(List.of(root, child));

        Node builtRoot = roots.get(0);
        assertNotSame(builtRoot.children, builtRoot.children.get(0).children);
        for (Node n : builtRoot.children) {
            assertTrue(n.id != builtRoot.id, "子节点列表里不能有自己");
        }
        assertEquals(1, builtRoot.children.size());
        assertEquals(2L, builtRoot.children.get(0).id);
    }

    @Test
    @DisplayName("多个根节点")
    void multipleRoots() {
        List<Node> roots = build(List.of(new Node(1L, 0L, "a"), new Node(2L, 0L, "b")));
        assertEquals(2, roots.size());
    }

    @Test
    @DisplayName("空列表 → 空树")
    void emptyList() {
        assertTrue(build(List.of()).isEmpty());
    }
}
