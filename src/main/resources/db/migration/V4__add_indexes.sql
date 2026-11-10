-- 解决删除引用检查的全表扫描
CREATE INDEX idx_teacher_dept_no ON teacher(dept_no);
CREATE INDEX idx_teacher_title_no ON teacher(title_no);
CREATE INDEX idx_major_dept_no ON major(dept_no);
CREATE INDEX idx_teach_class_semester ON teach_class(semester);

-- 解决通知列表查询的全表扫描（最关键的一个）
CREATE INDEX idx_notice_read_user_no ON sys_notice_read(user_no);