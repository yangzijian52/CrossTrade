# CrossTrade 2.2.1

CrossTrade 是面向 Paper 26.2 的安全交易插件，包含面对面交易和持久化玩家市场。Java 玩家使用箱子 GUI；安装 Floodgate 后，基岩玩家使用表单浏览、上架和购买。

## 运行要求

- Paper 26.2
- Java 25
- Vault 与兼容经济插件：执行金钱交易时必需
- Floodgate：可选，只用于基岩版表单

没有 Vault 时插件和市场仍可加载、浏览，但购买、货款领取和面对面金钱交易会被拒绝。没有 Floodgate 时不会加载 Cumulus 表单类，Java 功能保持可用。

## 发布验证

CrossTrade 2.2.0 已于 2026-07-11 在 Paper 26.2 目标服务器完成实际加载和功能验收，服务器内实测通过。CrossTrade 2.2.1 是 SpigotMC 文档、许可证和发布元数据补全版本，运行逻辑与 2.2.0 保持一致，并重新完成构建与自动化测试。

- Java 25 / Paper 26.2 API 编译成功
- Maven `clean package` 成功
- 9 项自动化测试通过，0 失败
- SQLite JDBC 已打入最终 JAR
- Paper、Vault、Floodgate API 保持 provided，未误打入插件
- `config.yml`、`gui.yml` 和中文物品名称资源完成 UTF-8 与解析检查

## SpigotMC 发布资料

本项目按免费资源准备 SpigotMC 发布资料，许可证为 MIT。

- 手动发布清单：`docs/SPIGOTMC_MANUAL_PUBLISHING.md`
- SpigotMC 资源页 BBCode：`docs/SPIGOTMC-RESOURCE.md`
- SpigotMC 文档说明 BBCode：`docs/SPIGOTMC-RESOURCE-BBCODE.txt`
- 变更记录：`CHANGELOG.md`
- 许可证：`LICENSE`

SpigotMC 资源页和支持渠道按英文维护，不提供中文支持；服务器管理员仍可自行修改 `config.yml`、`gui.yml` 中的玩家提示和 GUI 文案。

## 主要功能

### 面对面交易

- 交易中心第 22 格提供“玩家交易”，可浏览全部在线玩家并远程发送交易请求
- `/trade <玩家>` 或潜行右键玩家发起请求
- 唯一请求 ID、请求冷却和过期校验
- 54 格左右对称 GUI，金额支持 `+1/+10/+100/+1000` 和 `-1/-10/-100/-1000`
- 物品和金额任意变化都会撤销双方确认
- 单一倒计时任务，变化时只停止倒计时
- 最终成交前重新检查余额与双方背包空间
- 双向金额先按净额结算
- 关闭、退出、死亡、切换世界和停服时安全返还
- 阻止 Shift 点击、拖拽、数字键、双击、丢弃和副手交换等 GUI 旁路

### 玩家市场

- 交易广场按卖家头像聚合
- 玩家店铺、分页、卖家搜索和商品排序服务
- 背包选品、商品名、数量、单价、1–10 天上架时间和最终确认
- 单价出售并支持分批购买
- 到期、售罄和主动下架；主动下架后可补货或重新选择 1–10 天上架
- 下架商品支持单条删除或批量清除；仍在托管的剩余物品会进入待领取邮箱
- 我的上架、待领取物品、货款保障和交易记录；交易记录可按玩家侧清除显示
- Java 箱子 GUI 与 Floodgate 基岩表单
- 管理员检查、强制下架、复核、补偿和在线数据库备份

## 物品安全

市场以 Paper `ItemStack.serializeAsBytes()` 保存完整原型，并保存 SHA-256 指纹。数量单独保存，交付时按物品最大堆叠数拆分。

“同一种物品”使用 `ItemStack.isSimilar`：耐久、附魔、名称、Lore、属性、PDC、自定义模型和数据组件不同的物品不会合并。内容不同的潜影盒也不会合并。

启动时插件会执行潜影盒与 PDC 编解码自检。有内容的潜影盒在购买前可打开只读预览，预览界面禁止任何拿取或拖拽。

背包放不下的返还物品永不掉落到世界，而是写入 `mailbox_items`，通过 `/market mailbox` 领取。

## 交易一致性

市场购买按以下状态推进：

`PREPARED → WITHDRAWING → DEBITED → COMPLETE`

