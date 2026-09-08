# Spring MVC 请求映射注解

> 学习笔记 · 2026-09-08
> 配套项目：苍穹外卖 `/Users/luoshuangshuang/Downloads/sky-mine`
> 实测参考：`sky-server/src/main/java/com/sky/controller/admin/EmployeeController.java`

---

## 一、要解决的问题

浏览器发来一个请求：

```
POST /admin/employee/login
Content-Type: application/json

{"username":"admin","password":"123456"}
```

Spring 手里有几十个 Controller、上百个方法。它凭什么知道这个请求该交给
`EmployeeController.login()` 处理？

答案就是**映射注解**。你在方法上贴一张"标签"，写清楚"我负责哪个 URL、哪种
HTTP 方法"，Spring 启动时扫描全部标签，建立一张

```
(请求方法 + 路径) → (哪个类的哪个方法)
```

的对照表。请求进来时查表，找到就调用，找不到就 404。

---

## 二、@RequestMapping

最基础、最万能的映射注解，其他都是它的简写。

```java
@RequestMapping(
    value  = "/login",                 // 路径
    method = RequestMethod.POST,       // HTTP 方法
    params = "type=admin",             // 要求带某个参数（少用）
    headers = "X-Token"                // 要求带某个请求头（少用）
)
```

只写路径时可以省略 `value =`：

```java
@RequestMapping("/login")   // 等价于 @RequestMapping(value = "/login")
```

### 能贴在两个地方

| 位置 | 作用 |
|---|---|
| **类上** | 给这个 Controller 里所有方法加一个**路径前缀** |
| **方法上** | 这个方法具体负责的路径 |

两者**拼接**成最终 URL。项目里的实际写法：

```java
@RestController
@RequestMapping("/admin/employee")      // ← 类级前缀
public class EmployeeController {

    @PostMapping("/login")               // ← 方法级路径
    public Result<EmployeeLoginVO> login(@RequestBody EmployeeLoginDTO dto) { ... }

    @PostMapping("/logout")
    public Result<String> logout() { ... }
}
```

最终对外暴露：

```
POST /admin/employee/login
POST /admin/employee/logout
```

**为什么要分两层？** 同一个 Controller 里的接口天然共享前缀。写在类上一次，
后面每个方法少写一遍，改前缀时也只改一处。

---

## 三、@PostMapping 等一组简写

Spring 4.3 起提供了 5 个"组合注解"，把 `method` 固化了，**只能贴在方法上**：

| 简写 | 等价于 | 惯用语义 |
|---|---|---|
| `@GetMapping` | `@RequestMapping(method = GET)` | 查询数据 |
| `@PostMapping` | `@RequestMapping(method = POST)` | 新增、提交 |
| `@PutMapping` | `@RequestMapping(method = PUT)` | 修改 |
| `@DeleteMapping` | `@RequestMapping(method = DELETE)` | 删除 |
| `@PatchMapping` | `@RequestMapping(method = PATCH)` | 局部修改 |

所以这两行完全一样：

```java
@RequestMapping(value = "/login", method = RequestMethod.POST)
@PostMapping("/login")
```

**行业约定**：类上用 `@RequestMapping` 定前缀，方法上用简写。意图一眼可见，
也不容易漏写 `method`。

### 漏写 method 会怎样

`@RequestMapping("/login")` 不指定 `method`，等于**接受所有** HTTP 方法。
浏览器地址栏直接访问也能进来，容易掩盖设计问题，因此不推荐。

---

## 四、实测：405 是怎么来的

项目跑起来后（`localhost:8080`），用 GET 访问登录接口：

```bash
curl -o /dev/null -w "%{http_code}\n" http://localhost:8080/admin/employee/login
# 405
```

改成 POST：

```bash
curl -X POST http://localhost:8080/admin/employee/login \
     -H 'Content-Type: application/json' \
     -d '{"username":"admin","password":"123456"}'
# {"code":1,"msg":null,"data":{"id":1,"userName":"admin","name":"管理员","token":"eyJ..."}}
```

三种状态码的区别，正好对应查表的三种结果：

| 状态码 | 含义 | 原因 |
|---|---|---|
| **404** | Not Found | 路径没匹配上，表里查不到 |
| **405** | Method Not Allowed | 路径匹配上了，但 HTTP 方法不符 |
| **200** | OK | 路径 + 方法都对，方法被调用 |

**405 是个好消息**——它说明路径写对了，只是请求方式不对。调接口遇到 405 时
先检查 GET/POST，别急着怀疑路径。

---

## 五、常一起出现的三个注解

### @RestController

```java
@RestController = @Controller + @ResponseBody
```

- `@Controller`：告诉 Spring "这是个控制器，扫描我里面的映射注解"
- `@ResponseBody`：方法返回值**直接序列化成 JSON** 写进响应体

没有 `@ResponseBody` 的话，Spring 会把返回的字符串当作**视图名**去找页面模板。
前后端分离项目一律用 `@RestController`。

### @RequestBody

把请求体里的 JSON **反序列化**成 Java 对象：

```java
public Result<EmployeeLoginVO> login(@RequestBody EmployeeLoginDTO employeeLoginDTO)
```

请求体 `{"username":"admin","password":"123456"}` 会按**字段名**填进
`EmployeeLoginDTO` 的 `username`、`password`。名字对不上就是 `null`。

配 `POST`/`PUT` 用。GET 请求没有请求体，参数在 URL 上，要用别的方式接。

### @RequestParam

接 URL 查询参数（`?page=1&pageSize=10`）：

```java
@GetMapping("/page")
public Result<PageResult> page(@RequestParam Integer page,
                               @RequestParam(defaultValue = "10") Integer pageSize)
```

参数多的时候可以直接用一个实体接收，Spring 按字段名自动填充，不用写注解。

---

## 六、一个请求的完整链路

以登录为例，把注解的位置串起来：

```
POST /admin/employee/login  +  JSON 请求体
        │
        ▼
DispatcherServlet ── 查映射表 ──► 命中 EmployeeController.login()
        │                          （@RequestMapping + @PostMapping 建立的）
        ▼
@RequestBody ── JSON 反序列化 ──► EmployeeLoginDTO 对象
        │
        ▼
   login() 方法体：查库 → 校验密码 → 生成 JWT → 封装 VO
        │
        ▼
@ResponseBody ── 对象序列化 ──► JSON 响应体
```

映射注解只负责**最前面那一步**：把请求路由到正确的方法。它不管参数怎么转、
返回值怎么写——那是 `@RequestBody` / `@ResponseBody` 的事。

---

## 七、要点回顾

1. `@RequestMapping` 是总注解，`@GetMapping` / `@PostMapping` 等是它固定了
   `method` 的简写。
2. 类上的路径 + 方法上的路径 = 最终 URL。
3. 405 说明路径对了、方法错了；404 说明路径就没匹配上。
4. `@RestController` 让返回值走 JSON 而不是视图。
5. `@RequestBody` 收 JSON 请求体，`@RequestParam` 收 URL 查询参数。
