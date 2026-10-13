-- dev-20261012-058: order review evidence points to bound attachment refs.
ALTER TABLE review_flow
    ADD COLUMN attachment_ref_ids JSON NULL COMMENT '本次审核使用的 sys_attachment_ref.id 数组' AFTER attachment_ids;
