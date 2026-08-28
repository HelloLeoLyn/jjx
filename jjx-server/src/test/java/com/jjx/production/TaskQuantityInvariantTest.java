package com.jjx.production;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P4 回归测试（纯逻辑）：树级统一数量不变式
 * taskQuantity = subtreeCompleted + subtreePending + assignedOutstanding + ownRemaining
 * 覆盖 P4 Proposal Mock 场景（Admin200 → 主任120 → 组长80 → B30 + C20）。
 */
class TaskQuantityInvariantTest {

    static final BigDecimal ZERO = BigDecimal.ZERO;

    static class Task {
        final BigDecimal taskQuantity;
        BigDecimal ownPending = ZERO;
        BigDecimal ownCompleted = ZERO;
        final List<Task> children = new ArrayList<>();

        Task(BigDecimal taskQuantity) {
            this.taskQuantity = taskQuantity;
        }
    }

    /** 模拟 P4 展示口径（与 ProductionTaskServiceImpl.project 一致） */
    private static BigDecimal[] display(Task t) {
        BigDecimal[] subtree = subtree(t);
        BigDecimal subtreeCompleted = subtree[0];
        BigDecimal subtreePending = subtree[1];
        BigDecimal childAssigned = t.children.stream()
                .map(c -> c.taskQuantity).reduce(ZERO, BigDecimal::add);
        BigDecimal assigned = floorZero(childAssigned
                .subtract(subtreeCompleted.subtract(t.ownCompleted))
                .subtract(subtreePending.subtract(t.ownPending)));
        BigDecimal remaining = floorZero(t.taskQuantity.subtract(childAssigned)
                .subtract(t.ownPending).subtract(t.ownCompleted));
        return new BigDecimal[]{subtreeCompleted, subtreePending, assigned, remaining};
    }

    private static BigDecimal[] subtree(Task t) {
        BigDecimal completed = t.ownCompleted;
        BigDecimal pending = t.ownPending;
        for (Task c : t.children) {
            BigDecimal[] sub = subtree(c);
            completed = completed.add(sub[0]);
            pending = pending.add(sub[1]);
        }
        return new BigDecimal[]{completed, pending};
    }

    private static BigDecimal floorZero(BigDecimal v) {
        return v == null || v.compareTo(ZERO) < 0 ? ZERO : v;
    }

    /** 构造树并断言每层不变式 */
    private static void assertInvariant(Task root) {
        if (root == null) return;
        BigDecimal[] d = display(root);
        BigDecimal sum = d[0].add(d[1]).add(d[2]).add(d[3]);
        assertEquals(0, root.taskQuantity.compareTo(sum),
                "不变式破坏 task=" + root.taskQuantity + " vs " + sum);
        for (Task c : root.children) {
            assertInvariant(c);
        }
    }

    private static Task tree() {
        Task admin = new Task(new BigDecimal("200"));
        Task director = new Task(new BigDecimal("120"));
        Task leader = new Task(new BigDecimal("80"));
        Task b = new Task(new BigDecimal("30"));
        Task c = new Task(new BigDecimal("20"));
        admin.children.add(director);
        director.children.add(leader);
        leader.children.add(b);
        leader.children.add(c);
        return admin;
    }

    @Test
    void initialZero_invariantHolds() {
        assertInvariant(tree());
    }

    @Test
    void mockScenario1_BApproved10() {
        Task root = tree();
        // B APPROVED 10
        findB(root).ownCompleted = new BigDecimal("10");
        assertInvariant(root);
        BigDecimal[] admin = display(root);
        assertEquals(0, new BigDecimal("10").compareTo(admin[0]));   // completed
        assertEquals(0, ZERO.compareTo(admin[1]));                    // pending
        assertEquals(0, new BigDecimal("110").compareTo(admin[2]));   // assigned
        assertEquals(0, new BigDecimal("80").compareTo(admin[3]));    // remaining
    }

