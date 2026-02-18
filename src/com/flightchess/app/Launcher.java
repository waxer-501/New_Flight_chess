package com.flightchess.app;

import com.flightchess.ui.MainWindow;

/**
 * 程序入口。
 *
 * 仅依赖 JDK 标准库。
 * 示例命令行（在工程根目录执行）：
 *   javac -d out src/module-info.java src/com/flightchess/app/*.java src/com/flightchess/core/*.java src/com/flightchess/net/*.java src/com/flightchess/ui/*.java
 *   java -p out -m New_Flight_chess/com.flightchess.app.Launcher
 */
public class Launcher {

    public static void main(String[] args) {
        // 启动 Swing 主界面。
        MainWindow.launchUi();
    }
}

