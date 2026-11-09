package com.lab.labsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.lab.labsystem.common.BusinessException;
import com.lab.labsystem.common.FileTypeUtils;
import com.lab.labsystem.common.QueryLimits;
import com.lab.labsystem.entity.AdminClass;
import com.lab.labsystem.entity.Department;
import com.lab.labsystem.entity.ExpProject;
import com.lab.labsystem.entity.ExpReport;
import com.lab.labsystem.entity.Major;
import com.lab.labsystem.entity.Student;
import com.lab.labsystem.entity.TeachClass;
import com.lab.labsystem.mapper.AdminClassMapper;
import com.lab.labsystem.mapper.DepartmentMapper;
import com.lab.labsystem.mapper.ExpProjectMapper;
import com.lab.labsystem.mapper.ExpReportMapper;
import com.lab.labsystem.mapper.MajorMapper;
import com.lab.labsystem.mapper.StudentMapper;
import com.lab.labsystem.mapper.TeachClassMapper;
import com.lab.labsystem.service.MinioService;
import com.lab.labsystem.service.ReportService;
import com.lab.labsystem.service.TeacherScoreService;
import com.lab.labsystem.vo.ReportVO;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ReportServiceImpl implements ReportService {

    private static final List<String> ALLOWED_EXTENSIONS =
            Arrays.asList(".pdf", ".doc", ".docx", ".zip", ".rar");

    /** 下载/预览票据有效期 */
    private static final long TICKET_TTL_SECONDS = 30L;

    private final ExpReportMapper reportMapper;
    private final StudentMapper studentMapper;
    private final AdminClassMapper adminClassMapper;
    private final MajorMapper majorMapper;
    private final DepartmentMapper departmentMapper;
    private final ExpProjectMapper projectMapper;
    private final TeachClassMapper teachClassMapper;
    private final MinioService minioService;
    private final TeacherScoreService teacherScoreService;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;

    public ReportServiceImpl(ExpReportMapper reportMapper,
                             StudentMapper studentMapper,
                             AdminClassMapper adminClassMapper,
                             MajorMapper majorMapper,
                             DepartmentMapper departmentMapper,
                             ExpProjectMapper projectMapper,
                             TeachClassMapper teachClassMapper,
                             MinioService minioService,
                             TeacherScoreService teacherScoreService,
                             StringRedisTemplate stringRedisTemplate,
                             RedissonClient redissonClient) {
        this.reportMapper = reportMapper;
        this.studentMapper = studentMapper;
        this.adminClassMapper = adminClassMapper;
        this.majorMapper = majorMapper;
        this.departmentMapper = departmentMapper;
        this.projectMapper = projectMapper;
        this.teachClassMapper = teachClassMapper;
        this.minioService = minioService;
        this.teacherScoreService = teacherScoreService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.redissonClient = redissonClient;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String upload(MultipartFile file, String projectNo, String studentNo) {
        if (file.isEmpty()) {
            throw new BusinessException("上传的文件不能为空！");
        }

        String originalFilename = file.getOriginalFilename();
        String suffix = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            suffix = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        }
        if (!ALLOWED_EXTENSIONS.contains(suffix)) {
            throw new BusinessException("文件格式非法！只允许上传 PDF、Word、ZIP、RAR 格式。");
        }

        if (!FileTypeUtils.isValidFile(file, suffix)) {
            throw new BusinessException("文件内容与后缀不符，非法文件已被拦截！");
        }

        ExpProject project = projectMapper.selectById(projectNo);
        if (project == null) {
            throw new BusinessException("实验项目不存在！");
        }

        String newFileName = UUID.randomUUID().toString().replace("-", "") + suffix;
        boolean minioUploadSuccess = false;

        String lockKey = "lock:report:upload:" + studentNo + ":" + projectNo;
        RLock lock = redissonClient.getLock(lockKey);
        boolean isLocked = false;

        try {
            isLocked = lock.tryLock(3, -1, TimeUnit.SECONDS);
            if (!isLocked) {
                throw new BusinessException("您有报告正在上传中，请勿频繁点击！");
            }

            minioService.upload(file, newFileName);
            minioUploadSuccess = true;

            UpdateWrapper<ExpReport> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("student_no", studentNo)
                         .eq("project_no", projectNo)
                         .set("is_latest", 0);
            reportMapper.update(null, updateWrapper);

            reportMapper.deleteOldSoftDeletedReports(studentNo, projectNo);

            QueryWrapper<ExpReport> qw = new QueryWrapper<>();
            qw.eq("student_no", studentNo).eq("project_no", projectNo)
              .orderByDesc("version").last("limit 1");
            ExpReport lastReport = reportMapper.selectOne(qw);
            int newVersion = (lastReport != null) ? lastReport.getVersion() + 1 : 1;

            ExpReport report = new ExpReport();
            report.setReportNo("R" + UUID.randomUUID().toString().substring(0, 8));
            report.setStudentNo(studentNo);
            report.setProjectNo(projectNo);
            report.setFileUrl(newFileName);
            report.setVersion(newVersion);
            report.setIsLatest(1);
            report.setSubmitTime(LocalDateTime.now());
            reportMapper.insert(report);

            return "报告上传成功，当前为第 " + newVersion + " 版";

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("系统繁忙，请稍后重试！");
        } catch (DuplicateKeyException e) {
            log.warn("版本号冲突，并发插入被唯一键拦截: {}", newFileName);
            if (minioUploadSuccess) {
                minioService.remove(newFileName);
            }
            throw new BusinessException("网络开小差了，请稍后重试！");
        } catch (BusinessException e) {
            // 预期内的业务拒绝（例如没抢到锁），原样抛出：
            // 不能落到下面的通用分支，否则会被包装成"数据库保存失败"并误删刚上传的文件
            throw e;
        } catch (Exception e) {
            log.error("数据库写入失败，开始清理 MinIO 孤儿文件: {}", newFileName, e);
            if (minioUploadSuccess) {
                minioService.remove(newFileName);
            }
            throw new RuntimeException("数据库保存失败，文件已清理，请重试！", e);
        } finally {
            if (isLocked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public String createFileTicket(String fileName, String userNo, String role) {
        String ticket = UUID.randomUUID().toString().replace("-", "");
        stringRedisTemplate.opsForValue().set(
                "report:ticket:" + ticket,
                userNo + ":" + role + ":" + fileName,
                TICKET_TTL_SECONDS,
                TimeUnit.SECONDS);
        return ticket;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void grade(String reportNo, Double score, String comment, String currentWorkNo) {
        if (score == null || score < 0 || score > 100) {
            throw new BusinessException("分数必须在 0-100 之间！");
        }

        ExpReport report = reportMapper.selectById(reportNo);
        if (report == null) {
            throw new BusinessException("找不到该报告！");
        }

        ExpProject project = projectMapper.selectById(report.getProjectNo());
        if (project == null) {
            throw new BusinessException("报告所属项目不存在！");
        }

        TeachClass tc = teachClassMapper.selectById(project.getClassNo());
        if (tc == null || !currentWorkNo.equals(tc.getWorkNo())) {
            throw new BusinessException("非法操作：您无权批阅该报告！");
        }

        report.setScore(score);
        report.setComment(comment);
        reportMapper.updateById(report);

        teacherScoreService.recalculateAfterGrade(report.getStudentNo(), project.getClassNo());
    }

    @Override
    public List<ExpReport> listVersions(String studentNo, String projectNo) {
        QueryWrapper<ExpReport> qw = new QueryWrapper<>();
        qw.eq("student_no", studentNo)
          .eq("project_no", projectNo)
          .orderByDesc("version");
        return QueryLimits.list(reportMapper, qw);
    }

    @Override
    public List<ReportVO> listByProject(String projectNo, String currentWorkNo) {
        ExpProject project = projectMapper.selectById(projectNo);
        if (project == null) {
            throw new BusinessException("实验项目不存在！");
        }

        TeachClass tc = teachClassMapper.selectById(project.getClassNo());
        if (tc == null || !currentWorkNo.equals(tc.getWorkNo())) {
            throw new BusinessException("非法操作：您无权查看该项目报告！");
        }

        QueryWrapper<ExpReport> qw = new QueryWrapper<>();
        qw.eq("project_no", projectNo).eq("is_latest", 1);
        List<ExpReport> reportList = QueryLimits.list(reportMapper, qw);

        if (reportList.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> studentNos = reportList.stream()
                .map(ExpReport::getStudentNo).distinct().collect(Collectors.toList());
        Map<String, Student> studentMap = studentMapper.selectBatchIds(studentNos).stream()
                .collect(Collectors.toMap(Student::getStudentNo, s -> s, (k1, k2) -> k1));

        List<String> adminClassNos = studentMap.values().stream()
                .map(Student::getAdminClassNo).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<String, AdminClass> adminClassMap = adminClassNos.isEmpty() ? new HashMap<>() :
                adminClassMapper.selectBatchIds(adminClassNos).stream()
                .collect(Collectors.toMap(AdminClass::getAdminClassNo, ac -> ac, (k1, k2) -> k1));

        List<String> majorNos = adminClassMap.values().stream()
                .map(AdminClass::getMajorNo).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<String, Major> majorMap = majorNos.isEmpty() ? new HashMap<>() :
                majorMapper.selectBatchIds(majorNos).stream()
                .collect(Collectors.toMap(Major::getMajorNo, m -> m, (k1, k2) -> k1));

        List<String> deptNos = majorMap.values().stream()
                .map(Major::getDeptNo).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<String, Department> deptMap = deptNos.isEmpty() ? new HashMap<>() :
                departmentMapper.selectBatchIds(deptNos).stream()
                .collect(Collectors.toMap(Department::getDeptNo, d -> d, (k1, k2) -> k1));

        List<ReportVO> resultList = new ArrayList<>();
        for (ExpReport report : reportList) {
            ReportVO vo = new ReportVO();
            BeanUtils.copyProperties(report, vo);

            Student student = studentMap.get(report.getStudentNo());
            if (student != null) {
                vo.setStudentName(student.getName());
                if (student.getAdminClassNo() != null) {
                    AdminClass ac = adminClassMap.get(student.getAdminClassNo());
                    if (ac != null) {
                        Major major = majorMap.get(ac.getMajorNo());
                        String majorName = major != null ? major.getMajorName() : "未知专业";

                        String deptName = "";
                        if (major != null && major.getDeptNo() != null) {
                            Department dept = deptMap.get(major.getDeptNo());
                            if (dept != null) {
                                deptName = dept.getDeptName() + "-";
                            }
                        }

                        String yearStr = ac.getEnrollmentYear() != null
                                ? String.valueOf(ac.getEnrollmentYear()).substring(2) : "00";
                        String seqStr = ac.getClassSeq() != null
                                ? String.format("%02d", ac.getClassSeq()) : "00";
                        vo.setClassName(deptName + majorName + "-" + yearStr + seqStr + "班");
                    } else {
                        vo.setClassName(student.getAdminClassNo());
                    }
                } else {
                    vo.setClassName("未分班");
                }
            }
            resultList.add(vo);
        }
        return resultList;
    }
}
