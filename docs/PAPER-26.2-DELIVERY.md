# CrossTrade 2.2.1 Paper 26.2 交付说明

## 构建基线

- 插件：CrossTrade 2.2.1
- 编译 API：Paper `26.2.build.53-alpha`
- 运行验证：CrossTrade 2.2.0 已在 Paper 26.2 目标服务器内实际测试通过；2.2.1 运行逻辑不变，补充 SpigotMC 文档、许可证和发布元数据
- 自动验证：Maven clean package 成功，9 项测试通过，JAR 结构检查通过
- Java：25
- SQLite JDBC：3.51.3.0（打入 JAR）
- Floodgate API：2.2.3-SNAPSHOT（软依赖）
- VaultAPI：1.7（软依赖）

## 交付物

- `target/CrossTrade-2.2.1.jar`
- `src/main/java` 完整源码
- `src/main/resources/config.yml`
- `src/main/resources/gui.yml`
- `src/main/resources/plugin.yml`
- `README.md`
- `LICENSE`
- `docs/TEST-REPORT.md`

## 安装提醒

生产安装前关闭服务器并备份旧 CrossTrade 数据目录。首次启动后确认控制台出现 SQLite 和 ItemStack 编解码自检成功信息。

若没有 Vault，市场可浏览但不能成交。若没有 Floodgate，Java 功能仍正常，基岩表单不会加载。
