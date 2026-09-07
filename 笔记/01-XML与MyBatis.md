# XML 与 MyBatis 动态 SQL

> 学习笔记 · 2026-09-07
> 配套项目：苍穹外卖 `/Users/luoshuangshuang/Downloads/sky-mine`
> 实测参考：`sky-take-out/sky-take-out/sky-server/src/main/resources/mapper/EmployeeMapper.xml`

---

## 一、XML 是什么

XML **不是编程语言**，是一种**数据格式**。用成对的标签把数据包起来，表达层级关系。

和 JSON 干的是同一件事：

```xml
<!-- XML -->
<person>
  <name>张三</name>
  <age>18</age>
</person>
```

```json
// JSON，等价
{ "person": { "name": "张三", "age": 18 } }
```

XML 更啰嗦（标签要写两遍），但它有 JSON 没有的东西：

- **属性** —— `<select id="xxx">` 里的 `id` 就是属性
- **注释** —— `<!-- 这样写 -->`
- **格式校验** —— 可以用 DTD / Schema 强制规定「这个文件必须长什么样」

所以配置文件领域，XML 活得比 JSON 久。

**为什么 Java 生态 XML 特别多？** 纯历史原因。Java 早期（2000 年代）XML 正当红，
那批框架（Maven、Spring、MyBatis）就都用了 XML，后来它们太成功了，改不动了。

---

## 二、项目里的 XML 出现在两个地方

这两处**只是碰巧都用 XML 格式，彼此没有任何关系**。

| 文件 | 给谁看 | 管什么 |
|---|---|---|
| `pom.xml` | Maven（构建工具） | 这个项目怎么构建、依赖哪些库 |
| `resources/mapper/*.xml` | MyBatis（持久层框架） | 某个方法执行什么 SQL |

---

## 三、pom.xml —— 写给 Maven 看的"配方单"

Maven 是构建工具。它需要知道：项目叫什么、依赖哪些第三方库、用哪个 JDK 编译、怎么打包。
这些信息全写在 `pom.xml` 里。

```xml
<dependency>
    <groupId>org.projectlombok</groupId>   <!-- 谁家的 -->
    <artifactId>lombok</artifactId>        <!-- 哪个包 -->
    <version>1.18.30</version>             <!-- 什么版本 -->
</dependency>
```

写下这 5 行，`mvn install` 的时候 Maven 就自动去仓库下载 lombok 1.18.30 的 jar，
塞进编译路径。**这就是它全部的作用**——一份声明式的项目说明书。

> POM = Project Object Model，项目对象模型。

### 两个必须搞清的概念

**`packaging` 为什么父工程要写 `pom`？**

因为父工程不产出代码，它没有 `src/` 目录。`packaging=jar` 会让 Maven 去找源码然后打包，
但父工程根本没有源码。写 `pom` 表示「我只是个聚合器和配置容器」。

**`<dependencies>` 和 `<dependencyManagement>` 的区别？**（Maven 高频面试题）

```
<dependencies>          → 真的引入依赖，子模块无条件继承，全都会有
<dependencyManagement>  → 只是「版本备案」，不引入。
                          子模块要用还得自己声明一遍，但不用写 <version>
```

父工程里**只写 `<dependencyManagement>`** 才是对的。这样：

- `sky-pojo` 声明要 lombok → 自动拿到 1.18.30 版本
- `sky-pojo` 不声明 poi → 它就没有 poi，不会背上一堆用不到的 jar

如果父工程用 `<dependencies>` 写死，`sky-pojo` 也会被塞进 knife4j、阿里云 OSS、POI，纯污染。

> 补充：`spring-boot-starter-parent` 本身就是通过它内部的 `dependencyManagement`
> 管好了 Spring 全家桶的版本，所以你引 `spring-boot-starter-web` 从来不用写版本号。
> 你在 `<properties>` 里定义的版本号，管的是**它没帮你管的第三方依赖**
> （MyBatis、Druid、knife4j 这些）。

---

## 四、mapper/*.xml —— 写给 MyBatis 看的"SQL 仓库"

它干的事是：**把 SQL 语句和 Java 接口方法绑在一起**。

Java 那边你只写接口，**没有实现类**：

```java
public interface EmployeeMapper {
    Employee getByUsername(String username);   // 只有声明，没有方法体
}
```

XML 这边提供这个方法要执行的 SQL：

