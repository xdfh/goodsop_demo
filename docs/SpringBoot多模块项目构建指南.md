# SpringBoot多模块项目构建需求文档

## 1. 项目背景与技术栈

### 1.1 项目简介
基于Spring Boot 3.1.5构建的多模块RESTful API服务项目，采用扁平化的模块结构，实现用户管理、文件管理及IoT设备管理功能。

### 1.2 技术栈
- **基础框架**: Spring Boot 3.1.5
- **JDK版本**: Java 17
- **持久层**: MyBatis-Plus 3.5.3.2
- **数据库**: PostgreSQL 42.6.0
- **连接池**: Druid 1.2.20
- **文档工具**: Knife4j 4.3.0
- **测试框架**: JUnit 5 + Mockito
- **工具库**: Lombok, Hutool
- **开发环境**: Windows 10

## 2. 项目结构

### 2.1 模块架构
```
goodsop (父模块)
├── goodsop-common (公共模块)
├── goodsop-config (配置模块)
├── goodsop-modules (业务模块)
│   ├── goodsop-user (用户管理)
│   ├── goodsop-file (文件管理)
│   ├── goodsop-iot (IoT管理)
└── goodsop-server (应用入口)
```

### 2.2 包结构
```
com.goodsop
├── common (公共库)
│   ├── core (核心工具)
│   └── swagger (接口文档)
├── modules (业务模块)
│   ├── user (用户管理)
│   ├── file (文件管理)
│   └── iot (IoT管理)
└── server (应用入口)
```

## 3. 详细构建步骤

### 3.1 创建父模块
1. 创建`pom.xml`，指定SpringBoot父依赖版本3.1.5
2. 声明子模块及依赖管理
3. 统一管理依赖版本号

父POM示例：
```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.1.5</version>
    <relativePath/>
</parent>

<properties>
    <java.version>17</java.version>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
    <spring-boot.version>3.1.5</spring-boot.version>
    <mybatis-plus.version>3.5.3.2</mybatis-plus.version>
    <druid.version>1.2.20</druid.version>
    <postgresql.version>42.6.0</postgresql.version>
    <knife4j.version>4.3.0</knife4j.version>
    <hutool.version>5.8.23</hutool.version>
    <lombok.version>1.18.30</lombok.version>
    <junit.version>5.10.0</junit.version>
    <mockito.version>5.6.0</mockito.version>
</properties>
```

### 3.2 构建公共模块
1. 创建`goodsop-common`模块（JAR打包）
2. 实现核心功能：
   - API响应封装`Result<T>`
   - 通用异常处理
   - Swagger文档配置

Result类示例：
```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    private int code;
    private String msg;
    private T data;

    public static <T> Result<T> success() {
        return new Result<>(200, "success", null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data);
    }

    public static <T> Result<T> error(String msg) {
        return new Result<>(500, msg, null);
    }
}
```

### 3.3 构建配置模块
1. 创建`goodsop-config`模块（JAR打包）
2. 添加环境配置文件：
   - `application.yml`（主配置）
   - `application-dev.yml`（开发环境）
   - `application-test.yml`（测试环境）
   - `application-prod.yml`（生产环境）

主配置示例：
```yaml
spring:
  profiles:
    active: dev
```

### 3.4 构建业务模块
1. 创建业务模块父模块`goodsop-modules`
2. 创建用户模块`goodsop-user`：
   - 实体类、Mapper、Service、Controller
   - 单元测试
3. 创建文件模块`goodsop-file`（基础结构）
4. 创建IoT模块`goodsop-iot`（基础结构）

### 3.5 构建服务器模块
1. 创建`goodsop-server`模块
2. 添加主启动类
3. 添加Spring Boot打包配置

主启动类示例：
```java
@SpringBootApplication
@ComponentScan(basePackages = {"com.goodsop"})
@MapperScan(basePackages = {"com.goodsop.**.mapper"})
public class GoodsopApplication {
    public static void main(String[] args) {
        SpringApplication.run(GoodsopApplication.class, args);
    }
}
```

## 4. 数据库配置

