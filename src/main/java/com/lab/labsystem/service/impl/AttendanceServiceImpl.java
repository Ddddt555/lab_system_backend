package com.lab.labsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lab.labsystem.websocket.AttendanceWebSocket;
import com.lab.labsystem.dto.AttendanceExportDTO;
import com.lab.labsystem.entity.AttendanceRecord;
import com.lab.labsystem.entity.ExpProject;
import com.lab.labsystem.entity.ExpSession;
import com.lab.labsystem.entity.Student;
import com.lab.labsystem.entity.StudentBooking;
import com.lab.labsystem.entity.Teacher;
import com.lab.labsystem.mapper.AttendanceRecordMapper;
import com.lab.labsystem.mapper.ExpProjectMapper;
import com.lab.labsystem.mapper.ExpSessionMapper;
import com.lab.labsystem.mapper.StudentBookingMapper;
import com.lab.labsystem.mapper.StudentMapper;
import com.lab.labsystem.mapper.TeacherMapper;
import com.lab.labsystem.service.AttendanceService;
import com.lab.labsystem.vo.StudentVO;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.net.URLEncoder;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import com.lab.labsystem.common.BusinessException;

@Service
public class AttendanceServiceImpl implements AttendanceService {

    @Autowired private StringRedisTemplate stringRedisTemplate;
    @Autowired private AttendanceRecordMapper attendanceMapper;
    @Autowired private ExpSessionMapper sessionMapper;
    @Autowired private StudentBookingMapper bookingMapper;
    @Autowired private StudentMapper studentMapper;
    @Autowired private ExpProjectMapper projectMapper;
    @Autowired private TeacherMapper teacherMapper;

    @Override
    public String startAttendance(String currentWorkNo, String sessionNo) {
        ExpSession session = sessionMapper.selectById(sessionNo);
        if (session == null || !session.getWorkNo().equals(currentWorkNo)) {
            throw new BusinessException("非法操作：您无权发起该场次签到！");
        }
        String code = String.format("%06d", new Random().nextInt(1000000));
        stringRedisTemplate.opsForValue().set("attendance:code:" + sessionNo, code, Duration.ofMinutes(15));
        return code;
    }

    @Override
    public List<StudentVO> getAttendanceList(String currentWorkNo, String sessionNo) {
        ExpSession session = sessionMapper.selectById(sessionNo);
        if (session == null || !session.getWorkNo().equals(currentWorkNo)) {
            throw new BusinessException("非法操作：您无权查看该场次签到名单！");
        }

        List<AttendanceRecord> records = attendanceMapper.selectList(
                new QueryWrapper<AttendanceRecord>().eq("session_no", sessionNo));

        List<StudentVO> resultList = new ArrayList<>();
        for (AttendanceRecord record : records) {
            StudentVO vo = new StudentVO();
            vo.setStudentNo(record.getStudentNo());
            vo.setBookingTime(record.getSignTime());
            Student student = studentMapper.selectById(record.getStudentNo());
            vo.setName(student != null ? student.getName() : "未知学生");
            resultList.add(vo);
        }
        return resultList;
    }

    @Override
    public String signIn(String studentNo, String sessionNo, String code) {
        Long bookCount = bookingMapper.selectCount(new QueryWrapper<StudentBooking>()
                .eq("student_no", studentNo).eq("session_no", sessionNo).eq("status", 1));
        if (bookCount == 0) {
            throw new BusinessException("签到失败：您未预约此场次！");
        }

        String redisCode = stringRedisTemplate.opsForValue().get("attendance:code:" + sessionNo);
        if (redisCode == null) {
            throw new BusinessException("签到失败：验证码已过期或教师未开启签到！");
        }
        if (!redisCode.equals(code)) {
            throw new BusinessException("签到失败：验证码错误！");
        }

        Long exist = attendanceMapper.selectCount(new QueryWrapper<AttendanceRecord>()
                .eq("student_no", studentNo).eq("session_no", sessionNo));
        if (exist > 0) {
            throw new BusinessException("您已经签到过了，请勿重复操作！");
        }

        AttendanceRecord record = new AttendanceRecord();
        record.setSessionNo(sessionNo);
        record.setStudentNo(studentNo);
        record.setSignTime(LocalDateTime.now());
        record.setStatus(1);
        attendanceMapper.insert(record);

        AttendanceWebSocket.broadcast(sessionNo, "NEW_SIGN_IN");

        return "签到成功！";
    }

