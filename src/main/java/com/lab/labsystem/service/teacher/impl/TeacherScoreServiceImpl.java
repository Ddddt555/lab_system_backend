package com.lab.labsystem.service.teacher.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lab.labsystem.common.BusinessException;
import com.lab.labsystem.common.ErrorCode;
import com.lab.labsystem.entity.*;
import com.lab.labsystem.mapper.*;
import com.lab.labsystem.service.teacher.TeacherScoreService;
import com.lab.labsystem.vo.SessionVO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import com.lab.labsystem.common.PageResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import com.lab.labsystem.common.BatchOperations;

@Service
public class TeacherScoreServiceImpl implements TeacherScoreService {

    @Autowired private ExpProjectMapper projectMapper;
    @Autowired private ExpSessionMapper sessionMapper;
    @Autowired private StudentBookingMapper bookingMapper;
    @Autowired private StudentMapper studentMapper;
    @Autowired private ExpReportMapper reportMapper;
    @Autowired private ExpScoreMapper scoreMapper;
    @Autowired private TeachClassMapper teachClassMapper;
    @Autowired private BatchOperations batchOperations;

    // 动态计算总评的私有方法
    private double calculateTotal(double dailyScore, double reportScore, TeachClass tc) {
        int dw = (tc != null && tc.getDailyWeight() != null) ? tc.getDailyWeight() : 30;
        int rw = (tc != null && tc.getReportWeight() != null) ? tc.getReportWeight() : 70;
        double total = (dailyScore * dw / 100.0) + (reportScore * rw / 100.0);
        // 保留一位小数
        return Math.round(total * 10.0) / 10.0; 
    }

    @Override
    public String saveScore(String currentWorkNo, String studentNo, String classNo, Double dailyScore) {
        List<ExpProject> projects = projectMapper.selectList(new QueryWrapper<ExpProject>().eq("class_no", classNo));
        if (projects.isEmpty()) throw BusinessException.of(ErrorCode.DATA_NOT_FOUND, "该教学班暂无实验项目");
        List<String> projectNos = projects.stream().map(ExpProject::getProjectNo).collect(Collectors.toList());

        List<ExpSession> mySessions = sessionMapper.selectList(new QueryWrapper<ExpSession>().in("project_no", projectNos).eq("work_no", currentWorkNo));
        if (mySessions.isEmpty()) throw BusinessException.of(ErrorCode.FORBIDDEN, "非法操作：您无权录入该班级成绩！");

        List<ExpReport> reports = reportMapper.selectList(new QueryWrapper<ExpReport>().eq("student_no", studentNo).in("project_no", projectNos).eq("is_latest", 1).isNotNull("score"));
        double avgReportScore = 0.0;
        if (!reports.isEmpty()) {
            double sum = 0;
            for (ExpReport r : reports) sum += r.getScore();
            avgReportScore = sum / reports.size();
        }

        TeachClass tc = teachClassMapper.selectById(classNo);
        double totalScore = calculateTotal(dailyScore, avgReportScore, tc);

        // Upsert 策略（先更新，失败则插入）
        ExpScore updateEntity = new ExpScore();
        updateEntity.setDailyScore(dailyScore);
        updateEntity.setReportScore(avgReportScore);
        updateEntity.setTotalScore(totalScore);

        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<ExpScore> updateWrapper = 
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
        updateWrapper.eq("student_no", studentNo).eq("class_no", classNo);

        int rows = scoreMapper.update(updateEntity, updateWrapper);

        if (rows == 0) {
            // 在插入新成绩前，先物理删除之前被逻辑删除的旧记录，腾出唯一键的位置
            scoreMapper.deleteOldSoftDeletedScores(studentNo, classNo);
            // 没更新到，说明记录不存在，执行插入
            ExpScore newScore = new ExpScore();
            newScore.setScoreNo("SC" + UUID.randomUUID().toString().substring(0, 8));
            newScore.setStudentNo(studentNo);
            newScore.setClassNo(classNo);
            newScore.setDailyScore(dailyScore);
            newScore.setReportScore(avgReportScore);
            newScore.setTotalScore(totalScore);
            try {
                scoreMapper.insert(newScore);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // 兜底：如果并发导致被别人抢先插入了，再执行一次更新
                scoreMapper.update(updateEntity, updateWrapper);
            }
        }
        
        return "总评已算好: " + String.format("%.1f", totalScore) + " 分";
    }

