package com.crossborder.erp.user.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 通用树构建工具
 *
 * <p>权限树、部门树共用同一套「扁平列表 → 树」的逻辑。</p>
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
public final class TreeBuilder {

    private TreeBuilder() {
    }

    /**
     * 由扁平列表构建树
     *
     * @param list        扁平数据
     * @param idFn        取节点ID
     * @param parentIdFn  取父节点ID
     * @param childrenFn  设置子节点列表
     * @param rootParentId 根节点的父ID（权限/部门都是 0）
     * @return 树的根节点列表
     */
    public static <T> List<T> build(List<T> list,
                                    Function<T, Long> idFn,
                                    Function<T, Long> parentIdFn,
                                    java.util.function.BiConsumer<T, List<T>> childrenFn,
                                    Long rootParentId) {
        List<T> roots = new ArrayList<>();
        if (list == null || list.isEmpty()) {
            return roots;
        }
        Map<Long, List<T>> childrenMap = new LinkedHashMap<>();
        for (T node : list) {
            Long parentId = parentIdFn.apply(node);
            childrenMap.computeIfAbsent(parentId == null ? rootParentId : parentId, k -> new ArrayList<>())
                    .add(node);
        }
        for (T node : list) {
            // ⚠️ 这里必须用「自己的 id」去取子节点，而不是「父 id」——
            //    用父 id 取到的是「兄弟列表」（包含自己），会构成自引用死循环，
            //    序列化时直接 StackOverflowError。
            List<T> children = childrenMap.get(idFn.apply(node));
            childrenFn.accept(node, children == null ? new ArrayList<>() : children);

            Long parentId = parentIdFn.apply(node);
            Long effectiveParent = parentId == null ? rootParentId : parentId;
            if (rootParentId.equals(effectiveParent)) {
                roots.add(node);
            }
        }
        return roots;
    }
}