```xml
<mapper namespace="com.sky.mapper.EmployeeMapper">   <!-- 绑定到哪个接口 -->
    <select id="getByUsername"                       <!-- 绑定到哪个方法 -->
            resultType="com.sky.entity.Employee">    <!-- 查出来装进哪个类 -->
        select * from employee where username = #{username}
    </select>
</mapper>
```

配对规则：

| XML 里的 | 对应 Java 里的 |
|---|---|
| `namespace` | 接口的全限定名（包名 + 类名） |
| `id` | 方法名 |
| `resultType` | 返回值类型 |

MyBatis 启动时靠 `namespace` + `id` 把两边配对，然后**在运行时动态生成这个接口的实现类**。

所以你调 `employeeMapper.getByUsername("admin")`，实际执行的是 XML 里那条 SQL，
返回的 `Employee` 对象也是 MyBatis 帮你把结果集一列一列塞进去的。

---

## 五、动态 SQL：为什么 SQL 要"拼"出来

> 这一节是重点，也是最容易听不懂的地方。先忘掉 XML，从需求说起。

### 问题的来源

**SQL 有个规矩：多个查询条件之间要用 `and` 连起来。**

```sql
select * from employee where 姓名=张三 and 状态=启用
```

现在假设页面上有三个筛选框：**姓名、状态、手机号**。

用户可能只填一个，可能全填，也可能全不填。

**你写代码的时候，不知道用户会填哪几个。**

### 程序员的偷懒办法

给每个条件都**提前配一个 `and`**：

```
条件1： and 姓名=?
条件2： and 状态=?
条件3： and 手机号=?
```

运行的时候，把用户填了的那几条粘起来。

### 于是出问题了

假设**用户只填了「状态」**，粘出来是：

```
and 状态=?
```

放进 SQL 里：

```sql
select * from employee where and 状态=?
                             ↑↑↑
                       where 后面直接跟 and，SQL 报错
```

**开头多出来一个 `and`。**

### `<where>` 就是来擦这个屁股的

`<where>` 干两件事：

1. 里面**一个条件都没成立** → 整个 `WHERE` 不输出
2. **有条件成立** → 输出 `WHERE`，并把紧跟在后面的那个 `and`（或 `or`）**删掉**

```sql
select * from employee where 状态=?     ✅ 正常了
```

**它是个收尾的清理工。就这么一件事。**

### 为什么每个条件都要写 `and`（关键）

**因为你在写代码时，不知道运行时哪个条件会是"第一个"。**

```xml
<where>
    <if test="name != null">   and name   like ...    </if>
    <if test="status != null"> and status = #{status} </if>
    <if test="phone != null">  and phone  = #{phone}  </if>
</where>
```

运行时的各种可能：

| 用户传了什么 | 粘出的原始片段 | `<where>` 处理后 |
|---|---|---|
| 三个都传 | `and name... and status... and phone...` | `WHERE name... and status... and phone...` |
| 只传 status | `and status...` | `WHERE status...` |
| 只传 phone | `and phone...` | `WHERE phone...` |
| 都不传 | 空 | （没有 WHERE） |

**每种情况下"第一个"是谁都不一样。** 如果约定「第一个不写 and，后面的写」，
你根本没法写——`name` 可能是第一个，也可能根本不出现。

所以约定反过来：**所有条件统统写 `and`，最后由 `<where>` 把开头那个多余的删掉。**
这样每个 `<if>` 长得一模一样，互相独立，加减条件都不用改别人。

⚠️ **`<where>` 只删开头那一个**，中间的 `and` 全保留——不然条件就连不起来了。

### 实测证据

XML 源码（`EmployeeMapper.xml` 第 18-26 行）：

```xml
<select id="pageQuery" resultType="com.sky.entity.Employee">
    select * from employee
    <where>
        <if test="name != null and name != ''">
            and name like concat('%',#{name},'%')
        </if>
    </where>
</select>
```

打开 MyBatis 的 SQL 日志，实际执行的语句：

| 请求 | 真实执行的 SQL |
|---|---|
| 不传 name | `select * from employee LIMIT ?` ← **连 WHERE 都没有** |
| 传 name=管 | `select * from employee WHERE name like concat('%',?,'%') LIMIT ?` ← **`and` 不见了** |

如果没有 `<where>`，只是原样拼字符串，会得到：

| 情况 | 拼出来的 SQL | 结果 |
|---|---|---|
| 不传 name | `select * from employee WHERE` | ❌ 语法错误，空的 WHERE |
| 传 name | `select * from employee WHERE and name like ...` | ❌ WHERE 后面直接跟 and |