    @Override
    public void exportAttendance(String currentWorkNo, String sessionNo, HttpServletResponse response) throws Exception {
        ExpSession session = sessionMapper.selectById(sessionNo);
        if (session == null || !session.getWorkNo().equals(currentWorkNo)) {
            throw new BusinessException("非法操作：您无权导出该场次考勤！");
        }

        String projectName = "未知项目";
        if (session.getProjectNo() != null) {
            ExpProject project = projectMapper.selectById(session.getProjectNo());
            if (project != null) projectName = project.getProjectName();
        }
        String teacherName = "未知教师";
        Teacher teacher = teacherMapper.selectById(currentWorkNo);
        if (teacher != null) teacherName = teacher.getName();
        
        String timeStr = session.getSemester() + " 第" + session.getWeekNum() + "周 " + session.getPeriod();

        List<AttendanceRecord> signRecords = attendanceMapper.selectList(
                new QueryWrapper<AttendanceRecord>().eq("session_no", sessionNo));
        Map<String, AttendanceRecord> signMap = signRecords.stream()
                .collect(Collectors.toMap(AttendanceRecord::getStudentNo, r -> r));

        List<StudentBooking> bookings = bookingMapper.selectList(
                new QueryWrapper<StudentBooking>().eq("session_no", sessionNo).eq("status", 1));
        
        List<AttendanceExportDTO> exportList = new ArrayList<>();
        for (StudentBooking b : bookings) {
            Student student = studentMapper.selectById(b.getStudentNo());
            if (student == null) continue;

            AttendanceExportDTO dto = new AttendanceExportDTO();
            dto.setStudentNo(student.getStudentNo());
            dto.setName(student.getName());
            dto.setClassNo(student.getAdminClassNo() != null ? student.getAdminClassNo() : "未分班");

            if (signMap.containsKey(student.getStudentNo())) {
                dto.setStatus("已签到");
                dto.setSignTime(signMap.get(student.getStudentNo()).getSignTime().toString().replace("T", " ").substring(0, 19));
            } else {
                dto.setStatus("缺勤");
                dto.setSignTime("-");
            }
            exportList.add(dto);
        }

        // ⚠️ 核心修改：使用原生 POI 构建，彻底杜绝合并单元格冲突导致的 Excel 弹窗
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("考勤记录");
        
        // 设置表头加粗样式
        CellStyle headStyle = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        headStyle.setFont(font);
        
        // 第1行：实验项目 (合并 A1 到 E1)
        Row row1 = sheet.createRow(0);
        Cell cellA1 = row1.createCell(0);
        cellA1.setCellValue("实验项目：" + projectName);
        cellA1.setCellStyle(headStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 4));
        
        // 第2行：任课教师 (A2)，场次时间 (C2 到 E2)
        Row row2 = sheet.createRow(1);
        Cell cellA2 = row2.createCell(0);
        cellA2.setCellValue("任课教师：" + teacherName);
        cellA2.setCellStyle(headStyle);
        
        Cell cellC2 = row2.createCell(2);
        cellC2.setCellValue("场次时间：" + timeStr);
        cellC2.setCellStyle(headStyle);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 2, 4));
        
        // 第3行：具体列名
        Row row3 = sheet.createRow(2);
        String[] headers = {"学号", "姓名", "行政班", "签到状态", "签到时间"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row3.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headStyle);
        }
        
        // 第4行开始：填数据
        int rowNum = 3;
        for (AttendanceExportDTO dto : exportList) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(dto.getStudentNo());
            row.createCell(1).setCellValue(dto.getName());
            row.createCell(2).setCellValue(dto.getClassNo());
            row.createCell(3).setCellValue(dto.getStatus());
            row.createCell(4).setCellValue(dto.getSignTime());
        }
        
        // 设置列宽，避免内容挤在一起
        sheet.setColumnWidth(0, 4000); // 学号
        sheet.setColumnWidth(1, 3000); // 姓名
        sheet.setColumnWidth(2, 4000); // 行政班
        sheet.setColumnWidth(3, 3000); // 签到状态
        sheet.setColumnWidth(4, 6000); // 签到时间

        // 设置下载响应头并输出
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("考勤表_" + projectName + "_" + session.getWeekNum() + "周", "UTF-8").replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");

        OutputStream out = response.getOutputStream();
        workbook.write(out);
        workbook.close();
        out.flush();
    }
}