- 商品使用版本号更新和单商品锁，避免并发超卖。
- 扣款失败会释放库存。
- 物品发放失败会恢复背包快照并退款。
- 退款失败会建立待退款记录并将交易标记为 `REVIEW`。
- 卖家货款先写入不可变账本，再通过 Vault 支付。
- 服务器在付款中退出时，`PAYING` 会在下次启动改为 `REVIEW`，不会盲目重复付款。
- 未扣款的 `PREPARED` 预留会在启动时安全释放。

SQLite 启用外键、WAL、FULL synchronous 和 busy timeout。在线备份使用 SQLite `VACUUM INTO`，避免只复制主数据库而漏掉 WAL 内容。

## 安装

1. 安装 Java 25 和 Paper 26.2。
2. 安装 Vault 与经济插件。
3. 如需基岩表单，安装兼容版本的 Floodgate。
4. 将 `CrossTrade-2.2.1.jar` 放入服务器 `plugins`。
5. 完整重启服务器。
6. 首次启动后检查 `plugins/CrossTrade/config.yml` 和控制台自检信息。

不要使用 `/reload` 或第三方插件热卸载 CrossTrade。`/market admin reload` 只重载安全配置，不重建数据库或线程。

## 玩家命令

| 命令 | 说明 |
|---|---|
| `/market help` | 打印全部玩家与管理员命令的中文说明 |
| `/market` | 打开交易中心 |
| `/market plaza` | 打开按卖家排列的交易广场 |
| `/market sell` | 上架背包物品 |
| `/market mine` | 查看和下架自己的商品 |
| `/market mailbox` | 领取到期、下架或异常返还物品 |
| `/market earnings` | 查看货款保障并领取未自动到账的款项 |
| `/market history` | 查看购买和售出记录 |
| `/market search <玩家名>` | 搜索卖家 |
| `/market seller <玩家> [商品名]` | 打开指定店铺并按商品名筛选 |
| `/trade <玩家>` | 发起面对面交易 |
| `/trade market` | 打开玩家市场 |
| `/tradeaccept` | 接受当前有效请求 |
| `/tradedeny` | 拒绝当前有效请求 |

`/market` 默认别名为 `/ah`、`/pm`；`/trade` 默认别名为 `/ct`、`/crosstrade`。

## 管理员命令

| 命令 | 说明 |
|---|---|
| `/market admin reload` | 重载安全配置项 |
| `/market admin inspect <商品ID>` | 显示商品状态、卖家、数量、价格和指纹 |
| `/market admin remove <商品ID>` | 强制下架并把剩余物品写入卖家邮箱 |
| `/market admin seller <玩家>` | 查看指定卖家的有效商品 |
| `/market admin history <玩家>` | 查询最近交易 |
| `/market admin mailbox <玩家>` | 查询邮箱记录数 |
| `/market admin payouts <玩家>` | 查询待领取货款 |
| `/market admin review` | 列出模糊交易和上架草稿 |
| `/market admin compensate item <玩家> <数量>` | 以管理员主手物品创建邮箱补偿 |
| `/market admin compensate money <玩家> <金额>` | 创建待领取货款补偿 |
| `/market admin backup` | 创建 SQLite 一致性备份 |

## 权限

| 权限 | 默认值 | 说明 |
|---|---|---|
| `crosstrade.use` | true | 兼容总权限 |
| `crosstrade.direct` | true | 面对面交易 |
| `crosstrade.market.use` | true | 浏览市场 |
| `crosstrade.market.buy` | true | 购买商品 |
| `crosstrade.market.sell` | true | 上架商品 |
| `crosstrade.market.listings.10` | true | 10 个有效上架位 |
| `crosstrade.market.admin` | op | 市场管理 |
| `crosstrade.market.bypass.blacklist` | op | 绕过物品规则 |
| `crosstrade.market.bypass.price-limit` | op | 绕过价格范围 |

可以添加 `crosstrade.market.listings.20` 等权限增加指定玩家的上架上限，插件取其拥有节点中的最大数字。

## 上架流程

Java 玩家：

1. `/market sell`。
2. 在只读背包镜像中选择物品。
3. 聊天输入市场商品名。
4. GUI 选择数量或聊天输入自定义数量。
5. 聊天输入单价。
6. GUI 选择 1–10 天。
7. 最终确认。

最终确认时才重新扫描并扣除背包中的完整相似物品。任何字段失效或物品数量变化都会拒绝上架。

基岩玩家在物品选择后使用 CustomForm 输入商品名、数量、单价和天数，再使用 ModalForm 二次确认。

## 数据文件

默认位置：

- 数据库：`plugins/CrossTrade/data.db`
- 备份：`plugins/CrossTrade/backups/`
- 审计日志：`plugins/CrossTrade/logs/transactions.log`
- 主配置：`plugins/CrossTrade/config.yml`
- GUI 配置：`plugins/CrossTrade/gui.yml`

