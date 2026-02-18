package com.flightchess.ui;

import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import com.flightchess.net.NetConfig;

/**
 * 主界面：输入昵称，选择创建房间或加入房间。
 */
public class MainWindow extends JFrame {

    private final JTextField nicknameField = new JTextField("Player", 12);
    private final JTextField hostField = new JTextField("127.0.0.1", 12);
    private final JTextField portField = new JTextField(String.valueOf(NetConfig.DEFAULT_PORT), 6);

    private final UiController controller;

    public MainWindow() {
        super("Flight Chess - Main");
        this.controller = new UiController(this);
        initUi();
    }

    private void initUi() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JPanel center = new JPanel(new FlowLayout());
        center.add(new JLabel("昵称:"));
        center.add(nicknameField);

        center.add(new JLabel("房主IP:"));
        center.add(hostField);

        center.add(new JLabel("端口:"));
        center.add(portField);

        add(center, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout());
        JButton createBtn = new JButton("创建房间（本机做房主）");
        JButton joinBtn = new JButton("加入房间");

        bottom.add(createBtn);
        bottom.add(joinBtn);

        add(bottom, BorderLayout.SOUTH);

        createBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String nickname = nicknameField.getText().trim();
                controller.createAndHostRoom(nickname);
            }
        });

        joinBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String nickname = nicknameField.getText().trim();
                String host = hostField.getText().trim();
                int port = Integer.parseInt(portField.getText().trim());
                controller.joinRoom(host, port, nickname);
            }
        });

        pack();
        setLocationRelativeTo(null);
    }

    public static void launchUi() {
        EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                MainWindow win = new MainWindow();
                win.setVisible(true);
            }
        });
    }
}

