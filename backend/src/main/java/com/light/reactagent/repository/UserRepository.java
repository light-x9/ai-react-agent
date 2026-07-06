package com.light.reactagent.repository;

import com.light.reactagent.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 用户数据访问层
 * <p>
 * 继承 JpaRepository 获得基础 CRUD，再通过方法名衍生查询自动实现：
 * - findByUsername：按用户名查找用户（登录校验）
 * - existsByUsername：判断用户名是否已存在（注册去重）
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 按用户名查找用户
     *
     * @param username 用户名
     * @return 找到返回 Optional 包裹的用户，不存在返回 Optional.empty()
     */
    Optional<User> findByUsername(String username);

    /**
     * 判断用户名是否已被注册
     *
     * @param username 用户名
     * @return true 表示已存在
     */
    boolean existsByUsername(String username);
}
