# 04 - Spring Boot 2.7 → 3.2 迁移实录

> 时间：2026-10-02
> 范围：39 个文件，+365 / -347 行
> 迁移路径：Spring Boot 2.7.3（EOL）→ **3.2.5**，JDK 17，jakarta 命名空间全量迁移
> 验证：编译通过 + 运行时全链路点验（登录/鉴权/Redis/WebSocket/接口文档/前端联调）

---

## 〇、总览：为什么迁、换来什么

| 动机 | 说明 |
|---|---|
| Spring Boot 2.7 已 EOL | 不再收安全补丁，生产环境必须迁 |
| fastjson 1.x 一长串反序列化 RCE | 借迁移契机一并换成 Jackson |
| jjwt 0.9.1 弱密钥静默放行 | 0.12 直接拒绝 <256 位密钥 |
| knife4j 3 / springfox 停留在 Swagger 2 | 且不支持 jakarta，必须换 springdoc |

**一句话简历**：将遗留 Spring Boot 2.7（EOL）单体服务迁移至 Spring Boot 3.2 + JDK 17，
完成 jakarta 命名空间全量迁移；替换 fastjson 消除反序列化风险；迁移过程中解决
lombok、Druid、jjwt、消息转换器等 7 类兼容性问题，全部功能回归验证通过。

---

## 一、依赖层（4 个 pom.xml）

### 1.1 父 pom.xml

| 依赖 | 旧 → 新 | 原因 |
|---|---|---|
| spring-boot-starter-parent | 2.7.3 → **3.2.5** | 核心；JDK 定 17（机器可用版本） |
| lombok | 1.18.30 → **1.18.32** | **迁移第一大坑**：老 lombok 不认识 JDK 17+ 字节码，编译期直接崩 |
| mybatis-spring-boot-starter | 2.2.0 → **3.0.3** | Boot 3 必须配 mybatis 3.x，否则启动报错 |
| druid-spring-boot-starter | → **druid-spring-boot-3-starter**（换坐标） | Druid 为 Boot 3 单独发版，旧 starter 在 Boot 3 下连接池初始化失败 |
| pagehelper | 1.3.0 → **2.0.0** | 1.x 只兼容 Boot 2 |
| fastjson | 1.2.76 → **删除** | 反序列化 RCE；Jackson 由 spring-boot-starter-json 传递引入，零显式依赖 |
| knife4j 3.0.2 | → **springdoc-openapi-starter-webmvc-ui 2.5.0** | springfox 停滞在 Swagger 2 且不支持 jakarta |
| jjwt 0.9.1（单包） | → **jjwt-api / impl / jackson 0.12.5 三件套** | 0.12 起官方强制拆包：api 编译期、impl/jackson 运行期 |
| poi | 3.16 → **5.2.5** | 老版本带 CVE |
| mysql-connector-java | → **com.mysql:mysql-connector-j（换坐标）** | Boot 3 时代驱动坐标改名，旧坐标在 BOM 里已无版本 |

### 1.2 sky-pojo/pom.xml

- 删 fastjson、knife4j
- **踩坑记录**：knife4j 被删后，它**传递依赖**带进来的 `jackson-annotations`（`@JsonFormat`）
  和 spring-context（`@DateTimeFormat`）一起消失，DTO 编译报「程序包不存在」。
  **教训：删依赖前必须查它有没有传递依赖被业务实际使用**。显式补回：
  - `com.fasterxml.jackson.core:jackson-annotations`
  - `org.springframework:spring-context`
  - `io.swagger.core.v3:swagger-annotations-jakarta`（@Schema 注解）

---

## 二、命名空间：javax → jakarta（7 文件 16 行 import）

| 文件 | 改动 |
|---|---|
| 两个 JWT 拦截器 | `javax.servlet.http.*` → `jakarta.servlet.http.*` |
| WebSocketServer（来单提醒） | `javax.websocket.*` 6 个注解 + Session |
| PayNotifyController / ReportController / ReportService(Impl) | servlet 相关 |

**背景**：Oracle 攥着 javax 商标不放，Jakarta EE 社区整体改名（EE 9+）。
替换本身机械（sed 即可），**真正的坑在传递依赖**——任何老 starter 还引用 javax.servlet
就会运行时 ClassNotFound，这就是 druid / pagehelper / mybatis 必须一起换版本的原因。

**注意**：`WebSocketConfiguration` 的 `ServerEndpointExporter` 是 Spring 自家类
（`org.springframework.web.socket.*`），不用改——排查过确认。

---

## 三、JwtUtil 重写（jjwt 0.9 → 0.12 API 全变）

