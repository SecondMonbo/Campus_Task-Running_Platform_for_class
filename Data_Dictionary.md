# 校园跑腿系统数据字典

## 说明

本字典以需求规格说明书中的字段和已确认澄清为依据，用于统一用例输入、输出字段。`可空`表示该字段在相应业务记录中是否允许为空；来源编号均为原有 `XYP-SRS` 编号。英文名仅作为原用例中变量名的统一别名。

普通账号按已确认的规则可同时作为发布者和跑腿员。管理员仍是独立参与者；需求尚未规定管理员账号的创建/授权方式。文档中关于自动验收、虚拟币充值/小费/金额精度、取消补偿计算及用户需求来源清单仍有待需求方确认，相关字段保留原文出现过的内容，不表示这些规则已经确认。

## User（用户）

| 字段名（别名） | 类型 | 说明 | 约束 | 可空 | 来源需求编号 |
|---|---|---|---|---|---|
| user_id（account） | VARCHAR | 用户账号/用户标识 | 长度 4–12；唯一；区分大小写；允许字母、数字、`-`、`_`；0001–1000为原字典规定的系统保留号段 | 否 | XYP-SRS-1.1.0、1.2.0、1.3.0、1.4.0、1.5.0、1.6.0、8.2.0、9.5.0、9.6.0 |
| password_hash（password） | VARCHAR | 加密保存的登录凭据 | 用户输入长度按原数据字典为 6–20；禁止明文持久化 | 否 | XYP-SRS-1.1.0、1.2.0、9.5.0 |
| real_name（name） | VARCHAR | 姓名 | 长度 2–20（原字典） | 是 | XYP-SRS-1.1.0、1.4.0、8.2.0 |
| campus_id（student_no/staff_no） | VARCHAR | 学号或工号 | 原注册字典要求学号13位；工号格式未定义 | 是 | XYP-SRS-1.1.0、8.2.0 |
| phone（mobile） | CHAR | 手机号 | 11 位；是否强制唯一按原注册结果字段校验 | 是 | XYP-SRS-1.1.0、1.4.0、8.2.0 |
| contact | VARCHAR | 联系方式 | 字段长度和格式未在需求中规定 | 是 | XYP-SRS-1.4.0、8.2.0 |
| role_codes（roles） | 角色集合 | 用户可执行的系统角色 | 仅包含发布者、跑腿员、管理员；普通账号同时拥有发布者与跑腿员能力（用户已确认）；管理员授权机制未定义 | 否 | XYP-SRS-1.1.0、1.6.0、8.1.0 |
| failed_login_count | INTEGER | 连续登录失败次数 | 非负；达到 3 次触发账号锁定 | 否 | XYP-SRS-1.2.0、1.3.0 |
| locked_until | DATETIME | 登录锁定解除时刻 | 锁定时为当前时间后 30 分钟；未锁定时为空 | 是 | XYP-SRS-1.2.0、1.3.0 |
| session_expires_at | DATETIME | 登录态过期时刻 | 成功登录后最长 7 天；主动退出后会话失效 | 是 | XYP-SRS-1.5.0、9.6.0 |

| updated_at（修改时间） | DATETIME | 个人资料修改时间 | 系统记录修改时刻 | 条件可空 | XYP-SRS-1.4.0 |

## Wallet（虚拟币账户）

| 字段名（别名） | 类型 | 说明 | 约束 | 可空 | 来源需求编号 |
|---|---|---|---|---|---|
| user_id | VARCHAR | 账户所属用户 | 外键引用 User.user_id；每个账户对应一个用户 | 否 | XYP-SRS-2.1.0、4.3.0、6.1.0、6.4.0 |
| available_balance（balance） | DECIMAL/INTEGER | 可用虚拟币余额 | 非负；原文同时出现整数与保留两位小数要求，精度待确认 | 否 | XYP-SRS-2.1.0、4.3.0、6.1.0、6.2.0、6.3.0、6.4.0 |
| frozen_balance | DECIMAL/INTEGER | 已冻结虚拟币余额 | 非负；发布任务冻结金额，取消/结算处理规则待确认 | 否 | XYP-SRS-2.1.0、4.3.0、6.4.0、7.2.0 |

