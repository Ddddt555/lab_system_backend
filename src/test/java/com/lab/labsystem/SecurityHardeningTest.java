package com.lab.labsystem;

import com.lab.labsystem.common.Result;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * =====================================================================
 * 【自动化集成测试类：修改3阶段安全与并发修复验证】
 * 
 * 说明：修改3阶段（核心安全与稳定性）的 8 项修复是否持续生效。
 * 每次系统进行重大更新（特别是修改了 Controller、Service、SQL 或并发逻辑）后，
 * 必须运行本类以保证历史漏洞没有发生“回归（Regression）”。
 * 
 * 前置条件：
 * 1. MySQL、Redis 服务正常启动。
 * 2. application.yml 中 app.security.test-login-enabled 必须为 true。
 * 3. 数据库中必须至少存在一个教学班、一个教师、一个学生、一个实验项目。
 * =====================================================================
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS) 
public class SecurityHardeningTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String realStudentNo;
    private String realTeacherNo;
    private String realClassNo;
    private String studentToken;
    private String teacherToken;

    @BeforeAll
    void init() {
        System.out.println("\n🚀 ========== 开始执行 P0 阶段安全与并发自动化回归测试 ==========");
    }

    /**
     * 【对应修复项】步骤 3 (候补表物理删除清理) + 步骤 8 (唯一约束测试的干净环境准备)
     * 验证点：
     * 1. 能正确清理脏数据。
     * 2. 能自动从数据库匹配真实的业务 ID（防止因硬编码导致的测试失败）。
     * 3. 能通过测试后门（testLogin）成功获取 JWT Token。
     */
    @Test
    @Order(1)
    @DisplayName("0. 清理测试数据 & 获取基础数据")
    void cleanDataAndGetTokens() {
        System.out.println("🧹 [Prep] 正在清理测试流水数据...");
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        jdbcTemplate.execute("TRUNCATE TABLE student_booking");
        jdbcTemplate.execute("TRUNCATE TABLE waitlist_record");
        jdbcTemplate.execute("TRUNCATE TABLE exp_report");
        jdbcTemplate.execute("TRUNCATE TABLE exp_score");
        jdbcTemplate.execute("TRUNCATE TABLE attendance_record");
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
        
        // 重置场次余量，防止之前测试导致余量为 0 影响后续测试
        jdbcTemplate.update("UPDATE exp_session SET remaining_seats = capacity");
        System.out.println("✅ [Prep] 数据清理完毕！");

        System.out.println("🔍 [Prep] 正在从数据库动态获取真实的业务数据...");
        // 动态获取数据，避免硬编码导致找不到数据
        List<Map<String, Object>> classes = jdbcTemplate.queryForList(
            "SELECT DISTINCT class_no FROM exp_project WHERE deleted = 0 LIMIT 1"
        );
        if (classes.isEmpty()) {
            throw new RuntimeException("❌ 数据库里没有任何实验项目！请在系统里发布一个实验项目再测试。");
        }
        realClassNo = (String) classes.get(0).get("class_no");

        realTeacherNo = jdbcTemplate.queryForObject(
            "SELECT work_no FROM teach_class WHERE class_no = ?", String.class, realClassNo
        );

        List<Map<String, Object>> students = jdbcTemplate.queryForList(
            "SELECT student_no FROM student_teach_class WHERE class_no = ? LIMIT 1", realClassNo
        );
        realStudentNo = students.isEmpty() 
            ? jdbcTemplate.queryForObject("SELECT student_no FROM student LIMIT 1", String.class)
            : (String) students.get(0).get("student_no");

        System.out.println(String.format("✅ [Prep] 匹配到测试数据 -> 班级: %s, 教师: %s, 学生: %s", realClassNo, realTeacherNo, realStudentNo));

        // 获取免验证码 Token
        studentToken = fetchTestToken(realStudentNo, "student");
        teacherToken = fetchTestToken(realTeacherNo, "teacher");
        
        assertNotNull(studentToken, "学生 Token 获取失败，请检查配置项 test-login-enabled");
        assertNotNull(teacherToken, "教师 Token 获取失败");
        System.out.println("✅ [Prep] Token 获取成功！\n");
    }

    /**
     * 【对应修复项】步骤 1：通知接口角色防伪造
     * 验证点：
     * 1. 学生即使手动在 Postman/前端传 targetRole=all，后端也应根据其真实 JWT 角色拦截。
     * 2. 拦截后应返回业务异常提示“无权”。
     */
    @Test
    @Order(2)
    @DisplayName("1. 测试通知越权拦截")
    void testNoticeAuthority() {
        System.out.println("▶️ [用例1] 测试步骤 1：通知接口角色防伪造");
        
        // 学生尝试发布通知
        String url = "/api/notice/publish?title=test&content=test&targetRole=all";
        HttpHeaders headers = getHeaders(studentToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<Result> response = restTemplate.postForEntity(url, entity, Result.class);
        Result result = response.getBody();
        
        // 期望：被拦截并返回 400 错误
        assertEquals(400, result.getCode(), "越权校验失败，学生竟然能发通知！");
        assertTrue(result.getMsg().contains("无权"), "错误提示不对");
        System.out.println("  ✅ 学生发布通知被成功拦截，返回消息: " + result.getMsg() + "\n");
    }

    /**
     * 【对应修复项】步骤 7：生产环境异常脱敏
     * 验证点：
     * 1. 故意传入错误参数（如不存在的 bookingNo）。
     * 2. 后端不能抛出 Java 原生异常信息（如 NullPointerException、SQL 语句错误等）。
     * 3. 应被 GlobalExceptionHandler 捕获并返回友好的业务提示。
     */
    @Test
    @Order(3)
    @DisplayName("7. 测试异常脱敏")
    void testExceptionMasking() {
        System.out.println("▶️ [用例2] 测试步骤 7：异常信息脱敏");
        
        String url = "/api/student/cancel?bookingNo=INVALID_BOOKING_NO_123";
        HttpHeaders headers = getHeaders(studentToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<Result> response = restTemplate.postForEntity(url, entity, Result.class);
        Result result = response.getBody();

        assertNotNull(result);
        System.out.println("  ✅ 异常返回内容: " + result.getMsg());
        // 断言：返回的文本中绝对不能出现 java. 或 org.springframework 等底层信息
        assertFalse(result.getMsg().contains("java."), "异常脱敏失败，暴露了底层 Java 类名！");
        System.out.println("  ✅ 未暴露底层堆栈和类名\n");
    }

    /**
     * 【对应修复项】步骤 8：数据库唯一约束与并发保护 (Upsert 策略)
     * 验证点：
     * 1. 10 个线程同时请求保存同一个学生的成绩。
     * 2. 数据库不能被击穿（不能报 DuplicateKeyException）。
     * 3. 无论并发多高，最终 exp_score 表里该学生只有一条记录。
     */
    @Test
    @Order(4)
    @DisplayName("8. 测试并发保存成绩")
    void testConcurrentSaveScore() throws InterruptedException {
        System.out.println("▶️ [用例3] 测试步骤 8：并发录入成绩 (防重复插入)");
        
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        String url = "/api/teacher/score/save?studentNo=" + realStudentNo + "&classNo=" + realClassNo + "&dailyScore=85.0";
        HttpHeaders headers = getHeaders(teacherToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    ResponseEntity<Result> response = restTemplate.postForEntity(url, entity, Result.class);
                    if (response.getBody() != null && response.getBody().getCode() == 200) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        System.out.println("  ✅ 10个并发请求执行完毕，成功: " + successCount.get() + " 次，失败: " + failCount.get() + " 次");
        
        // 断言：全部请求必须成功（被我们的 try-catch 兜底并转化为 Update 操作）
        assertEquals(10, successCount.get(), "并发保存成绩发生报错，可能触发了唯一键冲突或权限校验失败！");
        
        // 核心断言：数据库校验通过，仅存在一条成绩记录
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM exp_score WHERE student_no = ? AND class_no = ?", 
                Integer.class, realStudentNo, realClassNo);
        assertEquals(1, count, "数据库中出现了重复的成绩记录！");
        System.out.println("  ✅ 数据库校验通过，仅存在一条成绩记录\n");
    }

    /**
     * 【对应修复项】步骤 9：报告下载/预览 对象级越权拦截
     * 验证点：
     * 1. 学生尝试获取不属于自己的文件（或不存在的文件）的下载链接时，应返回 400。
     * 2. 学生尝试直接请求文件流时（没有合法 ticket），应被拦截返回 403。
     */
    @Test
    @Order(5)
    @DisplayName("9. 测试报告下载越权拦截")
    void testReportDownloadSecurity() {
        System.out.println("▶️ [用例4] 测试步骤 9：报告下载对象级越权拦截");

        // 随便构造一个非本人拥有的文件名
        String otherFileName = "fake_file_other_student.pdf";

        // 1. 尝试获取该文件的下载链接（预期：hasPermission 拦截，返回 400）
        String getUrlApi = "/api/report/download-url/" + otherFileName;
        HttpHeaders headers = getHeaders(studentToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<Result> linkResponse = restTemplate.exchange(getUrlApi, HttpMethod.GET, entity, Result.class);
        Result linkResult = linkResponse.getBody();

        assertNotNull(linkResult);
        assertEquals(400, linkResult.getCode(), "越权获取下载链接未拦截！");
        assertEquals("没有权限！", linkResult.getMsg());
        System.out.println("  ✅ 越权获取下载链接被成功拦截，返回: " + linkResult.getMsg());

        // 2. 尝试绕过 Ticket 直接强行请求文件流（预期：streamFromMinio 内部校验拦截，返回 403）
        String downloadApi = "/api/report/download/" + otherFileName;
        ResponseEntity<byte[]> streamResponse = restTemplate.exchange(downloadApi, HttpMethod.GET, entity, byte[].class);

        // 注意：Spring 6+ 中使用 getStatusCode().value() 获取状态码
        assertEquals(403, streamResponse.getStatusCode().value(), "越权直接下载文件流未返回 403！");
        System.out.println("  ✅ 直接越权下载文件流被成功拦截，返回状态码: 403\n");
    }

    private String fetchTestToken(String username, String role) {
        String url = "/api/login/test?username=" + username + "&role=" + role;
        ResponseEntity<Result> response = restTemplate.postForEntity(url, null, Result.class);
        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null && response.getBody().getCode() == 200) {
            Map<String, Object> data = (Map<String, Object>) response.getBody().getData();
            return (String) data.get("token");
        }
        return null;
    }

    private HttpHeaders getHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
    
    /**
     * 【对应修复项】任务 2.1：预约并发防超卖与防连点
     * 验证点：
     * 1. 强行把余量改为 1。
     * 2. 用 10 个线程并发去抢。
     * 3. 无论多少人抢，只能成功 1 次，数据库余量必须变成 0，不能出现负数或超卖。
     */
    @Test
    @Order(6)
    @DisplayName("10. 测试预约并发防超卖")
    void testConcurrentBookSession() throws InterruptedException {
        System.out.println("▶️ [用例5] 测试步骤：预约并发防超卖与防连点");

        // 1. 从数据库找一个场次，并强行把余量改为 1
        List<Map<String, Object>> sessions = jdbcTemplate.queryForList(
                "SELECT session_no FROM exp_session WHERE deleted = 0 LIMIT 1"
        );
        if (sessions.isEmpty()) {
            throw new RuntimeException("数据库里没有场次，请先创建一个场次！");
        }
        String testSessionNo = (String) sessions.get(0).get("session_no");
        jdbcTemplate.update("UPDATE exp_session SET remaining_seats = 1, capacity = 1 WHERE session_no = ?", testSessionNo);
        
        // 清空之前可能产生的该场次预约记录
        jdbcTemplate.update("DELETE FROM student_booking WHERE session_no = ?", testSessionNo);

        // 2. 准备 10 个线程并发抢座
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        String url = "/api/student/book?sessionNo=" + testSessionNo;
        HttpHeaders headers = getHeaders(studentToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    ResponseEntity<Result> response = restTemplate.postForEntity(url, entity, Result.class);
                    if (response.getBody() != null && response.getBody().getCode() == 200) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        System.out.println("  ✅ 10个并发请求执行完毕，成功: " + successCount.get() + " 次，失败(被拦截): " + failCount.get() + " 次");

        // 3. 核心断言：必须只有 1 个人抢到，绝对不能超卖！
        assertEquals(1, successCount.get(), "并发防超卖失效：成功次数不是 1 次！");
        
        // 4. 数据库校验：余量必须刚好等于 0
        Integer remainingSeats = jdbcTemplate.queryForObject(
                "SELECT remaining_seats FROM exp_session WHERE session_no = ?",
                Integer.class, testSessionNo);
        assertEquals(0, remainingSeats, "并发防超卖失效：数据库剩余机位变成了负数或不为0！");

        // 5. 数据库校验：该场次只能有 1 条预约记录
        Integer bookingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM student_booking WHERE session_no = ?",
                Integer.class, testSessionNo);
        assertEquals(1, bookingCount, "并发防超卖失效：数据库出现了多条预约记录！");

        System.out.println("  ✅ 数据库校验通过，未发生超卖，且只有1条预约记录\n");
    }
}