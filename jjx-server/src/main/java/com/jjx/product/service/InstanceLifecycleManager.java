package com.jjx.product.service;

import com.jjx.product.domain.entity.ProductInstance;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 实例生命周期管理器
 * 管理产品实例的完整生命周期状态转换
 */
@Component
public class InstanceLifecycleManager {

    // 状态转换映射表
    private static final Map<String, Set<String>> STATE_TRANSITIONS = new HashMap<>();

    static {
        // 初始化状态转换规则
        Set<String> draftTransitions = new HashSet<>(Arrays.asList("planned", "cancelled"));
        STATE_TRANSITIONS.put("draft", draftTransitions);

        Set<String> plannedTransitions = new HashSet<>(Arrays.asList("in_production", "cancelled"));
        STATE_TRANSITIONS.put("planned", plannedTransitions);

        Set<String> inProductionTransitions = new HashSet<>(Arrays.asList("completed", "paused", "cancelled"));
        STATE_TRANSITIONS.put("in_production", inProductionTransitions);

        Set<String> pausedTransitions = new HashSet<>(Arrays.asList("in_production", "cancelled"));
        STATE_TRANSITIONS.put("paused", pausedTransitions);

        Set<String> completedTransitions = new HashSet<>(Arrays.asList("shipped", "stored"));
        STATE_TRANSITIONS.put("completed", completedTransitions);

        Set<String> shippedTransitions = new HashSet<>(Arrays.asList("delivered", "returned"));
        STATE_TRANSITIONS.put("shipped", shippedTransitions);

        Set<String> storedTransitions = new HashSet<>(Arrays.asList("shipped", "scrapped"));
        STATE_TRANSITIONS.put("stored", storedTransitions);

        Set<String> deliveredTransitions = new HashSet<>(Arrays.asList("installed", "returned"));
        STATE_TRANSITIONS.put("delivered", deliveredTransitions);

        Set<String> installedTransitions = new HashSet<>(Arrays.asList("in_service", "maintenance"));
        STATE_TRANSITIONS.put("installed", installedTransitions);

        Set<String> inServiceTransitions = new HashSet<>(Arrays.asList("maintenance", "decommissioned"));
        STATE_TRANSITIONS.put("in_service", inServiceTransitions);

        Set<String> maintenanceTransitions = new HashSet<>(Arrays.asList("in_service", "decommissioned"));
        STATE_TRANSITIONS.put("maintenance", maintenanceTransitions);

        Set<String> returnedTransitions = new HashSet<>(Arrays.asList("refurbished", "scrapped"));
        STATE_TRANSITIONS.put("returned", returnedTransitions);

        Set<String> refurbishedTransitions = new HashSet<>(Arrays.asList("in_stock", "scrapped"));
        STATE_TRANSITIONS.put("refurbished", refurbishedTransitions);

        Set<String> inStockTransitions = new HashSet<>(Arrays.asList("shipped", "scrapped"));
        STATE_TRANSITIONS.put("in_stock", inStockTransitions);

        Set<String> scrappedTransitions = new HashSet<>(Collections.emptyList());
        STATE_TRANSITIONS.put("scrapped", scrappedTransitions);

        Set<String> decommissionedTransitions = new HashSet<>(Collections.emptyList());
        STATE_TRANSITIONS.put("decommissioned", decommissionedTransitions);

        Set<String> cancelledTransitions = new HashSet<>(Collections.emptyList());
        STATE_TRANSITIONS.put("cancelled", cancelledTransitions);
    }

    /**
     * 验证状态转换是否允许
     * @param currentState 当前状态
     * @param targetState 目标状态
     * @return true: 允许转换, false: 不允许转换
     */
    public boolean validateStateTransition(String currentState, String targetState) {
        if (currentState == null || targetState == null) {
            return false;
        }

        // 相同状态不需要转换
        if (currentState.equals(targetState)) {
            return true;
        }

        Set<String> allowedTransitions = STATE_TRANSITIONS.get(currentState);
        return allowedTransitions != null && allowedTransitions.contains(targetState);
    }