## Task（任务）

| 字段名（别名） | 类型 | 说明 | 约束 | 可空 | 来源需求编号 |
|---|---|---|---|---|---|
| task_id（order_id） | VARCHAR | 任务唯一编号；原用例中的订单号 | 系统生成；唯一 | 否 | XYP-SRS-2.1.0、2.2.0、2.3.0、2.4.0、3.1.0、3.2.0、3.3.0、3.4.0、3.5.0、4.1.0、4.2.0、4.3.0、4.4.0、4.5.0、6.1.0、6.3.0、6.4.0、7.1.0–7.5.0、8.3.0 |
| publisher_id（user_id） | VARCHAR | 发布者账号 | 外键引用 User.user_id | 否 | XYP-SRS-2.1.0、2.2.0、4.2.0、4.5.0 |
| runner_id | VARCHAR | 接单跑腿员账号 | 外键引用 User.user_id；待接单时为空 | 是 | XYP-SRS-3.2.0、3.3.0、3.4.0、3.5.0、4.1.0、4.3.0、4.5.0 |
| title | VARCHAR | 任务标题 | 长度 1–50 | 否 | XYP-SRS-2.1.0、3.1.0、4.1.0 |
| task_description | VARCHAR | 任务描述 | 长度 0–200 | 是 | XYP-SRS-2.1.0 |
| fee（reward_amount） | DECIMAL/INTEGER | 跑腿报酬 | 功能要求大于0，原字典上限1000；币值精度冲突待确认 | 否 | XYP-SRS-2.1.0、4.3.0、6.1.0、6.4.0 |
| tip | DECIMAL/INTEGER | 小费 | 原数据字典范围0–100、缺省0；2.1.0 未说明该字段且 6.4.0 使用该字段，是否纳入本期待确认 | 是 | XYP-SRS-6.1.0、6.4.0 |
| deadline | DATETIME | 最晚送达时间 | 必须晚于发布时刻 | 否 | XYP-SRS-2.1.0、3.1.0 |
| pickup_location | VARCHAR | 取件地点 | 长度 1–100 | 否 | XYP-SRS-2.1.0、3.1.0 |
| delivery_location | VARCHAR | 送达地点 | 长度 1–100 | 否 | XYP-SRS-2.1.0、3.1.0 |
| status | ENUM | 任务状态 | 仅待接单、进行中、待验收、已完成、已取消五种；与第7模块出现的争议/已裁决等状态存在冲突，分开记录仅为待确认建议 | 否 | XYP-SRS-2.2.0、2.3.0、3.1.0、3.3.0、3.4.0、3.5.0、4.2.0、7.1.0–7.4.0 |
| created_at | DATETIME | 任务发布时间 | 系统记录 | 否 | XYP-SRS-2.1.0、2.4.0、3.1.0 |
| accepted_at | DATETIME | 接单时间 | 接单前为空 | 是 | XYP-SRS-3.2.0、3.3.0 |
| delivered_at | DATETIME | 跑腿员确认送达时间 | 送达前为空 | 是 | XYP-SRS-3.5.0、4.1.0 |
| completed_at | DATETIME | 发布者确认完成时间 | 完成前为空；自动确认规则待确认 | 是 | XYP-SRS-2.4.0、4.2.0、4.3.0、4.4.0、5.1.0、6.1.0 |

## Order（订单）

需求正文以“任务”和“订单”描述同一跑腿业务记录，`order_id` 是 `task_id` 的既有别名。本阶段将 Order 作为逻辑视图定义，不据此断言必须拆分成独立存储表。

