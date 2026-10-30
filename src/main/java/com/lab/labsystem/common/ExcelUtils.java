package com.lab.labsystem.common;

import com.alibaba.excel.EasyExcel;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.List;

public class ExcelUtils {
    // 导出 Excel 到浏览器
    public static <T> void export(HttpServletResponse response, String fileName, Class<T> clazz, List<T> data) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String encodedFileName = URLEncoder.encode(fileName, "UTF-8").replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + encodedFileName + ".xlsx");
        EasyExcel.write(response.getOutputStream(), clazz).sheet("数据").doWrite(data);
    }
}	