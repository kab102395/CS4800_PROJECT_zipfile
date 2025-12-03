package com.stanstate.ttt;
import com.google.gson.JsonObject;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
public class TttRoom extends Room {
  private ClientSession p1, p2;
  private int xMask=0, oMask=0; private boolean xTurn=true;
  private ScheduledFuture<?> timer;
  private static final int[] WINS = {
    0b111000000,0b000111000,0b000000111,
    0b100100100,0b010010010,0b001001001,
    0b100010001,0b001010100
  };
  public TttRoom(String id, ScheduledExecutorService s){ super(id,s); }
  public void addPlayer(ClientSession s, int seat){ s.matchId=id; s.seat=seat; if(seat==1)p1=s; else p2=s; }
  @Override public void start(){
    var m1=new JsonObject(); m1.addProperty("t","match"); m1.addProperty("match",id); m1.addProperty("seat",1); p1.send(m1);
    var m2=new JsonObject(); m2.addProperty("t","match"); m2.addProperty("match",id); m2.addProperty("seat",2); p2.send(m2);
    broadcastState("ongoing"); scheduleTimer();
  }
  @Override public void onMove(ClientSession s, int cell){
    sched.execute(() -> {
      System.out.println("MOVE: Player " + (s==p1?"X":"O") + " attempting move at cell " + cell + " in match " + id);
      boolean isX = (s==p1);
      if (xTurn != isX) {
        System.out.println("MOVE REJECTED: Not " + (isX?"X":"O") + "'s turn (current turn: " + (xTurn?"X":"O") + ")");
        return;
      }
      if (cell<0||cell>8) {
        System.out.println("MOVE REJECTED: Invalid cell " + cell);
        return;
      }
      int bit = 1 << (8 - cell);
      if (((xMask|oMask)&bit)!=0) {
        System.out.println("MOVE REJECTED: Cell " + cell + " already occupied");
        return;
      }
      if (isX) xMask|=bit; else oMask|=bit;
      xTurn=!xTurn;
      System.out.println("MOVE ACCEPTED: Cell " + cell + " marked by " + (isX?"X":"O") + ", next turn: " + (xTurn?"X":"O"));
      System.out.println("BOARD STATE: " + boardString());
      String res = result();
      System.out.println("GAME RESULT: " + res);
      if (!"ongoing".equals(res)){ 
        System.out.println("GAME ENDING: " + res);
        cancelTimer(); 
        broadcastOver(res); 
      } else { 
        System.out.println("GAME CONTINUING: Scheduling new timer");
        scheduleTimer(); 
        broadcastState(res); 
      }
    });
  }
  @Override public void onLeave(ClientSession s){
    sched.execute(() -> { cancelTimer(); String winner=(s==p1)?"O":"X"; broadcastOver("forfeit:"+winner); });
  }
  private String result(){
    // Check for X wins
    for(int w:WINS) if((xMask&w)==w) return "X";
    // Check for O wins  
    for(int w:WINS) if((oMask&w)==w) return "O";
    // Check for draw
    if ((xMask|oMask)==0b111111111) return "draw";
    return "ongoing";
  }
  private void scheduleTimer(){ 
    cancelTimer(); 
    System.out.println("TIMER: Scheduling 120-second forfeit timer for match " + id + ", current turn: " + (xTurn ? "X" : "O"));
    timer=sched.schedule(() -> {
      System.out.println("TIMER EXPIRED: 120 seconds elapsed for match " + id + ", declaring forfeit for " + (xTurn ? "X" : "O"));
      String winner = xTurn?"O":"X"; 
      broadcastOver("forfeit:"+winner);
    }, 120, TimeUnit.SECONDS); 
  }
  private void cancelTimer(){ 
    if (timer!=null) {
      System.out.println("TIMER: Canceling forfeit timer for match " + id);
      timer.cancel(false); 
    }
  }
  private void broadcastState(String res){
    var st=new JsonObject(); st.addProperty("t","state"); st.addProperty("match",id);
    st.addProperty("board", boardString()); st.addProperty("next", xTurn?"X":"O"); st.addProperty("result",res);
    p1.send(st); p2.send(st);
  }
  private void broadcastOver(String res){
    var over=new JsonObject(); over.addProperty("t","over"); over.addProperty("match",id); over.addProperty("result",res);
    p1.send(over); p2.send(over);
  }
  private String boardString(){
    StringBuilder sb=new StringBuilder(9);
    for (int i=8;i>=0;i--){
      int bit=1<<i;
      if ((xMask&bit)!=0) sb.append('X');
      else if ((oMask&bit)!=0) sb.append('O');
      else sb.append('.');
    }
    return sb.toString();
  }
}