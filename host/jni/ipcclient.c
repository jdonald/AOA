#include <sys/socket.h>
#include <sys/un.h>
#include <string.h>
#include "ipcclient.h"

static const char ipc_addr[] = "/data/data/com.leapmotion.leapdaemon/Leap Service";

int ipcclient_connect() {
  struct sockaddr_un addr;

  int fd = socket(PF_LOCAL, SOCK_STREAM, 0);
  if (fd < 0) {
    perror("client socket()");
    return -1;
  }

  memset(&addr, 0, sizeof(addr));
  addr.sun_family = PF_LOCAL;
  strncpy(addr.sun_path, ipc_addr, sizeof(addr.sun_path)-1);

  if (connect(fd, (struct sockaddr*)&addr, sizeof(addr))) {
    perror("client connect()");
    close(fd);
    return -1;
  }

  const int so_size = 262144;
  setsockopt(fd, SOL_SOCKET, SO_SNDBUF, &so_size, sizeof(so_size));
  const struct linger so_linger = { 1, 1 };
  setsockopt(fd, SOL_SOCKET, SO_LINGER, &so_linger, sizeof(so_linger));

  return fd;
}

int ipcclient_write(int fd, const void* pBuf, size_t nBytes) {
  return (nBytes == send(fd, pBuf, nBytes, MSG_NOSIGNAL));
}

size_t ipcclient_read(int fd, void* pBuf, size_t nBytes) {
  return recv(fd, pBuf, nBytes, MSG_NOSIGNAL);
}

/*
int main(int argc, char**argv) {
  int fd = ipcclient_connect();
  char message[] = "hello hello";
  int rt = ipcclient_write(fd, (const void*)message, sizeof(message));
  if (rt < 0) {
    perror("client write()");
    return -1;
  }
  return 0; 
}
*/
