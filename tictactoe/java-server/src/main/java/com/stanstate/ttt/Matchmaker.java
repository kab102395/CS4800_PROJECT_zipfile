package com.stanstate.ttt;
import com.google.gson.JsonObject;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Consumer;
public class Matchmaker {
  private final ScheduledExecutorService sched;
  private final ConcurrentLinkedQueue<ClientSession> tttWait = new ConcurrentLinkedQueue<>();
  private final Map<String, Room> rooms = new ConcurrentHashMap<>();
  public Matchmaker(ScheduledExecutorService s){ this.sched=s; }
  public void requestJoin(ClientSession s, String game){
    if (!"ttt".equals(game)) {
      var err = new JsonObject(); err.addProperty("t","error"); err.addProperty("code","UNSUPPORTED_GAME"); err.addProperty("msg","Only 'ttt'");
      s.send(err); return;
    }
    ClientSession other = tttWait.poll();
    if (other == null) {
      tttWait.add(s);
      var waiting = new JsonObject(); waiting.addProperty("t","waiting"); s.send(waiting);
    } else {
      String id = "M-" + UUID.randomUUID();
      TttRoom room = new TttRoom(id, sched);
      rooms.put(id, room);
      room.addPlayer(other,1);
      room.addPlayer(s,2);
      room.start();
    }
  }
  public void routeToRoom(String id, Consumer<Room> fn){ var r=rooms.get(id); if(r!=null) fn.accept(r); }
  public void onDisconnect(ClientSession s){
    if (s == null) return;
    if (s.matchId != null) { var r=rooms.get(s.matchId); if (r!=null) r.onLeave(s); }
    else { tttWait.remove(s); }
  }
}