package com.lab.labsystem.service.student;

/**
 * 学生选课中心。
 *
 * <p>从 {@code StudentController} 下沉出来的选课相关业务：可选教学班列表（含
 * 是否已选标记）、选课、退课。退课时需要校验该班下是否还有有效预约。
 */
public interface CourseSelectionService {

    /**
     * 可选教学班列表。
     *
     * @param studentNo 当前学生
     * @param page      页码，与 size 同时为空时返回列表而非分页对象
     * @param size      每页条数
     * @return {@code PageResult} 或 {@code List}
     */
    Object listAvailableClasses(String studentNo, Integer page, Integer size);

    /**
     * 选课。
     *
     * @return 成功提示
     * @throws com.lab.labsystem.common.BusinessException 重复选课
     */
    String selectClass(String studentNo, String classNo);

    /**
     * 退课。
     *
     * @return 成功提示
     * @throws com.lab.labsystem.common.BusinessException 该班下仍有有效场次预约
     */
    String dropClass(String studentNo, String classNo);
}
