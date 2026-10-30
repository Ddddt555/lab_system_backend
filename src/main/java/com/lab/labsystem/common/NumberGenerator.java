package com.lab.labsystem.common;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lab.labsystem.entity.*;
import com.lab.labsystem.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class NumberGenerator {

    @Autowired private StudentMapper studentMapper;
    @Autowired private TeacherMapper teacherMapper;
    @Autowired private TeachClassMapper teachClassMapper;
    @Autowired private ExpProjectMapper projectMapper;
    @Autowired private ExpSessionMapper sessionMapper;
    @Autowired private ExpCourseMapper courseMapper; 
    @Autowired private LabRoomMapper labRoomMapper; 
    @Autowired private LabEquipmentMapper labEquipmentMapper;
    @Autowired private DepartmentMapper departmentMapper;
    @Autowired private TitleDictMapper titleDictMapper;
    @Autowired private MajorMapper majorMapper;
    @Autowired private AdminClassMapper adminClassMapper;
    
    // ⚠️ 核心注入：Redis 模板
    @Autowired private StringRedisTemplate stringRedisTemplate;

    private final String currentYearStr = String.valueOf(LocalDate.now().getYear());
    private final String currentYY = currentYearStr.substring(2); 
    private final String currentMM = String.format("%02d", LocalDate.now().getMonthValue()); 
    private final String currentDD = String.format("%02d", LocalDate.now().getDayOfMonth()); 

    /**
     * ⚠️ 核心通用方法：使用 Redis 分布式自增生成序列号
     * @param redisKey Redis的键 (例如: "seq:student:2026")
     * @param prefix 编号前缀 (例如: "2026")
     * @param formatLength 补零长度 (例如: 4，就会生成 0001)
     * @param dbMaxValue 数据库当前的最大值 (例如: "20260015"，用于Redis缺失时兜底)
     * @return 拼接好的完整编号
     */
    private String generateNextSeq(String redisKey, String prefix, int formatLength, String dbMaxValue) {
        // 1. 如果 Redis 里没有这个 Key（比如服务刚上线、Redis 被清空），需要从数据库同步一次初始值
        Boolean hasKey = stringRedisTemplate.hasKey(redisKey);
        if (!Boolean.TRUE.equals(hasKey)) {
            int initialValue = 0;
            if (dbMaxValue != null && dbMaxValue.length() > prefix.length()) {
                try {
                    initialValue = Integer.parseInt(dbMaxValue.substring(prefix.length()));
                } catch (Exception ignored) {}
            }
            // 使用 setIfAbsent 防止高并发下两个线程同时初始化
            stringRedisTemplate.opsForValue().setIfAbsent(redisKey, String.valueOf(initialValue));
        }

        // 2. 使用 Redis 的 INCR 命令进行原子自增（无论多少台服务器同时请求，绝对不会重复）
        Long nextSeq = stringRedisTemplate.opsForValue().increment(redisKey);

        // 3. 格式化并拼接
        return prefix + String.format("%0" + formatLength + "d", nextSeq);
    }

    // 1. 生成学号：年份(4位) + 顺序号(4位)  例：20260001
    public String generateStudentNo() {
        String prefix = currentYearStr;
        String redisKey = "seq:student:" + prefix;
        Student last = studentMapper.selectOne(new QueryWrapper<Student>()
                .likeRight("student_no", prefix).orderByDesc("student_no").last("limit 1"));
        String dbMax = last != null ? last.getStudentNo() : null;
        return generateNextSeq(redisKey, prefix, 4, dbMax);
    }

    // 2. 生成工号：T + 年份(4位) + 顺序号(3位)  例：T2026001
    public String generateWorkNo() {
        String prefix = "T" + currentYearStr;
        String redisKey = "seq:teacher:" + prefix;
        Teacher last = teacherMapper.selectOne(new QueryWrapper<Teacher>()
                .likeRight("work_no", prefix).orderByDesc("work_no").last("limit 1"));
        String dbMax = last != null ? last.getWorkNo() : null;
        return generateNextSeq(redisKey, prefix, 3, dbMax);
    }

    // 3. 生成教学班号：TC + 课程号 + 年份后两位 + 顺序号(2位)  例：TCC0012601
    public String generateClassNo(String courseNo) {
        String prefix = "TC" + courseNo + currentYY;
        String redisKey = "seq:class:" + prefix;
        TeachClass last = teachClassMapper.selectOne(new QueryWrapper<TeachClass>()
                .likeRight("class_no", prefix).orderByDesc("class_no").last("limit 1"));
        String dbMax = last != null ? last.getClassNo() : null;
        return generateNextSeq(redisKey, prefix, 2, dbMax);
    }

    // 4. 生成项目号：P + 年份后两位 + 月份(2位) + 顺序号(3位)  例：P2610001
    public String generateProjectNo() {
        String prefix = "P" + currentYY + currentMM;
        String redisKey = "seq:project:" + prefix;
        ExpProject last = projectMapper.selectOne(new QueryWrapper<ExpProject>()
                .likeRight("project_no", prefix).orderByDesc("project_no").last("limit 1"));
        String dbMax = last != null ? last.getProjectNo() : null;
        return generateNextSeq(redisKey, prefix, 3, dbMax);
    }

    // 5. 生成场次号：S + 年份(4位) + 月份(2位) + 日(2位) + 顺序号(2位)  例：S2026102701
    public String generateSessionNo() {
        String prefix = "S" + currentYearStr + currentMM + currentDD;
        String redisKey = "seq:session:" + prefix;
        ExpSession last = sessionMapper.selectOne(new QueryWrapper<ExpSession>()
                .likeRight("session_no", prefix).orderByDesc("session_no").last("limit 1"));
        String dbMax = last != null ? last.getSessionNo() : null;
        return generateNextSeq(redisKey, prefix, 2, dbMax);
    }

    // 6. 生成课程号：C + 顺序号(3位)  例：C001
    public String generateCourseNo() {
        String prefix = "C";
        String redisKey = "seq:course";
        ExpCourse last = courseMapper.selectOne(new QueryWrapper<ExpCourse>()
                .likeRight("course_no", prefix).orderByDesc("course_no").last("limit 1"));
        String dbMax = last != null ? last.getCourseNo() : null;
        return generateNextSeq(redisKey, prefix, 3, dbMax);
    }

    // 7. 生成实验室号：L + 顺序号(3位)  例：L001
    public String generateRoomNo() {
        String prefix = "L";
        String redisKey = "seq:room";
        LabRoom last = labRoomMapper.selectOne(new QueryWrapper<LabRoom>()
                .likeRight("room_no", prefix).orderByDesc("room_no").last("limit 1"));
        String dbMax = last != null ? last.getRoomNo() : null;
        return generateNextSeq(redisKey, prefix, 3, dbMax);
    }
    
    // 8. 生成设备号：E + 顺序号(3位)  例：E001
    public String generateEquipNo() {
        String prefix = "E";
        String redisKey = "seq:equip";
        LabEquipment last = labEquipmentMapper.selectOne(new QueryWrapper<LabEquipment>()
                .likeRight("equip_no", prefix).orderByDesc("equip_no").last("limit 1"));
        String dbMax = last != null ? last.getEquipNo() : null;
        return generateNextSeq(redisKey, prefix, 3, dbMax);
    }

    // 9. 生成专业号：M + 顺序号(3位)  例：M001
    public String generateMajorNo() {
        String prefix = "M";
        String redisKey = "seq:major";
        Major last = majorMapper.selectOne(new QueryWrapper<Major>()
                .likeRight("major_no", prefix).orderByDesc("major_no").last("limit 1"));
        String dbMax = last != null ? last.getMajorNo() : null;
        return generateNextSeq(redisKey, prefix, 3, dbMax);
    }
    
    // 11. 生成院系号：D + 顺序号(3位)  例：D001
    public String generateDeptNo() {
        String prefix = "D";
        String redisKey = "seq:dept";
        Department last = departmentMapper.selectOne(new QueryWrapper<Department>()
                .likeRight("dept_no", prefix).orderByDesc("dept_no").last("limit 1"));
        String dbMax = last != null ? last.getDeptNo() : null;
        return generateNextSeq(redisKey, prefix, 3, dbMax);
    }

    // 12. 生成职称号：TT + 顺序号(3位)  例：TT001
    public String generateTitleNo() {
        String prefix = "TT";
        String redisKey = "seq:title";
        TitleDict last = titleDictMapper.selectOne(new QueryWrapper<TitleDict>()
                .likeRight("title_no", prefix).orderByDesc("title_no").last("limit 1"));
        String dbMax = last != null ? last.getTitleNo() : null;
        return generateNextSeq(redisKey, prefix, 3, dbMax);
    }

    // 10. 生成行政班号（此编号不依赖自增，直接拼接即可）
    public String generateAdminClassNo(Integer enrollmentYear, String deptNo, String majorNo, Integer classSeq) {
        String yearStr = String.valueOf(enrollmentYear).substring(2); 
        
        String deptStr = deptNo.replaceAll("[^0-9]", ""); 
        String majorStr = majorNo.replaceAll("[^0-9]", "");
        
        if (deptStr.length() > 2) deptStr = deptStr.substring(deptStr.length() - 2);
        if (deptStr.length() < 2) deptStr = String.format("%02d", Integer.parseInt(deptStr));
        
        if (majorStr.length() > 2) majorStr = majorStr.substring(majorStr.length() - 2);
        if (majorStr.length() < 2) majorStr = String.format("%02d", Integer.parseInt(majorStr));
        
        String seqStr = String.format("%02d", classSeq); 
        
        return yearStr + deptStr + majorStr + seqStr;
    }
}