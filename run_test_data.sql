-- 自动化测试专用的测试库初始化脚本
-- 使用方式：
-- 1. docker exec -i labsystem-mysql mysql -uroot "-p..." -e "DROP DATABASE IF EXISTS lab_management_test; CREATE DATABASE lab_management_test;"
-- 2. docker cp src/main/resources/db/migration/V1__init.sql labsystem-mysql:/tmp/V1.sql
-- 3. docker exec -i labsystem-mysql mysql -uroot "-p..." lab_management_test -e "source /tmp/V1.sql"
-- 4. docker exec -i labsystem-mysql mysql -uroot "-p..." lab_management_test -e "source /tmp/run_test_data.sql"

SET FOREIGN_KEY_CHECKS = 0;

INSERT IGNORE INTO department (dept_no, dept_name) VALUES ('D01', 'TestDept');
INSERT IGNORE INTO major (major_no, major_name, dept_no) VALUES ('M01', 'TestMajor', 'D01');
INSERT IGNORE INTO admin_class (admin_class_no, major_no, enrollment_year, class_seq) VALUES ('202601', 'M01', 2026, 1);
-- 强制改密必须设为 0，否则 JwtInterceptor 会拦截测试请求
INSERT IGNORE INTO teacher (work_no, name, password, must_change_password) VALUES ('T202601', 'TeacherWang', '123456', 0);
INSERT IGNORE INTO student (student_no, name, password, admin_class_no, must_change_password) VALUES ('20260001', 'ZhangSan', '123456', '202601', 0);
INSERT IGNORE INTO exp_course (course_no, course_name, semester) VALUES ('C01', 'TestCourse', '2026');
INSERT IGNORE INTO teach_class (class_no, course_no, work_no, semester) VALUES ('TC012601', 'C01', 'T202601', '2026');
INSERT IGNORE INTO lab_room (room_no, room_name, capacity) VALUES ('L01', 'TestLab', 30);
INSERT IGNORE INTO exp_project (project_no, project_name, class_no) VALUES ('P2611001', 'TestProject', 'TC012601');
INSERT IGNORE INTO exp_session (session_no, project_no, room_no, work_no, session_time, capacity, remaining_seats) VALUES ('S2026110301', 'P2611001', 'L01', 'T202601', NOW(), 30, 30);
INSERT IGNORE INTO student_teach_class (student_no, class_no, semester) VALUES ('20260001', 'TC012601', '2026');

UPDATE sys_admin SET must_change_password = 0;

SET FOREIGN_KEY_CHECKS = 1;