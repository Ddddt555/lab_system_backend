package com.lab.labsystem;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.*;

import com.lab.labsystem.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
public class SecurityHardeningTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private String realStudentNo;
    private String realTeacherNo;
    private String realClassNo;
    private String studentToken;
    private String teacherToken;
    private final String TEST_DEVICE_ID = "test-device-1234567890";

    @BeforeAll
    void init() {
        try {
            String dbUrl = jdbcTemplate.getDataSource().getConnection().getMetaData().getURL();
            System.out.println("当前测试连接的数据库 URL: " + dbUrl);

            if (dbUrl == null || !dbUrl.contains("_test")) {
                throw new RuntimeException(
                        "严重安全警报：检测到测试类试图连接非测试库 [" + dbUrl + "]！\n" +
                        "为了防止误删生产数据，已强行终止测试！请检查 src/test/resources/application.yaml");
            }
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException("测试数据库连接检查失败，测试已终止！", e);
        }

        System.out.println("\n========== 开始执行安全与并发自动化回归测试 ==========");
    }

    @Test
    @Order(1)
    @DisplayName("0. 清理测试数据 & 获取基础数据")
    void cleanDataAndGetTokens() {
        System.out.println("[Prep] 正在清理测试流水数据...");
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        jdbcTemplate.execute("TRUNCATE TABLE student_booking");
        jdbcTemplate.execute("TRUNCATE TABLE waitlist_record");
        jdbcTemplate.execute("TRUNCATE TABLE exp_report");
        jdbcTemplate.execute("TRUNCATE TABLE exp_score");
        jdbcTemplate.execute("TRUNCATE TABLE attendance_record");
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");

        jdbcTemplate.update("UPDATE exp_session SET remaining_seats = capacity");

        System.out.println("[Prep] 正在获取真实的业务数据...");
        List<Map<String, Object>> classes = jdbcTemplate.queryForList(
                "SELECT DISTINCT class_no FROM exp_project WHERE deleted = 0 LIMIT 1");
        if (classes.isEmpty()) {
            throw new RuntimeException("数据库里没有任何实验项目！请在系统里发布一个实验项目再测试。");
        }
        realClassNo = (String) classes.get(0).get("class_no");

        realTeacherNo = jdbcTemplate.queryForObject(
                "SELECT work_no FROM teach_class WHERE class_no = ?", String.class, realClassNo);

        List<Map<String, Object>> students = jdbcTemplate.queryForList(
                "SELECT student_no FROM student_teach_class WHERE class_no = ? LIMIT 1", realClassNo);
        realStudentNo = students.isEmpty()
                ? jdbcTemplate.queryForObject("SELECT student_no FROM student LIMIT 1", String.class)
                : (String) students.get(0).get("student_no");

        System.out.println(String.format("[Prep] 匹配到测试数据 -> 班级: %s, 教师: %s, 学生: %s", realClassNo, realTeacherNo, realStudentNo));

        cleanupRedisSession(realStudentNo);
        cleanupRedisSession(realTeacherNo);

        studentToken = fetchTestToken(realStudentNo, "student");
        teacherToken = fetchTestToken(realTeacherNo, "teacher");

        assertNotNull(studentToken, "学生 Token 获取失败");
        assertNotNull(teacherToken, "教师 Token 获取失败");
        System.out.println("[Prep] Token 获取成功！\n");
    }

    @Test
    @Order(2)
    @DisplayName("1. 测试通知越权拦截 (依赖 AOP 切面)")
    void testNoticeAuthority() {
        String url = "/api/notice/publish?title=test&content=test&targetRole=all";
        HttpHeaders headers = getHeaders(studentToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<Result> response = restTemplate.postForEntity(url, entity, Result.class);
        Result result = response.getBody();

        // AOP 抛出异常后由全局异常处理器接管，返回真实的 HTTP 403
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(), "越权应返回真实的 HTTP 403");
        
        assertNotNull(result);
        assertEquals(403, result.getCode(), "越权校验失败，学生竟然能发通知！");
        assertTrue(result.getMsg().contains("无权"));
    }

    @Test
    @Order(3)
    @DisplayName("2. 测试异常脱敏 (依赖 GlobalExceptionHandler)")
    void testExceptionMasking() {
        String url = "/api/student/cancel?bookingNo=INVALID_BOOKING_NO_123";
        HttpHeaders headers = getHeaders(studentToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<Result> response = restTemplate.postForEntity(url, entity, Result.class);
        Result result = response.getBody();

        // 业务异常(DATA_NOT_FOUND)映射为 HTTP 404
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode(), "业务异常(资源不存在)应返回 HTTP 404");
        
        assertNotNull(result);
        assertFalse(result.getMsg().contains("java."), "异常脱敏失败，暴露了底层 Java 类名！");
    }

    @Test
    @Order(4)
    @DisplayName("3. 测试并发保存成绩")
    void testConcurrentSaveScore() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);

        String url = "/api/teacher/score/save?studentNo=" + realStudentNo + "&classNo=" + realClassNo + "&dailyScore=85.0";
        HttpHeaders headers = getHeaders(teacherToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    ResponseEntity<Result> response = restTemplate.postForEntity(url, entity, Result.class);
                    if (response.getBody() != null && response.getBody().getCode() == 200) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(10, successCount.get(), "并发保存成绩发生报错！");

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM exp_score WHERE student_no = ? AND class_no = ?",
                Integer.class, realStudentNo, realClassNo);
        assertEquals(1, count, "数据库中出现了重复的成绩记录！");
    }

    @Test
    @Order(5)
    @DisplayName("4. 测试报告下载越权拦截")
    void testReportDownloadSecurity() {
        String otherFileName = "fake_file_other_student.pdf";
        HttpHeaders headers = getHeaders(studentToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        String getUrlApi = "/api/report/download-url/" + otherFileName;
        ResponseEntity<Result> linkResponse = restTemplate.exchange(getUrlApi, HttpMethod.GET, entity, Result.class);
        
        assertEquals(HttpStatus.OK, linkResponse.getStatusCode());
        assertEquals(400, linkResponse.getBody().getCode());
        assertEquals("没有权限！", linkResponse.getBody().getMsg());

        String downloadApi = "/api/report/download/" + otherFileName;
        ResponseEntity<byte[]> streamResponse = restTemplate.exchange(downloadApi, HttpMethod.GET, entity, byte[].class);
        
        assertEquals(403, streamResponse.getStatusCode().value(), "越权直接下载文件流未返回 403！");
    }

    @Test
    @Order(6)
    @DisplayName("5. 测试预约并发防超卖")
    void testConcurrentBookSession() throws InterruptedException {
        List<Map<String, Object>> sessions = jdbcTemplate.queryForList(
                "SELECT session_no FROM exp_session WHERE deleted = 0 LIMIT 1");
        String testSessionNo = (String) sessions.get(0).get("session_no");
        jdbcTemplate.update("UPDATE exp_session SET remaining_seats = 1, capacity = 1 WHERE session_no = ?", testSessionNo);
        jdbcTemplate.update("DELETE FROM student_booking WHERE session_no = ?", testSessionNo);

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);

        String url = "/api/student/book?sessionNo=" + testSessionNo;
        HttpHeaders headers = getHeaders(studentToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    ResponseEntity<Result> response = restTemplate.postForEntity(url, entity, Result.class);
                    if (response.getBody() != null && response.getBody().getCode() == 200) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(1, successCount.get(), "并发防超卖失效：成功次数不是 1 次！");

        Integer remainingSeats = jdbcTemplate.queryForObject(
                "SELECT remaining_seats FROM exp_session WHERE session_no = ?",
                Integer.class, testSessionNo);
        assertEquals(0, remainingSeats);
    }

    @Test
    @Order(7)
    @DisplayName("6. 验证登出后旧 Access Token 立即失效")
    void testLogoutInvalidatesAccessToken() {
        String checkUrl = "/api/notice/list";
        HttpHeaders headers = getHeaders(studentToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<Result> beforeLogout = restTemplate.exchange(checkUrl, HttpMethod.GET, entity, Result.class);
        assertEquals(200, beforeLogout.getStatusCode().value(), "Token 有效时，应能正常访问");

        String logoutUrl = "/api/logout";
        restTemplate.postForEntity(logoutUrl, entity, Result.class);

        ResponseEntity<Result> afterLogout = restTemplate.exchange(checkUrl, HttpMethod.GET, entity, Result.class);
        assertEquals(401, afterLogout.getStatusCode().value(), "登出后，旧 Access Token 应立即失效返回 401！");
    }

    private String fetchTestToken(String username, String role) {
        String url = "/api/login/test?username=" + username + "&role=" + role;
        ResponseEntity<Result> response = restTemplate.postForEntity(url, null, Result.class);
        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null && response.getBody().getCode() == 200) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) response.getBody().getData();
            return (String) data.get("token");
        }
        return null;
    }

    private HttpHeaders getHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        headers.set("X-Device-Id", TEST_DEVICE_ID);
        return headers;
    }

    private void cleanupRedisSession(String userNo) {
        String hashTag = "{" + userNo + ":" + TEST_DEVICE_ID + "}";
        stringRedisTemplate.delete("refresh_token:" + hashTag);
        stringRedisTemplate.delete("refresh:idem:" + hashTag);
        stringRedisTemplate.opsForZSet().remove("user:devices:" + userNo, TEST_DEVICE_ID);
    }
}