package com.lab.labsystem.common;

import com.baomidou.mybatisplus.extension.toolkit.Db;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * 批量写操作适配层。
 *
 * <p>MyBatis-Plus 的 {@link Db} 是静态工具类，调用时会去取全局
 * {@code SqlSessionFactory} 与 {@code TableInfoCache}。这带来两个问题：
 *
 * <ol>
 *   <li><b>单元测试不可用</b>：没有 Spring 上下文时它会直接抛
 *       {@code Not Found TableInfoCache}，导致业务逻辑无法被 mock 测试覆盖；</li>
 *   <li><b>依赖被隐藏</b>：service 的构造参数里看不出它依赖数据库会话，
 *       只能靠读实现发现。</li>
 * </ol>
 *
 * <p>这里用一层极薄的包装把静态调用变成可注入的实例方法：
 * 生产环境行为完全不变（仍是 MyBatis-Plus 的批量执行），
 * 测试里可以 mock 它来验证"是否真的走了批量"。
 */
@Component
public class BatchOperations {

    /**
     * 批量插入。分批大小由 MyBatis-Plus 默认值决定。
     *
     * @return 是否执行成功
     */
    public <T> boolean saveBatch(Collection<T> entities) {
        return Db.saveBatch(entities);
    }

    /**
     * 按主键批量更新。
     *
     * @return 是否执行成功
     */
    public <T> boolean updateBatchById(Collection<T> entities) {
        return Db.updateBatchById(entities);
    }
}
