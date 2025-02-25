package webserver;

import db.MemoryUserRepository;
import db.Repository;
import http.util.IOUtils;
import model.User;

import javax.swing.text.Document;
import javax.swing.text.Element;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static http.util.HttpRequestUtils.parseQueryParameter;

public class RequestHandler implements Runnable {
    Socket connection;
    private static final Logger log = Logger.getLogger(RequestHandler.class.getName());
    private static final String ROOT_URL = "./webapp";
    private static final String HOME_URL = "/index.html";
    private static final String LOGIN_FAILED_URL = "/user/login_failed.html";
    private static final String LOGIN_URL = "/user/login.html";
    private static final String LIST_URL = "/user/list.html";


    private final Repository repository;
    private static final String HOME_URL = "/index.html";
    
    public RequestHandler(Socket connection) {
        this.connection = connection;
        repository =  MemoryUserRepository.getInstance();
    }

    @Override
    public void run() {
        log.log(Level.INFO, "New Client Connect! Connected IP : " + connection.getInetAddress() + ", Port : " + connection.getPort());
        try (InputStream in = connection.getInputStream(); OutputStream out = connection.getOutputStream()) {
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            DataOutputStream dos = new DataOutputStream(out);

            byte[] body = new byte[0];

//            // Header 분석
//            String startLine = br.readLine();
//            String[] startLines = startLine.split(" ");
//            String method = startLines[0];
//            String url = startLines[1];

            int requestContentLength = 0;
            String cookie = "";

            while (true) {
                final String line = br.readLine();
                if (line.equals("")) {
                    break;
                }
                // header info
                if (line.startsWith("Content-Length")) {
                    requestContentLength = Integer.parseInt(line.split(": ")[1]);
                }

                if (line.startsWith("Cookie")) {
                    cookie = line.split(": ")[1];
                }
            }

//            //요구사항 1
            String url = br.readLine();
            String[] info = url.split(" ");
            String method1 = info[0];

            System.out.println("info[1]"+info[1]);
            String filePath = info[1];

            if (url != null) {
                System.out.println("url : "+url);
            }

            if (filePath.equals("/index.html")) {
                try {
                    body = Files.readAllBytes(Paths.get("webapp" + filePath));
                    String content = new String(body, StandardCharsets.UTF_8);
                    System.out.println(content);
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
            if (filePath.equals("/")) {
                try {
                    body = Files.readAllBytes(Paths.get("webapp" + filePath));
                    String content = new String(body, StandardCharsets.UTF_8);
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }

            //요구사항 2-1 화면에 form.html 띄우기
            if (filePath.equals("/user/form.html")) {
                try {
                    body = Files.readAllBytes(Paths.get("webapp" + filePath));
                    String content = new String(body, StandardCharsets.UTF_8);
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }

//            //요구사항 2-2: form에 적은 값 가져오기
            if (filePath.equals("/user/signup")) {
                String fileName = info[1];
                String SignUpInfoURL=info[1].split("\\?")[1];
                String[] SignUpInfos=SignUpInfoURL.split("&");
                String[] SignUpInfoValues=new String[4];
                for(int i=0;i<4;i++) {
                    SignUpInfoValues[i]=SignUpInfos[i].split("=")[1];
                }
                User user1=new User(SignUpInfoValues[0],SignUpInfoValues[1],SignUpInfoValues[2],SignUpInfoValues[3]);
                repository.addUser(user1);
                log.log(Level.INFO,"user1 name:"+repository.findUserById("137pjy").getName());
                response302Header(dos,HOME_URL);

                return;
            }


            // 요구 사항 2,3,4번
            if (url.equals("/user/signup")) {
                String queryString = IOUtils.readData(br, requestContentLength);
                Map<String, String> queryParameter = parseQueryParameter(queryString);
                User user = new User(queryParameter.get("userId"), queryParameter.get("password"), queryParameter.get("name"), queryParameter.get("email"));
                repository.addUser(user);
                response302Header(dos,HOME_URL);
                return;
            }

            // 요구 사항 5번
            if (url.equals("/user/login")) {
                String queryString = IOUtils.readData(br, requestContentLength);
                Map<String, String> queryParameter = parseQueryParameter(queryString);
                User user = repository.findUserById(queryParameter.get("userId"));
                login(dos, queryParameter, user);
                return;
            }

            // 요구 사항 6번
            if (url.equals("/user/userList")) {
                if (!cookie.equals("logined=true")) {
                    response302Header(dos,LOGIN_URL);
                    return;
                }
                body = Files.readAllBytes(Paths.get(ROOT_URL + LIST_URL));
            }

            response200Header(dos,body.length);
            responseBody(dos, body);

        } catch (IOException e) {
            log.log(Level.SEVERE, e.getMessage());
        }

    }


    private void login(DataOutputStream dos, Map<String, String> queryParameter, User user) {
        if (user != null && user.getPassword().equals(queryParameter.get("password"))) {
            response302HeaderWithCookie(dos,HOME_URL);
            return;
        }
        response302Header(dos,LOGIN_FAILED_URL);
    }

    private void response302HeaderWithCookie(DataOutputStream dos, String path) {
        try {
            dos.writeBytes("HTTP/1.1 302 Redirect \r\n");
            dos.writeBytes("Location: " + path + "\r\n");
            dos.writeBytes("Set-Cookie: logined=true" + "\r\n");

            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.log(Level.SEVERE, e.getMessage());
        }
    }

    private void response200Header(DataOutputStream dos, int lengthOfBodyContent) {
        try {
            dos.writeBytes("HTTP/1.1 200 OK \r\n");
            dos.writeBytes("Content-Type: text/html;charset=utf-8\r\n");
            dos.writeBytes("Content-Length: " + lengthOfBodyContent + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.log(Level.SEVERE, e.getMessage());
        }
    }

    private void response302Header(DataOutputStream dos, String path) {
        try {
            log.log(Level.INFO,"response302Header enter!!");
            dos.writeBytes("HTTP/1.1 302 Redirect \r\n");
            dos.writeBytes("Location: " + path + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.log(Level.SEVERE, e.getMessage());
        }
    }

    private void responseBody(DataOutputStream dos, byte[] body) {
        try {
            dos.write(body, 0, body.length);
            dos.flush();
        } catch (IOException e) {
            log.log(Level.SEVERE, e.getMessage());
        }
    }

}