| jjwt 0.9（旧） | jjwt 0.12（新） |
|---|---|
| `Jwts.builder().setClaims(claims)` | `.claims(claims)` |
| `.signWith(algo, secretBytes)` | `.signWith(key)`，key 需 `Keys.hmacShaKeyFor(bytes)` 构造 |
| `.setExpiration(exp)` | `.expiration(exp)` |
| `Jwts.parser().setSigningKey(bytes).parseClaimsJws(token).getBody()` | `Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload()` |

**设计决策**：方法签名保持 `createJWT(secretKey, ttlMillis, claims)` /
`parseJWT(secretKey, token)` 不变，4 个调用方（员工登录、用户登录、两个拦截器）零改动——
把 API 变化封装在工具类内部消化。**工具类的价值就是隔离这种变更。**

### 3.1 运行时踩坑：WeakKeyException

**现象**：登录接口 500，日志抛
`io.jsonwebtoken.security.WeakKeyException: The specified key byte array is 48 bits which is not secure enough`。

**原因**：课程配置的 JWT 密钥是 `itcast`（6 字符 = 48 位）。jjwt 0.12 按 RFC 7518
强制要求 HMAC-SHA 密钥 ≥256 位，**直接拒绝**——而 0.9 静默放行。
这不是 bug 是安全增强：旧版本等于用弱密钥裸奔。

**修法**：`application.yml` 两个密钥换成 44+ 字符的随机串。
（生产上应走环境变量注入，本地 dev 配置可接受。）

---

## 四、fastjson → Jackson（7 个文件）

| 文件 | 场景 | 换法 |
|---|---|---|
| PayNotifyController | 解析微信支付回调 | `JSON.parseObject` → `MAPPER.readTree`；`JSONObject` → `JsonNode`；`getString` → `get().asText()` |
| UserServiceImpl | 微信登录取 openid | 同上 |
| HttpClientUtil | 构造 POST JSON | `new JSONObject()` → `MAPPER.createObjectNode()` |
| WeChatPayUtil | 下单/退款构造请求体 + 二次签名 | `ObjectNode` 建树、`set()` 挂子节点 |
| OrderServiceImpl | 百度地图 API 响应解析 + WebSocket 推送序列化 | 嵌套导航 + `asInt()` 替代强转 |
| HttpClientTest | 测试 | 同 ObjectNode |

### 4.1 两库行为差异（最容易踩的两个坑）

1. **缺字段行为**：fastjson `getString()` 缺字段返回 null 可安全判空；Jackson `get()` 也返回
   null，但**不判空直接 `.asText()` 就 NPE**。WeChatPayUtil 里补了显式判空。
2. **异常模型**：fastjson 全是运行时异常；Jackson 的 `readTree` / `writeValueAsString`
   抛**受检异常** `JsonProcessingException`。

### 4.2 受检异常的连锁签名变更

异常模型的差异沿调用链向上烧：
`getOpenid() → wxLogin() → UserService 接口 → UserController`、
`checkOutOfRange() → submitOrder() → OrderService → OrderController`、
`paySuccess() → PayNotifyController`、`reminder() → …`——共 **5 条链、10+ 个签名加 `throws`**。

> 反思：换 JSON 库不只是换 API——受检异常会让「安全替换」升级成「侵入式改造」。
> 工程上更优的做法是在工具类内部 try-catch 包掉，不污染业务签名（留作后续重构）。

### 4.3 JacksonObjectMapper 转换器抢位坑（运行时才发现）

**现象**：`/v3/api-docs` 返回一串 base64，浏览器打不开接口文档。

**原因**：课程原代码 `extendMessageConverters` 里 `converters.add(0, converter)` 把自定义
Jackson 转换器插到**第 0 位**，抢在 `ByteArrayHttpMessageConverter` 之前，把 springdoc
返回的 `byte[]` 序列化成了 base64。Boot 2.7 + springfox 时代返回类型不同，不暴露。

**修法**：不抢位，遍历找到**默认的** `MappingJackson2HttpMessageConverter`，替换其内部
ObjectMapper 为 `JacksonObjectMapper`——既保住日期格式化（验证：订单时间输出
`2026-09-28 00:17`），又不破坏 byte[] 原生输出。

---

## 五、knife4j → springdoc-openapi（18 个文件）

### 5.1 注解替换（17 个控制器 + DTO/VO）

| Swagger 2 | OpenAPI 3 |
|---|---|
| `@Api(tags="员工相关接口")` | `@Tag(name="员工相关接口", description="…")`（**name 必填**） |
| `@ApiOperation("员工登录")` | `@Operation(summary="员工登录")` |
| `@ApiModel` / `@ApiModelProperty` | `@Schema` |

