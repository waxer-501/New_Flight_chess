/**
 * New_Flight_chess 一键启动器
 * 自动编译并运行 Java 模块，需要 JDK 17 或更高版本
 */
#include <windows.h>
#include <string>
#include <cstdlib>
#include <cstdio>
#include <cstring>

static const int REQUIRED_JAVA_MAJOR = 17;

static std::string getProjectRoot() {
    char buf[MAX_PATH];
    if (GetModuleFileNameA(NULL, buf, MAX_PATH) == 0)
        return "";
    std::string path(buf);
    size_t pos = path.find_last_of("\\/");
    std::string exeDir = (pos == std::string::npos) ? path : path.substr(0, pos);
    // 若 exe 在 launcher 子目录，则项目根为上一级
    size_t lastSep = exeDir.find_last_of("\\/");
    if (lastSep != std::string::npos) {
        std::string parentName = exeDir.substr(lastSep + 1);
        if (parentName == "launcher")
            return exeDir.substr(0, lastSep);
    }
    return exeDir;
}

/** 解析 java -version 输出，返回主版本号（如 17、21），失败返回 0 */
static int getJavaMajorVersion() {
    FILE* f = _popen("java -version 2>&1", "r");
    if (!f) return 0;
    char line[256];
    int major = 0;
    if (fgets(line, sizeof(line), f)) {
        const char* p = line;
        while (*p && *p != '"') p++;
        if (*p == '"') p++;
        if (strncmp(p, "1.", 2) == 0) {
            int minor = atoi(p + 2);
            major = (minor == 8) ? 8 : (minor < 8 ? minor : 9);
        } else {
            major = atoi(p);
        }
    }
    _pclose(f);
    return major;
}

int main() {
    int javaVer = getJavaMajorVersion();
    if (javaVer > 0 && javaVer < REQUIRED_JAVA_MAJOR) {
        printf("Java version too old. This project requires JDK %d or later.\n", REQUIRED_JAVA_MAJOR);
        printf("Current detected major version: %d\n", javaVer);
        printf("\nPlease install JDK %d+ from:\n  https://adoptium.net/  or  https://www.oracle.com/java/technologies/downloads/\n", REQUIRED_JAVA_MAJOR);
        system("pause");
        return 1;
    }
    if (javaVer == 0) {
        printf("Java not found or 'java' is not in PATH.\n");
        printf("Please install JDK %d or later and add its bin directory to PATH.\n", REQUIRED_JAVA_MAJOR);
        printf("Download: https://adoptium.net/  or  https://www.oracle.com/java/technologies/downloads/\n");
        system("pause");
        return 1;
    }

    std::string projectDir = getProjectRoot();
    if (projectDir.empty()) {
        printf("Error: Cannot get launcher path.\n");
        system("pause");
        return 1;
    }

    if (SetCurrentDirectoryA(projectDir.c_str()) == 0) {
        printf("Error: Cannot change to project directory: %s\n", projectDir.c_str());
        system("pause");
        return 1;
    }

    printf("Project directory: %s\n", projectDir.c_str());
    printf("Compiling Java sources...\n\n");

    int ret = system(
        "javac -encoding UTF-8 -d out "
        "src\\module-info.java "
        "src\\com\\flightchess\\app\\*.java "
        "src\\com\\flightchess\\core\\*.java "
        "src\\com\\flightchess\\net\\*.java "
        "src\\com\\flightchess\\ui\\*.java"
    );

    if (ret != 0) {
        printf("\nCompilation failed (exit code %d).\n", ret);
        system("pause");
        return ret;
    }

    printf("\nCompilation succeeded. Starting application...\n\n");
    ret = system("java -p out -m New_Flight_chess/com.flightchess.app.Launcher");

    if (ret != 0) {
        printf("\nApplication exited with code %d.\n", ret);
        system("pause");
    }
    return ret;
}
