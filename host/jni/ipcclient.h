#ifndef IPCCLIENT_H_
#define IPCCLIENT_H_

int ipcclient_connect();
int ipcclient_write(int fd, const void* pBuf, size_t nBytes);
int ipcclient_read(int fd, void* pBuf, size_t nBytes);

#endif // IPCCLIENT_H_
