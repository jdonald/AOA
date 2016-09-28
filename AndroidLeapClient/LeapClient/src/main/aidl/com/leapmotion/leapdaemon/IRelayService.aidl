package com.leapmotion.leapdaemon;

interface IRelayService {
    void scanAccessories();
    boolean isIPCServerRunning();
    boolean isAccessoryPermitted();
    boolean isAccessoryOpen();
}
