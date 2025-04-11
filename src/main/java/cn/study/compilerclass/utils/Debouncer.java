package cn.study.compilerclass.utils;

import java.util.Timer;
import java.util.TimerTask;

public class Debouncer {

  private final long delay;
  private Timer timer;

  public Debouncer(long delay) {
    this.delay = delay;
  }

  public void debounce(Runnable runnable) {
    if (timer != null) {
      timer.cancel();
    }
    timer = new Timer();
    timer.schedule(new TimerTask() {
      @Override
      public void run() {
        runnable.run();
        timer = null;
      }
    }, delay);
  }

  public void debounceFX(Runnable runnable) {
    if (timer != null) {
      timer.cancel();
      timer.purge();  // 清理已取消的任务
    }
    timer = new Timer();
    timer.schedule(new TimerTask() {
      @Override
      public void run() {
        javafx.application.Platform.runLater(runnable);
        timer = null;
      }
    }, delay);
  }

  public void cancel() {
    if (timer != null) {
      timer.cancel(); // 取消所有任务
      timer.purge();  // 清理已取消的任务
      timer = null;   // 将引用置为 null，便于垃圾回收
    }
  }

}