| 字段名（别名） | 类型 | 说明 | 约束 | 可空 | 来源需求编号 |
|---|---|---|---|---|---|
| order_id（task_id） | VARCHAR | 订单/任务编号 | 与 Task.task_id 一一对应 | 否 | XYP-SRS-3.2.0、3.5.0、4.2.0、4.3.0、6.1.0、6.3.0、7.1.0–7.5.0 |
| publisher_id | VARCHAR | 发布者账号 | 对应 Task.publisher_id | 否 | XYP-SRS-2.1.0、4.2.0、7.1.0 |
| runner_id | VARCHAR | 跑腿员账号 | 对应 Task.runner_id；待接单时为空 | 是 | XYP-SRS-3.2.0、3.3.0、3.5.0、4.3.0 |
| status | ENUM | 当前任务状态 | 取值应与 Task.status 对齐；第7模块冲突的状态映射尚未确认 | 否 | XYP-SRS-2.3.0、3.3.0、3.5.0、4.2.0、7.1.0–7.4.0 |

## Transaction（交易流水）

| 字段名（别名） | 类型 | 说明 | 约束 | 可空 | 来源需求编号 |
|---|---|---|---|---|---|
| txn_id | VARCHAR | 交易流水号 | 系统生成；唯一 | 否 | XYP-SRS-4.3.0、4.4.0、6.1.0、6.3.0、8.4.0 |
| user_id | VARCHAR | 流水所属用户 | 外键引用 User.user_id | 否 | XYP-SRS-4.3.0、6.1.0、6.3.0、8.4.0 |
| order_id（task_id） | VARCHAR | 关联任务/订单编号 | 外键/逻辑引用 Task.task_id；非任务类流水可空，是否存在此类流水待确认 | 是 | XYP-SRS-4.3.0、6.1.0、6.3.0、7.2.0、7.3.0、8.4.0 |
| type | ENUM | 流水类型 | 入账、支出、退款、罚款、补偿、冻结、解冻；退款在 6.3.0 筛选项出现 | 否 | XYP-SRS-4.3.0、4.4.0、6.1.0、6.3.0、7.2.0、7.3.0 |
| amount | DECIMAL/INTEGER | 本次变动金额 | 正数入账、负数出账；精度待确认 | 否 | XYP-SRS-4.3.0、6.1.0、6.3.0、7.2.0、7.3.0 |
| balance_after | DECIMAL/INTEGER | 变动后的账户余额 | 非负；精度待确认 | 否 | XYP-SRS-6.1.0、6.2.0、6.3.0 |
| trade_time | DATETIME | 交易时间 | 系统记录 | 否 | XYP-SRS-4.3.0、6.1.0、6.3.0、8.4.0 |
| result | VARCHAR | 交易处理结果 | 成功/失败；重试及人工处理阶段状态待确认 | 否 | XYP-SRS-4.3.0、6.1.0、7.2.0、7.3.0 |

## Evaluation（评价）

| 字段名（别名） | 类型 | 说明 | 约束 | 可空 | 来源需求编号 |
|---|---|---|---|---|---|
| evaluation_id | VARCHAR | 评价编号 | 系统生成；唯一 | 否 | XYP-SRS-4.5.0、5.1.0、5.2.0 |
| task_id（order_id） | VARCHAR | 评价关联的任务 | 仅已完成任务可评价；每任务仅评价一次 | 否 | XYP-SRS-4.5.0、5.1.0、5.2.0 |
| evaluator_id | VARCHAR | 评价人账号 | 外键引用 User.user_id；该用例为发布者 | 否 | XYP-SRS-4.5.0、5.1.0 |
| evaluated_user_id | VARCHAR | 被评价人账号 | 外键引用 User.user_id；该用例为跑腿员 | 否 | XYP-SRS-4.5.0、5.1.0、5.2.0 |
| score | INTEGER | 星级评分 | 1–5；原字典缺省5 | 否 | XYP-SRS-4.5.0、5.1.0、5.2.0 |
| content | VARCHAR | 文字评价 | 长度 0–200 | 是 | XYP-SRS-4.5.0、5.1.0、5.2.0 |
| evaluation_time | DATETIME | 评价时间 | 系统记录 | 否 | XYP-SRS-4.5.0、5.1.0 |

## Complaint（投诉）