    // 教师批阅报告后，自动重算该学生的报告均分和总评
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recalculateAfterGrade(String studentNo, String classNo) {
        List<ExpProject> projects = projectMapper.selectList(new QueryWrapper<ExpProject>().eq("class_no", classNo));
        if (projects.isEmpty()) return;
        List<String> projectNos = projects.stream().map(ExpProject::getProjectNo).collect(Collectors.toList());

        List<ExpReport> reports = reportMapper.selectList(new QueryWrapper<ExpReport>()
                .eq("student_no", studentNo).in("project_no", projectNos)
                .eq("is_latest", 1).isNotNull("score"));
        double avgReportScore = 0.0;
        if (!reports.isEmpty()) {
            double sum = 0;
            for (ExpReport r : reports) sum += r.getScore();
            avgReportScore = sum / reports.size();
        }

        ExpScore score = scoreMapper.selectOne(new QueryWrapper<ExpScore>().eq("student_no", studentNo).eq("class_no", classNo));
        TeachClass tc = teachClassMapper.selectById(classNo);

        if (score == null) {
            // 如果还没录入过成绩，先建一条记录，平时成绩暂记 0 分
            score = new ExpScore();
            score.setScoreNo("SC" + UUID.randomUUID().toString().substring(0, 8));
            score.setStudentNo(studentNo);
            score.setClassNo(classNo);
            score.setDailyScore(0.0);
            score.setReportScore(avgReportScore);
            score.setTotalScore(calculateTotal(0.0, avgReportScore, tc));
            scoreMapper.insert(score);
        } else {
            // 如果已有记录，只更新报告分和总评，保留已有的平时成绩
            score.setReportScore(avgReportScore);
            double dailyScore = score.getDailyScore() != null ? score.getDailyScore() : 0.0;
            score.setTotalScore(calculateTotal(dailyScore, avgReportScore, tc));
            scoreMapper.updateById(score);
        }
    }

