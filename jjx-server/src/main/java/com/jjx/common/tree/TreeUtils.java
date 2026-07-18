package com.jjx.common.tree;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.util.CollectionUtils;
import java.util.*;

/**
 * 树形结构构建工具类
 */
public class TreeUtils {

    /**
     * 构建树（一行代码搞定）
     * @param list 数据列表
     * @param rootParentId 根节点父ID
     * @return 树形结构
     */
    public static <T extends TreeNode<T>> List<T> build(List<T> list, Long rootParentId) {
        if (CollectionUtils.isEmpty(list)) {
            return Collections.emptyList();
        }

        // 创建映射
        Map<Long, T> map = list.stream()
                .collect(Collectors.toMap(T::getId, node -> node, (a, b) -> a));

        List<T> tree = new ArrayList<>();

        for (T node : list) {
            Long parentId = node.getParentId();
            if (parentId == null) {
                parentId = rootParentId;
            }

            if (Objects.equals(parentId, rootParentId)) {
                tree.add(node);
            } else {
                T parent = map.get(parentId);
                if (parent != null) {
                    if (parent.getChildren() == null) {
                        parent.setChildren(new ArrayList<>());
                    }
                    parent.getChildren().add(node);
                }
            }
        }

        return tree;
    }

    /**
     * 构建树（默认根节点ID为0）
     */
    public static <T extends TreeNode<T>> List<T> build(List<T> list) {
        return build(list, 0L);
    }

    /**
     * 构建树并排序
     */
    public static <T extends TreeNode<T>> List<T> buildAndSort(List<T> list,
                                                               Comparator<T> comparator,
                                                               Long rootParentId) {
        List<T> tree = build(list, rootParentId);
        sortTree(tree, comparator);
        return tree;
    }

    /**
     * 递归排序
     */
    private static <T extends TreeNode<T>> void sortTree(List<T> tree, Comparator<T> comparator) {
        if (CollectionUtils.isEmpty(tree)) {
            return;
        }
        tree.sort(comparator);
        for (T node : tree) {
            if (!CollectionUtils.isEmpty(node.getChildren())) {
                sortTree(node.getChildren(), comparator);
            }
        }
    }
}
