package com.stanstate.ttt;
import com.google.gson.JsonObject;
import org.java_websocket.WebSocket;
public class ClientSession {
  public final WebSocket conn;
  public String userId;
  public String matchId;
  public int seat;
  public ClientSession(WebSocket c){ this.conn=c; }
  public void send(JsonObject obj){ 
    System.out.println("Sending to client: " + obj.toString());
    conn.send(obj.toString()); 
  }
}