| 字段名（别名） | 类型 | 说明 | 约束 | 可空 | 来源需求编号 |
|---|---|---|---|---|---|
| complaint_id | VARCHAR | 投诉编号 | 系统生成；唯一 | 否 | XYP-SRS-8.5.0、9.4.0 |
| complainant_id | VARCHAR | 投诉人账号 | 外键引用 User.user_id | 否 | XYP-SRS-9.4.0 |
| target_user_id | VARCHAR | 被投诉人账号 | 外键引用 User.user_id；若原投诉不涉及特定用户则为空，需求未规定例外 | 是 | XYP-SRS-8.5.0、9.4.0 |
| complaint_type | ENUM | 投诉类型 | 未送达、物品损坏、超时、其他 | 否 | XYP-SRS-9.4.0 |
| complaint_description | VARCHAR | 投诉说明 | 长度 0–500（原数据字典） | 否 | XYP-SRS-9.4.0 |
| handling_result | VARCHAR | 管理员处理结果 | 长度 0–500；处理前为空 | 是 | XYP-SRS-8.5.0、9.4.0 |
| complaint_time | DATETIME | 投诉提交时间 | 系统记录 | 否 | XYP-SRS-9.4.0 |

## Notification（站内通知）

| 字段名（别名） | 类型 | 说明 | 约束 | 可空 | 来源需求编号 |
|---|---|---|---|---|---|
| notification_id | VARCHAR | 通知编号 | 系统生成；唯一 | 否 | XYP-SRS-4.1.0、7.1.0、7.2.0、9.1.0、9.2.0 |
| recipient_id | VARCHAR | 接收人账号 | 外键引用 User.user_id | 否 | XYP-SRS-4.1.0、7.1.0、7.2.0、9.1.0、9.2.0 |
| event_type | VARCHAR | 触发通知的事件类型 | 来源于已定义的送达、取消申请/应答、结算或裁决等流程 | 否 | XYP-SRS-4.1.0、6.1.0、7.1.0、7.2.0、7.3.0、9.1.0 |
| title | VARCHAR | 通知标题 | 长度未规定 | 是 | XYP-SRS-4.1.0、9.1.0、9.2.0 |
| content | VARCHAR | 通知内容 | 长度未规定；使用对应需求描述的提示内容 | 否 | XYP-SRS-4.1.0、6.1.0、7.1.0、7.2.0、7.3.0、9.1.0、9.2.0 |
| task_id（order_id） | VARCHAR | 关联任务/订单 | 可为空；有关联任务时对应 Task.task_id | 是 | XYP-SRS-4.1.0、6.1.0、7.1.0、7.2.0、7.3.0、9.1.0 |
| read_status | ENUM | 阅读状态 | 未读/已读 | 否 | XYP-SRS-9.2.0 |
| created_at | DATETIME | 通知产生时间 | 系统记录 | 否 | XYP-SRS-9.1.0、9.2.0 |

## OperationLog（操作日志）

| 字段名（别名） | 类型 | 说明 | 约束 | 可空 | 来源需求编号 |
|---|---|---|---|---|---|
| log_id | VARCHAR | 日志编号 | 系统生成；唯一 | 否 | XYP-SRS-1.6.0、7.3.0、7.4.0、8.6.0、9.3.0 |
| operator_id（user_id） | VARCHAR | 操作人账号 | 外键引用 User.user_id；系统操作可空 | 是 | XYP-SRS-1.6.0、7.3.0、7.4.0、9.3.0 |
| operation_type | VARCHAR | 操作类型 | 原字典包含发布、接单、送达、确认、取消；登录、权限拒绝、裁决、投诉处理等按对应需求记录 | 否 | XYP-SRS-1.6.0、7.3.0、7.4.0、9.3.0 |
| operation_content | VARCHAR | 操作内容 | 长度 0–500 | 否 | XYP-SRS-1.6.0、7.3.0、7.4.0、9.3.0 |
| operation_time | DATETIME | 操作时间 | 系统记录 | 否 | XYP-SRS-1.6.0、9.3.0 |
| operation_ip | VARCHAR | 操作来源 IP | 长度 0–45 | 是 | XYP-SRS-9.3.0 |

