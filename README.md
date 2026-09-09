# 零件入库管理

一个本地运行的 Android 机械零件入库管理应用。用于录入、查找、统计和管理螺丝、螺母、垫片等机械零件库存。

数据全部保存在手机本地 SQLite 数据库中，不需要网络，不需要登录。

## 功能

- 新增 / 编辑 / 删除零件记录
- 列表点击进入详情编辑页
- 可手动修改库存数量
- 拍照或从相册添加零件参考图片
- 多级筛选：大类 → 类型 → 规格 → 长度
- 关键词搜索，支持输入 `M3杯头` 这类组合词过滤
- 统计全部零件种类数、总数量以及当前筛选结果的种类数、总数量
- 导出全部数据为 CSV
- 从 CSV 导入数据，按“唯一编号”匹配，已存在则更新，不存在则新增

## 数据字段

| 字段 | 是否必填 | 说明 |
|---|---|---|
| 唯一编号 | 必填 | 零件“身份证”，不可重复，创建后不可修改 |
| 大类 | 选填 | 如：螺丝、螺母、垫片、轴承 |
| 类型 | 选填 | 如：平头螺丝、杯头螺丝、深沟球轴承 |
| 规格 | 选填 | 如：M2、M3、M4 |
| 长度 | 选填 | 如：6mm、10mm |
| 数量 | 选填 | 整数，默认 0 |
| 存放位置 | 选填 | 如：A区3号柜 |
| 备注 | 选填 | 如材质、表面处理 |
| 图片 | 选填 | 保存在本机，导出 CSV 时不包含图片 |

## 使用说明

1. 点击“新增零件”并填写信息。
2. 在列表中点击任意一条记录即可进入详情/编辑页。
3. 在编辑页可修改数量、位置、备注等信息，也可以删除该记录。
4. 使用搜索框输入关键词，或使用四个下拉框进行组合筛选。
5. 点击右上角菜单可导出 CSV 或导入 CSV 备份数据。

## 构建方式

### 方式一：GitHub Actions

仓库已包含 `.github/workflows/build-apk.yml`。

推送到 `main` 分支后会自动构建，或者在 GitHub 仓库的 Actions 页面手动触发 `Build Debug APK`。

构建完成后，在 Actions 运行记录的 “Artifacts” 中下载 `app-debug`，里面包含 `app-debug.apk`。

### 方式二：Android Studio

1. 克隆本项目到本地。
2. 使用 Android Studio 打开项目根目录。
3. 等待 Gradle 同步完成后，点击 `Build > Build Bundle(s) / APK(s) > Build APK(s)`。
4. 生成的 APK 位于：app/build/outputs/apk/debug/app-debug.apk


## 环境要求

- Android 8.0（API 26）及以上
- Android SDK 34
- JDK 17
- Gradle 8.7

## 技术栈

- Kotlin
- SQLite / SQLiteOpenHelper
- AndroidX
- Material Components
- GitHub Actions

## 项目结构
app/src/main/java/com/example/partmanager/
├── MainActivity.kt # 主界面：列表、搜索、筛选、统计、CSV 导入导出
├── EditPartActivity.kt # 新增/编辑零件：表单、拍照、相册、保存、删除
├── PartAdapter.kt # ListView 适配器
├── Part.kt # 零件数据模型
├── DatabaseHelper.kt # SQLite 数据库操作
└── CsvUtils.kt # CSV 导入导出

app/src/main/res/
├── layout/ # XML 布局
├── values/ # 颜色、主题、字符串
├── drawable/ # 圆角背景、图标资源
├── xml/ # FileProvider 路径配置
└── mipmap-anydpi-v26/ # 自适应应用图标


## 数据备份说明

- CSV 只保存文本字段，不包含图片。
- 导入 CSV 时按“唯一编号”匹配本地记录，已存在则更新，不存在则新增。
- 图片只保存当前手机 App 私有目录中，卸载或更换手机会丢失，需要手动备份。