---

## 六、`<set>` —— 同一个思路，换成逗号

`update` 语句里，要改的字段之间用**逗号**分隔：

```sql
update employee set 姓名=?, 手机号=? where id=?
```

同样不知道用户要改哪几个字段，所以**每个字段后面都提前配一个逗号**：

```xml
update employee
<set>
    <if test="name != null">name = #{name},</if>
    <if test="phone != null">phone = #{phone},</if>
    <if test="status != null">status = #{status},</if>
</set>
where id = #{id}
```

粘完之后**末尾必然多一个逗号**：

```sql
update employee set 姓名=?, where id=?
                          ↑ 多余的逗号，语法错误
```

`<set>` 就把末尾那个逗号删掉。

### 实测证据

只传 `name` 一个字段，实际执行：

```sql
update employee SET name = ?, update_Time = ?, update_User = ? where id = ?
```

**末尾逗号被删掉了**，`where` 正常接上。

（`update_Time`、`update_User` 是项目里的**公共字段自动填充切面**加的，day03 会学。）

### 顺带验证了动态 update 的价值

实测前后对比：只传了 `name`，`phone` 和 `status` **没有被清成 null**，它们压根没进 SQL。

要是不用 `<if>`、把所有字段都写死：

```sql
update employee set name=?, phone=?, status=? where id=?
```

那前端没传的字段就是 `null`，会**把数据库里原有的值覆盖掉**。这是新手高频坑。

---

## 七、`<where>` 和 `<set>` 的本质：都是 `<trim>`

这两个标签是 `<trim>` 的语法糖：

```xml
<where>  等价于  <trim prefix="WHERE" prefixOverrides="AND |OR ">
<set>    等价于  <trim prefix="SET"   suffixOverrides=",">
```

`<trim>` 的四个属性：

| 属性 | 作用 |
|---|---|
| `prefix` | 有内容才在**前面**加这个词 |
| `suffix` | 有内容才在**后面**加这个词 |
| `prefixOverrides` | 把内容**开头**匹配到的这些词删掉（多个用 `\|` 分隔） |
| `suffixOverrides` | 把内容**末尾**匹配到的这些词删掉 |

知道这个，以后遇到别的拼接场景（比如 `insert` 的字段列表和值列表）就能用 `<trim>`
自己定规则。面试可以多说这一句。

---

## 八、安全：`#{}` 和 `${}` 的致命区别

长得像，差别是致命的：

| 写法 | 行为 | 安全性 |
|---|---|---|
| `#{username}` | 生成 `?` 占位符，走**预编译参数绑定** | ✅ 安全 |
| `${username}` | **直接字符串替换**进 SQL | ❌ SQL 注入 |

`#{}` 传进去的值永远被当成**数据**，不会被解析成 SQL 语法。
所以有人传 `admin' or '1'='1`，它就老老实实去查一个名叫 `admin' or '1'='1` 的用户，查不到。

`${}` 只在少数场景不得不用（动态表名、`order by` 的列名——这些位置 SQL 语法上
不允许用占位符）。**用到 `${}` 就必须自己做白名单校验。**

> **项目里能用 `#{}` 就绝不用 `${}`。**
> 这是面试高频题，答的时候要说出**「预编译」**这个词。

---

## 九、常用动态 SQL 标签速查

| 标签 | 用途 |
|---|---|
| `<if test="...">` | 条件成立才输出里面的内容 |
| `<where>` | 自动加 WHERE，删开头多余的 and/or |
| `<set>` | 自动加 SET，删末尾多余的逗号 |
| `<trim>` | 上面两个的通用版，自己定规则 |
| `<foreach>` | 遍历集合，用于 `in (1,2,3)` 和批量插入 |
| `<choose>` / `<when>` / `<otherwise>` | 相当于 switch-case，只走一个分支 |
| `<sql>` + `<include>` | 抽取可复用的 SQL 片段 |

---

## 十、一句话总结

- **`pom.xml`** 管「这个项目怎么构建」
- **`mapper/*.xml`** 管「这个方法执行什么 SQL」
- **动态 SQL 的核心矛盾**：不知道运行时会用到哪几个条件，所以给每个条件都配上连接符号
  （`and` 或逗号）；粘完之后开头或末尾必然多出来一个，
  **`<where>` 和 `<set>` 负责把多出来的那个删掉**
- **`#{}` 安全，`${}` 会 SQL 注入**