> sed 批量替换的两个教训：
> ① `@Api(tags=…)` 换成 `@Tag(description=…)` 时漏了 name 必填——编译期 `@Tag 缺少 name` 报错才发现；
> ② `@ApiModelProperty(value="…")` 盲替换成 `@Schema(description=…)` 产生了
> `description = description = "…"` 语法错误。**批量替换必须每轮 grep 验证。**

### 5.2 Docket → GroupedOpenApi

```java
// 旧：ApiInfoBuilder 7 行 + Docket 链
// 新：
@Bean
public GroupedOpenApi adminApi() {
    return GroupedOpenApi.builder()
            .group("管理端接口")
            .packagesToScan("com.sky.controller.admin")
            .build();
}
```

**UI 入口变化**：`/doc.html` → **`/swagger-ui.html`**（右上角切分组），
JSON 规范在 `/v3/api-docs/{分组名}`。

### 5.3 WebMvcConfigurationSupport 连坐坑（运行时才发现）

**现象**：`/v3/api-docs` 正常但 `/swagger-ui/index.html` 404。

**原因**：`WebMvcConfiguration` 继承 `WebMvcConfigurationSupport`——这会**关掉 Boot 的
全部 MVC 自动配置**。springdoc 的 swagger-ui 静态资源映射是靠自动配置注册的，被连带关掉。

**修法**：在 `addResourceHandlers` 手动注册（注意 webjar 内部路径**带版本号一层**）：

```java
registry.addResourceHandler("/swagger-ui/**")
        .addResourceLocations("classpath:/META-INF/resources/webjars/swagger-ui/5.13.0/");
```

---

## 六、Redis 序列化（运行时才发现）

**现象**：`/user/shop/status` 500，`NullPointerException: status is null`。

**原因链**：`RedisConfiguration` 只设了 key 序列化器（String），value 用默认
JDK 序列化；Redis 里存的是老版本写入的 JDK 格式 Integer。读取强转 `(Integer)` 后拆箱 NPE。

**修法**（三处）：
1. `RedisConfiguration` 补 `redisTemplate.setValueSerializer(new StringRedisSerializer())`
2. 两个 ShopController 读写改字符串：存 `String.valueOf(status)`，
   读 `Integer.parseInt(statusStr)` + null 兜底
3. `redis-cli -n 10 del SHOP_STATUS` 清掉旧格式数据

---

## 七、验证清单（全部通过）

| 项 | 结果 |
|---|---|
| mvn install 编译（3 模块 141 源文件） | ✅ BUILD SUCCESS |
| 启动 | ✅ 2.0s（比 2.7 还快 0.5s） |
| 管理员登录（jjwt 0.12 签发） | ✅ |
| 带 token 调分页接口（拦截器校验） | ✅ |
| 店铺营业状态（Redis 字符串序列化） | ✅ |
| swagger-ui 页面 | ✅ 200 |
| 管理端 / 客户端分组 | ✅ 38 / 21 个接口 |
| 日期格式化（自定义 ObjectMapper） | ✅ `2026-09-28 00:17` |
| 前端联调 localhost:8088 | ✅ |

已知不验：微信支付真回调（课程无商户号，走 mock）、OSS 上传（示例凭证失效）——
均为课程原有问题，非迁移引入。

---

## 八、面试怎么讲

**Q：Spring Boot 3 迁移做了什么？**
A：jakarta 命名空间全量迁移 + 四个危险依赖替换（fastjson/knife4j/jjwt/POI）+
传递依赖版本对齐（mybatis3/druid-boot3/pagehelper2/mysql-connector-j）。7 类问题里
最有代表性的是三个运行时坑——jjwt 弱密钥拦截（安全增强，需换 256 位密钥）、
Redis 序列化跨版本不兼容（需清旧数据）、消息转换器 add(0) 抢位把 byte[]
序列化成 base64（改成替换默认转换器内部 ObjectMapper）。

**Q：为什么编译期发现不了？**
A：三个坑都是类型/配置在运行时才解析——弱密钥在 `Keys.hmacShaKeyFor()` 运行时校验；
Redis 值格式取决于历史写入；转换器顺序影响运行时匹配。**所以迁移必须跑全链路回归，
不能只看编译通过。**

**Q：换 JSON 库的教训？**
A：不只是换 API：异常模型不同（受检 vs 非受检）导致调用链上 10+ 个签名被迫加 throws。
更好做法是在工具类封装层把异常吃掉，不污染业务签名。