    /**
     * 执行状态转换
     * @param instance 产品实例
     * @param targetState 目标状态
     * @param transitionData 转换数据
     * @return 状态转换结果
     */
    public StateTransitionResult executeStateTransition(ProductInstance instance,
                                                       String targetState,
                                                       Map<String, Object> transitionData) {
        StateTransitionResult result = new StateTransitionResult();
        result.setInstanceId(instance.getInstanceId());
        result.setInstanceCode(instance.getInstanceCode());
        result.setCurrentState(instance.getInstanceStatus());
        result.setTargetState(targetState);

        // 验证状态转换
        if (!validateStateTransition(instance.getInstanceStatus(), targetState)) {
            result.setSuccess(false);
            result.setErrorMessage("状态转换不允许: " + instance.getInstanceStatus() + " -> " + targetState);
            return result;
        }

        try {
            // 执行状态转换前的钩子
            executePreTransitionHook(instance, targetState, transitionData);

            // 记录状态历史
            StateHistory history = createStateHistory(instance, targetState, transitionData);
            result.setStateHistory(history);

            // 更新实例状态
            String previousState = instance.getInstanceStatus();
            instance.setInstanceStatus(targetState);
            instance.setUpdateTime(LocalDateTime.now());

            // 根据目标状态设置相关字段
            updateInstanceFields(instance, targetState, transitionData);

            // 执行状态转换后的钩子
            executePostTransitionHook(instance, previousState, targetState, transitionData);

            result.setSuccess(true);
            result.setPreviousState(previousState);
            result.setNewState(targetState);
            result.setTransitionTime(LocalDateTime.now());

        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorMessage("状态转换失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 执行状态转换前的钩子
     */
    private void executePreTransitionHook(ProductInstance instance, String targetState,
                                         Map<String, Object> transitionData) {
        // 这里可以添加状态转换前的验证逻辑
        // 例如：检查实例是否满足转换条件

        switch (targetState) {
            case "in_production":
                validateProductionStart(instance, transitionData);
                break;
            case "completed":
                validateProductionCompletion(instance, transitionData);
                break;
            case "shipped":
                validateShipping(instance, transitionData);
                break;
            case "delivered":
                validateDelivery(instance, transitionData);
                break;
            case "installed":
                validateInstallation(instance, transitionData);
                break;
            case "scrapped":
                validateScrapping(instance, transitionData);
                break;
        }
    }

    /**
     * 执行状态转换后的钩子
     */
    private void executePostTransitionHook(ProductInstance instance, String previousState,
                                          String newState, Map<String, Object> transitionData) {
        // 这里可以添加状态转换后的处理逻辑
        // 例如：发送通知、更新相关记录等

        switch (newState) {
            case "in_production":
                onProductionStart(instance, transitionData);
                break;
            case "completed":
                onProductionCompletion(instance, transitionData);
                break;
            case "shipped":
                onShipping(instance, transitionData);
                break;
            case "delivered":
                onDelivery(instance, transitionData);
                break;
            case "installed":
                onInstallation(instance, transitionData);
                break;
            case "in_service":
                onServiceStart(instance, transitionData);
                break;
            case "scrapped":
                onScrapping(instance, transitionData);
                break;
        }
    }

    /**
     * 创建状态历史记录
     */
    private StateHistory createStateHistory(ProductInstance instance, String targetState,
                                           Map<String, Object> transitionData) {
        StateHistory history = new StateHistory();
        history.setInstanceId(instance.getInstanceId());
        history.setFromState(instance.getInstanceStatus());
        history.setToState(targetState);
        history.setTransitionTime(LocalDateTime.now());

        if (transitionData != null) {
            history.setOperatorId((Long) transitionData.get("operatorId"));
            history.setOperatorName((String) transitionData.get("operatorName"));
            history.setRemark((String) transitionData.get("remark"));
            history.setAdditionalData(transitionData);
        }

        return history;
    }

    /**
     * 更新实例字段
     */
    private void updateInstanceFields(ProductInstance instance, String targetState,
                                     Map<String, Object> transitionData) {
        LocalDateTime now = LocalDateTime.now();

//        switch (targetState) {
//            case "in_production":
//                instance.setProductionStartTime(now);
//                break;
//            case "completed":
//                instance.setProductionEndTime(now);
//                if (transitionData != null && transitionData.containsKey("actualQuantity")) {
//                    instance.setActualQuantity((Integer) transitionData.get("actualQuantity"));
//                }
//                break;
//            case "shipped":
//                instance.setShippingTime(now);
//                if (transitionData != null) {
//                    instance.setShippingNo((String) transitionData.get("shippingNo"));
//                    instance.setShippingCompany((String) transitionData.get("shippingCompany"));
//                }
//                break;
//            case "delivered":
//                instance.setDeliveryTime(now);
//                if (transitionData != null) {
//                    instance.setReceiverName((String) transitionData.get("receiverName"));
//                    instance.setReceiverPhone((String) transitionData.get("receiverPhone"));
//                }
//                break;
//            case "installed":
//                instance.setInstallationTime(now);
//                if (transitionData != null) {
//                    instance.setInstallationAddress((String) transitionData.get("installationAddress"));
//                    instance.setInstallerName((String) transitionData.get("installerName"));
//                }
//                break;
//            case "in_service":
//                instance.setServiceStartTime(now);
//                break;
//            case "scrapped":
//                instance.setScrapTime(now);
//                if (transitionData != null) {
//                    instance.setScrapReason((String) transitionData.get("scrapReason"));
//                    instance.setScrapApprover((String) transitionData.get("scrapApprover"));
//                }
//                break;
//        }
    }

    /**
     * 验证生产开始
     */
    private void validateProductionStart(ProductInstance instance, Map<String, Object> transitionData) {
        // 验证生产开始条件
//        if (instance.getPlannedQuantity() == null || instance.getPlannedQuantity() <= 0) {
//            throw new IllegalStateException("计划数量必须大于0");
//        }
//
//        if (instance.getProductionOrderId() == null) {
//            throw new IllegalStateException("生产订单不能为空");
//        }
    }

    /**
     * 验证生产完成
     */
    private void validateProductionCompletion(ProductInstance instance, Map<String, Object> transitionData) {
        // 验证生产完成条件
        if (transitionData == null || !transitionData.containsKey("actualQuantity")) {
            throw new IllegalStateException("实际数量不能为空");
        }

        Integer actualQuantity = (Integer) transitionData.get("actualQuantity");
        if (actualQuantity == null || actualQuantity <= 0) {
            throw new IllegalStateException("实际数量必须大于0");
        }
    }

    /**
     * 验证发货
     */
    private void validateShipping(ProductInstance instance, Map<String, Object> transitionData) {
        // 验证发货条件
        if (transitionData == null || !transitionData.containsKey("shippingNo")) {
            throw new IllegalStateException("发货单号不能为空");
        }

//        if (instance.getActualQuantity() == null || instance.getActualQuantity() <= 0) {
//            throw new IllegalStateException("实际数量必须大于0才能发货");
//        }
    }

    /**
     * 验证交付
     */
    private void validateDelivery(ProductInstance instance, Map<String, Object> transitionData) {
        // 验证交付条件
        if (transitionData == null || !transitionData.containsKey("receiverName")) {
            throw new IllegalStateException("收货人姓名不能为空");
        }
    }

    /**
     * 验证安装
     */
    private void validateInstallation(ProductInstance instance, Map<String, Object> transitionData) {
        // 验证安装条件
        if (transitionData == null || !transitionData.containsKey("installationAddress")) {
            throw new IllegalStateException("安装地址不能为空");
        }
    }

    /**
     * 验证报废
     */
    private void validateScrapping(ProductInstance instance, Map<String, Object> transitionData) {
        // 验证报废条件
        if (transitionData == null || !transitionData.containsKey("scrapReason")) {
            throw new IllegalStateException("报废原因不能为空");
        }

        if (!transitionData.containsKey("scrapApprover")) {
            throw new IllegalStateException("报废审批人不能为空");
        }
    }

    /**
     * 生产开始处理
     */
    private void onProductionStart(ProductInstance instance, Map<String, Object> transitionData) {
        // 生产开始后的处理逻辑
        // 例如：创建生产任务、分配资源等
    }

    /**
     * 生产完成处理
     */
    private void onProductionCompletion(ProductInstance instance, Map<String, Object> transitionData) {
        // 生产完成后的处理逻辑
        // 例如：更新库存、计算成本等
    }

    /**
     * 发货处理
     */
    private void onShipping(ProductInstance instance, Map<String, Object> transitionData) {
        // 发货后的处理逻辑
        // 例如：更新订单状态、发送发货通知等
    }

    /**
     * 交付处理
     */
    private void onDelivery(ProductInstance instance, Map<String, Object> transitionData) {
        // 交付后的处理逻辑
        // 例如：更新客户记录、发送交付确认等
    }

    /**
     * 安装处理
     */
    private void onInstallation(ProductInstance instance, Map<String, Object> transitionData) {
        // 安装后的处理逻辑
        // 例如：创建服务记录、发送安装完成通知等
    }

    /**
     * 服务开始处理
     */
    private void onServiceStart(ProductInstance instance, Map<String, Object> transitionData) {
        // 服务开始后的处理逻辑
        // 例如：创建服务合同、设置维保计划等
    }

    /**
     * 报废处理
     */
    private void onScrapping(ProductInstance instance, Map<String, Object> transitionData) {
        // 报废后的处理逻辑
        // 例如：更新资产记录、处理残值等
    }

    /**
     * 获取允许的状态转换
     * @param currentState 当前状态
     * @return 允许转换的状态列表
     */
    public List<String> getAllowedTransitions(String currentState) {
        Set<String> transitions = STATE_TRANSITIONS.get(currentState);
        return transitions != null ? new ArrayList<>(transitions) : new ArrayList<>();
    }

    /**
     * 获取实例生命周期状态
     * @param instance 产品实例
     * @return 生命周期状态
     */
    public LifecycleStatus getLifecycleStatus(ProductInstance instance) {
        LifecycleStatus status = new LifecycleStatus();
        status.setInstanceId(instance.getInstanceId());
        status.setInstanceCode(instance.getInstanceCode());
        status.setCurrentState(instance.getInstanceStatus());
        status.setAllowedTransitions(getAllowedTransitions(instance.getInstanceStatus()));

        // 计算生命周期阶段
        String phase = calculateLifecyclePhase(instance.getInstanceStatus());
        status.setLifecyclePhase(phase);

        // 计算进度百分比
        int progress = calculateLifecycleProgress(instance.getInstanceStatus());
        status.setProgressPercentage(progress);

        return status;
    }

    /**
     * 计算生命周期阶段
     */
    private String calculateLifecyclePhase(String state) {
        switch (state) {
            case "draft":
            case "planned":
                return "计划阶段";
            case "in_production":
            case "paused":
                return "生产阶段";
            case "completed":
            case "stored":
                return "完成阶段";
            case "shipped":
            case "delivered":
                return "交付阶段";
            case "installed":
            case "in_service":
            case "maintenance":
                return "服务阶段";
            case "returned":
            case "refurbished":
            case "in_stock":
                return "回收阶段";
            case "scrapped":
            case "decommissioned":
            case "cancelled":
                return "终止阶段";
            default:
                return "未知阶段";
        }
    }

    /**
     * 计算生命周期进度
     */
    private int calculateLifecycleProgress(String state) {
        Map<String, Integer> progressMap = new HashMap<>();
        progressMap.put("draft", 0);
        progressMap.put("planned", 10);
        progressMap.put("in_production", 30);
        progressMap.put("paused", 30);
        progressMap.put("completed", 60);
        progressMap.put("stored", 60);
        progressMap.put("shipped", 70);
        progressMap.put("delivered", 80);
        progressMap.put("installed", 85);
        progressMap.put("in_service", 90);
        progressMap.put("maintenance", 90);
        progressMap.put("returned", 95);
        progressMap.put("refurbished", 95);
        progressMap.put("in_stock", 95);
        progressMap.put("scrapped", 100);
        progressMap.put("decommissioned", 100);
        progressMap.put("cancelled", 100);

        return progressMap.getOrDefault(state, 0);
    }

    /**
     * 状态转换结果
     */
    public static class StateTransitionResult {
        private Long instanceId;
        private String instanceCode;
        private String currentState;
        private String targetState;
        private boolean success;
        private String errorMessage;
        private String previousState;
        private String newState;
        private LocalDateTime transitionTime;
        private StateHistory stateHistory;

        // Getters and Setters
        public Long getInstanceId() {
            return instanceId;
        }

        public void setInstanceId(Long instanceId) {
            this.instanceId = instanceId;
        }

        public String getInstanceCode() {
            return instanceCode;
        }

        public void setInstanceCode(String instanceCode) {
            this.instanceCode = instanceCode;
        }

        public String getCurrentState() {
            return currentState;
        }

        public void setCurrentState(String currentState) {
            this.currentState = currentState;
        }

        public String getTargetState() {
            return targetState;
        }

        public void setTargetState(String targetState) {
            this.targetState = targetState;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public String getPreviousState() {
            return previousState;
        }

        public void setPreviousState(String previousState) {
            this.previousState = previousState;
        }

        public String getNewState() {
            return newState;
        }

        public void setNewState(String newState) {
            this.newState = newState;
        }

        public LocalDateTime getTransitionTime() {
            return transitionTime;
        }

        public void setTransitionTime(LocalDateTime transitionTime) {
            this.transitionTime = transitionTime;
        }

        public StateHistory getStateHistory() {
            return stateHistory;
        }

        public void setStateHistory(StateHistory stateHistory) {
            this.stateHistory = stateHistory;
        }

        @Override
        public String toString() {
            return "StateTransitionResult{" +
                    "instanceId=" + instanceId +
                    ", instanceCode='" + instanceCode + '\'' +
                    ", currentState='" + currentState + '\'' +
                    ", targetState='" + targetState + '\'' +
                    ", success=" + success +
                    ", errorMessage='" + errorMessage + '\'' +
                    ", previousState='" + previousState + '\'' +
                    ", newState='" + newState + '\'' +
                    ", transitionTime=" + transitionTime +
                    ", stateHistory=" + stateHistory +
                    '}';
        }
    }

    /**
     * 状态历史记录
     */
    public static class StateHistory {
        private Long instanceId;
        private String fromState;
        private String toState;
        private LocalDateTime transitionTime;
        private Long operatorId;
        private String operatorName;
        private String remark;
        private Map<String, Object> additionalData;

        // Getters and Setters
        public Long getInstanceId() {
            return instanceId;
        }

        public void setInstanceId(Long instanceId) {
            this.instanceId = instanceId;
        }

        public String getFromState() {
            return fromState;
        }

        public void setFromState(String fromState) {
            this.fromState = fromState;
        }

        public String getToState() {
            return toState;
        }

        public void setToState(String toState) {
            this.toState = toState;
        }

        public LocalDateTime getTransitionTime() {
            return transitionTime;
        }

        public void setTransitionTime(LocalDateTime transitionTime) {
            this.transitionTime = transitionTime;
        }

        public Long getOperatorId() {
            return operatorId;
        }

        public void setOperatorId(Long operatorId) {
            this.operatorId = operatorId;
        }

        public String getOperatorName() {
            return operatorName;
        }

        public void setOperatorName(String operatorName) {
            this.operatorName = operatorName;
        }

        public String getRemark() {
            return remark;
        }

        public void setRemark(String remark) {
            this.remark = remark;
        }

        public Map<String, Object> getAdditionalData() {
            return additionalData;
        }

        public void setAdditionalData(Map<String, Object> additionalData) {
            this.additionalData = additionalData;
        }

        @Override
        public String toString() {
            return "StateHistory{" +
                    "instanceId=" + instanceId +
                    ", fromState='" + fromState + '\'' +
                    ", toState='" + toState + '\'' +
                    ", transitionTime=" + transitionTime +
                    ", operatorId=" + operatorId +
                    ", operatorName='" + operatorName + '\'' +
                    ", remark='" + remark + '\'' +
                    ", additionalData=" + additionalData +
                    '}';
        }
    }

    /**
     * 生命周期状态
     */
    public static class LifecycleStatus {
        private Long instanceId;
        private String instanceCode;
        private String currentState;
        private List<String> allowedTransitions;
        private String lifecyclePhase;
        private int progressPercentage;

        // Getters and Setters
        public Long getInstanceId() {
            return instanceId;
        }

        public void setInstanceId(Long instanceId) {
            this.instanceId = instanceId;
        }

        public String getInstanceCode() {
            return instanceCode;
        }

        public void setInstanceCode(String instanceCode) {
            this.instanceCode = instanceCode;
        }

        public String getCurrentState() {
            return currentState;
        }

        public void setCurrentState(String currentState) {
            this.currentState = currentState;
        }

        public List<String> getAllowedTransitions() {
            return allowedTransitions;
        }

        public void setAllowedTransitions(List<String> allowedTransitions) {
            this.allowedTransitions = allowedTransitions;
        }

        public String getLifecyclePhase() {
            return lifecyclePhase;
        }

        public void setLifecyclePhase(String lifecyclePhase) {
            this.lifecyclePhase = lifecyclePhase;
        }

        public int getProgressPercentage() {
            return progressPercentage;
        }

        public void setProgressPercentage(int progressPercentage) {
            this.progressPercentage = progressPercentage;
        }

        @Override
        public String toString() {
            return "LifecycleStatus{" +
                    "instanceId=" + instanceId +
                    ", instanceCode='" + instanceCode + '\'' +
                    ", currentState='" + currentState + '\'' +
                    ", allowedTransitions=" + allowedTransitions +
                    ", lifecyclePhase='" + lifecyclePhase + '\'' +
                    ", progressPercentage=" + progressPercentage +
                    '}';
        }
    }
}
