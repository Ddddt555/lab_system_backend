package com.lab.labsystem.service.student.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.labsystem.common.PageResult;
import com.lab.labsystem.common.QueryLimits;
import com.lab.labsystem.entity.ExpProject;
import com.lab.labsystem.entity.ExpSession;
import com.lab.labsystem.entity.Teacher;
import com.lab.labsystem.mapper.ExpProjectMapper;
import com.lab.labsystem.mapper.ExpSessionMapper;
import com.lab.labsystem.mapper.TeacherMapper;
import com.lab.labsystem.service.student.SessionQueryService;
import com.lab.labsystem.vo.SessionVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
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

    public SessionQueryServiceImpl(ExpSessionMapper sessionMapper,
                                   TeacherMapper teacherMapper,
                                   ExpProjectMapper projectMapper) {
        this.sessionMapper = sessionMapper;
        this.teacherMapper = teacherMapper;
        this.projectMapper = projectMapper;
    }

    @Override
    public Object listSessions(Integer page, Integer size) {
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

        List<SessionVO> resultList = new ArrayList<>();
        for (ExpSession session : sessionList) {
            SessionVO vo = new SessionVO();
            BeanUtils.copyProperties(session, vo);

            if (session.getWorkNo() != null) {
                vo.setTeacherName(teacherMap.getOrDefault(session.getWorkNo(), "未知教师"));
            }

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
}
