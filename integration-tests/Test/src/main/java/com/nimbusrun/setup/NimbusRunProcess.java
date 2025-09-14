package com.nimbusrun.setup;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class NimbusRunProcess implements AutoCloseable{
    private final Process process;
    private final Thread infoThread;
    private final Thread errorThread;

    public NimbusRunProcess(Process process, Thread infoThread, Thread errorThread) {
      this.process = process;
      this.infoThread = infoThread;
      this.errorThread = errorThread;
    }
    public boolean isAlive(){
      return process.isAlive();
    }
    @Override
    public void close() throws Exception {
      if(process.isAlive()) {
        process.destroyForcibly();
      }
      if(infoThread.isAlive()){
        infoThread.interrupt();
      }
      if(errorThread.isAlive()){
        errorThread.interrupt();
      }
    }
  }