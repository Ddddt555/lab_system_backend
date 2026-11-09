package com.lab.labsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.labsystem.common.BusinessException;
import com.lab.labsystem.common.NumberGenerator;
import com.lab.labsystem.common.PageResult;
import com.lab.labsystem.common.QueryLimits;
import com.lab.labsystem.entity.ExpProject;
import com.lab.labsystem.entity.ExpSession;
import com.lab.labsystem.entity.LabRoom;
import com.lab.labsystem.entity.Student;
import com.lab.labsystem.entity.StudentBooking;
import com.lab.labsystem.entity.SysSemester;
import com.lab.labsystem.entity.TeachClass;
import com.lab.labsystem.mapper.ExpProjectMapper;
import com.lab.labsystem.mapper.ExpSessionMapper;
import com.lab.labsystem.mapper.LabRoomMapper;
import com.lab.labsystem.mapper.StudentBookingMapper;
import com.lab.labsystem.mapper.StudentMapper;
import com.lab.labsystem.mapper.SysSemesterMapper;
import com.lab.labsystem.mapper.TeachClassMapper;
import com.lab.labsystem.service.TeacherSessionService;
import com.lab.labsystem.vo.SessionVO;
import com.lab.labsystem.vo.StudentVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class TeacherSessionServiceImpl implements TeacherSessionService {

    private final ExpProjectMapper projectMapper;
    private final TeachClassMapper teachClassMapper;
    private final LabRoomMapper labRoomMapper;
    private final ExpSessionMapper sessionMapper;
    private final SysSemesterMapper semesterMapper;
    private final StudentBookingMapper bookingMapper;
    private final StudentMapper studentMapper;
    private final NumberGenerator numberGenerator;

    public TeacherSessionServiceImpl(ExpProjectMapper projectMapper,
                                     TeachClassMapper teachClassMapper,
                                     LabRoomMapper labRoomMapper,
                                     ExpSessionMapper sessionMapper,
                                     SysSemesterMapper semesterMapper,
                                     StudentBookingMapper bookingMapper,
                                     StudentMapper studentMapper,
                                     NumberGenerator numberGenerator) {
        this.projectMapper = projectMapper;
        this.teachClassMapper = teachClassMapper;
        this.labRoomMapper = labRoomMapper;
        this.sessionMapper = sessionMapper;
        this.semesterMapper = semesterMapper;
        this.bookingMapper = bookingMapper;
        this.studentMapper = studentMapper;
        this.numberGenerator = numberGenerator;
    }

    @Override
    public String addSession(ExpSession session, String workNo) {
        session.setWorkNo(workNo);

        ExpProject project = projectMapper.selectById(session.getProjectNo());
        if (project == null) {
            throw new BusinessException("实验项目不存在！");
        }
        TeachClass tc = teachClassMapper.selectById(project.getClassNo());
        if (tc == null || !tc.getWorkNo().equals(workNo)) {
            throw new BusinessException("非法操作：您无权安排其他老师的实验项目！");
        }

        LabRoom room = labRoomMapper.selectById(session.getRoomNo());
        if (room == null) {
            throw new BusinessException("实验室不存在！");
        }
        if (session.getCapacity() > room.getCapacity()) {
            throw new BusinessException("排课容量不能超过实验室容量！");
        }

        QueryWrapper<ExpSession> conflictQw = new QueryWrapper<ExpSession>()
                .eq("semester", session.getSemester())
                .eq("week_num", session.getWeekNum())
                .eq("day_of_week", session.getDayOfWeek())
                .eq("period", session.getPeriod());

        List<ExpSession> existSessions = sessionMapper.selectList(conflictQw);
        for (ExpSession exist : existSessions) {
            if (exist.getWorkNo().equals(workNo)) {
                throw new BusinessException("排课失败：您在【第" + session.getWeekNum() + "周 "
                        + weekDayName(session.getDayOfWeek()) + " " + session.getPeriod() + "】已有其他排课！");
            }
            if (exist.getRoomNo().equals(session.getRoomNo())) {
                throw new BusinessException("排课失败：实验室【" + room.getRoomName() + "】在【第"
                        + session.getWeekNum() + "周 " + weekDayName(session.getDayOfWeek())
                        + " " + session.getPeriod() + "】已被占用！");
            }
            ExpProject existProject = projectMapper.selectById(exist.getProjectNo());
            if (existProject != null && existProject.getClassNo() != null
                    && existProject.getClassNo().equals(project.getClassNo())) {
                throw new BusinessException("排课失败：该教学班在此时段已经有排课了！");
            }
        }

        session.setSessionNo(numberGenerator.generateSessionNo());
        session.setSessionTime(calculateAbsoluteTime(
                session.getSemester(), session.getWeekNum(),
                session.getDayOfWeek(), session.getPeriod()));
        session.setRemainingSeats(session.getCapacity());

        sessionMapper.insert(session);
        return "场次安排成功！场次号为：" + session.getSessionNo();
    }

    @Override
    public Object listMySessions(String workNo, Integer page, Integer size) {
        boolean paged = page != null && size != null;

        QueryWrapper<ExpSession> qw = new QueryWrapper<ExpSession>()
                .eq("work_no", workNo).orderByAsc("session_time");

        List<ExpSession> sessionList;
        long total = 0;
        if (paged) {
            Page<ExpSession> sessionPage = sessionMapper.selectPage(new Page<>(page, size), qw);
            sessionList = sessionPage.getRecords();
            total = sessionPage.getTotal();
        } else {
            sessionList = QueryLimits.list(sessionMapper, qw);
        }

        if (sessionList.isEmpty()) {
            return paged ? new PageResult<>(0, new ArrayList<>()) : new ArrayList<>();
        }

        // 批量查询项目，消灭 N+1
        List<String> projectNos = sessionList.stream()
                .map(ExpSession::getProjectNo).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<String, ExpProject> projectMap = projectNos.isEmpty() ? new HashMap<>() :
                projectMapper.selectBatchIds(projectNos).stream()
                        .collect(Collectors.toMap(ExpProject::getProjectNo, p -> p, (k1, k2) -> k1));

        List<SessionVO> resultList = new ArrayList<>();
        for (ExpSession session : sessionList) {
            SessionVO vo = new SessionVO();
            BeanUtils.copyProperties(session, vo);
            if (session.getProjectNo() != null) {
                ExpProject project = projectMap.get(session.getProjectNo());
                if (project != null) {
                    vo.setProjectName(project.getProjectName());
                    vo.setGuideFile(project.getGuideFile());
                }
            }
            resultList.add(vo);
        }

        return paged ? new PageResult<>(total, resultList) : resultList;
    }

    @Override
    public List<StudentVO> listSessionBookings(String sessionNo, String workNo) {
        ExpSession session = sessionMapper.selectById(sessionNo);
        if (session == null || !session.getWorkNo().equals(workNo)) {
            throw new BusinessException("非法操作：您无权查看！");
        }

        List<StudentBooking> bookings = bookingMapper.selectList(
                new QueryWrapper<StudentBooking>().eq("session_no", sessionNo).eq("status", 1));
        if (bookings.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> studentNos = bookings.stream()
                .map(StudentBooking::getStudentNo).distinct().collect(Collectors.toList());
        Map<String, Student> studentMap = studentMapper.selectBatchIds(studentNos).stream()
                .collect(Collectors.toMap(Student::getStudentNo, s -> s, (k1, k2) -> k1));

        List<StudentVO> resultList = new ArrayList<>();
        for (StudentBooking booking : bookings) {
            Student student = studentMap.get(booking.getStudentNo());
            if (student != null) {
                StudentVO vo = new StudentVO();
                BeanUtils.copyProperties(student, vo);
                vo.setBookingTime(booking.getBookingTime());
                resultList.add(vo);
            }
        }
        return resultList;
    }

    @Override
    public String deleteSession(String sessionNo, String workNo) {
        ExpSession session = sessionMapper.selectById(sessionNo);
        if (session == null || !session.getWorkNo().equals(workNo)) {
            throw new BusinessException("非法操作！");
        }

        sessionMapper.deleteById(sessionNo);
        return "取消排课成功！";
    }

    /** 1-7 → 周一..周日，非法值返回“未知”。 */
    private String weekDayName(Integer dayOfWeek) {
        String[] days = {"", "周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        if (dayOfWeek != null && dayOfWeek >= 1 && dayOfWeek <= 7) {
            return days[dayOfWeek];
        }
        return "未知";
    }

    /**
     * 由“学期 + 周次 + 星期 + 节次”换算出绝对时间，用于排序和展示。
     */
    private LocalDateTime calculateAbsoluteTime(String semesterName, Integer weekNum,
                                                Integer dayOfWeek, String period) {
        SysSemester semester = semesterMapper.selectById(semesterName);
        if (semester == null) {
            throw new BusinessException("排课失败：该学期配置不存在！");
        }
        LocalDate targetDate = semester.getStartDate()
                .plusWeeks(weekNum - 1).plusDays(dayOfWeek - 1);

        LocalTime targetTime;
        switch (period) {
            case "1-2节": targetTime = LocalTime.of(8, 0); break;
            case "3-4节": targetTime = LocalTime.of(10, 0); break;
            case "5-6节": targetTime = LocalTime.of(14, 0); break;
            case "7-8节": targetTime = LocalTime.of(16, 0); break;
            case "9-10节": targetTime = LocalTime.of(19, 0); break;
            default: targetTime = LocalTime.of(8, 0);
        }
        return LocalDateTime.of(targetDate, targetTime);
    }
}
