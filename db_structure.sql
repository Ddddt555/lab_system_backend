-- MySQL dump 10.13  Distrib 8.0.29, for Win64 (x86_64)
--
-- Host: localhost    Database: lab_management
-- ------------------------------------------------------
-- Server version	8.0.29

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `admin_class`
--

DROP TABLE IF EXISTS `admin_class`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `admin_class` (
  `admin_class_no` varchar(50) NOT NULL COMMENT '行政班号，如 AC001',
  `major_no` varchar(50) NOT NULL COMMENT '所属专业号',
  `enrollment_year` int DEFAULT NULL COMMENT '入学年份，如2026',
  `class_seq` int DEFAULT NULL COMMENT '班级序号，如1',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除: 0未删, 1已删',
  PRIMARY KEY (`admin_class_no`),
  KEY `fk_major` (`major_no`),
  CONSTRAINT `fk_major` FOREIGN KEY (`major_no`) REFERENCES `major` (`major_no`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='行政班表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `attendance_record`
--

DROP TABLE IF EXISTS `attendance_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `attendance_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `session_no` varchar(50) NOT NULL COMMENT '场次号',
  `student_no` varchar(50) NOT NULL COMMENT '学号',
  `sign_time` datetime DEFAULT NULL COMMENT '签到时间',
  `status` int DEFAULT '1' COMMENT '1已签到',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_session_student` (`session_no`,`student_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='考勤签到记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `department`
--

DROP TABLE IF EXISTS `department`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `department` (
  `dept_no` varchar(50) NOT NULL COMMENT '院系号，如 D001',
  `dept_name` varchar(100) NOT NULL COMMENT '院系名称',
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`dept_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='院系表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `exp_course`
--

DROP TABLE IF EXISTS `exp_course`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `exp_course` (
  `course_no` varchar(20) NOT NULL COMMENT '课程号',
  `course_name` varchar(100) NOT NULL COMMENT '课程名',
  `credit` decimal(3,1) DEFAULT NULL COMMENT '学分',
  `hours` int DEFAULT NULL COMMENT '学时',
  `semester` varchar(20) DEFAULT NULL COMMENT '学期',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除: 0未删, 1已删',
  PRIMARY KEY (`course_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='实验课程表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `exp_project`
--

DROP TABLE IF EXISTS `exp_project`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `exp_project` (
  `project_no` varchar(20) NOT NULL COMMENT '项目号',
  `project_name` varchar(100) NOT NULL COMMENT '项目名称',
  `hours` int DEFAULT NULL COMMENT '学时',
  `guide_file` varchar(255) DEFAULT NULL COMMENT '实验指导书',
  `standard` varchar(255) DEFAULT NULL COMMENT '考核标准',
  `class_no` varchar(50) DEFAULT NULL COMMENT '所属教学班号',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除: 0未删, 1已删',
  PRIMARY KEY (`project_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='实验项目表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `exp_report`
--

DROP TABLE IF EXISTS `exp_report`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `exp_report` (
  `report_no` varchar(20) NOT NULL COMMENT '报告号',
  `student_no` varchar(20) NOT NULL COMMENT '学号',
  `project_no` varchar(20) NOT NULL COMMENT '项目号',
  `file_url` varchar(255) NOT NULL COMMENT '文件路径',
  `version` int DEFAULT '1' COMMENT '版本号',
  `is_latest` tinyint DEFAULT '1' COMMENT '是否最新版本：1是，0否',
  `submit_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
  `score` decimal(5,2) DEFAULT NULL COMMENT '报告得分',
  `comment` varchar(500) DEFAULT NULL COMMENT '教师评语',
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`report_no`),
  UNIQUE KEY `uk_student_project_version` (`student_no`,`project_no`,`version`,`deleted`),
  KEY `project_no` (`project_no`),
  CONSTRAINT `exp_report_ibfk_1` FOREIGN KEY (`student_no`) REFERENCES `student` (`student_no`) ON DELETE CASCADE,
  CONSTRAINT `exp_report_ibfk_2` FOREIGN KEY (`project_no`) REFERENCES `exp_project` (`project_no`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='实验报告表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `exp_score`
--

DROP TABLE IF EXISTS `exp_score`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `exp_score` (
  `score_no` varchar(20) NOT NULL COMMENT '成绩编号',
  `student_no` varchar(20) NOT NULL COMMENT '学号',
  `daily_score` decimal(5,2) DEFAULT NULL COMMENT '平时成绩',
  `report_score` decimal(5,2) DEFAULT NULL COMMENT '报告成绩',
  `total_score` decimal(5,2) DEFAULT NULL COMMENT '总评成绩',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `class_no` varchar(50) DEFAULT NULL COMMENT '关联教学班号',
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`score_no`),
  UNIQUE KEY `uk_student_class` (`student_no`,`class_no`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='成绩汇总表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `exp_session`
--

DROP TABLE IF EXISTS `exp_session`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `exp_session` (
  `session_no` varchar(20) NOT NULL COMMENT '场次号',
  `project_no` varchar(20) NOT NULL COMMENT '所属项目号',
  `room_no` varchar(20) NOT NULL COMMENT '所在实验室号',
  `work_no` varchar(20) DEFAULT NULL COMMENT '指导教师工号',
  `session_time` datetime NOT NULL COMMENT '上课日期时间',
  `capacity` int NOT NULL COMMENT '总机位容量',
  `remaining_seats` int NOT NULL COMMENT '剩余机位数',
  `semester` varchar(20) DEFAULT NULL COMMENT '学期，如: 2026秋季',
  `week_num` int DEFAULT NULL COMMENT '周次，如: 5 (代表第5周)',
  `day_of_week` int DEFAULT NULL COMMENT '星期几: 1~7',
  `period` varchar(20) DEFAULT NULL COMMENT '节次，如: 3-4节',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除: 0未删, 1已删',
  PRIMARY KEY (`session_no`),
  KEY `project_no` (`project_no`),
  KEY `room_no` (`room_no`),
  KEY `work_no` (`work_no`),
  CONSTRAINT `exp_session_ibfk_1` FOREIGN KEY (`project_no`) REFERENCES `exp_project` (`project_no`) ON DELETE CASCADE,
  CONSTRAINT `exp_session_ibfk_2` FOREIGN KEY (`room_no`) REFERENCES `lab_room` (`room_no`),
  CONSTRAINT `exp_session_ibfk_3` FOREIGN KEY (`work_no`) REFERENCES `teacher` (`work_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='实验场次表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `lab_equipment`
--

DROP TABLE IF EXISTS `lab_equipment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lab_equipment` (
  `equip_no` varchar(20) NOT NULL COMMENT '设备号',
  `equip_name` varchar(50) NOT NULL COMMENT '设备名称',
  `model` varchar(50) DEFAULT NULL COMMENT '型号',
  `quantity` int NOT NULL DEFAULT '1' COMMENT '数量',
  `room_no` varchar(20) DEFAULT NULL COMMENT '所属实验室号',
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`equip_no`),
  KEY `room_no` (`room_no`),
  CONSTRAINT `lab_equipment_ibfk_1` FOREIGN KEY (`room_no`) REFERENCES `lab_room` (`room_no`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='实验设备表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `lab_room`
--

DROP TABLE IF EXISTS `lab_room`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lab_room` (
  `room_no` varchar(20) NOT NULL COMMENT '实验室号',
  `room_name` varchar(50) NOT NULL COMMENT '实验室名称',
  `location` varchar(100) DEFAULT NULL COMMENT '地点',
  `capacity` int NOT NULL DEFAULT '0' COMMENT '容量(机位数)',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除: 0未删, 1已删',
  PRIMARY KEY (`room_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='实验室表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `major`
--

DROP TABLE IF EXISTS `major`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `major` (
  `major_no` varchar(50) NOT NULL COMMENT '专业号，如 M001',
  `major_name` varchar(100) NOT NULL COMMENT '专业名称',
  `dept_no` varchar(50) DEFAULT NULL COMMENT '所属院系',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除: 0未删, 1已删',
  PRIMARY KEY (`major_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='专业表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `student`
--

DROP TABLE IF EXISTS `student`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `student` (
  `student_no` varchar(20) NOT NULL COMMENT '学号',
  `name` varchar(50) NOT NULL COMMENT '姓名',
  `password` varchar(255) NOT NULL COMMENT '登录密码',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `admin_class_no` varchar(50) DEFAULT NULL COMMENT '所属行政班号',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除: 0未删, 1已删',
  PRIMARY KEY (`student_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学生表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `student_booking`
--

DROP TABLE IF EXISTS `student_booking`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `student_booking` (
  `booking_no` varchar(20) NOT NULL COMMENT '预约号',
  `student_no` varchar(20) NOT NULL COMMENT '学号',
  `session_no` varchar(20) NOT NULL COMMENT '场次号',
  `booking_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '预约时间',
  `status` tinyint DEFAULT '1' COMMENT '状态：1已预约，0已取消',
  PRIMARY KEY (`booking_no`),
  UNIQUE KEY `uk_student_session` (`student_no`,`session_no`),
  KEY `session_no` (`session_no`),
  CONSTRAINT `student_booking_ibfk_1` FOREIGN KEY (`student_no`) REFERENCES `student` (`student_no`) ON DELETE CASCADE,
  CONSTRAINT `student_booking_ibfk_2` FOREIGN KEY (`session_no`) REFERENCES `exp_session` (`session_no`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学生预约记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `student_teach_class`
--

DROP TABLE IF EXISTS `student_teach_class`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `student_teach_class` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `student_no` varchar(50) NOT NULL COMMENT '学号',
  `class_no` varchar(50) NOT NULL COMMENT '教学班号',
  `semester` varchar(50) DEFAULT NULL COMMENT '选课学期',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_student_class` (`student_no`,`class_no`)
) ENGINE=InnoDB AUTO_INCREMENT=23 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学生选教学班记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_admin`
--

DROP TABLE IF EXISTS `sys_admin`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_admin` (
  `username` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `password` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除: 0未删, 1已删',
  PRIMARY KEY (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_log`
--

DROP TABLE IF EXISTS `sys_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `operator` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作人账号',
  `action` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作动作',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=236 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_notice`
--

DROP TABLE IF EXISTS `sys_notice`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_notice` (
  `notice_id` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `title` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `publisher_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '发布人姓名',
  `target_role` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '目标角色: all/student/teacher',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`notice_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_semester`
--

DROP TABLE IF EXISTS `sys_semester`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_semester` (
  `semester_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '学期名称',
  `start_date` date NOT NULL COMMENT '开学第一周的周一日期',
  `total_weeks` int NOT NULL DEFAULT '20' COMMENT '总周数',
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`semester_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `teach_class`
--

DROP TABLE IF EXISTS `teach_class`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `teach_class` (
  `class_no` varchar(50) NOT NULL COMMENT '教学班号，如 TC001',
  `course_no` varchar(50) NOT NULL COMMENT '关联基础课程，如 C001',
  `work_no` varchar(50) NOT NULL COMMENT '授课教师工号',
  `semester` varchar(50) DEFAULT NULL COMMENT '开课学期',
  `daily_weight` int DEFAULT '30' COMMENT '平时成绩权重(%)',
  `report_weight` int DEFAULT '70' COMMENT '报告成绩权重(%)',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除: 0未删, 1已删',
  PRIMARY KEY (`class_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='教学班表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `teacher`
--

DROP TABLE IF EXISTS `teacher`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `teacher` (
  `work_no` varchar(20) NOT NULL COMMENT '工号',
  `name` varchar(50) NOT NULL COMMENT '姓名',
  `password` varchar(255) NOT NULL COMMENT '登录密码',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `dept_no` varchar(50) DEFAULT NULL COMMENT '所属院系',
  `title_no` varchar(50) DEFAULT NULL COMMENT '职称号',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除: 0未删, 1已删',
  PRIMARY KEY (`work_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='教师表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `title_dict`
--

DROP TABLE IF EXISTS `title_dict`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `title_dict` (
  `title_no` varchar(50) NOT NULL COMMENT '职称号，如 TT001',
  `title_name` varchar(100) NOT NULL COMMENT '职称名称',
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`title_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='职称字典表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `waitlist_record`
--

DROP TABLE IF EXISTS `waitlist_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `waitlist_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `session_no` varchar(50) NOT NULL COMMENT '场次号',
  `student_no` varchar(50) NOT NULL COMMENT '学号',
  `create_time` datetime DEFAULT NULL COMMENT '排队时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_session_student` (`session_no`,`student_no`),
  KEY `idx_session` (`session_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='候补记录表（物理删除，无逻辑删除列）';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-30 23:02:45
