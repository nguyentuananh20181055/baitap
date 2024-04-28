#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <string.h>
#include <sys/types.h>
#include <sys/wait.h>

#define MAX_CHILDREN 10 // Số lượng tiến trình con tối đa

void handle_client(int client_socket) {
    char buf[256];
    int ret = recv(client_socket, buf, sizeof(buf), 0);
    buf[ret] = '\0';
    printf("Received from client: %s\n", buf);

    // Gửi phản hồi cho client
    char *msg = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n<html><body><h1>Xin chao cac ban</h1></body></html>";
    send(client_socket, msg, strlen(msg), 0);

    // Đóng kết nối
    close(client_socket);
}

int main() {
    int listener, client;
    struct sockaddr_in servaddr;
    pid_t child_pids[MAX_CHILDREN];
    int num_children = 0;

    // Tạo socket
    listener = socket(AF_INET, SOCK_STREAM, 0);
    if (listener < 0) {
        perror("Socket creation failed");
        exit(EXIT_FAILURE);
    }

    // Khởi tạo địa chỉ server
    servaddr.sin_family = AF_INET;
    servaddr.sin_addr.s_addr = INADDR_ANY;
    servaddr.sin_port = htons(8080);

    // Bind socket tới địa chỉ và cổng
    if (bind(listener, (struct sockaddr *)&servaddr, sizeof(servaddr)) < 0) {
        perror("Bind failed");
        exit(EXIT_FAILURE);
    }

    // Lắng nghe các kết nối đến
    listen(listener, 10);

    // Preforking: tạo các tiến trình con
    while (1) {
        if (num_children < MAX_CHILDREN) {
            // Tạo tiến trình con mới
            pid_t pid = fork();

            if (pid == 0) {
                // Tiến trình con
                while (1) {
                    client = accept(listener, NULL, NULL);
                    printf("New client connected: %d\n", client);
                    handle_client(client);
                }
            } else if (pid > 0) {
                // Tiến trình cha
                child_pids[num_children++] = pid;
            } else {
                perror("Fork failed");
                exit(EXIT_FAILURE);
            }
        }

        // Quản lý các tiến trình con đã hoàn thành
        for (int i = 0; i < num_children; i++) {
            pid_t finished_pid = waitpid(child_pids[i], NULL, WNOHANG);
            if (finished_pid > 0) {
                // Tiến trình con đã kết thúc, tạo lại một tiến trình con mới
                pid_t new_pid = fork();
                if (new_pid == 0) {
                    // Tiến trình con mới
                    while (1) {
                        client = accept(listener, NULL, NULL);
                        printf("New client connected: %d\n", client);
                        handle_client(client);
                    }
                } else if (new_pid > 0) {
                    // Tiến trình cha
                    child_pids[i] = new_pid;
                } else {
                    perror("Fork failed");
                    exit(EXIT_FAILURE);
                }
            }
        }
    }

    // Đóng socket lắng nghe
    close(listener);

    return 0;
}