    @Test
    void mockScenario5_DirectorOwnPending5() {
        Task root = tree();
        Task b = findB(root);
        Task leader = root.children.get(0).children.get(0);
        Task director = root.children.get(0);
        // B APPROVED 10 + PENDING 5；C APPROVED 8；组长 APPROVED 10；主任 PENDING 5
        b.ownCompleted = new BigDecimal("10");
        b.ownPending = new BigDecimal("5");
        findC(root).ownCompleted = new BigDecimal("8");
        leader.ownCompleted = new BigDecimal("10");
        director.ownPending = new BigDecimal("5");
        assertInvariant(root);
        BigDecimal[] admin = display(root);
        assertEquals(0, new BigDecimal("28").compareTo(admin[0]));
        assertEquals(0, new BigDecimal("10").compareTo(admin[1]));
        assertEquals(0, new BigDecimal("82").compareTo(admin[2]));
        assertEquals(0, new BigDecimal("80").compareTo(admin[3]));
        // 组长：80 = 28 + 5 + 27 + 20
        BigDecimal[] leaderD = display(leader);
        assertEquals(0, new BigDecimal("28").compareTo(leaderD[0]));
        assertEquals(0, new BigDecimal("5").compareTo(leaderD[1]));
        assertEquals(0, new BigDecimal("27").compareTo(leaderD[2]));
        assertEquals(0, new BigDecimal("20").compareTo(leaderD[3]));
        // 主任：120 = 28 + 10 + 47 + 35
        BigDecimal[] directorD = display(director);
        assertEquals(0, new BigDecimal("28").compareTo(directorD[0]));
        assertEquals(0, new BigDecimal("10").compareTo(directorD[1]));
        assertEquals(0, new BigDecimal("47").compareTo(directorD[2]));
        assertEquals(0, new BigDecimal("35").compareTo(directorD[3]));
    }

    @Test
    void completionDetailSumEqualsCompleted() {
        // 明细合计 = subtreeCompleted（APPROVED 事实和；对账 invariant）
        Task root = tree();
        Task b = findB(root);
        Task leader = root.children.get(0).children.get(0);
        b.ownCompleted = new BigDecimal("10");
        findC(root).ownCompleted = new BigDecimal("8");
        leader.ownCompleted = new BigDecimal("10");
        BigDecimal[] admin = display(root);
        // 明细行 = B10 + C8 + 组长10
        assertEquals(0, new BigDecimal("28").compareTo(admin[0]));
    }

    @Test
    void responsibilitySummaryIncludesPendingFromEntireChildSubtree() {
        Task responsibility = new Task(new BigDecimal("120"));
        Task directChild = new Task(new BigDecimal("70"));
        Task grandchild = new Task(new BigDecimal("40"));
        responsibility.children.add(directChild);
        directChild.children.add(grandchild);

        responsibility.ownCompleted = new BigDecimal("10");
        responsibility.ownPending = new BigDecimal("5");
        directChild.ownCompleted = new BigDecimal("12");
        directChild.ownPending = new BigDecimal("3");
        grandchild.ownCompleted = new BigDecimal("8");
        grandchild.ownPending = new BigDecimal("7");

        BigDecimal[] ownDisplay = display(responsibility);
        BigDecimal[] childSubtree = subtree(directChild);
        BigDecimal completedIncludingChildren = responsibility.ownCompleted.add(childSubtree[0]);
        BigDecimal pendingIncludingChildren = responsibility.ownPending.add(childSubtree[1]);
        BigDecimal myProcessable = ownDisplay[3];
        BigDecimal childProcessing = directChild.taskQuantity.subtract(childSubtree[0]);
        BigDecimal childPending = childSubtree[1];
        BigDecimal childUnfinished = childProcessing.subtract(childPending);

        assertEquals(0, new BigDecimal("10").compareTo(childPending),
                "直接子任务的 pending 应包含孙级子树报工");
        BigDecimal summary = completedIncludingChildren.add(pendingIncludingChildren)
                .add(myProcessable).add(childUnfinished);
        assertEquals(0, responsibility.taskQuantity.compareTo(summary),
                "责任 = 已完成(含下级) + 待审批(含下级) + 可处理 + 下级未完成");
    }

    private static Task findB(Task root) {
        return root.children.get(0).children.get(0).children.get(0);
    }

    private static Task findC(Task root) {
        return root.children.get(0).children.get(0).children.get(1);
    }
}