### 4.1 PostgreSQL配置参数
```yaml
spring:
  datasource:
    driver-class-name: org.postgresql.Driver
    url: jdbc:postgresql://192.168.1.30:65432/mydb
    username: post
    password: admin@2025
    type: com.alibaba.druid.pool.DruidDataSource
    druid:
      initial-size: 5
      min-idle: 5
      max-active: 20
      max-wait: 60000
      time-between-eviction-runs-millis: 60000
      min-evictable-idle-time-millis: 300000
      validation-query: SELECT 1
      test-while-idle: true
      test-on-borrow: false
      test-on-return: false
      pool-prepared-statements: true
      max-pool-prepared-statement-per-connection-size: 20
```

### 4.2 MyBatis-Plus配置
```yaml
mybatis-plus:
  mapper-locations: classpath*:/mapper/**/*.xml
  typeAliasesPackage: com.goodsop.**.entity
  global-config:
    db-config:
      id-type: AUTO
      logic-delete-field: isDeleted
      logic-delete-value: 1
      logic-not-delete-value: 0
  configuration:
    map-underscore-to-camel-case: true
    cache-enabled: false
```

## 5. 测试配置

### 5.1 服务测试
- 使用MockMvc测试控制器
- 使用Mockito模拟依赖

### 5.2 单元测试
- 对于继承`ServiceImpl`的服务类，需使用`@Spy`注解正确模拟：
```java
@ExtendWith(MockitoExtension.class)
public class ServiceTest {
    @Mock
    private EntityMapper mapper;
    
    @Spy
    @InjectMocks
    private EntityServiceImpl service;
    
    @Test
    void testMethod() {
        // 模拟getBaseMapper方法
        doReturn(mapper).when(service).getBaseMapper();
        // 其他测试逻辑
    }
}
```

## 6. 开发注意事项

### 6.1 版本一致性
1. **确保SpringBoot版本统一**: 父POM和dependencyManagement中的版本必须一致，避免JdbcClient等类缺失问题
2. **避免Spring Boot 3.2.x**: 当前项目基于3.1.5，使用3.2.0+可能导致不兼容

### 6.2 模块依赖
1. **避免循环依赖**: 模块间依赖遵循自下而上原则
2. **依赖传递**: server模块应依赖所有业务模块
3. **统一版本管理**: 所有依赖版本在父POM中统一声明

### 6.3 数据库连接
1. **连接参数验证**:
   - 确认IP、端口、用户名密码正确
   - 确认数据库存在且用户有权限访问
2. **PostgreSQL注意事项**:
   - 确认pg_hba.conf允许应用连接
   - 确认postgresql.conf中listen_addresses配置正确
3. **替代方案**:
   - 如PostgreSQL不可用，可配置H2内存数据库

### 6.4 Windows开发环境特殊处理
1. **PowerShell命令限制**: 不支持&&作为命令分隔符
2. **解决方案**: 使用分号或单独运行命令
3. **文件路径**: 使用反斜杠(\\)或正斜杠(/)均可
4. **编码问题**: 确保所有文件使用UTF-8编码

### 6.5 项目重构注意事项
1. **保持包路径一致**: 重构时确保主要类的包路径不变
2. **更新引用关系**: 修改模块结构后更新所有模块间的依赖关系
3. **统一环境配置**: 使用Spring Boot的多环境配置替代多模块配置

## 7. 构建命令

### 7.1 项目构建
```
mvn clean install -DskipTests
```

### 7.2 运行应用
```
cd goodsop-server
mvn spring-boot:run
```

### 7.3 执行测试
```
mvn test
```

## 8. 接口访问

- API基础路径: http://localhost:8080/api
- 接口文档: http://localhost:8080/api/doc.html
- 健康检查: http://localhost:8080/api/actuator

## 9. 问题排查指南

### 9.1 版本不一致问题
如遇到类缺失问题，检查:
- 父POM中的Spring Boot版本
- dependencyManagement中的Spring Boot版本
- 所有子模块使用的Spring Boot版本

### 9.2 测试失败问题
针对MyBatis-Plus的ServiceImpl测试问题:
- 使用@Spy注解模拟service
- 使用doReturn().when()模式模拟getBaseMapper()方法

### 9.3 数据库连接问题
如遇到数据库连接失败:
- 验证服务器可访问性(ping)
- 验证端口开放状态(netstat)
- 验证数据库用户权限
- 配置应用程序防火墙权限

---

按照本文档指导构建项目，可避免之前遇到的版本不一致、依赖配置错误、测试框架使用不当等问题，快速构建出功能完善的多模块Spring Boot应用。 