主要表：

- `listings`：商品及托管物品
- `sales`：购买事务
- `mailbox_items`：待领取物品
- `payouts`：卖家货款保障账本。正常在线成交会自动到账；离线、入账失败或管理员补偿时保留待领取记录
- `refunds`：待处理退款
- `transaction_journal`：状态变化日志
- `player_settings`：玩家市场偏好预留表

不要手工编辑运行中的数据库。修改或迁移前先执行 `/market admin backup` 并关闭服务器。

## 配置说明

完整默认配置位于 `src/main/resources/config.yml`，全部箱子页面位于独立的 `src/main/resources/gui.yml`。常用选项：

- `direct-trade.*`：面对面交易冷却、超时、倒计时和取消规则
- `market.default-max-listings`：默认有效上架数
- `market.max-listing-days`：最长上架天数
- `market.listing-fee`：上架费，默认 0
- `market.transaction-tax-percent`：成交税百分比，默认 0
- `limits.*`：价格、数量、名称和序列化大小限制
- `item-rules.*`：容器、自定义物品和黑名单
- `database.*`：数据库、WAL、超时和备份
- `messages.*`：中文消息
- `gui.yml -> pages.<页面>`：每个页面独立配置标题、填充材质和静态按钮
- GUI 按钮支持 `slot/material/name/lore/permission/action/commands`
- `commands` 支持 `[command]`、`[console]`、`[message]`、`[close]`；修改后使用 `/market admin reload`
- 动态商品和潜影盒图标仍使用真实 ItemStack，不会被配置替换或丢失物品数据

自定义命令按钮示例：

```yaml
pages:
  home:
    buttons:
      custom-help:
        enabled: true
        require-existing: false
        slot: 47
        material: COMMAND_BLOCK
        name: "&e帮助"
        permission: "crosstrade.use"
        commands:
          - "[message] &a你好，{player}"
          - "[command] market help"
        run-internal-action: false
```

`action` 用于调用 CrossTrade 原有按钮功能；仅增加命令时保留原 action 和默认的 `run-internal-action: true`。所有箱子页面固定为 54 格，防止动态商品槽位和交易安全区域因错误尺寸配置发生冲突。

1.x 的 `trade.request-timeout`、`trade.confirm-countdown`、`trade.sneak-to-trade` 和 `trade.allow-one-sided` 保留为兼容回退项。2.0 优先读取 `direct-trade`。

## 从 CrossTrade 1.x 升级

1. 关闭服务器。
2. 备份旧 JAR 和 `plugins/CrossTrade`。
3. 替换为 2.2.1 JAR。
4. 启动服务器；插件会保留旧值并补充新配置。
5. 检查 Vault、Floodgate 和 SQLite 启动日志。
6. 先用测试账号完成小额面对面交易、市场上架、购买、下架和邮箱领取。

从 2.0.x 升级时会自动把数据库升级到 schema 3。旧版已经返还过物品的下架、到期、管理员下架和售罄记录会标记为 `RETURNED`，后续删除记录不会重复返还。升级前仍建议备份 `plugins/CrossTrade/data.db`。

1.x 没有市场数据库，因此不存在旧市场数据迁移。已有面对面交易只在内存中，升级前必须确保没有进行中的交易。

## 构建

```powershell
mvn clean package
```

最终 JAR：`target/CrossTrade-2.2.1.jar`。SQLite JDBC 被打入最终 JAR；Paper、Vault 和 Floodgate API 保持 provided。

## 运维与异常复核

- `REVIEW` 表示外部经济操作和数据库状态之间出现无法自动判断的崩溃窗口。
- 不要直接把 `REVIEW` 改成成功或失败。
- 使用 `/market admin review` 获取编号，结合 Vault 账目、`transactions.log` 和数据库备份判断。
- 确认后可使用管理员物品或货款补偿命令处理。

## 已知边界

- Vault API 本身不提供与 SQLite 的分布式原子事务，因此插件通过写前状态、补偿、`REVIEW` 和不盲目重试降低重复扣款或重复支付风险。
- 不同 Vault 经济实现对离线玩家入账的支持可能不同；插件会把失败款项保留在可靠货款账本中。
- Floodgate 表单需要目标服务器安装的 Floodgate 提供 Cumulus；未安装时插件不会加载表单类。
- 市场商品名称用于展示，不会修改原物品名称。

## 许可证

CrossTrade 使用 MIT License 发布，详见 `LICENSE`。