## CancellationRequest（取消/争议处理记录）

| 字段名（别名） | 类型 | 说明 | 约束 | 可空 | 来源需求编号 |
|---|---|---|---|---|---|
| cancel_id | VARCHAR | 取消申请编号 | 系统生成；唯一 | 否 | XYP-SRS-7.1.0、7.2.0、7.5.0 |
| task_id（order_id） | VARCHAR | 关联任务/订单编号 | 对应 Task.task_id | 否 | XYP-SRS-7.1.0–7.5.0 |
| cancel_by | ENUM | 申请人/发起方 | 发布者、跑腿员；原字典还出现系统发起 | 否 | XYP-SRS-7.1.0、7.5.0 |
| cancel_reason | VARCHAR | 取消原因 | 长度0–200；事件流要求必填，选项为不需要了/联系不上/时间冲突/其他 | 否 | XYP-SRS-7.1.0、7.5.0 |
| request_status | ENUM | 取消申请处理状态 | 待应答已见原文；应答后、争议及已裁决的状态归属与枚举待确认，不能直接作为任务状态新增值 | 否 | XYP-SRS-7.1.0、7.2.0、7.3.0 |
| reply_result | ENUM | 被通知方应答 | 同意/拒绝 | 是 | XYP-SRS-7.2.0 |
| reply_note | VARCHAR | 应答说明 | 可选；长度未规定 | 是 | XYP-SRS-7.2.0 |
| refund_amount | DECIMAL/INTEGER | 退款金额 | 非负；精度和计算规则待确认 | 是 | XYP-SRS-7.1.0、7.2.0、7.3.0、7.5.0 |
| compensate_amount | DECIMAL/INTEGER | 补偿金额 | 非负；计算规则待确认 | 是 | XYP-SRS-7.3.0、7.4.0 |
| ruling | VARCHAR | 管理员裁决结论 | 原事件流为支持申诉方/支持被申诉方/各担一定比例，原字典为各担一半，两者差异待确认 | 是 | XYP-SRS-7.3.0 |
| handling_note | VARCHAR | 处理/裁决说明 | 强制关闭时必须填写；其余长度未规定 | 是 | XYP-SRS-7.3.0、7.4.0 |
| handled_at | DATETIME | 处理时间 | 处理前为空 | 是 | XYP-SRS-7.3.0、7.5.0 |


## Request（非持久化操作输入）

本组描述交互参数，不要求将明文密码或会话凭据记入业务数据表。标识字段由登录态确定，不能直接信任客户端冒用的账号。

| 字段名（别名） | 类型 | 说明 | 约束 | 可空 | 来源需求编号 |
|---|---|---|---|---|---|
| password | STRING | 注册/登录时输入的密码 | 原登录字典6–12位、注册字典6–20位存在差异，须确认统一长度；不可写入日志 | 否 | XYP-SRS-1.1.0、1.2.0、8.1.0 |
| old_password | STRING | 修改密码时验证的原密码 | 与当前用户凭据核验；不持久化明文 | 否 | XYP-SRS-1.4.0、9.5.0 |
| new_password | STRING | 新密码输入 | 格式约束待统一；加密保存 | 否 | XYP-SRS-1.4.0、9.5.0 |
| confirm_password | STRING | 再次输入的新密码 | 必须与new_password一致；不持久化 | 否 | XYP-SRS-1.4.0、9.5.0 |
| session_id | STRING | 会话凭据的逻辑标识 | 对应有效登录态；具体实现留待设计 | 条件可空 | XYP-SRS-1.5.0、1.6.0、8.1.0、9.6.0 |
| operation | STRING | 请求的业务操作 | 限于43条既有功能对应的操作 | 否 | XYP-SRS-1.5.0、1.6.0、2.3.0、4.4.0、9.6.0 |
| acceptance_result | ENUM | 发布者验收结果 | 原字典为确认完成/有异议；异议处理规则须与第7模块统一 | 否 | XYP-SRS-4.2.0 |
| handle_type | ENUM | 异常订单处理方式 | 原7.4.0列强制关闭/继续等待/转派；适用条件及费用规则待确认 | 否 | XYP-SRS-7.4.0 |
| handle_note（ruling_detail） | STRING | 处理或裁决说明 | 对应CancellationRequest.handling_note | 条件可空 | XYP-SRS-7.3.0、7.4.0 |

