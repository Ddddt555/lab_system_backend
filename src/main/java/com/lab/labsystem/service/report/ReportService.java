package com.lab.labsystem.service.report;

import com.lab.labsystem.entity.ExpReport;
import com.lab.labsystem.vo.ReportVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 实验报告业务。
 *
 * <p>从 {@code ReportController} 中下沉出来的职责：文件校验与入库（含版本控制、
 * 分布式锁与失败回滚）、批阅与成绩重算、报告列表与版本查询、一次性下载票据签发。
 *
 * <p>不包含 HTTP 相关逻辑（响应流写出、URL 拼装、403 状态码），那些仍留在 controller。
 */
public interface ReportService {

    /**
     * 学生上传报告。上传成功后旧版本标记为历史，当前版本为最新。
     *
     * @return 成功提示（含版本号）
     * @throws com.lab.labsystem.common.BusinessException 校验不通过或上传被拒绝
     */
    String upload(MultipartFile file, String projectNo, String studentNo);

    /**
     * 签发一次性下载/预览票据（30 秒有效，绑定文件名）。
     *
     * @param fileName 对象存储中的文件名
     * @param userNo   当前用户
     * @param role     当前角色
     * @return 票据字符串
     */
    String createFileTicket(String fileName, String userNo, String role);

    /**
     * 教师批阅报告并触发成绩重算。
     *
     * @throws com.lab.labsystem.common.BusinessException 分数非法、报告不存在、无权批阅
     */
    void grade(String reportNo, Double score, String comment, String currentWorkNo);

    /**
     * 查询某学生在某项目下的报告版本历史（按版本倒序）。
     */
    List<ExpReport> listVersions(String studentNo, String projectNo);

    /**
     * 教师端：查询项目下所有最新报告，并补齐学生姓名与班级全称。
     *
     * @throws com.lab.labsystem.common.BusinessException 项目不存在或无权查看
     */
    List<ReportVO> listByProject(String projectNo, String currentWorkNo);
}
