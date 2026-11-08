package com.lab.labsystem.common;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;

/**
 * 无分页列表查询的兜底上限。
 *
 * <p>项目里有一部分接口在不传 page/size 时会直接 {@code selectList} 整表返回，
 * 例如系统操作日志、报告列表、场次列表。数据量增长后这类查询会带来内存与带宽压力，
 * 客户端只要不带分页参数就能触发（也是一种成本很低的 DoS 手段）。
 *
 * <p>这里在<b>不改变接口返回结构</b>（仍然是 List）的前提下加一个硬上限，
 * 避免改动前端调用方式。
 */
public final class QueryLimits {

    /** 单次无分页查询最多返回的条数 */
    public static final long MAX_LIST_SIZE = 200L;

    private QueryLimits() {
    }

    /**
     * 以最大条数上限执行一次查询。
     *
     * @param mapper  目标表 Mapper
     * @param wrapper 查询条件
     * @return 最多 {@link #MAX_LIST_SIZE} 条记录
     */
    public static <T> List<T> list(BaseMapper<T> mapper, Wrapper<T> wrapper) {
        return mapper.selectPage(new Page<>(1L, MAX_LIST_SIZE), wrapper).getRecords();
    }
}
