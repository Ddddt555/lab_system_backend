package com.lab.labsystem.service.student.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.labsystem.common.PageResult;
import com.lab.labsystem.common.QueryLimits;
import com.lab.labsystem.entity.ExpProject;
import com.lab.labsystem.entity.ExpSession;
import com.lab.labsystem.entity.StudentBooking;
import com.lab.labsystem.entity.StudentTeachClass;
import com.lab.labsystem.entity.Teacher;
import com.lab.labsystem.mapper.ExpProjectMapper;
import com.lab.labsystem.mapper.ExpSessionMapper;
import com.lab.labsystem.mapper.StudentBookingMapper;
import com.lab.labsystem.mapper.StudentTeachClassMapper;
import com.lab.labsystem.mapper.TeacherMapper;
import com.lab.labsystem.service.student.SessionQueryService;
import com.lab.labsystem.vo.SessionVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SessionQueryServiceImpl implements SessionQueryService {

    private final ExpSessionMapper sessionMapper;
    private final TeacherMapper teacherMapper;
    private final ExpProjectMapper projectMapper;
    private final StudentTeachClassMapper studentTeachClassMapper;
    private final StudentBookingMapper bookingMapper;

    public SessionQueryServiceImpl(ExpSessionMapper sessionMapper,
                                   TeacherMapper teacherMapper,
                                   ExpProjectMapper projectMapper,
                                   StudentTeachClassMapper studentTeachClassMapper,
                                   StudentBookingMapper bookingMapper) {
        this.sessionMapper = sessionMapper;
        this.teacherMapper = teacherMapper;
        this.projectMapper = projectMapper;
        this.studentTeachClassMapper = studentTeachClassMapper;
        this.bookingMapper = bookingMapper;
    }

    @Override
    public Object listSessions(String studentNo, Integer page, Integer size) {
        boolean paged = page != null && size != null;

        List<ExpSession> sessionList;
        long total = 0;
        if (paged) {
            Page<ExpSession> sessionPage = sessionMapper.selectPage(new Page<>(page, size), null);
            sessionList = sessionPage.getRecords();
            total = sessionPage.getTotal();
        } else {
            sessionList = QueryLimits.list(sessionMapper, null);
        }

        if (sessionList.isEmpty()) {
            return paged ? new PageResult<>(0, new ArrayList<>()) : new ArrayList<>();
        }

        // 批量查询教师和项目，消灭 N+1
        Set<String> workNos = sessionList.stream()
                .map(ExpSession::getWorkNo).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<String> projectNos = sessionList.stream()
                .map(ExpSession::getProjectNo).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<String, String> teacherMap = workNos.isEmpty() ? new HashMap<>() :
                teacherMapper.selectBatchIds(workNos).stream()
                        .collect(Collectors.toMap(Teacher::getWorkNo, Teacher::getName, (k1, k2) -> k1));

        Map<String, ExpProject> projectMap = projectNos.isEmpty() ? new HashMap<>() :
                projectMapper.selectBatchIds(projectNos).stream()
                        .collect(Collectors.toMap(ExpProject::getProjectNo, p -> p, (k1, k2) -> k1));

        // 批量查询该学生的选课和已预约场次，用于资格判断
        Set<String> myClassNos = new HashSet<>();
        List<ExpSession> bookedSessions = new ArrayList<>();
        if (studentNo != null) {
            myClassNos = studentTeachClassMapper.selectList(
                    new QueryWrapper<StudentTeachClass>().eq("student_no", studentNo))
                    .stream().map(StudentTeachClass::getClassNo).collect(Collectors.toSet());

            List<StudentBooking> myBookings = bookingMapper.selectList(
                    new QueryWrapper<StudentBooking>().eq("student_no", studentNo).eq("status", 1));
            List<String> bookedSessionNos = myBookings.stream()
                    .map(StudentBooking::getSessionNo).collect(Collectors.toList());
            if (!bookedSessionNos.isEmpty()) {
                bookedSessions = sessionMapper.selectBatchIds(bookedSessionNos);
            }
        }

        List<SessionVO> resultList = new ArrayList<>();
        for (ExpSession session : sessionList) {
            SessionVO vo = new SessionVO();
            BeanUtils.copyProperties(session, vo);

            if (session.getWorkNo() != null) {
                vo.setTeacherName(teacherMap.getOrDefault(session.getWorkNo(), "未知教师"));
            }

            ExpProject project = null;
            if (session.getProjectNo() != null) {
                project = projectMap.get(session.getProjectNo());
                if (project != null) {
                    vo.setProjectName(project.getProjectName());
                    vo.setGuideFile(project.getGuideFile());
                }
            }

            // 计算当前学生是否有资格预约/候补
            boolean eligible = true;
            
            // 条件1：是否已经预约过这个场次
            boolean alreadyBooked = bookedSessions.stream()
                    .anyMatch(s -> s.getSessionNo().equals(session.getSessionNo()));
            if (alreadyBooked) {
                eligible = false;
            }

            // 条件2：是否没选该实验所属的教学班
            if (eligible && project != null && project.getClassNo() != null) {
                if (!myClassNos.contains(project.getClassNo())) {
                    eligible = false;
                }
            }

            // 条件3：是否与已预约的其他场次时间冲突
            if (eligible) {
                for (ExpSession booked : bookedSessions) {
                    if (booked.getSessionNo().equals(session.getSessionNo())) continue;
                    if (booked.getSemester().equals(session.getSemester()) &&
                        booked.getWeekNum().equals(session.getWeekNum()) &&
                        booked.getDayOfWeek().equals(session.getDayOfWeek()) &&
                        booked.getPeriod().equals(session.getPeriod())) {
                        eligible = false;
                        break;
                    }
                }
            }
            vo.setIsEligible(eligible);

            resultList.add(vo);
        }

        return paged ? new PageResult<>(total, resultList) : resultList;
    }
}