## Query（查询与统计字段）

| 字段名（别名） | 类型 | 说明 | 约束 | 可空 | 来源需求编号 |
|---|---|---|---|---|---|
| start_date | DATE/DATETIME | 查询开始时间 | 不得晚于结束时间；收益统计默认当月，交易明细默认近30天 | 条件可空 | XYP-SRS-2.4.0、6.2.0、6.3.0、7.5.0、8.3.0、8.4.0、8.6.0 |
| end_date | DATE/DATETIME | 查询结束时间 | 不得早于开始时间；6.2.0原文限定跨度不超过1年 | 条件可空 | XYP-SRS-2.4.0、6.2.0、6.3.0、7.5.0、8.3.0、8.4.0、8.6.0 |
| page_no | INTEGER | 请求的列表页序号 | 正整数 | 是 | XYP-SRS-3.1.0 |
| page_size | INTEGER | 每页记录数 | 按5.5与8.2.1默认为10，可选20或50 | 是 | XYP-SRS-3.1.0 |
| total_count | INTEGER | 匹配记录总数 | 非负；查询输出 | 否 | XYP-SRS-3.1.0 |
| order_count | INTEGER | 累计完成订单数 | 非负；按本人范围统计 | 否 | XYP-SRS-6.2.0 |
| total_amount | DECIMAL/INTEGER | 累计入账金额 | 原文币值精度待确认 | 否 | XYP-SRS-6.2.0 |
| payable | DECIMAL/INTEGER | 本次应付金额 | 原6.4.0为fee+tip，小费范围和精度待确认 | 否 | XYP-SRS-6.4.0 |
| diff | DECIMAL/INTEGER | 可用余额与应付金额的不足差额 | 余额不足时显示；精度待确认 | 否 | XYP-SRS-6.4.0 |

## Response（通用操作结果）

| 字段名（别名） | 类型 | 说明 | 约束 | 可空 | 来源需求编号 |
|---|---|---|---|---|---|
| result_code | STRING/ENUM | 注册、登录、接单、校验、查询或处理结果 | 成功/失败；登录可包括锁定；结果以实际保存状态为准 | 否 | XYP-SRS-1.1.0至9.6.0的既有43条功能（按各用例输出适用） |
| message | STRING | 操作结果、失败原因或异常提示 | 不包含密码等敏感凭据；具体编码及接口格式留待设计 | 是 | XYP-SRS-1.1.0至9.6.0的既有43条功能（按各用例输出适用） |

## 原文字段别名映射

原用例的balance对应Wallet.available_balance，status需结合上下文区分Task.status与CancellationRequest.request_status；cancel_reason、cancel_by、reply_note、refund_amount、compensate_amount、ruling分别对应取消/争议记录的同名字段；handle_time对应handled_at；type对应Transaction.type；订单号order_id对应Task.task_id/Order.order_id。原“跑腿者”统一为“跑腿员”，不新增角色。

本字典标注“待确认”或“未规定”的条目属于需求问题，不得视为已经批准的数据库类型、状态约束或新业务规则。每个SRS用例的输入输出以本字典及对应条目共同解释。

原始数据约束留存说明：原登录字典含在线/离线/隐身/忙等聊天状态及MYQQ编号，已识别为异项目模板残留，不作为校园跑腿功能。原密码字段缺省值为123456，本稿不将其设置为批准的默认凭据，须在密码规则确认时一并处理。原密码字符范围为字母、数字、-、_、!、@、#、$、%、^、*、(、)；原文全角符号与半角符号写法的处理尚未规定。验收字段“确认人”对应当前发布者的User.user_id；原注册、接单和校验结果分别归入Response.result_code与message。
