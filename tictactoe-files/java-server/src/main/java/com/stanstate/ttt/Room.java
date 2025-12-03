package com.stanstate.ttt;
import com.google.gson.JsonObject;
import java.util.concurrent.ScheduledExecutorService;
public abstract class Room {
  protected final String id;
  protected final ScheduledExecutorService sched;
  protected Room(String id, ScheduledExecutorService s){ this.id=id; this.sched=s; }
  public abstract void start();
  public abstract void onMove(ClientSession s, int cell);
  public abstract void onLeave(ClientSession s);
  protected void send(ClientSession s, JsonObject m){ s.send(m); }
}