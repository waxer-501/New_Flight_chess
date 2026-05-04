# 一键启动器
#需要在 https://www.radmin-vpn.com 下载安装redmin vpn来联网
克隆仓库后可直接使用，**无需本地安装 C++ 或重新编译**：

- **方式一**：双击项目根目录下的 `Launcher.exe`
- **方式二**：双击本目录下的 `Launcher.exe`

启动器会自动执行 `javac` 编译并运行游戏。  
**环境要求**：本机已安装 **JDK 17 或更高版本**，且 `javac`、`java` 在系统 PATH 中。

---

### 如果提示“Java 版本过旧”或“找不到 Java”

1. **安装 JDK 17+**  
   - [Adoptium (Eclipse Temurin)](https://adoptium.net/)（推荐）  
   - [Oracle JDK](https://www.oracle.com/java/technologies/downloads/)  
   安装时勾选“设置 JAVA_HOME”或“添加到 PATH”。
2. **确认版本**：打开命令提示符，输入 `java -version` 和 `javac -version`，应显示 17 或更高。
3. **PATH**：若已安装仍提示找不到，请把 JDK 的 `bin` 目录（如 `C:\Program Files\Eclipse Adoptium\jdk-17.x.x\bin`）加入系统环境变量 PATH。

---

若仓库中未包含 `Launcher.exe`，可在此目录运行 `build_launcher.bat` 自行编译（需安装 g++，如 MinGW-w64）。

---

## 调试模式

对局中按 **G 键** 可直接输入骰子值（1~6），跳过随机掷骰，方便测试特定步数场景。

**开启方式：**

1. 打开 `src/com/flightchess/ui/GameWindow.java`
2. 找到第 23 行，将 `DEBUG_MODE` 设为 `true`：
   ```java
   private static final boolean DEBUG_MODE = false;  // 改为 true
   ```
3. 重新编译运行

**关闭方式：** 将 `DEBUG_MODE` 改回 `false` 后重新编译。发布时默认为关闭状态。