    // 动态表头导出
    @Override
    public void exportScores(String currentWorkNo, String classNo, HttpServletResponse response) throws IOException {
        // 1. 获取该班级动态配置的权重
        TeachClass tc = teachClassMapper.selectById(classNo);
        int dw = (tc != null && tc.getDailyWeight() != null) ? tc.getDailyWeight() : 30;
        int rw = (tc != null && tc.getReportWeight() != null) ? tc.getReportWeight() : 70;

        // 2. 动态组装 EasyExcel 表头
        List<List<String>> head = new ArrayList<>();
        head.add(Collections.singletonList("学号"));
        head.add(Collections.singletonList("姓名"));
        head.add(Collections.singletonList("所属行政班"));
        head.add(Collections.singletonList("平时成绩(" + dw + "%)"));
        head.add(Collections.singletonList("报告均分(" + rw + "%)"));
        head.add(Collections.singletonList("总评成绩"));

        List<List<Object>> dataList = new ArrayList<>();

        // 设置响应头
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = java.net.URLEncoder.encode("成绩单_" + classNo, "UTF-8").replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");

        // 3. 查询数据并判空
        List<ExpProject> projects = projectMapper.selectList(new QueryWrapper<ExpProject>().eq("class_no", classNo));
        if (projects.isEmpty()) {
            com.alibaba.excel.EasyExcel.write(response.getOutputStream()).head(head).sheet("成绩单").doWrite(dataList);
            return;
        }
        List<String> projectNos = projects.stream().map(ExpProject::getProjectNo).collect(Collectors.toList());

        List<ExpSession> sessions = sessionMapper.selectList(new QueryWrapper<ExpSession>().in("project_no", projectNos).eq("work_no", currentWorkNo));
        if (sessions.isEmpty()) {
            com.alibaba.excel.EasyExcel.write(response.getOutputStream()).head(head).sheet("成绩单").doWrite(dataList);
            return;
        }
        List<String> sessionNos = sessions.stream().map(ExpSession::getSessionNo).collect(Collectors.toList());

        List<StudentBooking> bookings = bookingMapper.selectList(new QueryWrapper<StudentBooking>().in("session_no", sessionNos).eq("status", 1));
        if (bookings.isEmpty()) {
            com.alibaba.excel.EasyExcel.write(response.getOutputStream()).head(head).sheet("成绩单").doWrite(dataList);
            return;
        }
        Set<String> studentNos = bookings.stream().map(StudentBooking::getStudentNo).collect(Collectors.toSet());

        // 4. 批量查询学生和成绩，消除 N+1
        Map<String, Student> studentMap = studentMapper.selectBatchIds(studentNos).stream()
                .collect(Collectors.toMap(Student::getStudentNo, s -> s));

        Map<String, ExpScore> scoreMap = scoreMapper.selectList(
                new QueryWrapper<ExpScore>().eq("class_no", classNo).in("student_no", studentNos))
                .stream().collect(Collectors.toMap(ExpScore::getStudentNo, s -> s));

        // 5. 组装数据
        for (String sNo : studentNos) {
            Student student = studentMap.get(sNo);
            if (student == null) continue;

            List<Object> row = new ArrayList<>();
            row.add(sNo);
            row.add(student.getName());
            row.add(student.getAdminClassNo() != null ? student.getAdminClassNo() : "未分班");

            ExpScore score = scoreMap.get(sNo);
            if (score != null) {
                row.add(score.getDailyScore());
                row.add(score.getReportScore());
                row.add(score.getTotalScore());
            } else {
                row.add(0.0); row.add(0.0); row.add(0.0);
            }
            dataList.add(row);
        }

        // 6. 写入动态表头和数据
        com.alibaba.excel.EasyExcel.write(response.getOutputStream())
                .head(head)
                .sheet("成绩单")
                .doWrite(dataList);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String updateWeight(String currentWorkNo, String classNo, Integer dailyWeight, Integer reportWeight) {
        if (dailyWeight == null || reportWeight == null || dailyWeight < 0 || reportWeight < 0) {
            throw BusinessException.of(ErrorCode.BAD_REQUEST, "权重不能为空或负数！");
        }
        if (dailyWeight + reportWeight != 100) {
            throw BusinessException.of(ErrorCode.BAD_REQUEST, "权重之和必须等于 100%！");
        }

        TeachClass tc = teachClassMapper.selectById(classNo);
        if (tc == null || !currentWorkNo.equals(tc.getWorkNo())) {
            throw BusinessException.of(ErrorCode.FORBIDDEN, "非法操作：您无权修改该班级权重！");
        }
        tc.setDailyWeight(dailyWeight);
        tc.setReportWeight(reportWeight);
        teachClassMapper.updateById(tc);

        List<ExpScore> scores = scoreMapper.selectList(
                new QueryWrapper<ExpScore>().eq("class_no", classNo));
        
        // 解决写放大：收集需要更新的对象，最后一次性批量提交
        List<ExpScore> updateList = new ArrayList<>();
        for (ExpScore score : scores) {
            if (score.getDailyScore() != null && score.getReportScore() != null) {
                double newTotal = calculateTotal(score.getDailyScore(), score.getReportScore(), tc);
                score.setTotalScore(newTotal);
                updateList.add(score);
            }
        }
        
        if (!updateList.isEmpty()) {
            // 批量更新（通过适配层，便于单元测试验证）
            batchOperations.updateBatchById(updateList);
        }
        
        return "权重修改成功，已自动重算 " + updateList.size() + " 名学生的成绩！";
    }
    
    @Override
    public Object getCourseScores(String currentWorkNo, String classNo, Integer page, Integer size) {
        List<ExpProject> projects = projectMapper.selectList(
                new QueryWrapper<ExpProject>().eq("class_no", classNo));
        if (projects.isEmpty()) {
            return (page != null && size != null) ? new PageResult<>(0, new ArrayList<>()) : new ArrayList<>();
        }
        List<String> projectNos = projects.stream()
                .map(ExpProject::getProjectNo).collect(Collectors.toList());

        List<ExpSession> sessions = sessionMapper.selectList(
                new QueryWrapper<ExpSession>().in("project_no", projectNos).eq("work_no", currentWorkNo));
        if (sessions.isEmpty()) {
            return (page != null && size != null) ? new PageResult<>(0, new ArrayList<>()) : new ArrayList<>();
        }
        List<String> sessionNos = sessions.stream()
                .map(ExpSession::getSessionNo).collect(Collectors.toList());

        List<StudentBooking> bookings = bookingMapper.selectList(
                new QueryWrapper<StudentBooking>().in("session_no", sessionNos).eq("status", 1));
        if (bookings.isEmpty()) {
            return (page != null && size != null) ? new PageResult<>(0, new ArrayList<>()) : new ArrayList<>();
        }

        List<String> allStudentNos = bookings.stream()
                .map(StudentBooking::getStudentNo).distinct().collect(Collectors.toList());

        long total = allStudentNos.size();
        List<String> pageStudentNos = allStudentNos;

        // 内存分页（截取当前页的学号）
        if (page != null && size != null) {
            int fromIndex = (page - 1) * size;
            if (fromIndex >= total) {
                return new PageResult<>(total, new ArrayList<>());
            }
            int toIndex = Math.min(fromIndex + size, allStudentNos.size());
            pageStudentNos = allStudentNos.subList(fromIndex, toIndex);
        }

        if (pageStudentNos.isEmpty()) {
            return new PageResult<>(total, new ArrayList<>());
        }

        Map<String, String> studentNameMap = studentMapper.selectBatchIds(pageStudentNos).stream()
                .collect(Collectors.toMap(Student::getStudentNo, Student::getName, (k1, k2) -> k1));

        Map<String, ExpScore> scoreMap = scoreMapper.selectList(
                new QueryWrapper<ExpScore>().eq("class_no", classNo).in("student_no", pageStudentNos))
                .stream().collect(Collectors.toMap(ExpScore::getStudentNo, s -> s, (k1, k2) -> k1));

        List<SessionVO> resultList = new ArrayList<>();
        for (String sNo : pageStudentNos) {
            SessionVO vo = new SessionVO();
            vo.setStudentNo(sNo);
            vo.setTeacherName(studentNameMap.getOrDefault(sNo, "未知学生"));

            ExpScore existScore = scoreMap.get(sNo);
            if (existScore != null) {
                vo.setScore(existScore.getDailyScore());
                vo.setReportScore(existScore.getReportScore());
                vo.setTotalScore(existScore.getTotalScore());
            } else {
                vo.setScore(0.0);
                vo.setReportScore(0.0);
                vo.setTotalScore(0.0);
            }
            resultList.add(vo);
        }

        return (page != null && size != null) ? new PageResult<>(total, resultList) : resultList;
    }

    @Override
    public Map<String, Integer> getScoreDistribution(String currentWorkNo) {
        int excellent = 0, good = 0, medium = 0, pass = 0, fail = 0;
        Map<String, Integer> result = new HashMap<>();

        List<TeachClass> myClasses = teachClassMapper.selectList(
                new QueryWrapper<TeachClass>().eq("work_no", currentWorkNo));
        List<String> myClassNos = myClasses.stream()
                .map(TeachClass::getClassNo).collect(Collectors.toList());

        if (myClassNos.isEmpty()) {
            result.put("优秀", 0);
            result.put("良好", 0);
            result.put("中等", 0);
            result.put("及格", 0);
            result.put("不及格", 0);
            return result;
        }

        List<ExpScore> scores = scoreMapper.selectList(
                new QueryWrapper<ExpScore>().in("class_no", myClassNos).isNotNull("total_score"));
        for (ExpScore s : scores) {
            double score = s.getTotalScore();
            if (score >= 90) excellent++;
            else if (score >= 80) good++;
            else if (score >= 70) medium++;
            else if (score >= 60) pass++;
            else fail++;
        }

        result.put("优秀", excellent);
        result.put("良好", good);
        result.put("中等", medium);
        result.put("及格", pass);
        result.put("不及格", fail);
        return result;
    }

    @Override
    public Map<String, Object> getExperimentRate(String currentWorkNo) {
        List<TeachClass> myClasses = teachClassMapper.selectList(
                new QueryWrapper<TeachClass>().eq("work_no", currentWorkNo));
        List<String> myClassNos = myClasses.stream()
                .map(TeachClass::getClassNo).collect(Collectors.toList());

        long totalProjects = 0;
        if (!myClassNos.isEmpty()) {
            totalProjects = projectMapper.selectCount(
                    new QueryWrapper<ExpProject>().in("class_no", myClassNos));
        }

        List<ExpSession> mySessions = sessionMapper.selectList(
                new QueryWrapper<ExpSession>().eq("work_no", currentWorkNo));
        long openedProjects = mySessions.stream()
                .map(ExpSession::getProjectNo).distinct().count();

        double rate = totalProjects == 0 ? 0 : (double) openedProjects / totalProjects * 100;

        Map<String, Object> result = new HashMap<>();
        result.put("total", totalProjects);
        result.put("opened", openedProjects);
        result.put("rate", String.format("%.1f", rate));
        return result;
    }
}