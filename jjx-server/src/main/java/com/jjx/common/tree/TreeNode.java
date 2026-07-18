package com.jjx.common.tree;

import java.util.List;

/**
 * 树节点接口 - 实体类实现此接口即可获得树形结构能力
 */
public interface TreeNode<T> {

    Long getId();

    Long getParentId();

    List<T> getChildren();

    void setChildren(List<T> children);
}
